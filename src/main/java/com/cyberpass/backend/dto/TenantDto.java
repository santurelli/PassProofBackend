package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * DTO representing tenant details linked to a user membership.
 */
public class TenantDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("role")
    private String role; // OWNER, EDITOR, VIEWER

    @JsonProperty("plan_tier")
    private String planTier;

    public TenantDto() {
    }

    public TenantDto(UUID id, String companyName, String role) {
        this.id = id;
        this.companyName = companyName;
        this.role = role;
        this.planTier = "STARTER";
    }

    public TenantDto(UUID id, String companyName, String role, String planTier) {
        this.id = id;
        this.companyName = companyName;
        this.role = role;
        this.planTier = planTier;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPlanTier() {
        return planTier;
    }

    public void setPlanTier(String planTier) {
        this.planTier = planTier;
    }
}
