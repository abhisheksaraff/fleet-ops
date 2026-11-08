package com.fleetops.news;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the readable text out of a news article's HTML: the paragraphs, without scripts, menus and
 * page furniture. It is a simple, forgiving reader, not a full web-page parser.
 */
public final class ArticleText {

    private static final Pattern NOISE = Pattern.compile(
            "(?is)<(script|style|noscript|nav|header|footer|aside|form|svg|iframe)\\b.*?</\\1\\s*>");
    private static final Pattern COMMENTS = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern PARAGRAPH = Pattern.compile("(?is)<p\\b[^>]*>(.*?)</p\\s*>");
    private static final int MIN_PARAGRAPH_CHARS = 40;

    private ArticleText() {}

    /**
     * @param html the page
     * @param maxChars the most characters to return
     * @return the paragraphs joined by spaces and cut at a word, or an empty string if none were found
     */
    public static String extract(String html, int maxChars) {
        if (html == null || html.isBlank() || maxChars < 1) {
            return "";
        }
        String body = COMMENTS.matcher(NOISE.matcher(html).replaceAll(" ")).replaceAll(" ");
        List<String> paragraphs = new ArrayList<>();
        Matcher m = PARAGRAPH.matcher(body);
        while (m.find()) {
            String text = HtmlText.plain(m.group(1));
            if (text.length() >= MIN_PARAGRAPH_CHARS) {
                paragraphs.add(text);
            }
        }
        String joined = String.join(" ", paragraphs);
        if (joined.length() <= maxChars) {
            return joined;
        }
        int cut = joined.lastIndexOf(' ', maxChars);
        return joined.substring(0, cut > maxChars / 2 ? cut : maxChars).stripTrailing();
    }
}
