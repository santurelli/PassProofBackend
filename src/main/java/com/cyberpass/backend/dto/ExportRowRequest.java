package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a single reviewed row containing the question, final approved answer,
 * and confidence score, to be exported into the output Excel file.
 */
public class ExportRowRequest {

    @JsonProperty("question")
    private String question;

    @JsonProperty("answer")
    private String answer;

    @JsonProperty("confidence")
    private double confidence;

    public ExportRowRequest() {
    }

    public ExportRowRequest(String question, String answer, double confidence) {
        this.question = question;
        this.answer = answer;
        this.confidence = confidence;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
