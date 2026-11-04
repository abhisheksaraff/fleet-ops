package com.fleetops.cli;

import com.fleetops.domain.geo.GeoPoint;
import com.fleetops.domain.incident.Incident;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

/** Small text-formatting helpers shared by the commands. */
final class CliFormat {

    private CliFormat() {}

    static String id(Incident incident) {
        return incident.id().map(String::valueOf).orElse("-");
    }

    static String point(GeoPoint p) {
        return String.format(Locale.ROOT, "%.4f, %.4f", p.lat(), p.lon());
    }

    static String clip(String text, int max) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, Math.max(0, max - 3)) + "...";
    }

    /** One incident per line, columns aligned. */
    static String table(List<Incident> incidents) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "%-4s %-10s %-12s %-9s %-7s %s%n", "ID", "TYPE", "STATE", "SEVERITY", "PEOPLE", "TITLE"));
        for (Incident i : incidents) {
            text.append(String.format(Locale.ROOT, "%-4s %-10s %-12s %-9s %-7d %s%n",
                    id(i), i.type().keyword(), i.state(), i.severity(), i.estimatedPeople(), clip(i.title(), 60)));
        }
        return text.toString().stripTrailing();
    }

    /** Asks a yes/no question; anything but "y" or "yes" (including end of input) counts as no. */
    static boolean confirm(BufferedReader input, PrintWriter out, String question) throws IOException {
        out.print(question + " [y/N] ");
        out.flush();
        String line = input.readLine();
        return line != null && (line.strip().equalsIgnoreCase("y") || line.strip().equalsIgnoreCase("yes"));
    }
}
