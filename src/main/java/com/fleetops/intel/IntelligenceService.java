package com.fleetops.intel;

import com.fleetops.ai.LlmException;
import com.fleetops.domain.geo.GeoArea;
import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import com.fleetops.domain.incident.IncidentType;
import com.fleetops.news.ArticleEnricher;
import com.fleetops.news.NewsCollector;
import com.fleetops.news.NewsItem;
import com.fleetops.news.NewsSource;
import com.fleetops.repository.DuplicateIncidentException;
import com.fleetops.repository.IncidentRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Facade for the whole "news to incident" pipeline: pick the newest unseen article for each topic,
 * have the {@link NewsAnalyzer} verify it and extract the details, then store it in the
 * {@link IncidentRepository} without duplicates. Callers (the GUI and the CLI) make one call,
 * {@link #scan}, and get back a report.
 *
 * <p>An item ends up in exactly one of four states, see {@link IngestOutcome.Status}. Rejected items
 * go to the {@link ReviewLog} and are never examined again; failed ones are tried again on the next
 * scan, but only up to {@link Settings#maxAnalysisFailures()} times.
 */
public final class IntelligenceService {

    private static final System.Logger LOG = System.getLogger(IntelligenceService.class.getName());

    /**
     * Tunable limits.
     *
     * @param minConfidence the AI must be at least this sure that an item is real (0 to 1)
     * @param duplicateDistanceKm a new incident of the same type this close to an active one is the same event
     * @param maxAnalysisFailures how many times the AI may fail on one item before it is given up on
     */
    public record Settings(double minConfidence, double duplicateDistanceKm, int maxAnalysisFailures) {

        public Settings {
            if (Double.isNaN(minConfidence) || minConfidence < 0 || minConfidence > 1) {
                throw new IllegalArgumentException("minConfidence must be between 0 and 1: " + minConfidence);
            }
            if (!(duplicateDistanceKm >= 0) || Double.isInfinite(duplicateDistanceKm)) {
                throw new IllegalArgumentException("duplicateDistanceKm must not be negative: " + duplicateDistanceKm);
            }
            if (maxAnalysisFailures < 1) {
                throw new IllegalArgumentException("maxAnalysisFailures must be at least 1: " + maxAnalysisFailures);
            }
        }

        public static Settings defaults() {
            return new Settings(0.6, 5.0, 2);
        }
    }

    private final ArticleEnricher enricher;
    private final NewsAnalyzer analyzer;
    private final IncidentRepository repository;
    private final Settings settings;
    private final Clock clock;
    private final ReviewLog reviewLog = new ReviewLog();
    private final NewsCollector collector;
    private final Map<String, Integer> failures = new HashMap<>();

    public IntelligenceService(NewsSource source, NewsAnalyzer analyzer, IncidentRepository repository, Settings settings, Clock clock) {
        this(source, ArticleEnricher.NONE, analyzer, repository, settings, clock);
    }

    /** @param enricher fetches more text for an item before the AI reads it */
    public IntelligenceService(NewsSource source, ArticleEnricher enricher, NewsAnalyzer analyzer, IncidentRepository repository,
            Settings settings, Clock clock) {
        Objects.requireNonNull(source, "source");
        this.enricher = Objects.requireNonNull(enricher, "enricher");
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.collector = new NewsCollector(source, repository, item -> reviewLog.contains(item.url()));
    }

    /** Everything the AI has turned down so far, with reasons. */
    public ReviewLog reviewLog() {
        return reviewLog;
    }

    /**
     * Looks for one new item per topic and processes each.
     *
     * @return what happened; never throws because of a single bad item
     */
    public synchronized IngestReport scan(Collection<IncidentType> topics) {
        NewsCollector.CollectedNews collected = collector.collectOnePerTopic(topics);
        List<IngestOutcome> outcomes = new ArrayList<>();
        for (NewsItem item : collected.items()) {
            outcomes.add(process(item));
        }
        IngestReport report = new IngestReport(outcomes, collected.failures());
        LOG.log(System.Logger.Level.INFO, "Scan finished: {0}", report.summary());
        return report;
    }

    private IngestOutcome process(NewsItem item) {
        Assessment assessment;
        try {
            assessment = analyzer.analyze(enricher.enrich(item));
        } catch (LlmException | AnalysisException e) {
            return analysisFailed(item, e);
        }
        failures.remove(item.key());

        if (!assessment.real()) {
            return reject(item, "not a real incident" + because(assessment));
        }
        if (assessment.confidence() < settings.minConfidence()) {
            return reject(item, String.format(Locale.ROOT, "confidence %.2f is below %.2f%s",
                    assessment.confidence(), settings.minConfidence(), because(assessment)));
        }
        if (assessment.location().isEmpty()) {
            return reject(item, "the location could not be determined" + because(assessment));
        }
        return store(item, assessment, assessment.location().get());
    }

    private IngestOutcome store(NewsItem item, Assessment assessment, GeoPoint center) {
        Incident incident = Incident.builder(assessment.type(), assessment.title(), new GeoArea(center, assessment.areaRadiusKm()))
                .summary(summaryWithPlace(assessment))
                .severity(assessment.severity())
                .estimatedPeople(assessment.estimatedPeople())
                .sourceUrl(item.url())
                .reportedAt(item.publishedAt())
                .build();

        Optional<Incident> sameEvent = findSameEvent(incident);
        if (sameEvent.isPresent()) {
            incident.markDuplicate();
        } else {
            incident.verify();
        }
        try {
            repository.save(incident);
        } catch (DuplicateIncidentException e) {
            return new IngestOutcome(item, IngestOutcome.Status.DUPLICATE, Optional.empty(), "this article is already stored");
        }
        if (sameEvent.isPresent()) {
            Incident original = sameEvent.get();
            return new IngestOutcome(item, IngestOutcome.Status.DUPLICATE, Optional.of(incident), String.format(Locale.ROOT,
                    "same event as incident #%s, %.1f km away", original.id().map(String::valueOf).orElse("?"),
                    original.area().center().distanceKmTo(center)));
        }
        return new IngestOutcome(item, IngestOutcome.Status.CREATED, Optional.of(incident), "verified" + because(assessment));
    }

    private Optional<Incident> findSameEvent(Incident candidate) {
        GeoPoint center = candidate.area().center();
        return repository.findActive().stream()
                .filter(other -> other.type() == candidate.type())
                .filter(other -> other.area().center().distanceKmTo(center) <= settings.duplicateDistanceKm())
                .findFirst();
    }

    private IngestOutcome analysisFailed(NewsItem item, RuntimeException cause) {
        int count = failures.merge(item.key(), 1, Integer::sum);
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        if (count >= settings.maxAnalysisFailures()) {
            failures.remove(item.key());
            return reject(item, "analysis failed " + count + " times, giving up: " + message);
        }
        return new IngestOutcome(item, IngestOutcome.Status.FAILED, Optional.empty(),
                "analysis failed (attempt " + count + " of " + settings.maxAnalysisFailures() + "): " + message);
    }

    private IngestOutcome reject(NewsItem item, String reason) {
        reviewLog.add(item, reason, clock.instant());
        return new IngestOutcome(item, IngestOutcome.Status.REJECTED, Optional.empty(), reason);
    }

    private static String because(Assessment assessment) {
        return assessment.reason().isEmpty() ? "" : " (" + assessment.reason() + ")";
    }

    private static String summaryWithPlace(Assessment assessment) {
        return assessment.locationName().isEmpty() ? assessment.summary() : assessment.summary() + " [" + assessment.locationName() + "]";
    }
}
