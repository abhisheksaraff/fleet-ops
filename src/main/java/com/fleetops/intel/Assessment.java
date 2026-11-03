package com.fleetops.intel;

import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.IncidentType;
import com.fleetops.domain.incident.Severity;
import com.fleetops.news.NewsItem;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the AI concluded about one news item: is it a real incident, how sure is it, and the
 * structured details needed to draw the incident on the map (place, size, severity, people).
 *
 * <p>The AI's answer is untrusted input. {@link #fromJson} accepts the small variations models
 * produce (numbers as text, percentages for confidence) but rejects answers that are wrong, and
 * every number is clamped to a range the simulation can use.
 *
 * @param real true only if the text reports an actual, current incident
 * @param confidence 0 to 1
 * @param type the kind of incident
 * @param title short headline
 * @param summary one or two sentences
 * @param locationName the place in words, or empty
 * @param location the centre of the affected area; empty if the AI could not tell
 * @param areaRadiusKm half the side of the affected square
 * @param severity how urgent it is
 * @param estimatedPeople how many people may need help
 * @param reason the AI's explanation, shown to the operator
 */
public record Assessment(
        boolean real,
        double confidence,
        IncidentType type,
        String title,
        String summary,
        String locationName,
        Optional<GeoPoint> location,
        double areaRadiusKm,
        Severity severity,
        int estimatedPeople,
        String reason) {

    public static final double MIN_RADIUS_KM = 0.5;
    public static final double MAX_RADIUS_KM = 3.0;
    public static final double DEFAULT_RADIUS_KM = 1.5;
    public static final int MAX_PEOPLE = 500;
    public static final int DEFAULT_PEOPLE = 5;

    private static final int MAX_TITLE_CHARS = 160;
    private static final int MAX_SUMMARY_CHARS = 400;

    public Assessment {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(location, "location");
    }

    /**
     * Reads the AI's JSON answer.
     *
     * @param json the parsed answer
     * @param item the item that was assessed; supplies defaults for anything the AI left out
     * @throws InvalidAssessmentException if the answer is unusable
     */
    public static Assessment fromJson(Object json, NewsItem item) {
        Objects.requireNonNull(item, "item");
        if (!(json instanceof Map<?, ?> fields)) {
            throw new InvalidAssessmentException("The answer must be one JSON object, not "
                    + (json instanceof List ? "a list" : "something else"));
        }
        boolean real = readBoolean(fields.get("real"));
        double confidence = readConfidence(fields.get("confidence"), real);
        IncidentType type = readType(fields.get("type"), item.topic());
        Optional<GeoPoint> location = readLocation(fields.get("latitude"), fields.get("longitude"));
        double radius = clamp(readNumber(fields.get("areaRadiusKm"), "areaRadiusKm", DEFAULT_RADIUS_KM), MIN_RADIUS_KM, MAX_RADIUS_KM);
        Severity severity = readSeverity(fields.get("severity"));
        int people = (int) Math.round(clamp(readNumber(fields.get("estimatedPeople"), "estimatedPeople", DEFAULT_PEOPLE), 0, MAX_PEOPLE));

        String title = cut(readText(fields.get("title")), MAX_TITLE_CHARS);
        String summary = cut(readText(fields.get("summary")), MAX_SUMMARY_CHARS);
        return new Assessment(
                real,
                confidence,
                type,
                title.isEmpty() ? cut(item.title(), MAX_TITLE_CHARS) : title,
                summary.isEmpty() ? cut(item.text(), MAX_SUMMARY_CHARS) : summary,
                cut(readText(fields.get("locationName")), MAX_TITLE_CHARS),
                location,
                radius,
                severity,
                people,
                cut(readText(fields.get("reason")), MAX_SUMMARY_CHARS));
    }

    private static boolean readBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            String t = s.strip().toLowerCase(Locale.ROOT);
            if (t.equals("true") || t.equals("yes")) {
                return true;
            }
            if (t.equals("false") || t.equals("no")) {
                return false;
            }
        }
        throw new InvalidAssessmentException("\"real\" must be true or false");
    }

    private static double readConfidence(Object value, boolean real) {
        double c = readNumber(value, "confidence", real ? 0.5 : 0.0);
        if (c > 1.0 && c <= 100.0) {
            c = c / 100.0; // some models answer in percent
        }
        return clamp(c, 0.0, 1.0);
    }

    private static IncidentType readType(Object value, IncidentType fallback) {
        if (value instanceof String s && !s.isBlank()) {
            try {
                return IncidentType.parse(s);
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Severity readSeverity(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Severity.parse(s);
            } catch (IllegalArgumentException e) {
                return Severity.MODERATE;
            }
        }
        return Severity.MODERATE;
    }

    private static Optional<GeoPoint> readLocation(Object latValue, Object lonValue) {
        boolean noLat = latValue == null;
        boolean noLon = lonValue == null;
        if (noLat && noLon) {
            return Optional.empty();
        }
        if (noLat || noLon) {
            throw new InvalidAssessmentException("Give both \"latitude\" and \"longitude\", or null for both");
        }
        double lat = readNumber(latValue, "latitude", Double.NaN);
        double lon = readNumber(lonValue, "longitude", Double.NaN);
        if (lat < -90 || lat > 90) {
            throw new InvalidAssessmentException("\"latitude\" must be between -90 and 90, not " + lat);
        }
        if (lon < -180 || lon > 180) {
            throw new InvalidAssessmentException("\"longitude\" must be between -180 and 180, not " + lon);
        }
        if (lat == 0.0 && lon == 0.0) {
            return Optional.empty(); // "0, 0" is what models write when they do not know
        }
        return Optional.of(new GeoPoint(lat, lon));
    }

    private static double readNumber(Object value, String name, double whenMissing) {
        if (value == null) {
            if (Double.isNaN(whenMissing)) {
                throw new InvalidAssessmentException("\"" + name + "\" is missing");
            }
            return whenMissing;
        }
        double result;
        if (value instanceof Number number) {
            result = number.doubleValue();
        } else if (value instanceof String text) {
            try {
                result = Double.parseDouble(text.strip());
            } catch (NumberFormatException e) {
                throw new InvalidAssessmentException("\"" + name + "\" must be a number, not \"" + text + "\"");
            }
        } else {
            throw new InvalidAssessmentException("\"" + name + "\" must be a number");
        }
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new InvalidAssessmentException("\"" + name + "\" must be a finite number");
        }
        return result;
    }

    private static String readText(Object value) {
        return value instanceof String s ? s.strip() : "";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3).stripTrailing() + "...";
    }
}
