package com.arkil.policy;

import org.springframework.security.core.AuthenticationException;

/**
 * Raised when a local password login is attempted before email verification.
 */
public class EmailNotVerifiedAuthenticationException extends AuthenticationException {

    private final String email;

    public EmailNotVerifiedAuthenticationException(String email) {
        super("Email address is not verified");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
