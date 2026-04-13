package com.arkil.policy;

import org.springframework.security.core.AuthenticationException;

/**
 * Raised when a hosted password login needs a TOTP code.
 */
public class TotpRequiredAuthenticationException extends AuthenticationException {

    private final String identifier;

    public TotpRequiredAuthenticationException(String identifier) {
        super("TOTP code required");
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }
}
