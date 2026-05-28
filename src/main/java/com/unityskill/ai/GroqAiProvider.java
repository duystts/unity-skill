package com.unityskill.ai;

import com.unityskill.common.exception.AiApiUnavailableException;
import com.unityskill.common.exception.AiRateLimitException;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Groq Cloud implementation of {@link AiProvider}.
 * <p>
 * Activated when {@code ai.provider=groq}.
 * Free tier: 14 400 req/day, 30 req/min — no credit card required.
 * Get a key at <a href="https://console.groq.com">console.groq.com</a>.
 * <p>
 * Uses the OpenAI-compatible chat-completions endpoint, so the same key
 * works with any OpenAI SDK. Models available on free tier include:
 * llama3-70b-8192, llama3-8b-8192, mixtral-8x7b-32768, gemma2-9b-it.
 */
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "groq")
@Slf4j
public class GroqAiProvider implements AiProvider {

    private static final String GROQ_API_URL =
            "https://api.groq.com/openai/v1/chat/completions";

    @Value("${ai.groq.api-key}")
    private String apiKey;

    @Value("${ai.groq.model:llama-3.3-70b-versatile}")
    private String model;

    private final RestClient restClient;

    // Free tier: 30 req/min — queued (not rejected) to stay within quota
    private final Bucket rateLimitBucket;

    public GroqAiProvider(RestClient restClient) {
        this.restClient = restClient;
        this.rateLimitBucket = Bucket.builder()
                .addLimit(Bandwidth.classic(30, Refill.greedy(30, Duration.ofMinutes(1))))
                .build();
    }

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

        // OpenAI-compatible request format
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(GROQ_API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            // OpenAI response: choices[0].message.content
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> message =
                    (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Groq API quota exceeded (429).");
            throw new AiRateLimitException("AI quota exceeded. Please try again in a moment.");
        } catch (HttpClientErrorException e) {
            // 4xx client errors (bad model name, invalid key, etc.) — do NOT retry
            log.error("Groq API client error {}: {}", e.getStatusCode(), e.getMessage());
            throw new AiRateLimitException("AI request error: " + e.getStatusCode()
                    + ". Check GROQ_MODEL and GROQ_API_KEY in .env");
        } catch (Exception e) {
            log.error("Groq API call failed: {}", e.getMessage());
            throw new AiApiUnavailableException("Groq API call failed: " + e.getMessage());
        }
    }
}
