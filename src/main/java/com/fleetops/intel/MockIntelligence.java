package com.fleetops.intel;

import com.fleetops.ai.Json;
import com.fleetops.ai.LlmRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The offline stand-in for the AI's verification desk, used when {@code LLM_PROVIDER=mock}. It
 * reads the same question a real model would receive and answers in the same JSON format, using
 * simple text rules: it looks for coordinates in brackets such as {@code (49.9100, -97.1250)}, a
 * head count, and a severity word. The built-in demo stories contain all three. Ordinary news has
 * no coordinates in its text, so the mock reports it as "not locatable"; use Gemini or Ollama for
 * real feeds.
 */
public final class MockIntelligence implements Function<LlmRequest, String> {

    private static final Pattern COORDINATES = Pattern.compile("\\((-?\\d{1,3}\\.\\d+),\\s*(-?\\d{1,3}\\.\\d+)\\)");
    private static final Pattern PEOPLE = Pattern.compile("(\\d+)\\s+(?:people|hikers|residents|families|survivors|persons)");
    private static final Pattern SEVERITY_LABEL = Pattern.compile("Severity:\\s*(low|moderate|high|critical)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACE =
            Pattern.compile("(?:in|near|of|off|toward)\\s+(?:the\\s+)?([A-Z][A-Za-z' .-]+(?:,\\s*[A-Z][A-Za-z ]+)?)\\s*\\(");
    private static final Pattern TOPIC = Pattern.compile("^" + Pattern.quote(NewsPrompts.TOPIC_PREFIX) + "(\\w+)", Pattern.MULTILINE);
    private static final Pattern NOT_REAL = Pattern.compile("\\b(drill|exercise|anniversary|remembers|remembrance|historical|forecast)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String apply(LlmRequest request) {
        String prompt = request.user();
        String article = between(prompt, NewsPrompts.ARTICLE_START, NewsPrompts.ARTICLE_END);
        String title = line(article, NewsPrompts.TITLE_PREFIX).replaceFirst("^Demo:\\s*", "");
        String text = line(article, NewsPrompts.TEXT_PREFIX).replaceFirst("^DEMO DATA\\.\\s*", "");
        Matcher topic = TOPIC.matcher(prompt);
        String type = topic.find() ? topic.group(1) : "flood";

        Map<String, Object> answer = new LinkedHashMap<>();
        Matcher where = COORDINATES.matcher(text);
        boolean located = where.find();
        boolean notReal = NOT_REAL.matcher(title + " " + text).find();
        boolean real = located && !notReal;

        answer.put("real", real);
        answer.put("confidence", real ? 0.9 : 0.15);
        answer.put("type", type);
        answer.put("title", title);
        answer.put("summary", firstSentence(text));
        Matcher place = PLACE.matcher(text);
        answer.put("locationName", place.find() ? place.group(1).strip() : "");
        answer.put("latitude", located ? Double.valueOf(where.group(1)) : null);
        answer.put("longitude", located ? Double.valueOf(where.group(2)) : null);
        String severity = severity(text);
        answer.put("areaRadiusKm", switch (severity) {
            case "critical" -> 2.5;
            case "high" -> 2.0;
            case "low" -> 1.0;
            default -> 1.5;
        });
        answer.put("severity", severity);
        Matcher people = PEOPLE.matcher(text);
        answer.put("estimatedPeople", people.find() ? Integer.valueOf(people.group(1)) : Integer.valueOf(Assessment.DEFAULT_PEOPLE));
        answer.put("reason", notReal ? "Mock desk: looks like a drill, anniversary or forecast, not a live incident."
                : located ? "Mock desk: the text gives a location and describes a live situation."
                : "Mock desk: no coordinates in the text, so the place cannot be located.");
        return Json.write(answer);
    }

    private static String severity(String text) {
        Matcher label = SEVERITY_LABEL.matcher(text);
        if (label.find()) {
            return label.group(1).toLowerCase(Locale.ROOT);
        }
        String lower = NewsPrompts.lower(text);
        if (lower.contains("critical")) {
            return "critical";
        }
        if (lower.contains("serious") || lower.contains("severe")) {
            return "high";
        }
        return lower.contains("minor") ? "low" : "moderate";
    }

    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        int to = text.lastIndexOf(end);
        return from >= 0 && to > from ? text.substring(from + start.length(), to) : text;
    }

    private static String line(String block, String prefix) {
        for (String line : block.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).strip();
            }
        }
        return "";
    }

    private static String firstSentence(String text) {
        int end = text.indexOf(". ");
        return (end > 0 ? text.substring(0, end + 1) : text).strip();
    }
}
