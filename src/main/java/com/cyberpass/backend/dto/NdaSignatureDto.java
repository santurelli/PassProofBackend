package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Data Transfer Object representing a digital NDA signature.
 */
public class NdaSignatureDto {

    private UUID id;
    
    @JsonProperty("tenant_id")
    private UUID tenantId;
    
    @JsonProperty("full_name")
    private String fullName;
    
    private String email;
    
    @JsonProperty("company_name")
    private String companyName;
    
    @JsonProperty("signed_at")
    private String signedAt;

    public NdaSignatureDto() {
    }

    public NdaSignatureDto(UUID id, UUID tenantId, String fullName, String email, String companyName, String signedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.fullName = fullName;
        this.email = email;
        this.companyName = companyName;
        this.signedAt = signedAt;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(String signedAt) {
        this.signedAt = signedAt;
    }
}
