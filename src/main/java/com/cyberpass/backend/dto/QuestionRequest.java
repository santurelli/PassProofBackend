package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing an incoming question to be processed by the RAG engine.
 */
public class QuestionRequest {

    @JsonProperty("question")
    private String question;

    @JsonProperty("reference_id")
    private String referenceId;

    public QuestionRequest() {
    }

    public QuestionRequest(String question, String referenceId) {
        this.question = question;
        this.referenceId = referenceId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }
}
