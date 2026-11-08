package com.fleetops.news;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the full article behind a Google News item. The feed gives only a headline, so the AI would
 * often have no place name to work from; the article text gives it one. Items from other sources
 * (the demo feed, Telegram posts that already hold the whole text) pass through unchanged, and so
 * does any item whose article cannot be found, downloaded or read.
 */
public final class GoogleArticleEnricher implements ArticleEnricher {

    private static final System.Logger LOG = System.getLogger(GoogleArticleEnricher.class.getName());

    /** Longest text kept; the AI prompt keeps about this much anyway. */
    public static final int MAX_CHARS = 4000;
    private static final int MIN_USEFUL_CHARS = 200;

    private final HttpFetcher fetcher;

    public GoogleArticleEnricher(HttpFetcher fetcher) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    @Override
    public NewsItem enrich(NewsItem item) {
        if (!GoogleNewsLinks.isGoogleArticle(item.url())) {
            return item;
        }
        Optional<URI> address = GoogleNewsLinks.resolve(fetcher, item.url());
        if (address.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "Could not find the article behind {0}; using the headline only", item.url());
            return item;
        }
        try {
            String text = ArticleText.extract(fetcher.get(address.get()), MAX_CHARS);
            if (text.length() < MIN_USEFUL_CHARS) {
                LOG.log(System.Logger.Level.INFO, "{0} had no readable article text; using the headline only", address.get());
                return item;
            }
            return new NewsItem(item.topic(), item.title(), text, item.url(), item.source(), item.publishedAt());
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.INFO, "Could not read {0}: {1}; using the headline only", address.get(), e.getMessage());
            return item;
        }
    }
}
