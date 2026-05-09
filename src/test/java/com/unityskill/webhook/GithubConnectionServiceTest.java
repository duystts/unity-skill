package com.unityskill.webhook;

import com.unityskill.common.exception.GithubNotConnectedException;
import com.unityskill.common.exception.UnauthorizedAccessException;
import com.unityskill.webhook.dto.GithubConnectionResponse;
import com.unityskill.webhook.entity.GithubConnection;
import com.unityskill.workspace.WorkspaceMemberRepository;
import com.unityskill.workspace.entity.WorkspaceMember;
import com.unityskill.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GithubConnectionServiceTest {

    @Mock GithubConnectionRepository connectionRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock GithubApiClient githubApiClient;
    @Mock TextEncryptor textEncryptor;

    private GithubConnectionService buildService() {
        return new GithubConnectionService(
                connectionRepository,
                memberRepository,
                githubApiClient,
                textEncryptor,
                "http://localhost:3000",
                "http://localhost:8080/api/v1/webhooks/github"
        );
    }

    private WorkspaceMember member(UUID workspaceId, UUID userId, WorkspaceRole role) {
        return WorkspaceMember.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .role(role)
                .build();
    }

    @Test
    void buildAuthorizationUrl_asPm_containsClientIdAndState() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(member(workspaceId, userId, WorkspaceRole.PM)));
        when(githubApiClient.buildAuthorizationUrl(any()))
                .thenReturn("https://github.com/login/oauth/authorize?client_id=test&state=abc");

        GithubConnectionService service = buildService();
        String url = service.buildAuthorizationUrl(projectId, workspaceId, userId);

        assertThat(url).contains("github.com/login/oauth/authorize");
        verify(githubApiClient).buildAuthorizationUrl(any());
    }

    @Test
    void handleCallback_validCode_encryptsAndStoresToken() {
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        String state = java.util.Base64.getEncoder().encodeToString(
                (projectId + "|" + workspaceId + "|" + callerId).getBytes());

        when(githubApiClient.exchangeCodeForToken("test-code")).thenReturn("raw-token");
        when(textEncryptor.encrypt("raw-token")).thenReturn("enc-token");
        when(connectionRepository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(connectionRepository.save(any(GithubConnection.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GithubConnectionService service = buildService();
        String redirectUrl = service.handleCallback("test-code", state);

        assertThat(redirectUrl).contains(workspaceId.toString()).contains("connected=true");
        verify(textEncryptor).encrypt("raw-token");
        verify(connectionRepository).save(any(GithubConnection.class));
    }

    @Test
    void getConnection_whenConnected_returnsRepoName() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        GithubConnection conn = GithubConnection.builder()
                .projectId(projectId)
                .workspaceId(workspaceId)
                .repoFullName("owner/repo")
                .encryptedOauthToken("enc")
                .build();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(member(workspaceId, userId, WorkspaceRole.PM)));
        when(connectionRepository.findByProjectId(projectId)).thenReturn(Optional.of(conn));

        GithubConnectionService service = buildService();
        GithubConnectionResponse resp = service.getConnection(workspaceId, projectId, userId);

        assertThat(resp.connected()).isTrue();
        assertThat(resp.repoFullName()).isEqualTo("owner/repo");
    }

    @Test
    void getConnection_whenNotConnected_returnsFalse() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(member(workspaceId, userId, WorkspaceRole.ADMIN)));
        when(connectionRepository.findByProjectId(projectId)).thenReturn(Optional.empty());

        GithubConnectionService service = buildService();
        GithubConnectionResponse resp = service.getConnection(workspaceId, projectId, userId);

        assertThat(resp.connected()).isFalse();
        assertThat(resp.repoFullName()).isNull();
    }

    @Test
    void registerWebhook_asPm_callsGithubApiAndSavesSecret() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        GithubConnection conn = GithubConnection.builder()
                .projectId(projectId)
                .workspaceId(workspaceId)
                .encryptedOauthToken("enc-token")
                .build();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(member(workspaceId, userId, WorkspaceRole.PM)));
        when(connectionRepository.findByProjectId(projectId)).thenReturn(Optional.of(conn));
        when(textEncryptor.decrypt("enc-token")).thenReturn("raw-token");
        when(connectionRepository.save(any(GithubConnection.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GithubConnectionService service = buildService();
        service.registerWebhook(workspaceId, projectId, userId, "owner/repo");

        verify(githubApiClient).registerWebhook(eq("raw-token"), eq("owner/repo"), any(), any());
        verify(connectionRepository).save(any(GithubConnection.class));
    }

    @Test
    void registerWebhook_asDeveloper_throwsForbidden() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(member(workspaceId, userId, WorkspaceRole.DEVELOPER)));

        GithubConnectionService service = buildService();
        assertThatThrownBy(() -> service.registerWebhook(workspaceId, projectId, userId, "owner/repo"))
                .isInstanceOf(UnauthorizedAccessException.class);

        verifyNoInteractions(connectionRepository);
    }

    @Test
    void registerWebhook_notConnected_throws400() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(member(workspaceId, userId, WorkspaceRole.PM)));
        when(connectionRepository.findByProjectId(projectId)).thenReturn(Optional.empty());

        GithubConnectionService service = buildService();
        assertThatThrownBy(() -> service.registerWebhook(workspaceId, projectId, userId, "owner/repo"))
                .isInstanceOf(GithubNotConnectedException.class);

        verifyNoInteractions(githubApiClient);
    }
}
