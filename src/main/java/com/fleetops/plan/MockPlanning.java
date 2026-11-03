package com.fleetops.plan;

import com.fleetops.ai.Json;
import com.fleetops.ai.LlmRequest;
import com.fleetops.domain.incident.Severity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The offline stand-in for the AI planner (used when {@code LLM_PROVIDER=mock}). It reads the same
 * question a real model would get and answers in the same JSON format, applying the fixed
 * rules of {@link RuleBasedPlanner}.
 */
public final class MockPlanning implements Function<LlmRequest, String> {

    private static final Pattern SEVERITY = Pattern.compile("^Severity: (\\w+)", Pattern.MULTILINE);
    private static final Pattern PEOPLE = Pattern.compile("^Estimated people: (\\d+)", Pattern.MULTILINE);
    private static final Pattern DRONES = Pattern.compile("^Available drones: (\\d+)", Pattern.MULTILINE);
    private static final Pattern HELICOPTERS = Pattern.compile("^Available helicopters: (\\d+)", Pattern.MULTILINE);

    /** True if the request is a planning question (as opposed to, say, a news verification). */
    public static boolean handles(LlmRequest request) {
        return request.system().equals(PlanPrompts.SYSTEM.strip());
    }

    @Override
    public String apply(LlmRequest request) {
        // Read only the program-generated header, never the fenced text from the internet.
        String prompt = request.user();
        int fence = prompt.indexOf(PlanPrompts.INCIDENT_START);
        String header = fence >= 0 ? prompt.substring(0, fence) : prompt;

        Severity severity = Severity.parse(find(SEVERITY, header, "MODERATE"));
        int people = Integer.parseInt(find(PEOPLE, header, "0"));
        int drones = Math.min(RuleBasedPlanner.droneCountFor(severity), Math.max(1, Integer.parseInt(find(DRONES, header, "1"))));
        int helicopters = Math.min(RuleBasedPlanner.helicoptersFor(people), Integer.parseInt(find(HELICOPTERS, header, "1")));

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("drones", drones);
        answer.put("strategy", "lawnmower");
        answer.put("helicopters", helicopters);
        answer.put("rationale", "Mock planner: " + severity + " severity gets " + drones + " drone(s); about " + people
                + " people get " + helicopters + " helicopter(s).");
        return Json.write(answer);
    }

    private static String find(Pattern pattern, String text, String fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : fallback;
    }
}
