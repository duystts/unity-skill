package com.unityskill.webhook;

import com.unityskill.common.exception.GithubNotConnectedException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import com.unityskill.webhook.dto.GithubConnectionResponse;
import com.unityskill.webhook.entity.GithubConnection;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class GithubConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GithubConnectionService.class);

    private final GithubConnectionRepository connectionRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final GithubApiClient githubApiClient;
    private final TextEncryptor textEncryptor;
    private final String frontendRedirectBaseUrl;
    private final String webhookEndpointUrl;

    public GithubConnectionService(
            GithubConnectionRepository connectionRepository,
            WorkspaceMemberRepository memberRepository,
            GithubApiClient githubApiClient,
            TextEncryptor textEncryptor,
            @Value("${github.frontend-redirect-base-url}") String frontendRedirectBaseUrl,
            @Value("${github.webhook-endpoint-url}") String webhookEndpointUrl) {
        this.connectionRepository = connectionRepository;
        this.memberRepository = memberRepository;
        this.githubApiClient = githubApiClient;
        this.textEncryptor = textEncryptor;
        this.frontendRedirectBaseUrl = frontendRedirectBaseUrl;
        this.webhookEndpointUrl = webhookEndpointUrl;
    }

    public String buildAuthorizationUrl(UUID projectId, UUID workspaceId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);
        String state = Base64.getEncoder().encodeToString(
                (projectId + "|" + workspaceId + "|" + callerId)
                        .getBytes(StandardCharsets.UTF_8));
        return githubApiClient.buildAuthorizationUrl(state);
    }

    @Transactional
    public String handleCallback(String code, String state) {
        String decoded = new String(Base64.getDecoder().decode(state), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|");
        UUID projectId = UUID.fromString(parts[0]);
        UUID workspaceId = UUID.fromString(parts[1]);

        String accessToken = githubApiClient.exchangeCodeForToken(code);
        String encrypted = textEncryptor.encrypt(accessToken);

        GithubConnection conn = connectionRepository.findByProjectId(projectId)
                .orElse(GithubConnection.builder()
                        .projectId(projectId)
                        .workspaceId(workspaceId)
                        .build());
        conn.setEncryptedOauthToken(encrypted);
        connectionRepository.save(conn);

        return frontendRedirectBaseUrl + "/" + workspaceId + "/settings/github?connected=true";
    }

    public GithubConnectionResponse getConnection(UUID workspaceId, UUID projectId, UUID callerId) {
        requirePmOrAdmin(workspaceId, callerId);
        return connectionRepository.findByProjectId(projectId)
                .map(c -> new GithubConnectionResponse(true, c.getRepoFullName()))
                .orElse(new GithubConnectionResponse(false, null));
    }

    @Transactional
    public void registerWebhook(UUID workspaceId, UUID projectId, UUID callerId, String repoFullName) {
        requirePmOrAdmin(workspaceId, callerId);
        GithubConnection conn = connectionRepository.findByProjectId(projectId)
                .orElseThrow(GithubNotConnectedException::new);

        String token = textEncryptor.decrypt(conn.getEncryptedOauthToken());
        String secret = UUID.randomUUID().toString();

        // Log token prefix for debugging (never log full token)
        log.info("Registering webhook for repo='{}', tokenPrefix='{}'",
                repoFullName, token.substring(0, Math.min(8, token.length())) + "...");

        // Pre-check: log which GitHub account this token belongs to
        try {
            Map<String, Object> userInfo = githubApiClient.getGithubUserInfo(token);
            log.info("Token belongs to GitHub user: login='{}', id={}", userInfo.get("login"), userInfo.get("id"));
        } catch (Exception e) {
            log.warn("Could not get user info for token: {}", e.getMessage());
        }

        // Pre-check: verify repo is accessible with this token
        try {
            Map<String, Object> repoInfo = githubApiClient.getRepo(token, repoFullName);
            log.info("Repo access OK: fullName='{}', private={}, permissions={}",
                    repoInfo.get("full_name"), repoInfo.get("private"), repoInfo.get("permissions"));
        } catch (HttpClientErrorException.NotFound e) {
            log.error("Pre-check 404: token cannot access repo '{}'. Body: {}", repoFullName, e.getResponseBodyAsString());
            throw new IllegalArgumentException(
                "Repository \"" + repoFullName + "\" not found or not accessible. " +
                "Make sure the repo name is correct (owner/repo) and re-authorize GitHub.");
        } catch (HttpClientErrorException e) {
            log.warn("Pre-check {} for repo '{}': {}", e.getStatusCode(), repoFullName, e.getResponseBodyAsString());
        }

        try {
            githubApiClient.registerWebhook(token, repoFullName, webhookEndpointUrl, secret);
        } catch (HttpClientErrorException.NotFound e) {
            log.error("GitHub 404 for repo='{}': {}", repoFullName, e.getResponseBodyAsString());
            throw new IllegalArgumentException(
                "Repository \"" + repoFullName + "\" not found. " +
                "Check the name (format: owner/repo) and make sure you have admin access to it.");
        } catch (HttpClientErrorException.Forbidden e) {
            log.error("GitHub 403 for repo='{}': {}", repoFullName, e.getResponseBodyAsString());
            throw new IllegalArgumentException(
                "Permission denied. You need admin access to \"" + repoFullName + "\" to register webhooks.");
        } catch (HttpClientErrorException e) {
            log.error("GitHub {} for repo='{}': {}", e.getStatusCode().value(), repoFullName, e.getResponseBodyAsString());
            String body = e.getResponseBodyAsString();
            if (body.contains("localhost") || body.contains("isn't reachable")) {
                throw new IllegalArgumentException(
                    "Webhook URL is not reachable from the internet. " +
                    "Use a tunnel (e.g. ngrok) to expose your local server and update GITHUB_WEBHOOK_ENDPOINT_URL.");
            }
            throw new IllegalArgumentException(
                "GitHub error " + e.getStatusCode().value() + ": " + e.getStatusText() +
                ". Re-authorize GitHub access and try again.");
        }

        conn.setRepoFullName(repoFullName);
        conn.setWebhookSecret(secret);
        connectionRepository.save(conn);
    }

    private void requirePmOrAdmin(UUID workspaceId, UUID userId) {
        WorkspaceMember member = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedAccessException("Access denied: not a workspace member"));
        if (member.getRole() == WorkspaceRole.DEVELOPER) {
            throw new UnauthorizedAccessException("Access denied: PM or Admin role required");
        }
    }
}
