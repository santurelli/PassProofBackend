package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO representing the response at different stages of the login flow.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    @JsonProperty("status")
    private String status; // MFA_SETUP_REQUIRED, MFA_CHALLENGE_REQUIRED, SUCCESS

    @JsonProperty("temp_token")
    private String tempToken; // Temporary JWT used for MFA step 2 verification

    @JsonProperty("totp_uri")
    private String totpUri; // Key URI for QR Code generation (returned during MFA setup)

    @JsonProperty("backup_codes")
    private List<String> backupCodes; // Recovery backup codes (returned during MFA setup)

    @JsonProperty("token")
    private String finalToken; // Final JWT token (returned on successful authentication)

    public LoginResponse() {
    }

    public static LoginResponse setupRequired(String tempToken, String totpUri, List<String> backupCodes) {
        LoginResponse resp = new LoginResponse();
        resp.setStatus("MFA_SETUP_REQUIRED");
        resp.setTempToken(tempToken);
        resp.setTotpUri(totpUri);
        resp.setBackupCodes(backupCodes);
        return resp;
    }

    public static LoginResponse challengeRequired(String tempToken) {
        LoginResponse resp = new LoginResponse();
        resp.setStatus("MFA_CHALLENGE_REQUIRED");
        resp.setTempToken(tempToken);
        return resp;
    }

    public static LoginResponse success(String finalToken) {
        LoginResponse resp = new LoginResponse();
        resp.setStatus("SUCCESS");
        resp.setFinalToken(finalToken);
        return resp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTempToken() {
        return tempToken;
    }

    public void setTempToken(String tempToken) {
        this.tempToken = tempToken;
    }

    public String getTotpUri() {
        return totpUri;
    }

    public void setTotpUri(String totpUri) {
        this.totpUri = totpUri;
    }

    public List<String> getBackupCodes() {
        return backupCodes;
    }

    public void setBackupCodes(List<String> backupCodes) {
        this.backupCodes = backupCodes;
    }

    public String getFinalToken() {
        return finalToken;
    }

    public void setFinalToken(String finalToken) {
        this.finalToken = finalToken;
    }
}
