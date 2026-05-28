package com.unityskill.auth;

import com.unityskill.auth.entity.RefreshToken;
import com.unityskill.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri:http://localhost:8080/api/v1/auth/google/login/callback}")
    private String redirectUri;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    private static final String AUTH_URL   = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL  = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    /** Build Google OAuth URL for login */
    public String buildAuthorizationUrl(String state) {
        return AUTH_URL
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=openid%20email%20profile"
                + "&state=" + state
                + "&access_type=offline"
                + "&prompt=select_account";
    }

    /**
     * Exchange code → access token → user info → find/create user → issue refresh token.
     * Returns the raw refresh token to be set as HttpOnly cookie.
     */
    @Transactional
    public String handleCallback(String code) {
        String accessToken = exchangeCodeForToken(code);
        Map<String, Object> userInfo = getUserInfo(accessToken);

        String googleId  = String.valueOf(userInfo.get("sub"));   // Google unique ID
        String email     = (String) userInfo.get("email");
        String name      = (String) userInfo.get("name");
        String avatarUrl = (String) userInfo.get("picture");

        if (email == null || email.isBlank()) {
            throw new IllegalStateException("No email returned from Google");
        }

        // Find by googleId first, then fall back to email (link existing account)
        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(email))
                .orElse(null);

        if (user == null) {
            user = User.builder()
                    .email(email)
                    .displayName(name != null && !name.isBlank() ? name : email)
                    .avatarUrl(avatarUrl)
                    .googleId(googleId)
                    .build();
        } else {
            if (user.getGoogleId() == null) user.setGoogleId(googleId);
            if (user.getAvatarUrl() == null) user.setAvatarUrl(avatarUrl);
        }
        user = userRepository.save(user);

        String rawRefreshToken = UUID.randomUUID().toString();
        saveRefreshToken(user, rawRefreshToken);
        return rawRefreshToken;
    }

    private String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                TOKEN_URL, HttpMethod.POST, request,
                new ParameterizedTypeReference<>() {}
        );

        if (response.getBody() == null || !response.getBody().containsKey("access_token")) {
            throw new IllegalStateException("No access_token in Google token response");
        }
        return (String) response.getBody().get("access_token");
    }

    private Map<String, Object> getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                USERINFO_URL, HttpMethod.GET, request,
                new ParameterizedTypeReference<>() {}
        );
        if (response.getBody() == null) {
            throw new IllegalStateException("Empty response from Google userinfo");
        }
        return response.getBody();
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
