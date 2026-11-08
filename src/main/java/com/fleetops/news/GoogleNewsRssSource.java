package com.fleetops.news;

import com.fleetops.domain.incident.IncidentType;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Adapter for the Google News search feed: asks for the topic's keyword over the last week and
 * converts the RSS items into {@link NewsItem}s.
 */
public final class GoogleNewsRssSource implements NewsSource {

    /** Canadian English edition. */
    public static final String DEFAULT_EDITION = "hl=en-CA&gl=CA&ceid=CA:en";

    private static final String BASE = "https://news.google.com/rss/search?q=";

    private final HttpFetcher fetcher;
    private final Clock clock;
    private final String edition;

    public GoogleNewsRssSource(HttpFetcher fetcher, Clock clock) {
        this(fetcher, clock, DEFAULT_EDITION);
    }

    public GoogleNewsRssSource(HttpFetcher fetcher, Clock clock, String edition) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.edition = Objects.requireNonNull(edition, "edition");
    }

    @Override
    public String name() {
        return "Google News";
    }

    /** The feed address used for a topic. */
    URI searchUri(IncidentType topic) {
        String query = URLEncoder.encode(topic.keyword() + " when:7d", StandardCharsets.UTF_8);
        return URI.create(BASE + query + "&" + edition);
    }

    @Override
    public List<NewsItem> fetch(IncidentType topic, int limit) {
        Objects.requireNonNull(topic, "topic");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
        String xml;
        try {
            xml = fetcher.get(searchUri(topic));
        } catch (IOException e) {
            throw new NewsSourceException("Could not download Google News for '" + topic.keyword() + "': " + e.getMessage(), e);
        }
        return parse(xml, topic, limit);
    }

    List<NewsItem> parse(String xml, IncidentType topic, int limit) {
        Document document = parseXml(xml);
        NodeList itemNodes = document.getElementsByTagName("item");
        List<NewsItem> items = new ArrayList<>();
        for (int i = 0; i < itemNodes.getLength() && items.size() < limit; i++) {
            Element item = (Element) itemNodes.item(i);
            String title = HtmlText.plain(childText(item, "title"));
            String link = childText(item, "link").strip();
            if (title.isEmpty() || link.isEmpty()) {
                continue;
            }
            String outlet = HtmlText.plain(childText(item, "source"));
            items.add(new NewsItem(
                    topic,
                    withoutOutlet(title, outlet),
                    HtmlText.plain(childText(item, "description")),
                    link,
                    outlet.isEmpty() ? name() : outlet,
                    parseDate(childText(item, "pubDate"))));
        }
        return items;
    }

    /** Google adds " - Outlet" to every headline; the outlet is kept separately, so it is dropped here. */
    static String withoutOutlet(String title, String outlet) {
        String suffix = " - " + outlet;
        if (!outlet.isEmpty() && title.endsWith(suffix) && title.length() > suffix.length()) {
            return title.substring(0, title.length() - suffix.length()).stripTrailing();
        }
        return title;
    }

    private Instant parseDate(String raw) {
        try {
            return ZonedDateTime.parse(raw.strip(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return clock.instant();
        }
    }

    private static Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // A news feed must never be allowed to pull in outside files or addresses.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler()); // report problems by exception only, not on stderr
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new NewsSourceException("Google News returned something that is not a valid feed: " + e.getMessage(), e);
        }
    }

    private static String childText(Element parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return child.getTextContent() == null ? "" : child.getTextContent();
            }
        }
        return "";
    }
}
