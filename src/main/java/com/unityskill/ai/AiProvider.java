package com.unityskill.ai;

/**
 * Abstraction over AI text generation providers.
 * <p>
 * Implementations are selected via {@code ai.provider} in application config:
 * <ul>
 *   <li>{@code anthropic} → {@link AiPipelineService} (paid)</li>
 *   <li>{@code gemini}    → {@link GeminiAiProvider}  (free tier — default for dev)</li>
 * </ul>
 * All callers ({@code ContributionService}, {@code TranscriptService}, {@code AgendaService})
 * depend only on this interface — swapping providers requires zero code changes in those classes.
 */
public interface AiProvider {

    /**
     * Sends a prompt to the configured AI provider and returns the generated text.
     *
     * @param prompt the instruction/question to send
     * @return the generated text response
     * @throws com.unityskill.common.exception.AiApiUnavailableException if the provider is unreachable
     */
    String generateText(String prompt);
}
