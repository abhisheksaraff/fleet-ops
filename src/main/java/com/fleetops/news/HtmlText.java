package com.fleetops.news;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a snippet of HTML into plain text on one line. Line breaks and paragraph ends become spaces;
 * inline tags such as {@code <b>} simply disappear, so {@code wor<b>ld</b>} stays one word.
 */
public final class HtmlText {

    private static final Pattern BREAKS = Pattern.compile("(?i)<br\\s*/?>|</p>|</div>|</li>");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0]+");
    private static final Map<String, String> NAMED = Map.of(
            "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ",
            "ndash", "-", "mdash", "-", "hellip", "...", "rsquo", "'");

    private HtmlText() {}

    public static String plain(String html) {
        if (html == null) {
            return "";
        }
        String text = BREAKS.matcher(html).replaceAll(" ");
        text = TAGS.matcher(text).replaceAll("");
        text = unescape(text);
        return SPACES.matcher(text).replaceAll(" ").strip();
    }

    private static String unescape(String text) {
        Matcher m = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(decode(m.group(0), m.group(1))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String decode(String whole, String name) {
        try {
            if (name.startsWith("#x") || name.startsWith("#X")) {
                return new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
            }
            if (name.startsWith("#")) {
                return new String(Character.toChars(Integer.parseInt(name.substring(1))));
            }
        } catch (IllegalArgumentException e) {
            return whole;
        }
        return NAMED.getOrDefault(name.toLowerCase(Locale.ROOT), whole);
    }
}
