package com.arkil.audit;

/**
 * Immutable request metadata that is safe to use after an HTTP request has completed.
 */
record AuditRequestDetails(String ipAddress, String userAgent) {

    static AuditRequestDetails empty() {
        return new AuditRequestDetails(null, null);
    }
}
