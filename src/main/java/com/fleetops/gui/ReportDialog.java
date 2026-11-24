package com.fleetops.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * The window for reporting an incident by hand: the operator types or pastes a report in plain words, or
 * loads a task file (the same files the command line's {@code agent --input} reads). It only collects the
 * text; verifying it is the caller's job.
 */
final class ReportDialog {

    private ReportDialog() {}

    /**
     * Shows the window over {@code parent} and waits for the operator.
     *
     * @param tasksFolder where "Load file..." starts, if that folder exists
     * @return the text to verify, or empty if the operator cancelled
     */
    static Optional<String> ask(Component parent, Path tasksFolder) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Report an incident", JDialog.DEFAULT_MODALITY_TYPE);
        String[] result = {null};

        JTextArea area = new JTextArea("", 10, 56);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.mono(12f));
        area.setForeground(Theme.TEXT);
        area.setBackground(Theme.CONSOLE_BG);
        area.setCaretColor(Theme.TEXT);
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        javax.swing.JScrollPane scroll = Ui.scroll(area);
        scroll.setPreferredSize(new Dimension(640, 220));

        JButton load = Ui.secondary(new JButton("Load file..."));
        JButton cancel = Ui.secondary(new JButton("Cancel"));
        JButton verify = Ui.primary(new JButton("Verify"));
        verify.setEnabled(false);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                verify.setEnabled(!area.getText().isBlank());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                verify.setEnabled(!area.getText().isBlank());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                verify.setEnabled(!area.getText().isBlank());
            }
        });

        load.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(startFolder(tasksFolder).toFile());
            chooser.setDialogTitle("Load a task file");
            chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
            if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                area.setText(Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8));
                area.setCaretPosition(0);
            } catch (IOException | RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, "Cannot read the file: " + ex.getMessage(), "Load file", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(e -> dialog.dispose());
        verify.addActionListener(e -> {
            result[0] = area.getText();
            dialog.dispose();
        });

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel heading = Ui.heading("Report an incident");
        heading.setAlignmentX(0f);
        top.add(heading);
        top.add(Box.createVerticalStrut(8));
        javax.swing.JLabel hint = Ui.muted("<html><body style='width:600px'>Write what happened in plain words, with the place and how many "
                + "people are in danger. The AI checks it like any news story. Separate several reports with a line of ---, "
                + "or load a task file.</body></html>");
        hint.setAlignmentX(0f);
        top.add(hint);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(cancel);
        right.add(verify);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.add(load, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(verify);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true); // blocks until the dialog is disposed
        return Optional.ofNullable(result[0]);
    }

    /** The tasks folder if it exists, otherwise the folder the program was started from. */
    private static Path startFolder(Path tasksFolder) {
        Path tasks = tasksFolder.toAbsolutePath();
        return Files.isDirectory(tasks) ? tasks : Path.of("").toAbsolutePath();
    }
}
