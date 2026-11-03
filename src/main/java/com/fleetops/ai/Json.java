package com.fleetops.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A small JSON reader and writer, enough to talk to the AI services without an extra library.
 *
 * <p>Parsed values are plain Java objects: {@code Map<String, Object>} (keeps key order),
 * {@code List<Object>}, {@code String}, {@code Long} (whole numbers), {@code Double},
 * {@code Boolean} and {@code null}.
 */
public final class Json {

    private static final int MAX_DEPTH = 100;

    private Json() {}

    // ---- reading -------------------------------------------------------------------------------

    /** Parses one JSON value; anything after it (other than spaces) is an error. */
    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("No JSON text to read");
        }
        Parser parser = new Parser(text);
        parser.skipSpaces();
        Object value = parser.value(0);
        parser.skipSpaces();
        if (!parser.atEnd()) {
            throw parser.error("Unexpected text after the JSON value");
        }
        return value;
    }

    /**
     * Reads the JSON in an AI model's answer, which is often wrapped in a Markdown code fence or
     * surrounded by chatty sentences. Takes the whole text if it is valid JSON, otherwise the first
     * complete object or array found in it.
     */
    public static Object parseFromText(String modelText) {
        if (modelText == null || modelText.isBlank()) {
            throw new JsonException("The model's answer is empty");
        }
        String candidate = stripFence(modelText.strip());
        try {
            return parse(candidate);
        } catch (JsonException whole) {
            for (int start = 0; start < candidate.length(); start++) {
                char c = candidate.charAt(start);
                if (c == '{' || c == '[') {
                    int end = balancedEnd(candidate, start);
                    if (end > start) {
                        try {
                            return parse(candidate.substring(start, end + 1));
                        } catch (JsonException ignored) {
                            // not valid either; keep looking
                        }
                    }
                }
            }
            throw new JsonException("No valid JSON found in the model's answer: " + whole.getMessage());
        }
    }

    /**
     * Follows a path through parsed JSON: a {@code String} step picks an object key, an
     * {@code Integer} step picks a list element. Empty if any step is missing or of the wrong kind.
     */
    public static Optional<Object> path(Object root, Object... steps) {
        Object current = root;
        for (Object step : steps) {
            if (step instanceof String key && current instanceof Map<?, ?> map) {
                current = map.get(key);
            } else if (step instanceof Integer index && current instanceof List<?> list && index >= 0 && index < list.size()) {
                current = list.get(index);
            } else {
                return Optional.empty();
            }
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(current);
    }

    private static String stripFence(String text) {
        int open = text.indexOf("```");
        if (open < 0) {
            return text;
        }
        int bodyStart = text.indexOf('\n', open);
        if (bodyStart < 0) {
            return text;
        }
        int close = text.indexOf("```", bodyStart);
        return (close < 0 ? text.substring(bodyStart + 1) : text.substring(bodyStart + 1, close)).strip();
    }

    /** Index of the bracket that closes the one at {@code start}, ignoring brackets inside strings; -1 if none. */
    private static int balancedEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        JsonException error(String message) {
            return new JsonException(message + " (at character " + pos + ")");
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("JSON is nested too deeply");
            }
            if (atEnd()) {
                throw error("Unexpected end of JSON");
            }
            char c = text.charAt(pos);
            switch (c) {
                case '{':
                    return object(depth);
                case '[':
                    return array(depth);
                case '"':
                    return string();
                case 't':
                    return literal("true", Boolean.TRUE);
                case 'f':
                    return literal("false", Boolean.FALSE);
                case 'n':
                    return literal("null", null);
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return number();
                    }
                    throw error("Unexpected character '" + c + "'");
            }
        }

        private Object literal(String word, Object result) {
            if (!text.startsWith(word, pos)) {
                throw error("Unexpected text; expected " + word);
            }
            pos += word.length();
            return result;
        }

        private Map<String, Object> object(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipSpaces();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipSpaces();
                if (!peek('"')) {
                    throw error("Expected a quoted key");
                }
                String key = string();
                skipSpaces();
                expect(':');
                skipSpaces();
                map.put(key, value(depth + 1));
                skipSpaces();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    return map;
                } else {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private List<Object> array(int depth) {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipSpaces();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                skipSpaces();
                list.add(value(depth + 1));
                skipSpaces();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    return list;
                } else {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private String string() {
            pos++; // opening quote
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c); // raw line breaks inside strings are tolerated: models emit them
                    continue;
                }
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char escape = text.charAt(pos++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw error("Incomplete \\u escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("Invalid \\u escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("Invalid escape \\" + escape);
                }
            }
        }

        private Object number() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            int digitsStart = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (pos == digitsStart) {
                throw error("Invalid number");
            }
            if (text.charAt(digitsStart) == '0' && pos - digitsStart > 1) {
                throw error("Numbers cannot start with a leading zero");
            }
            boolean whole = true;
            if (peek('.')) {
                whole = false;
                pos++;
                int fraction = pos;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
                if (pos == fraction) {
                    throw error("Invalid number");
                }
            }
            if (peek('e') || peek('E')) {
                whole = false;
                pos++;
                if (peek('+') || peek('-')) {
                    pos++;
                }
                int exponent = pos;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
                if (pos == exponent) {
                    throw error("Invalid number");
                }
            }
            String literal = text.substring(start, pos);
            if (whole) {
                try {
                    return Long.parseLong(literal);
                } catch (NumberFormatException tooBig) {
                    return Double.parseDouble(literal);
                }
            }
            return Double.parseDouble(literal);
        }

        private boolean peek(char c) {
            return pos < text.length() && text.charAt(pos) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }
    }

    // ---- writing -------------------------------------------------------------------------------

    /** Writes a value as compact JSON. Accepts the types {@link #parse} produces, plus any Number or CharSequence. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonException("Value is nested too deeply to write");
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof Boolean || value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte) {
            out.append(value);
        } else if (value instanceof Number number) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new JsonException("NaN and infinity cannot be written as JSON");
            }
            out.append(value);
        } else if (value instanceof CharSequence text) {
            quote(text, out);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out, depth + 1);
            }
            out.append('}');
        } else if (value instanceof Collection<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(item, out, depth + 1);
            }
            out.append(']');
        } else {
            throw new JsonException("Cannot write a " + value.getClass().getName() + " as JSON");
        }
    }

    private static void quote(CharSequence text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
