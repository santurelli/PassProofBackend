package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Data Transfer Object representing the public trust/security profile of a tenant.
 */
public class TrustProfileDto {

    private UUID id;
    
    @JsonProperty("tenant_id")
    private UUID tenantId;
    
    @JsonProperty("public_name")
    private String publicName;
    
    @JsonProperty("company_description")
    private String companyDescription;
    
    @JsonProperty("security_measures")
    private String securityMeasures; // JSON string representing configured security checks
    
    private boolean published;

    public TrustProfileDto() {
    }

    public TrustProfileDto(UUID id, UUID tenantId, String publicName, String companyDescription, String securityMeasures, boolean published) {
        this.id = id;
        this.tenantId = tenantId;
        this.publicName = publicName;
        this.companyDescription = companyDescription;
        this.securityMeasures = securityMeasures;
        this.published = published;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPublicName() {
        return publicName;
    }

    public void setPublicName(String publicName) {
        this.publicName = publicName;
    }

    public String getCompanyDescription() {
        return companyDescription;
    }

    public void setCompanyDescription(String companyDescription) {
        this.companyDescription = companyDescription;
    }

    public String getSecurityMeasures() {
        return securityMeasures;
    }

    public void setSecurityMeasures(String securityMeasures) {
        this.securityMeasures = securityMeasures;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }
}
