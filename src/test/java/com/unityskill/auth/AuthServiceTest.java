package com.unityskill.auth;

import com.unityskill.auth.dto.LoginRequest;
import com.unityskill.auth.dto.RegisterRequest;
import com.unityskill.auth.entity.RefreshToken;
import com.unityskill.auth.entity.UiMode;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.EmailAlreadyExistsException;
import com.unityskill.common.exception.InvalidCredentialsException;
import com.unityskill.common.exception.TokenInactiveException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthService authService;

    // ── Register ─────────────────────────────────────────────────────────────

    @Test
    void register_validRequest_returnsRegistrationResult() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        var request = new RegisterRequest("test@example.com", "password123", "Test User");
        var savedUser = User.builder().id(UUID.randomUUID()).email("test@example.com")
                           .displayName("Test User").passwordHash("hashed").build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateAccessToken(any(), anyString())).thenReturn("jwt-token");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        var result = authService.register(request);

        assertThat(result.response().user().email()).isEqualTo("test@example.com");
        assertThat(result.response().accessToken()).isEqualTo("jwt-token");
        assertThat(result.rawRefreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyExistsException() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        var request = new RegisterRequest("existing@example.com", "password123", "User");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(EmailAlreadyExistsException.class);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsLoginResult() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        var savedUser = User.builder().id(UUID.randomUUID()).email("test@example.com")
                           .displayName("Test User").passwordHash("hashed").build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtUtil.generateAccessToken(any(), anyString())).thenReturn("jwt-token");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        var result = authService.login(new LoginRequest("test@example.com", "password123"));

        assertThat(result.response().user().email()).isEqualTo("test@example.com");
        assertThat(result.response().accessToken()).isEqualTo("jwt-token");
        assertThat(result.rawRefreshToken()).isNotBlank();
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentialsException() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("no@example.com", "password")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        var savedUser = User.builder().id(UUID.randomUUID()).email("test@example.com")
                           .displayName("Test").passwordHash("hashed").build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "wrong")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    // ── RefreshAccessToken ────────────────────────────────────────────────────

    @Test
    void refreshAccessToken_validToken_returnsNewAccessToken() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        var user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
        var rt = RefreshToken.builder()
                .user(user)
                .tokenHash("placeholder")
                .expiresAt(Instant.now().plusSeconds(3600))
                .lastActiveAt(Instant.now())
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(rt));
        when(refreshTokenRepository.save(any())).thenReturn(rt);
        when(jwtUtil.generateAccessToken(any(), anyString())).thenReturn("new-jwt-token");

        String result = authService.refreshAccessToken("any-raw-token");

        assertThat(result).isEqualTo("new-jwt-token");
        verify(refreshTokenRepository).save(rt);
    }

    @Test
    void refreshAccessToken_inactiveToken_throwsTokenInactiveException() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        var user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
        var rt = RefreshToken.builder()
                .user(user)
                .tokenHash("placeholder")
                .expiresAt(Instant.now().plusSeconds(3600))
                .lastActiveAt(Instant.now().minusSeconds(8 * 24 * 3600)) // 8 days ago — exceeds 7-day inactivity threshold
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> authService.refreshAccessToken("any-raw-token"))
            .isInstanceOf(TokenInactiveException.class);
        verify(refreshTokenRepository).delete(rt);
    }

    @Test
    void refreshAccessToken_notFound_throwsTokenInactiveException() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshAccessToken("unknown-token"))
            .isInstanceOf(TokenInactiveException.class);
    }

    // ── UpdatePreferences ─────────────────────────────────────────────────────

    @Test
    void updatePreferences_success_savesAndReturnsUpdatedUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("a@b.com").displayName("A").uiMode(UiMode.CHARACTER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        var result = authService.updatePreferences(userId, UiMode.SERIOUS);

        assertThat(result.uiMode()).isEqualTo("SERIOUS");
        verify(userRepository).save(any(User.class));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_validToken_deletesRefreshToken() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        var rt = RefreshToken.builder().tokenHash("hash")
                             .expiresAt(Instant.now().plusSeconds(100))
                             .lastActiveAt(Instant.now()).build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(rt));

        authService.logout("any-raw-token");

        verify(refreshTokenRepository).delete(rt);
    }

    @Test
    void logout_nullToken_noOp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
        authService.logout(null);
        verifyNoInteractions(refreshTokenRepository);
    }
}
