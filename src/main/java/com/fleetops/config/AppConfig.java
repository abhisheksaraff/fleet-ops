package com.fleetops.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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

    public static final String DEFAULT_LLM_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_DB_URL = "jdbc:h2:./data/fleetops";
    public static final String DEFAULT_MAP_TILES = "osm";

    /** The value shipped in {@code .env.example}; it is treated as "no key". */
    public static final String PLACEHOLDER_API_KEY = "YOUR_LOCAL_API_KEY_HERE";

    private final LlmProviderType llmProvider;
    private final String llmApiKey; // may be null
    private final String llmModel; // may be null
    private final String llmBaseUrl;
    private final String dbUrl;
    private final String mapTiles;

    private AppConfig(
            LlmProviderType llmProvider,
            String llmApiKey,
            String llmModel,
            String llmBaseUrl,
            String dbUrl,
            String mapTiles) {
        this.llmProvider = llmProvider;
        this.llmApiKey = llmApiKey;
        this.llmModel = llmModel;
        this.llmBaseUrl = llmBaseUrl;
        this.dbUrl = dbUrl;
        this.mapTiles = mapTiles;
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

        return new AppConfig(
                provider,
                apiKey,
                pick(KEY_LLM_MODEL, environment, fileValues),
                orDefault(pick(KEY_LLM_BASE_URL, environment, fileValues), DEFAULT_LLM_BASE_URL),
                orDefault(pick(KEY_DB_URL, environment, fileValues), DEFAULT_DB_URL),
                orDefault(pick(KEY_MAP_TILES, environment, fileValues), DEFAULT_MAP_TILES));
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

    /** Human-readable summary that is safe to print, log or show on screen (no secrets). */
    public String describe() {
        return String.join(
                System.lineSeparator(),
                "LLM provider : " + llmProvider.name().toLowerCase(Locale.ROOT),
                "LLM model    : " + llmModel().orElse("(provider default)"),
                "LLM base URL : " + llmBaseUrl,
                "LLM API key  : " + (llmApiKey != null ? "set" : "not set"),
                "Database URL : " + dbUrl,
                "Map tiles    : " + mapTiles);
    }

    @Override
    public String toString() {
        return "AppConfig[" + describe().replace(System.lineSeparator(), ", ") + "]";
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
