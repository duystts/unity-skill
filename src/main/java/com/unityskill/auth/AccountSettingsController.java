package com.unityskill.auth;

import com.unityskill.auth.dto.*;
import com.unityskill.attachment.CloudinaryService;
import org.springframework.web.multipart.MultipartFile;
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
    private final CloudinaryService cloudinaryService;

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
     * POST /api/v1/users/me/avatar/upload
     * Multipart file upload — uploads to Cloudinary and sets the user's avatarUrl.
     */
    @PostMapping(value = "/api/v1/users/me/avatar/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String userId) {
        try {
            var result = cloudinaryService.upload(file, "avatars/" + userId);
            String url = (String) result.get("secure_url");
            AccountResponse updated = accountSettingsService.updateAvatar(
                    UUID.fromString(userId), new UpdateAvatarRequest(url));
            return ResponseEntity.ok(Map.of("data", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to upload avatar: " + e.getMessage()));
        }
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
     * GET /api/v1/users/me/notification-prefs
     * Returns the user's notification preferences map.
     */
    @GetMapping("/api/v1/users/me/notification-prefs")
    public ResponseEntity<Map<String, Object>> getNotificationPrefs(
            @AuthenticationPrincipal String userId) {
        var prefs = accountSettingsService.getNotificationPrefs(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("data", prefs));
    }

    /**
     * PATCH /api/v1/users/me/notification-prefs
     * Body: { "ticketAssigned": true, "achievementEarned": false, ... }
     */
    @PatchMapping("/api/v1/users/me/notification-prefs")
    public ResponseEntity<Map<String, Object>> updateNotificationPrefs(
            @RequestBody Map<String, Boolean> prefs,
            @AuthenticationPrincipal String userId) {
        var updated = accountSettingsService.updateNotificationPrefs(UUID.fromString(userId), prefs);
        return ResponseEntity.ok(Map.of("data", updated));
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
