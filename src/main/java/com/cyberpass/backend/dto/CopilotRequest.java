package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for incoming questions sent to the AI Security Copilot.
 */
public class CopilotRequest {

    @JsonProperty("question")
    private String question;

    @JsonProperty("context_hint")
    private String contextHint;

    public CopilotRequest() {
    }

    public CopilotRequest(String question, String contextHint) {
        this.question = question;
        this.contextHint = contextHint;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getContextHint() {
        return contextHint;
    }

    public void setContextHint(String contextHint) {
        this.contextHint = contextHint;
    }
}
