package com.fleetops.gui;

import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentState;
import com.fleetops.domain.incident.IncidentType;
import com.fleetops.domain.incident.Severity;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The incident list in the sidebar: a search box, "show" chips, a sort menu and one card per incident.
 *
 * <p>It owns the full list of incidents and a filtered, sorted view of it. The map keeps using the
 * full list; only this panel is filtered. Selection is reported through a listener that also says
 * whether the person caused it (a click or the keyboard) or the program did (a refresh, a click on the
 * map), because only the first should move the map.
 */
final class IncidentList extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int CELL_HEIGHT = 78;

    /** Which incidents are listed. */
    enum Show {
        ALL("All") {
            @Override
            boolean accepts(Incident i) {
                return true;
            }
        },
        READY("Ready") {
            @Override
            boolean accepts(Incident i) {
                return i.state() == IncidentState.NEW || i.state() == IncidentState.VERIFIED;
            }
        },
        ACTIVE("Active") {
            @Override
            boolean accepts(Incident i) {
                return i.state() == IncidentState.PLANNED || i.state() == IncidentState.IN_PROGRESS;
            }
        },
        DONE("Done") {
            @Override
            boolean accepts(Incident i) {
                return i.state() == IncidentState.COMPLETED;
            }
        };

        private final String label;

        Show(String label) {
            this.label = label;
        }

        abstract boolean accepts(Incident incident);

        String label() {
            return label;
        }
    }

    /** How the listed incidents are ordered. */
    enum Sort {
        NEWEST("Newest", Comparator.comparing(Incident::reportedAt).reversed()
                .thenComparing(Comparator.comparingLong((Incident i) -> i.id().orElse(0L)).reversed())),
        SEVERITY("Severity", Comparator.comparingInt((Incident i) -> i.severity().level()).reversed()
                .thenComparing(Comparator.comparingInt(Incident::estimatedPeople).reversed())),
        PEOPLE("People", Comparator.comparingInt(Incident::estimatedPeople).reversed()),
        TITLE("Title", Comparator.comparing(Incident::title, String.CASE_INSENSITIVE_ORDER));

        private final String label;
        private final transient Comparator<Incident> order;

        Sort(String label, Comparator<Incident> order) {
            this.label = label;
            this.order = order;
        }

        Comparator<Incident> order() {
            return order;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final DefaultListModel<Incident> listModel = new DefaultListModel<>();
    private final JList<Incident> list = new JList<>(listModel);
    private final JTextField search = new JTextField();
    private final JComboBox<Sort> sortBox = new JComboBox<>(Sort.values());
    private final JLabel count = new JLabel(" ");
    private final transient Map<Show, Ui.Tab> chips = new EnumMap<>(Show.class);
    private final transient List<BiConsumer<Incident, Boolean>> listeners = new ArrayList<>();

    private transient List<Incident> all = List.of();
    private Show show = Show.ALL;
    private boolean muted;
    private int hoverIndex = -1;

    IncidentList() {
        super(new BorderLayout(0, 8));
        setOpaque(false);

        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search incidents");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.search());
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.putClientProperty(FlatClientProperties.STYLE, "arc:3; margin:4,8,4,8; focusWidth:1");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuild(false);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuild(false);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuild(false);
            }
        });

        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        chipRow.setOpaque(false);
        chipRow.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        ButtonGroup group = new ButtonGroup();
        for (Show option : Show.values()) {
            Ui.Tab chip = new Ui.Tab(option.label());
            chip.setSelected(option == show);
            chip.addActionListener(e -> setShow(option));
            group.add(chip);
            chips.put(option, chip);
            chipRow.add(chip);
        }

        count.setForeground(Theme.MUTED);
        count.setFont(Theme.mono(11f));
        sortBox.putClientProperty(FlatClientProperties.STYLE, "arc:3");
        sortBox.setFocusable(false);
        sortBox.addActionListener(e -> rebuild(false));
        JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        sortRow.setOpaque(false);
        sortRow.add(Ui.muted("Sort"));
        sortRow.add(sortBox);
        JPanel metaRow = new JPanel(new BorderLayout(8, 0));
        metaRow.setOpaque(false);
        metaRow.add(count, BorderLayout.WEST);
        metaRow.add(sortRow, BorderLayout.EAST);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(search);
        top.add(Box.createVerticalStrut(10));
        top.add(chipRow);
        top.add(Box.createVerticalStrut(8));
        top.add(metaRow);
        top.add(Box.createVerticalStrut(4));
        for (Component c : top.getComponents()) {
            JComponent jc = (JComponent) c;
            jc.setAlignmentX(0f);
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height));
        }
        add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(CELL_HEIGHT);
        list.setBackground(Theme.PANEL);
        list.setCellRenderer(new Card());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !muted) {
                fire(selected(), true);
            }
        });
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && !list.getCellBounds(index, index).contains(e.getPoint())) {
                    index = -1;
                }
                if (index != hoverIndex) {
                    hoverIndex = index;
                    list.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverIndex = -1;
                list.repaint();
            }
        };
        list.addMouseMotionListener(hover);
        list.addMouseListener(hover);

        JScrollPane scroll = new JScrollPane(new EmptyHint(list));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.PANEL);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        setMinimumSize(new Dimension(0, 120));
        updateCount();
    }

    // ---- data -----------------------------------------------------------------------------------

    /** Replaces the incidents, keeps the selection (matched by id) and applies the current filter. */
    void setIncidents(List<Incident> incidents) {
        all = List.copyOf(incidents);
        rebuild(true);
    }

    /** Every incident, including those the filter hides: this is what the map draws. */
    List<Incident> all() {
        return all;
    }

    /** The incidents currently listed, in order. */
    List<Incident> visible() {
        return List.copyOf(java.util.Collections.list(listModel.elements()));
    }

    Incident selected() {
        return list.getSelectedValue();
    }

    /** Selects an incident without telling the listener it was the person's doing. */
    void select(Incident incident) {
        if (incident == null) {
            return;
        }
        if (indexOf(incident) < 0) {
            // Hidden by the filter: show everything so the selection is visible.
            search.setText("");
            chips.get(Show.ALL).setSelected(true);
            show = Show.ALL;
            rebuild(false);
        }
        int index = indexOf(incident);
        if (index < 0) {
            return;
        }
        muted = true;
        try {
            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
        } finally {
            muted = false;
        }
        fire(selected(), false);
    }

    /** Selects the row at an index as if the person had clicked it. */
    void selectIndex(int index) {
        list.setSelectedIndex(index);
    }

    void clearSelection() {
        list.clearSelection();
    }

    /** Called with the selected incident (or null) and whether the person caused the change. */
    void addSelectionListener(BiConsumer<Incident, Boolean> listener) {
        listeners.add(listener);
    }

    // ---- filters --------------------------------------------------------------------------------

    void setShow(Show option) {
        show = option;
        chips.get(option).setSelected(true);
        rebuild(false);
    }

    void setSort(Sort order) {
        sortBox.setSelectedItem(order);
    }

    void setQuery(String text) {
        search.setText(text);
    }

    Show shown() {
        return show;
    }

    private boolean matches(Incident i) {
        if (!show.accepts(i)) {
            return false;
        }
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }
        String haystack = (i.title() + " " + i.summary() + " " + i.type().keyword() + " "
                + i.state().name() + " " + i.severity().name() + " #" + i.id().map(String::valueOf).orElse(""))
                .toLowerCase(Locale.ROOT).replace('_', ' ');
        for (String token : query.split("\\s+")) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private void rebuild(boolean force) {
        Incident before = selected();
        Sort order = (Sort) sortBox.getSelectedItem();
        List<Incident> view = all.stream()
                .filter(this::matches)
                .sorted((order == null ? Sort.NEWEST : order).order())
                .toList();
        muted = true;
        try {
            listModel.clear();
            view.forEach(listModel::addElement);
            int index = before == null ? -1 : indexOf(before);
            if (index >= 0) {
                list.setSelectedIndex(index);
            }
        } finally {
            muted = false;
        }
        hoverIndex = -1;
        updateCount();
        Incident after = selected();
        if (force ? (before != null || after != null) : before != after) {
            fire(after, false);
        }
    }

    private int indexOf(Incident incident) {
        for (int i = 0; i < listModel.size(); i++) {
            Incident candidate = listModel.get(i);
            if (candidate == incident
                    || (incident.id().isPresent() && incident.id().equals(candidate.id()))) {
                return i;
            }
        }
        return -1;
    }

    private void updateCount() {
        int shown = listModel.size();
        count.setText(shown == all.size()
                ? all.size() + (all.size() == 1 ? " incident" : " incidents")
                : shown + " of " + all.size());
    }

    private void fire(Incident incident, boolean byUser) {
        for (BiConsumer<Incident, Boolean> listener : List.copyOf(listeners)) {
            listener.accept(incident, byUser);
        }
    }

    String emptyTitle() {
        return all.isEmpty() ? "No incidents yet" : "Nothing matches";
    }

    String emptyHint() {
        return all.isEmpty() ? "Click “Scan news” to look for disasters." : "Try another search or filter.";
    }

    // ---- painting -------------------------------------------------------------------------------

    /** Wraps the list so an empty list can show a friendly message instead of blank space. */
    private final class EmptyHint extends JPanel {
        private static final long serialVersionUID = 1L;

        EmptyHint(JList<Incident> inner) {
            super(new BorderLayout());
            setBackground(Theme.PANEL);
            add(inner, BorderLayout.NORTH);
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            if (listModel.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(Theme.serif(15f));
                g2.setColor(Theme.TEXT);
                String title = emptyTitle();
                FontMetrics fm = g2.getFontMetrics();
                int y = Math.min(getHeight() / 2, 120);
                g2.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, y);
                g2.setFont(Theme.ui(Font.PLAIN, 12f));
                g2.setColor(Theme.MUTED);
                String hint = emptyHint();
                g2.drawString(hint, (getWidth() - g2.getFontMetrics().stringWidth(hint)) / 2, y + 20);
                g2.dispose();
            }
        }
    }

    /**
     * One incident drawn as a ruled row of a map legend: the disaster's symbol in a square frame, the
     * title in the serif face, the grid reference in mono, then severity, people and state. No card,
     * no fill unless the row is selected or hovered.
     */
    private final class Card extends JComponent implements ListCellRenderer<Incident> {
        private static final long serialVersionUID = 1L;
        private transient Incident incident;
        private boolean selected;
        private boolean hover;

        @Override
        public Component getListCellRendererComponent(JList<? extends Incident> l, Incident value, int index,
                boolean isSelected, boolean hasFocus) {
            incident = value;
            selected = isSelected;
            hover = index == hoverIndex;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (incident == null) {
                return;
            }
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            if (selected) {
                g.setColor(new Color(0x1A2A39));
                g.fillRect(0, 0, w, h);
            } else if (hover) {
                g.setColor(new Color(0x122030));
                g.fillRect(0, 0, w, h);
            }
            g.setColor(selected ? Theme.ACCENT : Theme.BORDER);
            g.setStroke(new BasicStroke(1f));
            if (selected) {
                g.drawRect(0, 0, w - 1, h - 1);
            } else {
                g.drawLine(0, h - 1, w, h - 1);
            }

            // the disaster's symbol in a square frame
            int pad = 12;
            int box = 36;
            int bx = pad;
            int by = (h - box) / 2;
            Color tc = typeColor(incident.type());
            g.setColor(Ui.alpha(tc, 150));
            g.drawRect(bx, by, box, box);
            paintTypeIcon(g, incident.type(), bx + box / 2, by + box / 2, 15, tc);

            int left = bx + box + 12;
            int right = w - pad;

            // line 1: the title, in the serif face
            g.setFont(Theme.serif(14.5f));
            g.setColor(Theme.TEXT);
            FontMetrics tf = g.getFontMetrics();
            int line1 = by + tf.getAscent() - 3;
            g.drawString(ellipsize(incident.title(), tf, right - left), left, line1);

            // line 2: grid reference in mono
            g.setFont(Theme.mono(11f));
            g.setColor(Theme.MUTED);
            FontMetrics mf = g.getFontMetrics();
            int line2 = line1 + 17;
            String grid = gridRef(incident);
            g.drawString(grid, left, line2);

            // line 3: severity, people, state on the right
            int line3 = line2 + 16;
            Color sev = severityColor(incident.severity());
            g.setColor(sev);
            g.fillRect(left, line3 - 8, 8, 8);
            g.setFont(Theme.ui(Font.BOLD, 12f));
            FontMetrics bf = g.getFontMetrics();
            String sevName = titleCase(incident.severity().name());
            g.drawString(sevName, left + 14, line3);
            int after = left + 14 + bf.stringWidth(sevName);
            g.setFont(Theme.ui(Font.PLAIN, 12f));
            g.setColor(Theme.MUTED);
            String people = "  \u00B7  " + incident.estimatedPeople() + " people";
            g.drawString(people, after, line3);

            String[] chip = stateChip(incident.state());
            Color sc = stateColor(incident.state());
            g.setFont(Theme.mono(11f).deriveFont(Font.BOLD));
            FontMetrics sf = g.getFontMetrics();
            int sw = sf.stringWidth(chip[0]);
            g.setColor(sc);
            g.drawString(chip[0], right - sw, line3);
            g.fillRect(right - sw - 12, line3 - 8, 7, 7);
            g.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, CELL_HEIGHT);
        }
    }

    /** The incident's centre as a map grid reference, for example {@code 63.747N 068.517W}. */
    static String gridRef(Incident incident) {
        double lat = incident.area().center().lat();
        double lon = incident.area().center().lon();
        return String.format(Locale.ROOT, "%06.3f%s %07.3f%s", Math.abs(lat), lat >= 0 ? "N" : "S",
                Math.abs(lon), lon >= 0 ? "E" : "W");
    }

    private static String ellipsize(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String dots = "…";
        int end = text.length();
        while (end > 1 && fm.stringWidth(text.substring(0, end).stripTrailing() + dots) > width) {
            end--;
        }
        return text.substring(0, end).stripTrailing() + dots;
    }

    private static String titleCase(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    static Color typeColor(IncidentType type) {
        return switch (type) {
            case FLOOD -> Theme.INFO;
            case FIRE -> new Color(0xE9783D);
            case EARTHQUAKE -> new Color(0xC79A6A);
            case FAMINE -> new Color(0xC2BA6A);
        };
    }

    static Color severityColor(Severity severity) {
        return switch (severity) {
            case LOW -> new Color(0x9CB87C);
            case MODERATE -> new Color(0xE3C14F);
            case HIGH -> new Color(0xE9783D);
            case CRITICAL -> Theme.DANGER;
        };
    }

    /** The short label shown on a row for a lifecycle state. */
    static String[] stateChip(IncidentState state) {
        return switch (state) {
            case NEW -> new String[] {"NEW"};
            case VERIFIED -> new String[] {"READY"};
            case PLANNED -> new String[] {"PLANNED"};
            case IN_PROGRESS -> new String[] {"RESCUING"};
            case COMPLETED -> new String[] {"DONE"};
            case REJECTED -> new String[] {"REJECTED"};
            case DUPLICATE -> new String[] {"DUPLICATE"};
        };
    }

    static Color stateColor(IncidentState state) {
        return switch (state) {
            case VERIFIED -> Theme.INFO;
            case PLANNED -> Theme.WARN;
            case IN_PROGRESS -> Theme.LIVE;
            case COMPLETED -> Theme.DONE;
            default -> Theme.MUTED;
        };
    }

    /** A small line drawing of the disaster, centred on a point; callers draw their own frame around it. */
    static void paintTypeIcon(Graphics2D g0, IncidentType type, int cx, int cy, int radius, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(cx, cy);
        double scale = radius / 20.0;
        g.scale(scale, scale);
        g.setColor(color);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        switch (type) {
            case FLOOD -> {
                for (int row = -1; row <= 1; row++) {
                    float y = row * 6f;
                    Path2D wave = new Path2D.Float();
                    wave.moveTo(-10, y);
                    wave.curveTo(-7, y - 4.5, -3.5, y - 4.5, 0, y);
                    wave.curveTo(3.5, y + 4.5, 7, y + 4.5, 10, y);
                    g.draw(wave);
                }
            }
            case FIRE -> {
                Path2D flame = new Path2D.Float();
                flame.moveTo(0, -11);
                flame.curveTo(2, -5, 9, -2, 8, 4);
                flame.curveTo(7.5, 10, -7.5, 10, -8, 4);
                flame.curveTo(-8.5, 0, -5, -1, -4, -5);
                flame.curveTo(-2, -4, -1.5, -3, 0, -11);
                flame.closePath();
                g.fill(flame);
                g.setColor(Theme.PANEL);
                g.fill(new Ellipse2D.Float(-3f, 3f, 6f, 6f));
            }
            case EARTHQUAKE -> {
                Path2D crack = new Path2D.Float();
                crack.moveTo(-11, 0);
                crack.lineTo(-6, 0);
                crack.lineTo(-3, -9);
                crack.lineTo(1, 9);
                crack.lineTo(4, -6);
                crack.lineTo(6, 0);
                crack.lineTo(11, 0);
                g.draw(crack);
            }
            case FAMINE -> {
                // an ear of wheat: a stem with three pairs of grains
                g.drawLine(0, 12, 0, -8);
                for (int y = -4; y <= 6; y += 5) {
                    g.drawLine(0, y, -6, y - 5);
                    g.drawLine(0, y, 6, y - 5);
                }
                g.fill(new Ellipse2D.Float(-2f, -12f, 4f, 6f));
            }
        }
        g.dispose();
    }
}
