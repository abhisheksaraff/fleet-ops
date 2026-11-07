package com.fleetops.debrief;

import com.fleetops.ai.Json;
import com.fleetops.ai.LlmRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The offline stand-in for the AI debriefer (used when {@code LLM_PROVIDER=mock}). It reads the same
 * question a real model would get and answers in the same JSON format, applying the fixed rules of
 * {@link DebriefRules} to the numbers in the header.
 */
public final class MockDebriefing implements Function<LlmRequest, String> {

    /** True if the request is a debrief question (as opposed to, say, a planning question). */
    public static boolean handles(LlmRequest request) {
        return request.system().equals(DebriefPrompts.SYSTEM.strip());
    }

    @Override
    public String apply(LlmRequest request) {
        // Read only the program-generated header, never the fenced text.
        String prompt = request.user();
        int fence = prompt.indexOf(DebriefPrompts.MISSION_START);
        String header = fence >= 0 ? prompt.substring(0, fence) : prompt;

        Debrief debrief = DebriefRules.write(DebriefFacts.fromHeader(header), "mock", true);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("summary", debrief.summary());
        answer.put("wentWell", debrief.wentWell());
        answer.put("problems", debrief.problems());
        answer.put("nextTime", debrief.nextTime());
        return Json.write(answer);
    }
}
