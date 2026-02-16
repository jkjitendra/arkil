package com.arkil.tenant;

import java.util.UUID;

/**
 * ThreadLocal holder for the current tenant context.
 * Set early in the request lifecycle by TenantContextFilter,
 * cleared after the request completes.
 *
 * For API requests: resolved from JWT tenant_id claim.
 * For end-user auth flows: resolved from client context → project → tenantId.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Returns true if a tenant context is available for the current thread.
     */
    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    /**
     * Get tenant ID or throw if not set. Use in code paths that require tenant scoping.
     */
    public static UUID requireTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context available - this operation requires tenant scoping");
        }
        return tenantId;
    }
}
