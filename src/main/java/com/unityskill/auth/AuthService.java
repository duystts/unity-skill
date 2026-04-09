package com.unityskill.auth;

import com.unityskill.auth.dto.AuthUserResponse;
import com.unityskill.auth.dto.LoginRequest;
import com.unityskill.auth.dto.LoginResponse;
import com.unityskill.auth.dto.RegisterRequest;
import com.unityskill.auth.dto.RegisterResponse;
import com.unityskill.auth.entity.RefreshToken;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.EmailAlreadyExistsException;
import com.unityskill.common.exception.InvalidCredentialsException;
import com.unityskill.common.exception.TokenInactiveException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    // ── Internal result records (prevent 2-token creation) ──────────────────

    public record RegistrationResult(RegisterResponse response, String rawRefreshToken) {}
    public record LoginResult(LoginResponse response, String rawRefreshToken) {}

    // ── Registration (Story 1.4) ─────────────────────────────────────────────

    @Transactional
    public RegistrationResult register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .build();
        user = userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = UUID.randomUUID().toString();

        saveRefreshToken(user, rawRefreshToken);

        return new RegistrationResult(
            new RegisterResponse(AuthUserResponse.from(user), accessToken),
            rawRefreshToken
        );
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = UUID.randomUUID().toString();

        saveRefreshToken(user, rawRefreshToken);

        return new LoginResult(
            new LoginResponse(AuthUserResponse.from(user), accessToken),
            rawRefreshToken
        );
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    @Transactional
    public String refreshAccessToken(String rawToken) {
        String tokenHash = sha256(rawToken);
        RefreshToken rt = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(TokenInactiveException::new);

        if (rt.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(rt);
            throw new TokenInactiveException();
        }

        if (Duration.between(rt.getLastActiveAt(), Instant.now()).toHours() > 24) {
            refreshTokenRepository.delete(rt);
            throw new TokenInactiveException();
        }

        rt.setLastActiveAt(Instant.now());
        refreshTokenRepository.save(rt);

        // Lazy-load safe: @Transactional keeps session open
        return jwtUtil.generateAccessToken(rt.getUser().getId(), rt.getUser().getEmail());
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null) return;
        String tokenHash = sha256(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(refreshTokenRepository::delete);
    }

    // ── Me ───────────────────────────────────────────────────────────────────

    public AuthUserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return AuthUserResponse.from(user);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
