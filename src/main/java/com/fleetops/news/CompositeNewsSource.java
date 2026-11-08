package com.fleetops.news;

import com.fleetops.domain.incident.IncidentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Several sources used as one. Results are merged, the same article found by two sources is kept
 * once, and a source that is listed first comes first (so a real feed listed before the demo feed
 * is always read before it); within one source the newest comes first. A source that fails is skipped (and logged) so one broken
 * feed does not stop the others; only when every source fails is an exception thrown.
 */
public final class CompositeNewsSource implements NewsSource {

    private static final System.Logger LOG = System.getLogger(CompositeNewsSource.class.getName());

    private final List<NewsSource> sources;

    public CompositeNewsSource(List<? extends NewsSource> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public String name() {
        return sources.stream().map(NewsSource::name).collect(Collectors.joining(" + "));
    }

    @Override
    public List<NewsItem> fetch(IncidentType topic, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
        Map<String, NewsItem> unique = new LinkedHashMap<>();
        List<NewsItem> merged = new ArrayList<>();
        List<RuntimeException> failures = new ArrayList<>();
        for (NewsSource source : sources) {
            try {
                source.fetch(topic, limit).stream()
                        .sorted(Comparator.comparing(NewsItem::publishedAt).reversed())
                        .forEach(item -> {
                            if (unique.putIfAbsent(item.key(), item) == null) {
                                merged.add(item);
                            }
                        });
            } catch (NewsSourceException e) {
                failures.add(e);
                LOG.log(System.Logger.Level.WARNING, "{0} failed: {1}", source.name(), e.getMessage());
            }
        }
        if (!sources.isEmpty() && failures.size() == sources.size()) {
            throw new NewsSourceException("Every news source failed. First problem: " + failures.get(0).getMessage(), failures.get(0));
        }
        return merged.stream().limit(limit).toList();
    }
}
