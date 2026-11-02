package com.fleetops.news;

import com.fleetops.domain.incident.IncidentType;
import java.util.List;

/**
 * The interface the program wants from any place that publishes news. Each real source (Google
 * News, Telegram, ...) has its own format; an <em>adapter</em> class converts it to
 * {@link NewsItem}s so nothing else needs to know the difference: the Adapter pattern.
 */
public interface NewsSource {

    /** Name for logs and screens, for example {@code Google News}. */
    String name();

    /**
     * Finds recent items about a topic.
     *
     * @param topic what to look for
     * @param limit the most items to return (at least 1)
     * @return items, newest first; empty if nothing matches
     * @throws NewsSourceException if the source cannot be read
     */
    List<NewsItem> fetch(IncidentType topic, int limit);
}
