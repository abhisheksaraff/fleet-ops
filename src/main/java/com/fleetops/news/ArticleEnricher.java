package com.fleetops.news;

/**
 * Gives an item more text to read before the AI judges it. News feeds often carry only a headline,
 * which is too little to find a place or a number of people; an enricher fetches the article itself.
 * Failing to do so is never an error: the item simply stays as the feed gave it.
 */
@FunctionalInterface
public interface ArticleEnricher {

    /** An enricher that changes nothing. */
    ArticleEnricher NONE = item -> item;

    /** @return the item with a longer text, or the same item if nothing better could be found */
    NewsItem enrich(NewsItem item);
}
