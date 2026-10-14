package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * DTO for self-service tenant registration request.
 * Contains the owner account credentials and company registration info.
 */
public class TenantRegisterRequest {

    // User details
    @JsonProperty("email")
    private String email;

    @JsonProperty("password")
    private String password;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    // Company details
    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("vat_number")
    private String vatNumber;

    @JsonProperty("billing_data")
    private Map<String, Object> billingData;

    public TenantRegisterRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getVatNumber() {
        return vatNumber;
    }

    public void setVatNumber(String vatNumber) {
        this.vatNumber = vatNumber;
    }

    public Map<String, Object> getBillingData() {
        return billingData;
    }

    public void setBillingData(Map<String, Object> billingData) {
        this.billingData = billingData;
    }
}
