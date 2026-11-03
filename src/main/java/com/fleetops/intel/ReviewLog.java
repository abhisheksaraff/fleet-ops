package com.fleetops.intel;

import com.fleetops.news.NewsItem;
import com.fleetops.repository.SourceUrls;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Remembers the news items the AI looked at and turned down, with the reason, so the same article
 * is not sent to the AI again on the next scan. Operators can read it to see what was ignored.
 * Safe to use from several threads; kept in memory only.
 */
public final class ReviewLog {

    /** One turned-down item. */
    public record Entry(NewsItem item, String reason, Instant at) {

        public Entry {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(at, "at");
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public synchronized void add(NewsItem item, String reason, Instant at) {
        entries.putIfAbsent(item.key(), new Entry(item, reason, at));
    }

    public synchronized boolean contains(String url) {
        return url != null && !url.isBlank() && entries.containsKey(SourceUrls.normalize(url));
    }

    /** Everything turned down so far, oldest first. */
    public synchronized List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }
}
