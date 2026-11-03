package com.fleetops.ai;

/**
 * The only thing the rest of the program knows about AI: send a request, get text back. Gemini,
 * Ollama and the offline mock all implement it, so changing the AI vendor means choosing a
 * different implementation in one place and nothing else.
 */
public interface LlmClient {

    /** Short label for logs and screens, for example {@code gemini:gemini-2.5-flash}. */
    String name();

    /**
     * Asks the model and returns its answer text.
     *
     * @throws LlmException if the model cannot be reached or refuses to answer
     */
    String complete(LlmRequest request);

    /**
     * Asks the model and reads its answer as JSON (see {@link Json#parseFromText}).
     *
     * @throws LlmException if the call fails or the answer contains no valid JSON
     */
    default Object completeJson(LlmRequest request) {
        String answer = complete(request);
        try {
            return Json.parseFromText(answer);
        } catch (JsonException e) {
            throw new LlmException(name() + " did not return valid JSON: " + e.getMessage(), false, e);
        }
    }
}
