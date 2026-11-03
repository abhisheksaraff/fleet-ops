package com.fleetops.news;

import com.fleetops.domain.incident.IncidentType;
import com.fleetops.repository.IncidentRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Picks what to look at next: for each topic, the newest item whose article has not been stored as
 * an incident yet. One item per topic, so a single scan never floods the AI with the same story.
 */
public final class NewsCollector {

    private static final int CANDIDATES_PER_TOPIC = 10;

    /**
     * @param items the chosen items, in the order the topics were given
     * @param failures topics whose source could not be read, with the reason
     */
    public record CollectedNews(List<NewsItem> items, Map<IncidentType, String> failures) {

        public CollectedNews {
            items = List.copyOf(items);
            failures = Map.copyOf(failures);
        }
    }

    private final NewsSource source;
    private final IncidentRepository repository;
    private final Predicate<NewsItem> alsoSkip;

    public NewsCollector(NewsSource source, IncidentRepository repository) {
        this(source, repository, item -> false);
    }

    /**
     * @param alsoSkip items to leave out besides the ones already stored, for example articles that
     *     were examined and turned down
     */
    public NewsCollector(NewsSource source, IncidentRepository repository, Predicate<NewsItem> alsoSkip) {
        this.source = Objects.requireNonNull(source, "source");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.alsoSkip = Objects.requireNonNull(alsoSkip, "alsoSkip");
    }

    public CollectedNews collectOnePerTopic(Collection<IncidentType> topics) {
        List<NewsItem> chosen = new ArrayList<>();
        Map<IncidentType, String> failures = new LinkedHashMap<>();
        Set<String> alreadyChosen = new HashSet<>();
        for (IncidentType topic : new LinkedHashSet<>(topics)) {
            try {
                Optional<NewsItem> next = source.fetch(topic, CANDIDATES_PER_TOPIC).stream()
                        .filter(item -> !repository.existsBySourceUrl(item.url()))
                        .filter(item -> !alsoSkip.test(item))
                        .filter(item -> !alreadyChosen.contains(item.key()))
                        .findFirst();
                next.ifPresent(item -> {
                    chosen.add(item);
                    alreadyChosen.add(item.key());
                });
            } catch (NewsSourceException e) {
                failures.put(topic, e.getMessage());
            }
        }
        return new CollectedNews(chosen, failures);
    }
}
