package com.fleetops.gui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;
import javax.swing.UIManager;

/**
 * The look of the application: a dark FlatLaf theme with one mint accent colour, the Inter font
 * for the interface and JetBrains Mono for the console. Everything visual that is shared between
 * windows lives here, so the whole look can be changed in one place.
 */
final class Theme {

    static final Color BG = new Color(0x101215);
    static final Color PANEL = new Color(0x15181C);
    static final Color RAISED = new Color(0x1D2126);
    static final Color BORDER = new Color(0x2A2F36);
    static final Color TEXT = new Color(0xE8EAED);
    static final Color MUTED = new Color(0x8B939D);
    static final Color ACCENT = new Color(0x46E0A0);
    static final Color ON_ACCENT = new Color(0x07140E);
    static final Color DANGER = new Color(0xFF6B6B);
    static final Color WARN = new Color(0xF5A524);
    static final Color INFO = new Color(0x5AA9FF);
    static final Color CONSOLE_BG = new Color(0x0B0D0F);

    private static boolean installed;

    private Theme() {}

    /** Installs the theme. Call once, on the event thread, before any window is created. */
    static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            FlatInterFont.installLazy();
            FlatJetBrainsMonoFont.installLazy();
            FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
            FlatLaf.setPreferredLightFontFamily(FlatInterFont.FAMILY_LIGHT);
            FlatLaf.setPreferredSemiboldFontFamily(FlatInterFont.FAMILY_SEMIBOLD);
            FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);
        } catch (RuntimeException e) {
            // The system fonts are used instead; the theme still works.
        }
        Map<String, String> extra = new HashMap<>();
        extra.put("@accentColor", hex(ACCENT));
        extra.put("@background", hex(PANEL));
        extra.put("@foreground", hex(TEXT));
        extra.put("@selectionBackground", "#24332D");
        extra.put("@selectionForeground", hex(TEXT));
        FlatLaf.setGlobalExtraDefaults(extra);
        FlatMacDarkLaf.setup();

        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.track", PANEL);
        UIManager.put("SplitPane.dividerSize", 6);
        UIManager.put("SplitPaneDivider.gripColor", BORDER);
        UIManager.put("SplitPaneDivider.background", BG);
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));
        UIManager.put("Table.selectionBackground", new Color(0x24, 0x44, 0x38));
        UIManager.put("Table.selectionInactiveBackground", new Color(0x24, 0x33, 0x2D));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", PANEL);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("TableHeader.bottomSeparatorColor", BORDER);
        UIManager.put("TableHeader.separatorColor", PANEL);
        UIManager.put("TableHeader.height", 30);
        UIManager.put("ToolTip.background", RAISED);
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createLineBorder(BORDER));
        installed = true;
    }

    static Font mono(float size) {
        Font base = new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 12);
        return base.getFamily().equals(FlatJetBrainsMonoFont.FAMILY)
                ? base.deriveFont(size)
                : new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size));
    }

    static Font ui(int style, float size) {
        Font base = UIManager.getFont("defaultFont");
        return (base == null ? new Font(Font.SANS_SERIF, style, Math.round(size)) : base).deriveFont(style, size);
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
