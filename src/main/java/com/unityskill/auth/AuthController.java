package com.unityskill.auth;

import com.unityskill.auth.dto.LoginRequest;
import com.unityskill.auth.dto.RegisterRequest;
import com.unityskill.common.exception.TokenInactiveException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        var result = authService.register(request);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "data", Map.of(
                "id", result.response().user().id(),
                "email", result.response().user().email(),
                "displayName", result.response().user().displayName(),
                "uiMode", result.response().user().uiMode(),
                "accessToken", result.response().accessToken()
            )
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        var result = authService.login(request);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.ok(Map.of(
            "data", Map.of(
                "id", result.response().user().id(),
                "email", result.response().user().email(),
                "displayName", result.response().user().displayName(),
                "uiMode", result.response().user().uiMode(),
                "accessToken", result.response().accessToken()
            )
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken) {
        if (rawRefreshToken == null) {
            throw new TokenInactiveException("No refresh token provided");
        }
        String newAccessToken = authService.refreshAccessToken(rawRefreshToken);
        return ResponseEntity.ok(Map.of("data", Map.of("accessToken", newAccessToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken,
            HttpServletResponse response) {
        authService.logout(rawRefreshToken);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @AuthenticationPrincipal String userId) {
        var user = authService.getMe(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", Map.of(
            "id", user.id(),
            "email", user.email(),
            "displayName", user.displayName(),
            "uiMode", user.uiMode()
        )));
    }

    // ── Cookie helpers ───────────────────────────────────────────────────────

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie("refreshToken", rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(7 * 24 * 3600);
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
