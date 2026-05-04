package com.unityskill.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unityskill.auth.JwtUtil;
import com.unityskill.common.security.SecurityConfig;
import com.unityskill.webhook.entity.GithubConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@Import(SecurityConfig.class)
class WebhookControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private GithubConnectionRepository connectionRepository;
    @MockitoBean private WebhookProcessor webhookProcessor;
    @MockitoBean private WebhookRateLimiter rateLimiter;
    @MockitoBean private JwtUtil jwtUtil;

    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private String computeSignature(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(payload);
        return "sha256=" + HexFormat.of().formatHex(hmac);
    }

    private GithubConnection sampleConnection() {
        return GithubConnection.builder()
                .id(UUID.randomUUID())
                .workspaceId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .repoFullName("owner/repo")
                .webhookSecret(WEBHOOK_SECRET)
                .build();
    }

    @Test
    void receiveWebhook_validSignature_returns200() throws Exception {
        byte[] payload = objectMapper.writeValueAsBytes(
                Map.of("repository", Map.of("full_name", "owner/repo"), "action", "opened"));
        String signature = computeSignature(payload, WEBHOOK_SECRET);

        when(rateLimiter.tryConsume()).thenReturn(true);
        when(connectionRepository.findByRepoFullName("owner/repo"))
                .thenReturn(Optional.of(sampleConnection()));
        doNothing().when(webhookProcessor).processAsync(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(webhookProcessor).processAsync(any(), eq("pull_request"), any(), any(), eq("owner/repo"));
    }

    @Test
    void receiveWebhook_invalidSignature_returns401() throws Exception {
        byte[] payload = objectMapper.writeValueAsBytes(
                Map.of("repository", Map.of("full_name", "owner/repo")));

        when(rateLimiter.tryConsume()).thenReturn(true);
        when(connectionRepository.findByRepoFullName("owner/repo"))
                .thenReturn(Optional.of(sampleConnection()));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=invalidsignature00000000000000000000000000000000000000000000000000")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(webhookProcessor);
    }

    @Test
    void receiveWebhook_missingSignatureHeader_returns401() throws Exception {
        byte[] payload = objectMapper.writeValueAsBytes(
                Map.of("repository", Map.of("full_name", "owner/repo")));

        when(rateLimiter.tryConsume()).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(webhookProcessor);
    }

    @Test
    void receiveWebhook_rateLimitExceeded_returns429() throws Exception {
        when(rateLimiter.tryConsume()).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=anything")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of())))
                .andExpect(status().isTooManyRequests());

        verifyNoInteractions(connectionRepository, webhookProcessor);
    }
}
