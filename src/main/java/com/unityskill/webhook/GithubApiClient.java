package com.unityskill.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class GithubApiClient {

    private final RestClient restClient;
    // Project-connect OAuth App credentials
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    // Login OAuth App credentials (separate GitHub OAuth App)
    private final String loginClientId;
    private final String loginClientSecret;
    private final String loginRedirectUri;

    public GithubApiClient(
            RestClient restClient,
            @Value("${github.client-id}") String clientId,
            @Value("${github.client-secret}") String clientSecret,
            @Value("${github.redirect-uri}") String redirectUri,
            @Value("${github.login-client-id}") String loginClientId,
            @Value("${github.login-client-secret}") String loginClientSecret,
            @Value("${github.login-redirect-uri}") String loginRedirectUri) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.loginClientId = loginClientId;
        this.loginClientSecret = loginClientSecret;
        this.loginRedirectUri = loginRedirectUri;
    }

    public String buildAuthorizationUrl(String state) {
        // repo         — full access to public & private repos (includes repo:status)
        // admin:repo_hook — create/manage webhooks on repos the user administers
        return "https://github.com/login/oauth/authorize" +
               "?client_id=" + clientId +
               "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
               "&scope=repo%2Cadmin%3Arepo_hook" +
               "&state=" + state;
    }

    @SuppressWarnings("unchecked")
    public String exchangeCodeForToken(String code) {
        Map<String, Object> tokenResponse = restClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "client_id", clientId,
                        "client_secret", clientSecret,
                        "code", code,
                        "redirect_uri", redirectUri
                ))
                .retrieve()
                .body(Map.class);

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain GitHub access token");
        }
        return (String) tokenResponse.get("access_token");
    }

    // ── Login OAuth ─────────────────────────────────────────────────────────────

    public String buildLoginAuthorizationUrl(String state) {
        return "https://github.com/login/oauth/authorize" +
               "?client_id=" + loginClientId +
               "&redirect_uri=" + URLEncoder.encode(loginRedirectUri, StandardCharsets.UTF_8) +
               "&scope=user%3Aemail" +
               "&state=" + state;
    }

    @SuppressWarnings("unchecked")
    public String exchangeLoginCodeForToken(String code) {
        Map<String, Object> tokenResponse = restClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "client_id", loginClientId,
                        "client_secret", loginClientSecret,
                        "code", code,
                        "redirect_uri", loginRedirectUri
                ))
                .retrieve()
                .body(Map.class);

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain GitHub access token");
        }
        return (String) tokenResponse.get("access_token");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getGithubUserInfo(String token) {
        return restClient.get()
                .uri("https://api.github.com/user")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getGithubUserEmails(String token) {
        return restClient.get()
                .uri("https://api.github.com/user/emails")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(List.class);
    }

    // ── Repo access check ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRepo(String token, String repoFullName) {
        return restClient.get()
                .uri("https://api.github.com/repos/" + repoFullName)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(Map.class);
    }

    // ── Webhook registration ─────────────────────────────────────────────────────

    public void registerWebhook(String token, String repoFullName,
                                String webhookUrl, String webhookSecret) {
        restClient.post()
                .uri("https://api.github.com/repos/" + repoFullName + "/hooks")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "web",
                        "active", true,
                        "events", List.of("pull_request", "pull_request_review"),
                        "config", Map.of(
                                "url", webhookUrl,
                                "content_type", "json",
                                "secret", webhookSecret
                        )
                ))
                .retrieve()
                .toBodilessEntity();
    }
}
