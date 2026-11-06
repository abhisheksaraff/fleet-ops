package com.fleetops.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The look of the console: the big FLEETOPS banner and the rules that colour what commands print. */
final class ConsoleStyle {

    /** A piece of text with a colour. */
    record Segment(String text, Color color, boolean bold) {}

    private static final Color BODY = new Color(0xC9CED4);
    private static final Color DONE = new Color(0x4C9F7A);
    private static final Color ORANGE = new Color(0xFF8A4C);

    private static final Map<String, Color> WORDS = Map.ofEntries(
            Map.entry("VERIFIED", Theme.INFO),
            Map.entry("PLANNED", Theme.WARN),
            Map.entry("IN_PROGRESS", Theme.ACCENT),
            Map.entry("COMPLETED", DONE),
            Map.entry("CREATED", Theme.ACCENT),
            Map.entry("NEW", Theme.MUTED),
            Map.entry("REJECTED", Theme.MUTED),
            Map.entry("DUPLICATE", Theme.MUTED),
            Map.entry("FAILED", Theme.DANGER),
            Map.entry("LOW", new Color(0x7BD389)),
            Map.entry("MODERATE", Theme.WARN),
            Map.entry("HIGH", ORANGE),
            Map.entry("CRITICAL", Theme.DANGER));

    private static final Pattern WORD = Pattern.compile("\\b(" + String.join("|", WORDS.keySet()) + ")\\b");

    private static final String[][] LETTERS = {
        {"███████╗", "██╔════╝", "█████╗  ", "██╔══╝  ", "██║     ", "╚═╝     "},
        {"██╗     ", "██║     ", "██║     ", "██║     ", "███████╗", "╚══════╝"},
        {"███████╗", "██╔════╝", "█████╗  ", "██╔══╝  ", "███████╗", "╚══════╝"},
        {"████████╗", "╚══██╔══╝", "   ██║   ", "   ██║   ", "   ██║   ", "   ╚═╝   "},
        {" ██████╗ ", "██╔═══██╗", "██║   ██║", "██║   ██║", "╚██████╔╝", " ╚═════╝ "},
        {"██████╗ ", "██╔══██╗", "██████╔╝", "██╔═══╝ ", "██║     ", "╚═╝     "},
        {"███████╗", "██╔════╝", "███████╗", "╚════██║", "███████║", "╚══════╝"},
    };

    /** F, L, E, E, T, O, P, S - indexes into {@link #LETTERS}. */
    private static final int[] WORD_ORDER = {0, 1, 2, 2, 3, 4, 5, 6};

    private ConsoleStyle() {}

    /** The six rows of the banner, built from the letter shapes so the rows always line up. */
    static List<String> banner() {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < 6; row++) {
            StringBuilder line = new StringBuilder();
            for (int letter : WORD_ORDER) {
                line.append(LETTERS[letter][row]);
            }
            rows.add(line.toString());
        }
        return rows;
    }

    /** The banner colour for a row: mint at the top fading to blue at the bottom. */
    static Color bannerColor(int row) {
        float t = row / 5f;
        return new Color(
                Math.round(Theme.ACCENT.getRed() + (Theme.INFO.getRed() - Theme.ACCENT.getRed()) * t),
                Math.round(Theme.ACCENT.getGreen() + (Theme.INFO.getGreen() - Theme.ACCENT.getGreen()) * t),
                Math.round(Theme.ACCENT.getBlue() + (Theme.INFO.getBlue() - Theme.ACCENT.getBlue()) * t));
    }

    /** Splits a piece of ordinary output into coloured segments. */
    static List<Segment> colour(String text) {
        String trimmed = text.strip();
        Color line = null;
        boolean bold = false;
        if (trimmed.startsWith("Mission COMPLETED")) {
            line = Theme.ACCENT;
            bold = true;
        } else if (trimmed.startsWith("Mission INCOMPLETE")) {
            line = Theme.WARN;
            bold = true;
        } else if (trimmed.startsWith("Mission ABORTED")) {
            line = Theme.DANGER;
            bold = true;
        } else if (trimmed.toLowerCase(Locale.ROOT).contains("area cleared")) {
            line = Theme.ACCENT;
            bold = true;
        } else if (trimmed.startsWith("[") && trimmed.contains(" rescued ")) {
            line = Theme.ACCENT;
        } else if (trimmed.startsWith("[") && trimmed.contains(" found ")) {
            line = Theme.WARN;
        } else if (trimmed.startsWith("Note:") || trimmed.startsWith("Dry run")) {
            line = Theme.WARN;
        } else if (trimmed.startsWith("ID ") && trimmed.contains("TITLE")) {
            line = Theme.MUTED;
            bold = true;
        } else if (trimmed.toLowerCase(Locale.ROOT).startsWith("error") || trimmed.startsWith("No incident")
                || trimmed.startsWith("Could not")) {
            line = Theme.DANGER;
        }
        List<Segment> out = new ArrayList<>();
        if (line != null) {
            out.add(new Segment(text, line, bold));
            return out;
        }
        Matcher m = WORD.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out.add(new Segment(text.substring(last, m.start()), BODY, false));
            }
            out.add(new Segment(m.group(), WORDS.get(m.group()), true));
            last = m.end();
        }
        if (last < text.length()) {
            out.add(new Segment(text.substring(last), BODY, false));
        }
        return out;
    }
}
