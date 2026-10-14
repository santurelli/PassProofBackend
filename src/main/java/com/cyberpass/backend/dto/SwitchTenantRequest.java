package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for switching tenant context.
 */
public class SwitchTenantRequest {

    @JsonProperty("tenant_id")
    private String tenantId;

    public SwitchTenantRequest() {
    }

    public SwitchTenantRequest(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
