package com.arkil.client;

/**
 * Available authentication modules that clients can enable/disable.
 */
public enum AuthModule {
    /**
     * Username/email + password authentication.
     */
    EMAIL_PASSWORD("Email/Password", "Standard username and password login"),

    /**
     * Google OAuth2 social login.
     */
    OAUTH2_GOOGLE("Google", "Sign in with Google"),

    /**
     * GitHub OAuth2 social login.
     */
    OAUTH2_GITHUB("GitHub", "Sign in with GitHub"),

    /**
     * Apple OAuth2 social login.
     */
    OAUTH2_APPLE("Apple", "Sign in with Apple"),

    /**
     * LinkedIn OAuth2 social login.
     */
    OAUTH2_LINKEDIN("LinkedIn", "Sign in with LinkedIn"),

    /**
     * Passkey/WebAuthn authentication.
     */
    PASSKEY("Passkey", "Passwordless authentication with passkeys"),

    /**
     * TOTP-based multi-factor authentication.
     */
    TOTP("Authenticator App", "Time-based one-time password (TOTP)");

    private final String displayName;
    private final String description;

    AuthModule(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
