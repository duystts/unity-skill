package com.unityskill.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.common.exception.WebhookSignatureException;
import com.unityskill.webhook.entity.GithubConnection;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final GithubConnectionRepository connectionRepository;
    private final WebhookProcessor webhookProcessor;
    private final WebhookRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @PostMapping("/api/v1/webhooks/github")
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "unknown") String eventType,
            HttpServletRequest request) throws Exception {

        // AC 5: Rate limiting
        if (!rateLimiter.tryConsume()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        // AC 2: Require signature header
        if (signature == null) {
            throw new WebhookSignatureException("Missing X-Hub-Signature-256 header");
        }

        // Read raw body for HMAC validation + persistence
        byte[] rawBytes = request.getInputStream().readAllBytes();
        String payloadJson = new String(rawBytes, StandardCharsets.UTF_8);

        // Parse repo name to look up connection and webhook secret
        JsonNode json = objectMapper.readTree(rawBytes);
        String repoFullName = json.path("repository").path("full_name").asText(null);

        GithubConnection conn = connectionRepository.findByRepoFullName(repoFullName)
                .orElseThrow(() -> new WebhookSignatureException("No connection found for repository"));

        // AC 1 & 2: Validate HMAC-SHA256 signature
        if (!isValidSignature(rawBytes, signature, conn.getWebhookSecret())) {
            throw new WebhookSignatureException("Invalid webhook signature");
        }

        // AC 1: Return 200 immediately, process async (NFR16)
        webhookProcessor.processAsync(payloadJson, eventType,
                conn.getWorkspaceId(), conn.getProjectId(), repoFullName);

        return ResponseEntity.ok().build();
    }

    private boolean isValidSignature(byte[] payload, String signature, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(payload);
        String expected = "sha256=" + HexFormat.of().formatHex(hmac);
        // Constant-time comparison prevents timing attacks
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
