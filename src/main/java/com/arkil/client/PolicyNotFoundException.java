package com.arkil.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(String clientId) {
        super("Policy not found for client: " + clientId);
    }
}
