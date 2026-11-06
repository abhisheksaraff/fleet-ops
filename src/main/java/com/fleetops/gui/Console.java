package com.fleetops.gui;

import com.fleetops.app.AppContext;
import com.fleetops.cli.FleetOpsCli;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import picocli.CommandLine;

/**
 * The terminal at the bottom of the window. It shows the FLEETOPS banner, streams what happens in the
 * simulation, and runs the real command-line commands ({@code scan}, {@code incidents}, {@code show},
 * {@code run}, {@code reset}, {@code config}) against the same database the window uses, so a command
 * typed here and a button clicked there always agree.
 *
 * <p>Commands run on a background thread so the window stays responsive. While one runs, whatever is
 * typed is handed to it as the answer to its question (for example {@code y} to "Launch this mission?").
 */
final class Console extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String PROMPT = "fleetops ❯ ";
    private static final int MAX_LINES = 2500;
    private static final int MAX_HISTORY = 200;
    private static final float FONT_SIZE = 13f;
    private static final List<String> BUILT_INS = List.of("help", "clear", "exit");

    /** What the console needs from the window around it. */
    interface Host {
        AppContext app();

        boolean busy();

        /** True while a mission started from the window is running on the map. */
        boolean missionRunning();

        void setBusy(boolean busy, String message);

        /** Called on the event thread after a command ends, so the window can refresh what it shows. */
        void commandFinished(String command, int exitCode);

        List<Long> incidentIds();

        /** Hides the console (the {@code exit} command). */
        void hide();
    }

    private final transient Host host;
    private final JTextPane pane = new JTextPane() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean getScrollableTracksViewportWidth() {
            // no wrapping, so the banner never breaks; the background still fills a wide window
            return getUI().getPreferredSize(this).width <= getParent().getSize().width;
        }
    };
    private final JTextField input = new JTextField();
    private final JLabel prompt = new JLabel(PROMPT);
    private final transient StyledDocument doc = pane.getStyledDocument();
    private final transient Map<String, SimpleAttributeSet> styles = new HashMap<>();
    private final transient List<String> history = new ArrayList<>();
    private int historyIndex;
    private String draft = "";
    private String lastTab = "";

    private final transient LineFeed feed = new LineFeed();
    private final transient CommandLine cli;
    private final transient ConsoleCompleter completer;
    private final transient ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fleetops-console");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running;

    Console(Host host) {
        super(new BorderLayout());
        this.host = host;
        setBackground(Theme.CONSOLE_BG);

        cli = FleetOpsCli.commandLine(host::app, feed);
        cli.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));
        cli.setOut(new PrintWriter(new Sink(false), true));
        cli.setErr(new PrintWriter(new Sink(true), true));
        completer = new ConsoleCompleter(cli, BUILT_INS, host::incidentIds);

        pane.setEditable(false);
        pane.setBackground(Theme.CONSOLE_BG);
        pane.setFont(Theme.mono(FONT_SIZE));
        pane.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        pane.setCaretColor(Theme.CONSOLE_BG);
        ((javax.swing.text.DefaultCaret) pane.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        pane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (pane.getSelectedText() == null) {
                    input.requestFocusInWindow();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.CONSOLE_BG);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);

        prompt.setFont(Theme.mono(FONT_SIZE).deriveFont(Font.BOLD));
        prompt.setForeground(Theme.ACCENT);
        input.setFont(Theme.mono(FONT_SIZE));
        input.setForeground(Theme.TEXT);
        input.setCaretColor(Theme.ACCENT);
        input.setOpaque(true);
        input.setBackground(new Color(0x0F1215));
        input.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        input.setFocusTraversalKeysEnabled(false);
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(new Color(0x0F1215));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        row.add(prompt, BorderLayout.WEST);
        row.add(input, BorderLayout.CENTER);
        add(row, BorderLayout.SOUTH);
        setMinimumSize(new Dimension(0, 0));

        wireKeys();
        printBanner();
    }

    // ---- public surface -------------------------------------------------------------------------

    /** Shows a line of simulation news. May be called from any thread. */
    void event(String line) {
        write(line + "\n", false);
    }

    /** Runs a line as if it had been typed. Must be called on the event thread. */
    void submit(String line) {
        String text = line.strip();
        if (running) {
            echoAnswer(text);
            feed.offer(text);
            return;
        }
        println(PROMPT, Theme.ACCENT, true, false);
        print(text + "\n", Theme.TEXT, true);
        if (text.isEmpty()) {
            return;
        }
        if (history.isEmpty() || !history.get(history.size() - 1).equals(text)) {
            history.add(text);
            if (history.size() > MAX_HISTORY) {
                history.remove(0);
            }
        }
        historyIndex = history.size();
        List<String> args = tokenize(text);
        if (args.isEmpty()) {
            return;
        }
        String name = args.get(0).toLowerCase(Locale.ROOT);
        switch (name) {
            case "help", "?" -> help();
            case "clear", "cls" -> clear();
            case "exit", "quit" -> host.hide();
            default -> execute(name, args);
        }
    }

    /** Everything printed so far, without colours; used by tests. */
    String plainText() {
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    boolean isRunning() {
        return running;
    }

    ConsoleCompleter.Result complete(String line) {
        return completer.complete(line);
    }

    JTextField inputField() {
        return input;
    }

    /** Empties the screen but keeps the history. */
    void clear() {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ignored) {
            // nothing to clear
        }
    }

    // ---- commands -------------------------------------------------------------------------------

    private void execute(String name, List<String> args) {
        if (cli.getSubcommands().get(name) == null && !name.startsWith("-")) {
            println("Unknown command '" + name + "'. Type help to see what is available.", Theme.DANGER, false, true);
            return;
        }
        if (host.busy()) {
            println("The window is busy; try again when it has finished.", Theme.WARN, false, true);
            return;
        }
        if (host.missionRunning() && (name.equals("run") || name.equals("reset"))) {
            println("A mission is running on the map. Abort it (or wait for it to end) before using '" + name + "'.",
                    Theme.WARN, false, true);
            return;
        }
        running = true;
        updatePrompt();
        host.setBusy(true, "Running '" + String.join(" ", args) + "'...");
        worker.submit(() -> {
            int code;
            try {
                code = cli.execute(args.toArray(new String[0]));
            } catch (RuntimeException e) {
                write("Error: " + e.getMessage() + "\n", true);
                code = 4;
            }
            cli.getOut().flush();
            cli.getErr().flush();
            int exit = code;
            SwingUtilities.invokeLater(() -> {
                feed.clear();
                running = false;
                updatePrompt();
                host.setBusy(false, " ");
                if (exit != 0) {
                    println("exit code " + exit, Theme.MUTED, false, true);
                }
                host.commandFinished(name, exit);
            });
        });
    }

    private void help() {
        String[][] rows = {
            {"scan [-t TYPE] [-v]", "read the news, let the AI verify it, store new incidents"},
            {"incidents", "list the stored incidents"},
            {"show <id>", "everything about one incident"},
            {"run <id> [-y] [--progress]", "plan and simulate a mission here (it is not drawn on the map)"},
            {"reset [-f] [-y]", "delete stored incidents"},
            {"config", "show the active configuration"},
            {"clear", "clear this screen"},
            {"exit", "hide the console"},
        };
        println("Commands", Theme.MUTED, true, true);
        for (String[] row : rows) {
            print(String.format(Locale.ROOT, "  %-30s", row[0]), Theme.ACCENT, false);
            print(row[1] + "\n", Theme.TEXT, false);
        }
        println("Add --help to any command for its options.  Up/Down: history   Tab: complete   Ctrl+L: clear",
                Theme.MUTED, false, true);
    }

    private void printBanner() {
        List<String> rows = ConsoleStyle.banner();
        for (int i = 0; i < rows.size(); i++) {
            println(rows.get(i), ConsoleStyle.bannerColor(i), false, true);
        }
        println("", Theme.TEXT, false, true);
        println("mission control for heterogeneous rescue fleets", Theme.TEXT, false, true);
        AppContext app = host.app();
        println(String.format(Locale.ROOT, "db %s  ·  ai %s  ·  news %s  ·  map %s",
                app.config().dbUrl(), app.config().llmProvider().name().toLowerCase(Locale.ROOT),
                app.config().newsSources().stream().map(s -> s.name().toLowerCase(Locale.ROOT)).toList(),
                app.config().mapTiles()), Theme.MUTED, false, true);
        println("type help for commands, Tab completes, Up/Down walks the history", Theme.MUTED, false, true);
        println("", Theme.TEXT, false, true);
    }

    // ---- input ----------------------------------------------------------------------------------

    private void wireKeys() {
        input.addActionListener(e -> {
            String text = input.getText();
            input.setText("");
            draft = "";
            lastTab = "";
            submit(text);
        });
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP -> {
                        walkHistory(-1);
                        e.consume();
                    }
                    case KeyEvent.VK_DOWN -> {
                        walkHistory(1);
                        e.consume();
                    }
                    case KeyEvent.VK_TAB -> {
                        tab();
                        e.consume();
                    }
                    default -> lastTab = "";
                }
            }
        });
        input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK), "clear");
        input.getActionMap().put("clear", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clear();
            }
        });
    }

    private void walkHistory(int step) {
        if (history.isEmpty()) {
            return;
        }
        if (historyIndex == history.size()) {
            draft = input.getText();
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + step));
        input.setText(historyIndex == history.size() ? draft : history.get(historyIndex));
        input.setCaretPosition(input.getText().length());
    }

    private void tab() {
        if (running) {
            return;
        }
        ConsoleCompleter.Result result = completer.complete(input.getText());
        if (!result.line().equals(input.getText())) {
            input.setText(result.line());
            input.setCaretPosition(result.line().length());
            lastTab = "";
        } else if (!result.candidates().isEmpty() && lastTab.equals(input.getText())) {
            println(String.join("   ", result.candidates()), Theme.MUTED, false, true);
        } else {
            lastTab = input.getText();
        }
    }

    private void updatePrompt() {
        prompt.setForeground(running ? Theme.MUTED : Theme.ACCENT);
        prompt.setText(running ? "… " : PROMPT);
    }

    private void echoAnswer(String text) {
        print(text + "\n", Theme.TEXT, true);
    }

    /** Splits a command line on spaces, keeping "quoted text" together. */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (char c : text.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                any = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (any) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    any = false;
                }
            } else {
                current.append(c);
                any = true;
            }
        }
        if (any) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    // ---- output ---------------------------------------------------------------------------------

    /** Writes command or event text, coloured by {@link ConsoleStyle}; thread-safe. */
    private void write(String text, boolean error) {
        SwingUtilities.invokeLater(() -> {
            int start = 0;
            while (start < text.length()) {
                int end = text.indexOf('\n', start);
                String piece = end < 0 ? text.substring(start) : text.substring(start, end + 1);
                start = end < 0 ? text.length() : end + 1;
                if (error) {
                    appendSegment(piece, Theme.DANGER, false);
                } else {
                    String body = piece.endsWith("\n") ? piece.substring(0, piece.length() - 1) : piece;
                    ConsoleStyle.colour(body).forEach(s -> appendSegment(s.text(), s.color(), s.bold()));
                    if (piece.endsWith("\n")) {
                        appendSegment("\n", Theme.TEXT, false);
                    }
                }
            }
            trimAndScroll();
        });
    }

    private void println(String text, Color color, boolean bold, boolean newline) {
        print(newline ? text + "\n" : text, color, bold);
    }

    private void print(String text, Color color, boolean bold) {
        appendSegment(text, color, bold);
        trimAndScroll();
    }

    private void appendSegment(String text, Color color, boolean bold) {
        String key = color.getRGB() + (bold ? "b" : "n");
        SimpleAttributeSet attrs = styles.computeIfAbsent(key, k -> {
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setForeground(a, color);
            StyleConstants.setBold(a, bold);
            StyleConstants.setFontFamily(a, Theme.mono(FONT_SIZE).getFamily());
            StyleConstants.setFontSize(a, (int) FONT_SIZE);
            return a;
        });
        try {
            doc.insertString(doc.getLength(), text, attrs);
        } catch (BadLocationException ignored) {
            // appending at the end is always a valid position
        }
    }

    private void trimAndScroll() {
        int lines = doc.getDefaultRootElement().getElementCount();
        if (lines > MAX_LINES) {
            try {
                int cut = doc.getDefaultRootElement().getElement(lines - MAX_LINES / 2).getStartOffset();
                ((AbstractDocument) doc).remove(0, cut);
            } catch (BadLocationException ignored) {
                // the screen is only informational
            }
        }
        pane.setCaretPosition(doc.getLength());
    }

    /** The writer behind a command's standard output or error. */
    private final class Sink extends Writer {
        private final boolean error;
        private final StringBuilder pending = new StringBuilder();

        Sink(boolean error) {
            this.error = error;
        }

        @Override
        public synchronized void write(char[] chars, int off, int len) {
            pending.append(chars, off, len);
        }

        @Override
        public synchronized void flush() {
            if (pending.length() > 0) {
                String text = pending.toString();
                pending.setLength(0);
                Console.this.write(text, error);
            }
        }

        @Override
        public void close() {
            flush();
        }
    }

    /** Typed lines on their way to a running command that asks a question. */
    private static final class LineFeed extends InputStream {
        private final BlockingQueue<byte[]> lines = new LinkedBlockingQueue<>();
        private byte[] current = new byte[0];
        private int position;

        void offer(String line) {
            lines.add((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        void clear() {
            lines.clear();
        }

        @Override
        public int read() throws java.io.IOException {
            while (position >= current.length) {
                try {
                    current = lines.take();
                    position = 0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }
            return current[position++] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws java.io.IOException {
            if (len == 0) {
                return 0;
            }
            int first = read();
            if (first < 0) {
                return -1;
            }
            buffer[off] = (byte) first;
            int count = 1;
            while (count < len && position < current.length) {
                buffer[off + count++] = current[position++];
            }
            return count;
        }
    }
}
