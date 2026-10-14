package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Data Transfer Object representing a row in questionnaire processing history.
 */
public class QuestionnaireHistoryDto {
    
    private UUID id;
    
    @JsonProperty("file_name")
    private String fileName;
    
    @JsonProperty("questions_count")
    private int questionsCount;
    
    private String data; // JSON string representing the final answers array
    
    private String status; // 'DRAFT' or 'COMPLETED'
    
    @JsonProperty("created_at")
    private String createdAt;

    public QuestionnaireHistoryDto() {
    }

    public QuestionnaireHistoryDto(UUID id, String fileName, int questionsCount, String data, String status, String createdAt) {
        this.id = id;
        this.fileName = fileName;
        this.questionsCount = questionsCount;
        this.data = data;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getQuestionsCount() {
        return questionsCount;
    }

    public void setQuestionsCount(int questionsCount) {
        this.questionsCount = questionsCount;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
