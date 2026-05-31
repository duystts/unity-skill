package com.unityskill.auth.dto;

/**
 * Confirmation payload for account deletion.
 * The user must type the phrase "delete my account" to confirm.
 * Password is required for password-auth accounts; omit for OAuth-only.
 */
public record DeleteAccountRequest(
        String confirmation,   // must equal "delete my account"
        String password        // required only if hasPassword=true
) {}
