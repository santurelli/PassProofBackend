package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for team invitation requests.
 */
public class InviteRequest {

    @JsonProperty("email")
    private String email;

    @JsonProperty("role")
    private String role; // OWNER, EDITOR, VIEWER

    public InviteRequest() {
    }

    public InviteRequest(String email, String role) {
        this.email = email;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
