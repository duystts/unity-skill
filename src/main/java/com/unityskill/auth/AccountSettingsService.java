package com.unityskill.auth;

import com.unityskill.auth.dto.*;
import com.unityskill.auth.entity.User;
import com.unityskill.common.exception.BadRequestException;
import com.unityskill.common.exception.EmailAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountSettingsService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    // ── GET account ──────────────────────────────────────────────────────────

    public AccountResponse getAccount(UUID userId) {
        User user = requireUser(userId);
        return AccountResponse.from(user);
    }

    // ── Update profile (displayName, title, timezone, bio) ───────────────────

    @Transactional
    public AccountResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = requireUser(userId);
        if (req.displayName() != null && !req.displayName().isBlank()) {
            user.setDisplayName(req.displayName().trim());
        }
        if (req.title() != null) {
            user.setTitle(req.title().isBlank() ? null : req.title().trim());
        }
        if (req.timezone() != null && !req.timezone().isBlank()) {
            user.setTimezone(req.timezone().trim());
        }
        if (req.bio() != null) {
            user.setBio(req.bio().isBlank() ? null : req.bio().trim());
        }
        return AccountResponse.from(userRepository.save(user));
    }

    // ── Update avatar ────────────────────────────────────────────────────────

    @Transactional
    public AccountResponse updateAvatar(UUID userId, UpdateAvatarRequest req) {
        User user = requireUser(userId);
        // Accept null to remove avatar (reset to initials)
        String url = req.avatarUrl();
        if (url != null && !url.isBlank() && !url.startsWith("http")) {
            throw new BadRequestException("avatarUrl must be a valid URL or null");
        }
        user.setAvatarUrl(url == null || url.isBlank() ? null : url.trim());
        return AccountResponse.from(userRepository.save(user));
    }

    // ── Change email ─────────────────────────────────────────────────────────

    @Transactional
    public AccountResponse changeEmail(UUID userId, ChangeEmailRequest req) {
        User user = requireUser(userId);
        String newEmail = req.newEmail().trim().toLowerCase();

        // Must verify password if account has one
        if (user.getPasswordHash() != null) {
            if (req.currentPassword() == null || req.currentPassword().isBlank()) {
                throw new BadRequestException("Current password is required to change your email");
            }
            if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyExistsException(newEmail);
        }

        user.setEmail(newEmail);
        return AccountResponse.from(userRepository.save(user));
    }

    // ── Change / set password ────────────────────────────────────────────────

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = requireUser(userId);

        // If account already has a password, verify the current one
        if (user.getPasswordHash() != null) {
            if (req.currentPassword() == null || req.currentPassword().isBlank()) {
                throw new BadRequestException("Current password is required");
            }
            if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
            if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
                throw new BadRequestException("New password must differ from the current password");
            }
        }
        // OAuth-only users are setting a password for the first time — no current password needed

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        log.info("Password updated for user {}", userId);
    }

    // ── Delete account ───────────────────────────────────────────────────────

    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest req) {
        User user = requireUser(userId);

        // Require typed confirmation
        if (!"delete my account".equals(req.confirmation())) {
            throw new BadRequestException("Please type \"delete my account\" to confirm");
        }

        // Verify password for password-auth accounts
        if (user.getPasswordHash() != null) {
            if (req.password() == null || req.password().isBlank()) {
                throw new BadRequestException("Password is required to delete your account");
            }
            if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                throw new BadRequestException("Password is incorrect");
            }
        }

        // Revoke all sessions first
        refreshTokenRepository.deleteAllByUserId(userId);

        // Delete the user — cascade rules in DB handle related data
        userRepository.deleteById(userId);
        log.warn("Account deleted: user {}", userId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }
}
