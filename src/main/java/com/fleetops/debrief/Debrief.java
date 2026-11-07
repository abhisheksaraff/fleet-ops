package com.fleetops.debrief;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The after-action debrief of one mission: what happened, what went well, what went wrong and what
 * to change next time. It is the same shape whether an AI model or the fixed rules wrote it, so the
 * screens and the command line do not care who the author was.
 *
 * <p>Whatever the author supplied is tidied here: white space is collapsed, blank items are
 * dropped and each list is cut to {@value #MAX_ITEMS} short items, so a rambling model cannot flood
 * the card.
 *
 * @param summary one or two sentences on how the mission went
 * @param wentWell what worked
 * @param problems what did not
 * @param nextTime what to change for the next mission
 * @param writtenBy who wrote it, for example {@code gemini:gemini-2.5-flash} or {@code rules}
 * @param ai true if an AI model wrote it, false if the fixed rules did
 */
public record Debrief(
        String summary, List<String> wentWell, List<String> problems, List<String> nextTime, String writtenBy, boolean ai) {

    public static final int MAX_ITEMS = 3;
    public static final int MAX_SUMMARY_CHARS = 400;
    public static final int MAX_ITEM_CHARS = 200;

    public Debrief {
        summary = tidy(summary, MAX_SUMMARY_CHARS);
        if (summary.isEmpty()) {
            throw new IllegalArgumentException("A debrief needs a summary");
        }
        wentWell = items(wentWell);
        problems = items(problems);
        nextTime = items(nextTime);
        Objects.requireNonNull(writtenBy, "writtenBy");
        if (writtenBy.isBlank()) {
            throw new IllegalArgumentException("A debrief needs an author");
        }
    }

    /** The same debrief under another author line, used when the fixed rules stand in for the AI. */
    public Debrief withAuthor(String newAuthor, boolean newAi) {
        return new Debrief(summary, wentWell, problems, nextTime, newAuthor, newAi);
    }

    /** A multi-line text for the console and the command line. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append("Debrief (written by ").append(writtenBy).append(")\n");
        text.append("  ").append(summary);
        section(text, "Went well", wentWell);
        section(text, "Problems", problems);
        section(text, "Next time", nextTime);
        return text.toString();
    }

    private static void section(StringBuilder text, String title, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        text.append("\n  ").append(title);
        lines.forEach(line -> text.append("\n    - ").append(line));
    }

    private static List<String> items(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String item : raw) {
                String clean = tidy(item, MAX_ITEM_CHARS);
                if (!clean.isEmpty() && out.size() < MAX_ITEMS) {
                    out.add(clean);
                }
            }
        }
        return List.copyOf(out);
    }

    private static String tidy(String text, int max) {
        String clean = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return clean.length() <= max ? clean : clean.substring(0, max - 3).stripTrailing() + "...";
    }
}
