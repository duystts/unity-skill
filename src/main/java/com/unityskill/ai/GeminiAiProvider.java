package com.unityskill.ai;

import com.unityskill.common.exception.AiApiUnavailableException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini implementation of {@link AiProvider}.
 * <p>
 * Activated when {@code ai.provider=gemini} — the default for dev/student environments.
 * Uses the Gemini Flash free tier: 15 RPM, 1 500 req/day, no credit card required.
 * <p>
 * Free tier limits (Gemini 2.0 Flash as of 2026):
 * <ul>
 *   <li>15 requests per minute</li>
 *   <li>1 000 000 tokens per minute</li>
 *   <li>1 500 requests per day</li>
 * </ul>
 * Requires env var: {@code GEMINI_API_KEY}
 * Get a free key at: <a href="https://aistudio.google.com/apikey">Google AI Studio</a>
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
@Slf4j
public class GeminiAiProvider implements AiProvider {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    @Value("${ai.gemini.api-key}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-2.0-flash}")
    private String model;

    private final RestClient restClient;

    // Free tier: 15 RPM — queued (not rejected) to stay within quota
    private final Bucket rateLimitBucket;

    public GeminiAiProvider(RestClient restClient) {
        this.restClient = restClient;
        this.rateLimitBucket = Bucket.builder()
                .addLimit(Bandwidth.classic(15, Refill.greedy(15, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * Sends a prompt to the Gemini API and returns the generated text.
     * Blocks until a rate-limit token is available (queued, not rejected).
     * Retried up to 3 times on {@link AiApiUnavailableException}.
     */
    @Override
    @Retryable(retryFor = AiApiUnavailableException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 2000, multiplier = 2))
    public String generateText(String prompt) {
        try {
            rateLimitBucket.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiApiUnavailableException("AI service interrupted while waiting for rate limit");
        }

        // Gemini request format
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(GEMINI_API_URL + "?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            // Gemini response: candidates[0].content.parts[0].text
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) response.get("candidates");
            @SuppressWarnings("unchecked")
            Map<String, Object> content =
                    (Map<String, Object>) candidates.get(0).get("content");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts =
                    (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new AiApiUnavailableException("Gemini API call failed: " + e.getMessage());
        }
    }
}
