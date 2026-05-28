package com.unityskill.auth;

import com.unityskill.auth.entity.RefreshToken;
import com.unityskill.auth.entity.User;
import com.unityskill.webhook.GithubApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GithubAuthService {

    private final GithubApiClient githubApiClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    /** Build GitHub OAuth URL for login (scope: user:email) */
    public String buildLoginAuthorizationUrl() {
        String state = UUID.randomUUID().toString();
        return githubApiClient.buildLoginAuthorizationUrl(state);
    }

    /**
     * Exchange OAuth code → find/create user → issue refresh token.
     * Returns the raw refresh token to be set as an HttpOnly cookie.
     */
    @Transactional
    public String handleLoginCallback(String code) {
        String githubToken = githubApiClient.exchangeLoginCodeForToken(code);

        Map<String, Object> githubUser = githubApiClient.getGithubUserInfo(githubToken);
        String githubId   = String.valueOf(githubUser.get("id"));
        String name       = (String) githubUser.get("name");
        String login      = (String) githubUser.get("login");
        String avatarUrl  = (String) githubUser.get("avatar_url");
        String email      = (String) githubUser.get("email");

        // GitHub may not expose public email — fallback to /user/emails
        if (email == null || email.isBlank()) {
            List<Map<String, Object>> emails = githubApiClient.getGithubUserEmails(githubToken);
            email = emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("primary"))
                              && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No verified primary email found on GitHub account"));
        }

        final String resolvedEmail = email;

        // Find by githubId first, then fall back to email (link existing account)
        User user = userRepository.findByGithubId(githubId)
                .or(() -> userRepository.findByEmail(resolvedEmail))
                .orElse(null);

        if (user == null) {
            user = User.builder()
                    .email(resolvedEmail)
                    .displayName(name != null && !name.isBlank() ? name : login)
                    .avatarUrl(avatarUrl)
                    .githubId(githubId)
                    .build();
        } else {
            // Link githubId to existing email-based account on first OAuth login
            if (user.getGithubId() == null) user.setGithubId(githubId);
            if (user.getAvatarUrl() == null) user.setAvatarUrl(avatarUrl);
        }
        user = userRepository.save(user);

        String rawRefreshToken = UUID.randomUUID().toString();
        saveRefreshToken(user, rawRefreshToken);
        return rawRefreshToken;
    }

    private void saveRefreshToken(User user, String rawToken) {
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .expiresAt(Instant.now().plusSeconds(refreshTokenExpiryDays * 24L * 3600))
                .lastActiveAt(Instant.now())
                .build();
        refreshTokenRepository.save(rt);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
