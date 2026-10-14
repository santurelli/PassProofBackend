package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO representing the response from the RAG engine, containing the suggested answer,
 * list of sources used for retrieval, and computed confidence score.
 */
public class QuestionResponse {

    @JsonProperty("question")
    private String question;

    @JsonProperty("suggested_answer")
    private String suggestedAnswer;

    @JsonProperty("sources")
    private List<SourceInfo> sources;

    @JsonProperty("confidence")
    private double confidence;

    public QuestionResponse() {
    }

    public QuestionResponse(String question, String suggestedAnswer, List<SourceInfo> sources, double confidence) {
        this.question = question;
        this.suggestedAnswer = suggestedAnswer;
        this.sources = sources;
        this.confidence = confidence;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSuggestedAnswer() {
        return suggestedAnswer;
    }

    public void setSuggestedAnswer(String suggestedAnswer) {
        this.suggestedAnswer = suggestedAnswer;
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

    /**
     * Nested class modeling a source snippet details.
     */
    public static class SourceInfo {
        @JsonProperty("document_reference")
        private String documentReference;

        @JsonProperty("content_excerpt")
        private String contentExcerpt;

        @JsonProperty("similarity_score")
        private double similarityScore;

        public SourceInfo() {
        }

        public SourceInfo(String documentReference, String contentExcerpt, double similarityScore) {
            this.documentReference = documentReference;
            this.contentExcerpt = contentExcerpt;
            this.similarityScore = similarityScore;
        }

        public String getDocumentReference() {
            return documentReference;
        }

        public void setDocumentReference(String documentReference) {
            this.documentReference = documentReference;
        }

        public String getContentExcerpt() {
            return contentExcerpt;
        }

        public void setContentExcerpt(String contentExcerpt) {
            this.contentExcerpt = contentExcerpt;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public void setSimilarityScore(double similarityScore) {
            this.similarityScore = similarityScore;
        }
    }
}
