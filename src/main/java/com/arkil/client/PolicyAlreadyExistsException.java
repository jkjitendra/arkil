package com.arkil.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PolicyAlreadyExistsException extends RuntimeException {
    public PolicyAlreadyExistsException(String clientId) {
        super("Policy already exists for client: " + clientId);
    }
}
