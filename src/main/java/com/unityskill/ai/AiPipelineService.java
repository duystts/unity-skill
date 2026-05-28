package com.unityskill.ai;

import com.unityskill.common.exception.AiApiUnavailableException;
import com.unityskill.common.exception.AiRateLimitException;
import org.springframework.web.client.HttpClientErrorException;
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
 * Anthropic Claude implementation of {@link AiProvider}.
 * <p>
 * Activated when {@code ai.provider=anthropic} (or when the property is absent —
 * use {@code matchIfMissing=false} keeps Gemini as default for new installs).
 * <p>
 * Requires env var: {@code ANTHROPIC_API_KEY}
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "anthropic")
@Slf4j
public class AiPipelineService implements AiProvider {

    @Value("${ai.anthropic.api-key}")
    private String apiKey;

    @Value("${ai.anthropic.model:claude-haiku-4-5-20251001}")
    private String model;

    private final RestClient restClient;

    // Rate limit: 10 AI requests per minute — queued (not rejected) per NFR15
    private final Bucket rateLimitBucket;

    public AiPipelineService(RestClient restClient) {
        this.restClient = restClient;
        this.rateLimitBucket = Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * Sends a prompt to the Anthropic Claude API and returns the generated text.
     * Blocks until a rate-limit token is available (queued, not rejected — NFR15).
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

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            return (String) content.get(0).get("text");
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Anthropic API quota exceeded (429). Please check your API key quota.");
            throw new AiRateLimitException("AI quota exceeded. Please try again later.");
        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", e.getMessage());
            throw new AiApiUnavailableException("Anthropic API call failed: " + e.getMessage());
        }
    }
}
