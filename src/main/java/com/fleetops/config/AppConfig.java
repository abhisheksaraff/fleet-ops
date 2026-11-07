package com.fleetops.config;

import com.fleetops.domain.incident.IncidentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable application settings.
 *
 * <p>Values are resolved in this order: process environment variables, then the local {@code .env}
 * file, then built-in defaults. The {@code .env} file holds secrets such as the LLM API key and is
 * gitignored; only {@code .env.example} (placeholders) is committed.
 *
 * <p>The API key is never included in {@link #toString()} or {@link #describe()}.
 */
public final class AppConfig {

    public static final String KEY_LLM_PROVIDER = "LLM_PROVIDER";
    public static final String KEY_LLM_API_KEY = "LLM_API_KEY";
    public static final String KEY_LLM_MODEL = "LLM_MODEL";
    public static final String KEY_LLM_BASE_URL = "LLM_BASE_URL";
    public static final String KEY_DB_URL = "DB_URL";
    public static final String KEY_MAP_TILES = "MAP_TILES";
    public static final String KEY_CARTO_API_KEY = "CARTO_API_KEY";
    public static final String KEY_DB_USER = "DB_USER";
    public static final String KEY_DB_PASSWORD = "DB_PASSWORD";
    public static final String KEY_NEWS_SOURCES = "NEWS_SOURCES";
    public static final String KEY_TELEGRAM_CHANNELS = "TELEGRAM_CHANNELS";
    public static final String KEY_NEWS_GOOGLE_EDITION = "NEWS_GOOGLE_EDITION";
    public static final String KEY_NEWS_TOPICS = "NEWS_TOPICS";

    public static final String DEFAULT_LLM_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_DB_URL = "jdbc:h2:./data/fleetops";
    public static final String DEFAULT_MAP_TILES = "dark";
    /** The built-in H2 user; embedded H2 creates the database for whoever connects first. */
    public static final String DEFAULT_DB_USER = "sa";
    /** A {@code DB_URL} of {@code memory} keeps incidents in memory only (nothing is saved). */
    public static final String MEMORY_DB_URL = "memory";
    /** Canadian English edition of Google News. */
    public static final String DEFAULT_NEWS_GOOGLE_EDITION = "hl=en-CA&gl=CA&ceid=CA:en";

    /** The value shipped in {@code .env.example}; it is treated as "no key". */
    public static final String PLACEHOLDER_API_KEY = "YOUR_LOCAL_API_KEY_HERE";
    /** The value shipped in {@code .env.example} for the CARTO key; also treated as "no key". */
    public static final String PLACEHOLDER_CARTO_KEY = "YOUR_CARTO_KEY_HERE";

    private final LlmProviderType llmProvider;
    private final String llmApiKey; // may be null
    private final String llmModel; // may be null
    private final String llmBaseUrl;
    private final String dbUrl;
    private final String mapTiles;
    private final String cartoApiKey; // may be null
    private final String dbUser;
    private final String dbPassword; // may be empty, never printed
    private final List<NewsSourceType> newsSources;
    private final List<String> telegramChannels;
    private final String newsGoogleEdition;
    private final List<IncidentType> newsTopics;

    private AppConfig(
            LlmProviderType llmProvider,
            String llmApiKey,
            String llmModel,
            String llmBaseUrl,
            String dbUrl,
            String mapTiles,
            String cartoApiKey,
            String dbUser,
            String dbPassword,
            List<NewsSourceType> newsSources,
            List<String> telegramChannels,
            String newsGoogleEdition,
            List<IncidentType> newsTopics) {
        this.llmProvider = llmProvider;
        this.llmApiKey = llmApiKey;
        this.llmModel = llmModel;
        this.llmBaseUrl = llmBaseUrl;
        this.dbUrl = dbUrl;
        this.mapTiles = mapTiles;
        this.cartoApiKey = cartoApiKey;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.newsSources = newsSources;
        this.telegramChannels = telegramChannels;
        this.newsGoogleEdition = newsGoogleEdition;
        this.newsTopics = newsTopics;
    }

    /** Loads {@code ./.env} (if present) combined with the real process environment. */
    public static AppConfig fromEnvironment() {
        return load(Path.of(".env"), System.getenv());
    }

    /**
     * Loads settings from an optional {@code .env} file and an environment map.
     *
     * @param envFile path of the .env file; a missing file is fine
     * @param environment environment variables, which take precedence over the file
     * @throws ConfigException if the file cannot be read or the settings are invalid
     */
    public static AppConfig load(Path envFile, Map<String, String> environment) {
        Map<String, String> fileValues = readEnvFile(envFile);

        LlmProviderType provider = pick(KEY_LLM_PROVIDER, environment, fileValues) == null
                ? LlmProviderType.MOCK
                : LlmProviderType.parse(pick(KEY_LLM_PROVIDER, environment, fileValues));

        String apiKey = pick(KEY_LLM_API_KEY, environment, fileValues);
        if (PLACEHOLDER_API_KEY.equals(apiKey)) {
            apiKey = null;
        }
        if (provider == LlmProviderType.GEMINI && apiKey == null) {
            throw new ConfigException("LLM_PROVIDER=gemini needs LLM_API_KEY. Copy .env.example to .env "
                    + "and put your key there (the real .env file is gitignored).");
        }

        List<NewsSourceType> sources = parseSources(pick(KEY_NEWS_SOURCES, environment, fileValues));
        List<String> channels = splitList(pick(KEY_TELEGRAM_CHANNELS, environment, fileValues));
        if (sources.contains(NewsSourceType.TELEGRAM) && channels.isEmpty()) {
            throw new ConfigException("NEWS_SOURCES includes telegram, so TELEGRAM_CHANNELS must list at least one "
                    + "public channel name (for example: bcnews,emergency_alerts).");
        }

        return new AppConfig(
                provider,
                apiKey,
                pick(KEY_LLM_MODEL, environment, fileValues),
                orDefault(pick(KEY_LLM_BASE_URL, environment, fileValues), DEFAULT_LLM_BASE_URL),
                orDefault(pick(KEY_DB_URL, environment, fileValues), DEFAULT_DB_URL),
                orDefault(pick(KEY_MAP_TILES, environment, fileValues), DEFAULT_MAP_TILES),
                cartoKey(pick(KEY_CARTO_API_KEY, environment, fileValues)),
                orDefault(pick(KEY_DB_USER, environment, fileValues), DEFAULT_DB_USER),
                orDefault(pick(KEY_DB_PASSWORD, environment, fileValues), ""),
                sources,
                channels,
                orDefault(pick(KEY_NEWS_GOOGLE_EDITION, environment, fileValues), DEFAULT_NEWS_GOOGLE_EDITION),
                parseTopics(pick(KEY_NEWS_TOPICS, environment, fileValues)));
    }

    private static String cartoKey(String raw) {
        return raw == null || raw.isBlank() || raw.equals(PLACEHOLDER_CARTO_KEY) ? null : raw.strip();
    }

    public LlmProviderType llmProvider() {
        return llmProvider;
    }

    public Optional<String> llmApiKey() {
        return Optional.ofNullable(llmApiKey);
    }

    public Optional<String> llmModel() {
        return Optional.ofNullable(llmModel);
    }

    public String llmBaseUrl() {
        return llmBaseUrl;
    }

    public String dbUrl() {
        return dbUrl;
    }

    public String mapTiles() {
        return mapTiles;
    }

    /** The CARTO basemap key the dark map needs; empty when none is set. Keep it out of logs and screens. */
    public Optional<String> cartoApiKey() {
        return Optional.ofNullable(cartoApiKey);
    }

    public String dbUser() {
        return dbUser;
    }

    /** The database password; empty when none is set. Keep it out of logs and screens. */
    public String dbPassword() {
        return dbPassword;
    }

    /** The news sources to read, in the order given (never empty). */
    public List<NewsSourceType> newsSources() {
        return newsSources;
    }

    /** Public Telegram channel names without the leading {@code @}. */
    public List<String> telegramChannels() {
        return telegramChannels;
    }

    /** The Google News edition parameters, for example {@code hl=en-CA&gl=CA&ceid=CA:en}. */
    public String newsGoogleEdition() {
        return newsGoogleEdition;
    }

    /** The disaster types to look for (never empty). */
    public List<IncidentType> newsTopics() {
        return newsTopics;
    }

    /** Human-readable summary that is safe to print, log or show on screen (no secrets). */
    public String describe() {
        return String.join(
                System.lineSeparator(),
                "LLM provider : " + llmProvider.name().toLowerCase(Locale.ROOT),
                "LLM model    : " + llmModel().orElse("(provider default)"),
                "LLM base URL : " + llmBaseUrl,
                "LLM API key  : " + (llmApiKey != null ? "set" : "not set"),
                "Database URL : " + dbUrl,
                "Database user: " + dbUser + (dbPassword.isEmpty() ? " (no password)" : " (password set)"),
                "Map tiles    : " + mapTiles,
                "CARTO key    : " + (cartoApiKey != null ? "set" : "not set"),
                "News sources : " + newsSources.stream().map(t -> t.name().toLowerCase(Locale.ROOT)).toList(),
                "Telegram     : " + (telegramChannels.isEmpty() ? "(none)" : telegramChannels.size() + " channel(s)"),
                "News topics  : " + newsTopics.stream().map(IncidentType::keyword).toList());
    }

    @Override
    public String toString() {
        return "AppConfig[" + describe().replace(System.lineSeparator(), ", ") + "]";
    }

    // ---- list settings ------------------------------------------------------------------------

    /** Splits {@code a, b,,c} into {@code [a, b, c]}: trimmed, no blanks, no repeats, order kept. */
    static List<String> splitList(String raw) {
        if (raw == null) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(Arrays.stream(raw.split(","))
                .map(String::strip)
                .map(item -> item.startsWith("@") ? item.substring(1).strip() : item)
                .filter(item -> !item.isEmpty())
                .toList()));
    }

    private static List<NewsSourceType> parseSources(String raw) {
        List<NewsSourceType> sources = new ArrayList<>();
        for (String name : splitList(raw)) {
            NewsSourceType type = NewsSourceType.parse(name);
            if (!sources.contains(type)) {
                sources.add(type);
            }
        }
        return sources.isEmpty() ? List.of(NewsSourceType.DEMO) : List.copyOf(sources);
    }

    private static List<IncidentType> parseTopics(String raw) {
        List<IncidentType> topics = new ArrayList<>();
        try {
            for (String name : splitList(raw)) {
                IncidentType type = IncidentType.parse(name);
                if (!topics.contains(type)) {
                    topics.add(type);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Invalid NEWS_TOPICS: " + e.getMessage(), e);
        }
        return topics.isEmpty() ? List.of(IncidentType.values()) : List.copyOf(topics);
    }

    // ---- .env parsing -------------------------------------------------------------------------

    private static Map<String, String> readEnvFile(Path envFile) {
        if (envFile == null || !Files.isRegularFile(envFile)) {
            return Map.of();
        }
        try {
            return parseEnvLines(Files.readAllLines(envFile));
        } catch (IOException e) {
            throw new ConfigException("Could not read " + envFile + ": " + e.getMessage(), e);
        }
    }

    /** Parses {@code KEY=VALUE} lines. Blank lines, comments and malformed lines are skipped. */
    static Map<String, String> parseEnvLines(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            values.put(line.substring(0, eq).strip(), parseValue(line.substring(eq + 1).strip()));
        }
        return values;
    }

    private static String parseValue(String value) {
        if (!value.isEmpty() && (value.charAt(0) == '"' || value.charAt(0) == '\'')) {
            char quote = value.charAt(0);
            int end = value.indexOf(quote, 1);
            if (end > 0) {
                return value.substring(1, end);
            }
        }
        int comment = value.indexOf(" #");
        if (comment >= 0) {
            value = value.substring(0, comment);
        }
        return value.strip();
    }

    /** Environment first, then file; blank values count as unset. */
    private static String pick(String key, Map<String, String> environment, Map<String, String> fileValues) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            value = fileValues.get(key);
        }
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
