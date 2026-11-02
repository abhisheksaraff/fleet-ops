package com.fleetops.news;

import com.fleetops.domain.incident.IncidentType;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter for public Telegram channels. It reads each channel's public web preview
 * ({@code https://t.me/s/<channel>}, no account or bot token needed) and keeps the posts that
 * mention the topic's keyword.
 */
public final class TelegramChannelSource implements NewsSource {

    private static final System.Logger LOG = System.getLogger(TelegramChannelSource.class.getName());
    private static final Pattern CHANNEL = Pattern.compile("[A-Za-z0-9_]{4,32}");
    private static final String BLOCK_MARKER = "tgme_widget_message_wrap";
    private static final Pattern POST_ID = Pattern.compile("data-post=\"([^\"]+)\"");
    private static final Pattern TEXT_DIV =
            Pattern.compile("<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);
    private static final Pattern TIME = Pattern.compile("<time[^>]*datetime=\"([^\"]+)\"");
    private static final int TITLE_CHARS = 120;

    private final HttpFetcher fetcher;
    private final Clock clock;
    private final List<String> channels;

    public TelegramChannelSource(HttpFetcher fetcher, Clock clock, List<String> channels) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        List<String> cleaned = new ArrayList<>();
        for (String raw : channels) {
            String channel = raw == null ? "" : raw.strip().replaceFirst("^@", "");
            if (!CHANNEL.matcher(channel).matches()) {
                throw new IllegalArgumentException("Not a valid Telegram channel name: '" + raw + "'");
            }
            cleaned.add(channel);
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("At least one Telegram channel is needed");
        }
        this.channels = List.copyOf(cleaned);
    }

    @Override
    public String name() {
        return "Telegram";
    }

    @Override
    public List<NewsItem> fetch(IncidentType topic, int limit) {
        Objects.requireNonNull(topic, "topic");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
        List<NewsItem> found = new ArrayList<>();
        IOException lastFailure = null;
        int failed = 0;
        for (String channel : channels) {
            try {
                found.addAll(parse(fetcher.get(URI.create("https://t.me/s/" + channel)), channel, topic));
            } catch (IOException e) {
                failed++;
                lastFailure = e;
                LOG.log(System.Logger.Level.WARNING, "Telegram channel {0} could not be read: {1}", channel, e.getMessage());
            }
        }
        if (failed == channels.size()) {
            throw new NewsSourceException("No Telegram channel could be read: " + lastFailure.getMessage(), lastFailure);
        }
        found.sort(Comparator.comparing(NewsItem::publishedAt).reversed());
        return found.size() > limit ? List.copyOf(found.subList(0, limit)) : List.copyOf(found);
    }

    List<NewsItem> parse(String html, String channel, IncidentType topic) {
        List<NewsItem> items = new ArrayList<>();
        String keyword = topic.keyword().toLowerCase(Locale.ROOT);
        String[] blocks = html.split(BLOCK_MARKER);
        for (int i = 1; i < blocks.length; i++) { // blocks[0] is the page header
            String block = blocks[i];
            Matcher text = TEXT_DIV.matcher(block);
            Matcher post = POST_ID.matcher(block);
            if (!text.find() || !post.find()) {
                continue; // a photo-only post, or something that is not a message
            }
            String plain = HtmlText.plain(text.group(1));
            if (plain.isEmpty() || !plain.toLowerCase(Locale.ROOT).contains(keyword)) {
                continue;
            }
            items.add(new NewsItem(topic, headline(plain), plain, "https://t.me/" + post.group(1), "Telegram @" + channel, postedAt(block)));
        }
        return items;
    }

    private Instant postedAt(String block) {
        Matcher time = TIME.matcher(block);
        if (time.find()) {
            try {
                return OffsetDateTime.parse(time.group(1)).toInstant();
            } catch (DateTimeParseException e) {
                // fall through to "now"
            }
        }
        return clock.instant();
    }

    private static String headline(String text) {
        if (text.length() <= TITLE_CHARS) {
            return text;
        }
        int cut = text.lastIndexOf(' ', TITLE_CHARS);
        return text.substring(0, cut > 40 ? cut : TITLE_CHARS) + "...";
    }
}
