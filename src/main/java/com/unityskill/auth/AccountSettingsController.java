package com.unityskill.auth;

import com.unityskill.auth.dto.*;
// all DTOs in package
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Account settings endpoints — all require authentication.
 *
 * <pre>
 * GET    /api/v1/users/me/account          — full account info for settings page
 * PATCH  /api/v1/users/me/avatar           — update or remove avatar URL
 * PATCH  /api/v1/users/me/email            — change email (password verification for pwd accounts)
 * PATCH  /api/v1/users/me/password         — change / set password
 * DELETE /api/v1/users/me                  — permanently delete account
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class AccountSettingsController {

    private final AccountSettingsService accountSettingsService;

    /**
     * GET /api/v1/users/me/account
     * Returns full account info: profile, connected accounts, hasPassword flag.
     * Used by the Account Settings page to populate all fields at once.
     */
    @GetMapping("/api/v1/users/me/account")
    public ResponseEntity<Map<String, Object>> getAccount(
            @AuthenticationPrincipal String userId) {
        AccountResponse account = accountSettingsService.getAccount(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", account));
    }

    /**
     * PATCH /api/v1/users/me/profile
     * Body: { displayName?, title?, timezone?, bio? } — all fields optional.
     */
    @PatchMapping("/api/v1/users/me/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal String userId) {
        AccountResponse updated = accountSettingsService.updateProfile(UUID.fromString(userId), request);
        return ResponseEntity.ok(Map.of("data", updated));
    }

    /**
     * PATCH /api/v1/users/me/avatar
     * Body: { "avatarUrl": "https://..." }  — pass null to remove avatar.
     */
    @PatchMapping("/api/v1/users/me/avatar")
    public ResponseEntity<Map<String, Object>> updateAvatar(
            @RequestBody UpdateAvatarRequest request,
            @AuthenticationPrincipal String userId) {
        AccountResponse updated = accountSettingsService.updateAvatar(UUID.fromString(userId), request);
        return ResponseEntity.ok(Map.of("data", updated));
    }

    /**
     * PATCH /api/v1/users/me/email
     * Body: { "newEmail": "...", "currentPassword": "..." }
     * currentPassword required for accounts that have a password.
     * OAuth-only accounts can change email without a password.
     */
    @PatchMapping("/api/v1/users/me/email")
    public ResponseEntity<Map<String, Object>> changeEmail(
            @Valid @RequestBody ChangeEmailRequest request,
            @AuthenticationPrincipal String userId) {
        AccountResponse updated = accountSettingsService.changeEmail(UUID.fromString(userId), request);
        return ResponseEntity.ok(Map.of("data", updated));
    }

    /**
     * PATCH /api/v1/users/me/password
     * Body: { "currentPassword": "...", "newPassword": "..." }
     * currentPassword is required when the account already has a password.
     * OAuth-only users can omit currentPassword to set a password for the first time.
     */
    @PatchMapping("/api/v1/users/me/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal String userId) {
        accountSettingsService.changePassword(UUID.fromString(userId), request);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    /**
     * DELETE /api/v1/users/me
     * Body: { "confirmation": "delete my account", "password": "..." }
     * Permanently deletes the account and revokes all sessions.
     * password is required only for accounts that have a password set.
     */
    @DeleteMapping("/api/v1/users/me")
    public ResponseEntity<Map<String, Object>> deleteAccount(
            @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal String userId) {
        accountSettingsService.deleteAccount(UUID.fromString(userId), request);
        return ResponseEntity.ok(Map.of("message", "Account deleted"));
    }
}
