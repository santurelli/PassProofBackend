package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for step 2 MFA verification request.
 */
public class MfaVerifyRequest {

    @JsonProperty("temp_token")
    private String tempToken;

    @JsonProperty("code")
    private int code;

    public MfaVerifyRequest() {
    }

    public MfaVerifyRequest(String tempToken, int code) {
        this.tempToken = tempToken;
        this.code = code;
    }

    public String getTempToken() {
        return tempToken;
    }

    public void setTempToken(String tempToken) {
        this.tempToken = tempToken;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }
}
