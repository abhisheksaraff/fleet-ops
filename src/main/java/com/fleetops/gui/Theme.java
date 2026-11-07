package com.fleetops.gui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.swing.UIManager;

/**
 * The look of the application: a survey sheet read at night. An inked blue-black ground, paper-cream
 * text, ochre for the one thing to act on, water blue for what is ready, moss green for what is live
 * and brick red for danger. Place names are set in Young Serif, the interface in Work Sans and
 * coordinates, numbers and the console in IBM Plex Mono (all bundled, all under the SIL Open Font
 * License). Everything visual that is shared between windows lives here.
 */
final class Theme {

    static final Color BG = new Color(0x0A121A);
    static final Color PANEL = new Color(0x0E1823);
    static final Color RAISED = new Color(0x15232F);
    static final Color BORDER = new Color(0x2A3B4A);
    static final Color TEXT = new Color(0xEBE4D0);
    static final Color MUTED = new Color(0xA39F8E);
    /** The one colour to act on: primary buttons, selection, focus. */
    static final Color ACCENT = new Color(0xDDA94B);
    static final Color ON_ACCENT = new Color(0x1B1407);
    static final Color DANGER = new Color(0xE0624A);
    static final Color WARN = new Color(0xDDA94B);
    /** Water blue: an incident that is ready to plan. */
    static final Color INFO = new Color(0x6FB3D2);
    /** Moss green: something that is happening now. */
    static final Color LIVE = new Color(0x9CCB6B);
    /** Sage: something that is finished. */
    static final Color DONE = new Color(0x7A9A86);
    static final Color CONSOLE_BG = new Color(0x070D13);

    private static final String[] FONT_FILES = {
        "WorkSans-Regular", "WorkSans-Bold", "YoungSerif-Regular", "IBMPlexMono-Regular", "IBMPlexMono-Bold"};
    private static final String SANS = "Work Sans";
    private static final String SERIF = "Young Serif";
    private static final String MONO = "IBM Plex Mono";

    private static boolean installed;

    private Theme() {}

    /** Installs the theme. Call once, on the event thread, before any window is created. */
    static synchronized void install() {
        if (installed) {
            return;
        }
        boolean fonts = registerFonts();
        if (fonts) {
            FlatLaf.setPreferredFontFamily(SANS);
            FlatLaf.setPreferredMonospacedFontFamily(MONO);
        }
        Map<String, String> extra = new HashMap<>();
        extra.put("@accentColor", hex(ACCENT));
        extra.put("@background", hex(PANEL));
        extra.put("@foreground", hex(TEXT));
        extra.put("@selectionBackground", "#4A3B1C");
        extra.put("@selectionForeground", hex(TEXT));
        FlatLaf.setGlobalExtraDefaults(extra);
        FlatMacDarkLaf.setup();

        UIManager.put("Component.arc", 3);
        UIManager.put("Button.arc", 3);
        UIManager.put("TextComponent.arc", 3);
        UIManager.put("ProgressBar.arc", 0);
        UIManager.put("ScrollBar.thumbArc", 0);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 3, 2, 3));
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.track", PANEL);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.hoverThumbColor", new Color(0x3A5064));
        UIManager.put("SplitPane.dividerSize", 5);
        UIManager.put("SplitPaneDivider.gripColor", BORDER);
        UIManager.put("SplitPaneDivider.background", BG);
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));
        UIManager.put("Table.selectionBackground", new Color(0x3A, 0x2F, 0x16));
        UIManager.put("Table.selectionInactiveBackground", new Color(0x2A, 0x25, 0x18));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", PANEL);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("TableHeader.bottomSeparatorColor", BORDER);
        UIManager.put("TableHeader.separatorColor", PANEL);
        UIManager.put("TableHeader.height", 30);
        UIManager.put("ToolTip.background", RAISED);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createLineBorder(BORDER));
        UIManager.put("Button.default.background", ACCENT);
        UIManager.put("Button.default.foreground", ON_ACCENT);
        installed = true;
    }

    /** Loads the bundled fonts; returns false (and leaves the system fonts in use) if any is missing. */
    private static boolean registerFonts() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        try {
            for (String file : FONT_FILES) {
                try (InputStream in = Theme.class.getResourceAsStream("/fonts/" + file + ".ttf")) {
                    if (in == null) {
                        return false;
                    }
                    env.registerFont(Font.createFont(Font.TRUETYPE_FONT, in));
                }
            }
            return true;
        } catch (FontFormatException | IOException | RuntimeException e) {
            return false;
        }
    }

    /** Coordinates, numbers and the console. */
    static Font mono(float size) {
        return family(MONO, Font.MONOSPACED, Font.PLAIN, size);
    }

    static Font monoBold(float size) {
        return family(MONO, Font.MONOSPACED, Font.BOLD, size);
    }

    /** Place names and headings. Only one weight exists, so it is always drawn regular. */
    static Font serif(float size) {
        return family(SERIF, Font.SERIF, Font.PLAIN, size);
    }

    static Font ui(int style, float size) {
        Font base = UIManager.getFont("defaultFont");
        return (base == null ? new Font(Font.SANS_SERIF, style, Math.round(size)) : base).deriveFont(style, size);
    }

    private static Font family(String name, String fallback, int style, float size) {
        Font wanted = new Font(name, style, 12);
        return wanted.getFamily().equals(name)
                ? wanted.deriveFont(size)
                : new Font(fallback, style, Math.round(size));
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
