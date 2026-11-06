package com.fleetops.app;

import com.fleetops.ai.JdkHttpPoster;
import com.fleetops.ai.LlmClient;
import com.fleetops.ai.LlmClients;
import com.fleetops.config.AppConfig;
import com.fleetops.config.ConfigException;
import com.fleetops.config.NewsSourceType;
import com.fleetops.intel.IntelligenceService;
import com.fleetops.intel.LlmNewsAnalyzer;
import com.fleetops.intel.MockIntelligence;
import com.fleetops.news.CompositeNewsSource;
import com.fleetops.news.DemoNewsSource;
import com.fleetops.news.GoogleNewsRssSource;
import com.fleetops.news.HttpFetcher;
import com.fleetops.news.JdkHttpFetcher;
import com.fleetops.news.NewsSource;
import com.fleetops.news.TelegramChannelSource;
import com.fleetops.plan.LlmResponsePlanner;
import com.fleetops.plan.MockPlanning;
import com.fleetops.plan.ResilientPlanner;
import com.fleetops.plan.ResponsePlanner;
import com.fleetops.plan.RuleBasedPlanner;
import com.fleetops.repository.InMemoryIncidentRepository;
import com.fleetops.repository.IncidentRepository;
import com.fleetops.repository.RepositoryException;
import com.fleetops.repository.jdbc.DriverManagerConnectionSource;
import com.fleetops.repository.jdbc.JdbcIncidentRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Composition root: the single place that decides which concrete implementations the application
 * uses (LLM vendor, database, news sources).
 *
 * <p>Both the GUI and the CLI obtain their collaborators from here, and the core packages only ever
 * see interfaces. Swapping a technology means changing the choice made in this class (driven by
 * {@code .env}) and adding one adapter, never touching the core:
 *
 * <ul>
 *   <li>{@code LLM_PROVIDER} picks the AI client ({@link #llm()});
 *   <li>{@code DB_URL} picks the incident store: {@code memory}, or any JDBC URL ({@link #repository()});
 *   <li>{@code NEWS_SOURCES} picks the feeds that are read ({@link #newsSource()}).
 * </ul>
 *
 * <p>Every collaborator is built on first use and then shared. Call {@link #close()} when the program
 * ends so that the database is closed cleanly.
 */
public final class AppContext implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(AppContext.class.getName());

    private final AppConfig config;
    private final Clock clock;
    private LlmClient llm;
    private IncidentRepository repository;
    private NewsSource newsSource;
    private IntelligenceService intelligence;
    private ResponsePlanner planner;
    private boolean keepOpen;

    private AppContext(AppConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    public static AppContext create(AppConfig config) {
        return create(config, Clock.systemUTC());
    }

    /** @param clock the time source for news ages and report times; tests pass a fixed clock */
    public static AppContext create(AppConfig config, Clock clock) {
        return new AppContext(Objects.requireNonNull(config, "config"), Objects.requireNonNull(clock, "clock"));
    }

    /** Builds a context from {@code ./.env} and the process environment. */
    public static AppContext fromEnvironment() {
        return create(AppConfig.fromEnvironment());
    }

    public AppConfig config() {
        return config;
    }

    public Clock clock() {
        return clock;
    }

    /**
     * The AI client chosen by {@code LLM_PROVIDER}, built on first use and then shared. A missing
     * Gemini key is already reported when the configuration is loaded.
     */
    public synchronized LlmClient llm() {
        if (llm == null) {
            MockIntelligence verification = new MockIntelligence();
            MockPlanning planning = new MockPlanning();
            llm = LlmClients.create(config, new JdkHttpPoster(),
                    request -> MockPlanning.handles(request) ? planning.apply(request) : verification.apply(request));
        }
        return llm;
    }

    /**
     * Where incidents are stored, chosen by {@code DB_URL}: {@code memory} keeps them in memory
     * only, a {@code jdbc:} URL (H2 by default) saves them to a database.
     *
     * @throws ConfigException if the URL is not understood or the database cannot be opened
     */
    public synchronized IncidentRepository repository() {
        if (repository == null) {
            repository = openRepository(config);
        }
        return repository;
    }

    /**
     * The news feeds chosen by {@code NEWS_SOURCES}, combined into one source when there are several.
     *
     * @throws ConfigException if a feed is set up wrongly (for example an invalid Telegram channel name)
     */
    public synchronized NewsSource newsSource() {
        if (newsSource == null) {
            newsSource = buildNewsSource();
        }
        return newsSource;
    }

    /** The service that reads the news, has the AI verify it and stores new incidents. */
    public synchronized IntelligenceService intelligence() {
        if (intelligence == null) {
            intelligence = new IntelligenceService(
                    newsSource(), new LlmNewsAnalyzer(llm()), repository(), IntelligenceService.Settings.defaults(), clock);
        }
        return intelligence;
    }

    /** The AI planner, with the fixed rules as a fallback when the AI is unavailable or answers badly. */
    public synchronized ResponsePlanner planner() {
        if (planner == null) {
            planner = new ResilientPlanner(new LlmResponsePlanner(llm()), new RuleBasedPlanner());
        }
        return planner;
    }

    /**
     * Makes {@link #close()} do nothing from now on, so code that closes the context it is given (every
     * command-line command does) can run against a context that its caller still owns, as the GUI's
     * console does. The owner ends the context with {@link #shutdown()}.
     */
    public synchronized AppContext keepOpen() {
        keepOpen = true;
        return this;
    }

    /** Closes the database for real, even after {@link #keepOpen()}. */
    public synchronized void shutdown() {
        keepOpen = false;
        close();
    }

    /** Closes the database if one was opened. Safe to call more than once; does nothing after {@link #keepOpen()}. */
    @Override
    public synchronized void close() {
        if (keepOpen) {
            return;
        }
        if (repository instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not close the incident store cleanly: " + e.getMessage());
            }
        }
        repository = null;
        intelligence = null;
    }

    // ---- choices made from the configuration ---------------------------------------------------

    static IncidentRepository openRepository(AppConfig config) {
        String url = config.dbUrl();
        if (url.equalsIgnoreCase(AppConfig.MEMORY_DB_URL)) {
            return new InMemoryIncidentRepository();
        }
        if (!url.regionMatches(true, 0, "jdbc:", 0, 5)) {
            throw new ConfigException("DB_URL must be '" + AppConfig.MEMORY_DB_URL
                    + "' or a JDBC URL such as jdbc:h2:./data/fleetops, but it is '" + url + "'.");
        }
        try {
            return new JdbcIncidentRepository(new DriverManagerConnectionSource(url, config.dbUser(), config.dbPassword()));
        } catch (RepositoryException e) {
            throw new ConfigException("Could not open the database " + url + ": " + e.getMessage()
                    + " (Is the database driver on the classpath? Run 'mvn compile' once so Maven downloads it.)", e);
        }
    }

    private NewsSource buildNewsSource() {
        HttpFetcher fetcher = new JdkHttpFetcher();
        List<NewsSource> sources = new ArrayList<>();
        for (NewsSourceType type : config.newsSources()) {
            switch (type) {
                case DEMO -> sources.add(new DemoNewsSource(clock));
                case GOOGLE -> sources.add(new GoogleNewsRssSource(fetcher, clock, config.newsGoogleEdition()));
                case TELEGRAM -> {
                    try {
                        sources.add(new TelegramChannelSource(fetcher, clock, config.telegramChannels()));
                    } catch (IllegalArgumentException e) {
                        throw new ConfigException("TELEGRAM_CHANNELS: " + e.getMessage(), e);
                    }
                }
            }
        }
        return sources.size() == 1 ? sources.get(0) : new CompositeNewsSource(sources);
    }
}
