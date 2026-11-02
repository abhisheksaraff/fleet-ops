package com.fleetops.news;

import com.fleetops.domain.incident.IncidentType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Built-in sample items for working offline (the default while no real source is configured, and
 * for demos). These are invented stories with {@code demo.fleetops.invalid} links, not real news.
 */
public final class DemoNewsSource implements NewsSource {

    private record Sample(String title, String text) {}

    private static final Map<IncidentType, List<Sample>> SAMPLES = Map.of(
            IncidentType.FLOOD, List.of(
                    new Sample("Demo: River floods Point Douglas district in Winnipeg",
                            "DEMO DATA. Rising river water has flooded the Point Douglas district of Winnipeg, Manitoba (49.9100, -97.1250). "
                                    + "Roughly 14 people are reported stranded on rooftops and the situation is serious."),
                    new Sample("Demo: Flash flood cuts off village near Kelowna",
                            "DEMO DATA. A flash flood has cut off a small village near Kelowna, British Columbia (49.8880, -119.4960). "
                                    + "About 8 residents are missing; conditions are moderate.")),
            IncidentType.FIRE, List.of(
                    new Sample("Demo: Wildfire spreads toward Jasper townsite",
                            "DEMO DATA. A fast-moving wildfire near Jasper, Alberta (52.8737, -118.0814) is spreading toward the townsite. "
                                    + "An estimated 18 hikers are unaccounted for. Severity: critical.")),
            IncidentType.EARTHQUAKE, List.of(
                    new Sample("Demo: Strong earthquake rattles Tofino coast",
                            "DEMO DATA. A strong earthquake struck off Tofino, British Columbia (49.1530, -125.9060). "
                                    + "About 11 people are trapped in collapsed buildings. Severity: high.")),
            IncidentType.FAMINE, List.of(
                    new Sample("Demo: Food shortage leaves northern community without supplies",
                            "DEMO DATA. Severe food shortages are reported in a remote community near Iqaluit, Nunavut (63.7467, -68.5170). "
                                    + "Around 12 families need urgent supplies. Severity: moderate.")));

    private final Clock clock;

    public DemoNewsSource(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "Demo feed";
    }

    @Override
    public List<NewsItem> fetch(IncidentType topic, int limit) {
        Objects.requireNonNull(topic, "topic");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
        List<Sample> samples = SAMPLES.getOrDefault(topic, List.of());
        List<NewsItem> items = new ArrayList<>();
        Instant now = clock.instant();
        for (int i = 0; i < samples.size() && items.size() < limit; i++) {
            Sample sample = samples.get(i);
            items.add(new NewsItem(topic, sample.title(), sample.text(),
                    "https://demo.fleetops.invalid/" + topic.keyword() + "/" + (i + 1),
                    name(), now.minus(Duration.ofHours(i + 1L))));
        }
        return items;
    }
}
