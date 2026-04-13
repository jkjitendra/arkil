package com.arkil.policy;

import org.springframework.security.core.AuthenticationException;

/**
 * Raised when the submitted TOTP code is invalid during hosted login.
 */
public class InvalidTotpAuthenticationException extends AuthenticationException {

    private final String identifier;

    public InvalidTotpAuthenticationException(String identifier) {
        super("Invalid TOTP code");
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }
}
