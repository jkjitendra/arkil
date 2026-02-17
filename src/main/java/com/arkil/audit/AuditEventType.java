package com.arkil.audit;

/**
 * Types of security-relevant events to audit.
 */
public enum AuditEventType {
    // Authentication events
    AUTH_LOGIN_SUCCESS,
    AUTH_LOGIN_FAILURE,
    AUTH_LOGOUT,
    AUTH_TOKEN_ISSUED,
    AUTH_TOKEN_REVOKED,

    // MFA events
    MFA_ENROLLED,
    MFA_VERIFIED,
    MFA_FAILED,

    // Admin events
    ADMIN_LOGIN,
    ADMIN_LOGOUT,
    ADMIN_CREATED,

    // Configuration events
    CONFIG_CLIENT_CREATED,
    CONFIG_CLIENT_UPDATED,
    CONFIG_CLIENT_DELETED,
    CONFIG_POLICY_UPDATED,

    // User lifecycle
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    USER_PASSWORD_CHANGED,
    USER_BLOCKED,
    USER_UNBLOCKED,

    // Session lifecycle
    SESSION_CREATED,

    // Webhook events
    WEBHOOK_CREATED,
    WEBHOOK_UPDATED,
    WEBHOOK_DELETED,
    WEBHOOK_DELIVERED,
    WEBHOOK_DELIVERY_FAILED,

    // Rate limiting
    RATE_LIMIT_EXCEEDED
}
