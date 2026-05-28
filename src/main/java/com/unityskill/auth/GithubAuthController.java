package com.unityskill.auth;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class GithubAuthController {

    private final GithubAuthService githubAuthService;

    @Value("${auth.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    @Value("${github.frontend-redirect-base-url}")
    private String frontendBaseUrl;

    /** Step 1 — frontend calls this to get the GitHub OAuth URL */
    @GetMapping("/api/v1/auth/github/login")
    public ResponseEntity<Map<String, Object>> initiateLogin() {
        String authUrl = githubAuthService.buildLoginAuthorizationUrl();
        return ResponseEntity.ok(Map.of("data", Map.of("authUrl", authUrl)));
    }

    /** Step 2 — GitHub redirects here after user authorizes */
    @GetMapping("/api/v1/auth/github/login/callback")
    public void handleLoginCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletResponse response) throws IOException {

        try {
            String rawRefreshToken = githubAuthService.handleLoginCallback(code);

            ResponseCookie cookie = ResponseCookie.from("refreshToken", rawRefreshToken)
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .path("/api/v1/auth")
                    .maxAge(Duration.ofDays(refreshTokenExpiryDays))
                    .sameSite("Lax")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            response.sendRedirect(frontendBaseUrl + "/workspaces");
        } catch (Exception e) {
            response.sendRedirect(frontendBaseUrl + "/login?error=github_oauth_failed");
        }
    }
}
