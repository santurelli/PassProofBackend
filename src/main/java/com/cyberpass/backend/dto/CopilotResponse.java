package com.cyberpass.backend.dto;

import com.cyberpass.backend.dto.QuestionResponse.SourceInfo;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO returning the AI Copilot's answer, cited knowledge sources, and calculated confidence level.
 */
public class CopilotResponse {

    @JsonProperty("question")
    private String question;

    @JsonProperty("answer")
    private String answer;

    @JsonProperty("sources")
    private List<SourceInfo> sources;

    @JsonProperty("confidence")
    private double confidence;

    public CopilotResponse() {
    }

    public CopilotResponse(String question, String answer, List<SourceInfo> sources, double confidence) {
        this.question = question;
        this.answer = answer;
        this.sources = sources;
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

    public List<SourceInfo> getSources() {
        return sources;
    }

    public void setSources(List<SourceInfo> sources) {
        this.sources = sources;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
