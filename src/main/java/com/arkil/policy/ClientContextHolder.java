package com.arkil.policy;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped holder for the current client context.
 * Populated by ClientContextFilter, consumed by guards and controllers.
 */
@Component
@RequestScope
public class ClientContextHolder {

    private ClientContext context;

    public ClientContext getContext() {
        return context;
    }

    public void setContext(ClientContext context) {
        this.context = context;
    }

    public boolean hasContext() {
        return context != null && context.isResolved();
    }
}
