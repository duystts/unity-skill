package com.unityskill.auth;

import com.unityskill.auth.dto.PreferencesResponse;
import com.unityskill.auth.entity.UiMode;
import com.unityskill.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceIncognitoTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtUtil jwtUtil;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthService authService;

    private User buildUser(boolean incognito) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("dev@test.com")
                .displayName("Dev User")
                .uiMode(UiMode.CHARACTER)
                .isIncognito(incognito)
                .build();
    }

    @Test
    void getPreferences_returnsCurrentState() {
        // AC4
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(false)));

        PreferencesResponse result = authService.getPreferences(userId);

        assertThat(result.incognitoMode()).isFalse();
        assertThat(result.uiMode()).isEqualTo("CHARACTER");
    }

    @Test
    void enableIncognito_setsIncognitoTrue() {
        // AC1
        UUID userId = UUID.randomUUID();
        User user = buildUser(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PreferencesResponse result = authService.enableIncognito(userId);

        assertThat(result.incognitoMode()).isTrue();
        assertThat(user.isIncognito()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void disableIncognito_setsIncognitoFalse() {
        // AC3
        UUID userId = UUID.randomUUID();
        User user = buildUser(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PreferencesResponse result = authService.disableIncognito(userId);

        assertThat(result.incognitoMode()).isFalse();
        assertThat(user.isIncognito()).isFalse();
        verify(userRepository).save(user);
    }
}
