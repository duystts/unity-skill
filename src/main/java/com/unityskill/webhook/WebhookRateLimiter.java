package com.unityskill.webhook;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class WebhookRateLimiter {

    private final Bucket bucket;

    public WebhookRateLimiter() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(100)
                .refillGreedy(100, Duration.ofMinutes(1))
                .build();
        this.bucket = Bucket.builder().addLimit(limit).build();
    }

    // Package-private constructor for testing: allows injecting a pre-configured bucket
    WebhookRateLimiter(Bucket bucket) {
        this.bucket = bucket;
    }

    public boolean tryConsume() {
        return bucket.tryConsume(1);
    }
}
