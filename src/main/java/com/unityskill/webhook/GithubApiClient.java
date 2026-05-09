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
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GithubApiClient(
            RestClient restClient,
            @Value("${github.client-id}") String clientId,
            @Value("${github.client-secret}") String clientSecret,
            @Value("${github.redirect-uri}") String redirectUri) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public String buildAuthorizationUrl(String state) {
        return "https://github.com/login/oauth/authorize" +
               "?client_id=" + clientId +
               "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
               "&scope=repo%3Astatus%2Cpublic_repo" +
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

    public void registerWebhook(String token, String repoFullName,
                                String webhookUrl, String webhookSecret) {
        restClient.post()
                .uri("https://api.github.com/repos/{repo}/hooks", repoFullName)
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
