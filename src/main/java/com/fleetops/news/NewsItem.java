package com.fleetops.news;

import com.fleetops.domain.incident.IncidentType;
import com.fleetops.repository.SourceUrls;
import java.time.Instant;
import java.util.Objects;

/**
 * One article or post, in the same shape whichever source it came from. This is what the adapters
 * produce and what the AI later reads to decide whether it is a real incident.
 *
 * @param topic the keyword the item was found with
 * @param title headline or first words of the post
 * @param text the longer text (summary or full post); never blank, falls back to the title
 * @param url link to the original
 * @param source who published it, for example the outlet or the Telegram channel
 * @param publishedAt when it was published
 */
public record NewsItem(IncidentType topic, String title, String text, String url, String source, Instant publishedAt) {

    public NewsItem {
        Objects.requireNonNull(topic, "topic");
        title = requireText(title, "title");
        url = requireText(url, "url");
        source = requireText(source, "source");
        Objects.requireNonNull(publishedAt, "publishedAt");
        text = text == null || text.isBlank() ? title : text.strip();
    }

    /** A key that is the same for every spelling of this article's link. */
    public String key() {
        return SourceUrls.normalize(url);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
