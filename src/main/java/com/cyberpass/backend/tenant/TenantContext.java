package com.cyberpass.backend.tenant;

import jakarta.enterprise.context.RequestScoped;
import java.util.UUID;

/**
 * Request-scoped bean to store the current tenant identifier resolved from the HTTP request context.
 */
@RequestScoped
public class TenantContext {

    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setTenantId(String tenantIdStr) {
        if (tenantIdStr != null && !tenantIdStr.trim().isEmpty()) {
            this.tenantId = UUID.fromString(tenantIdStr.trim());
        } else {
            this.tenantId = null;
        }
    }

    public boolean hasTenantId() {
        return tenantId != null;
    }
}
