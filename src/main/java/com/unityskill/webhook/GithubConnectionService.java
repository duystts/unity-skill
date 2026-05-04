package com.unityskill.webhook;

import com.unityskill.common.exception.GithubNotConnectedException;
import com.unityskill.common.exception.UnauthorizedAccessException;
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
import java.util.UUID;

@Service
public class GithubConnectionService {

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

        githubApiClient.registerWebhook(token, repoFullName, webhookEndpointUrl, secret);

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
