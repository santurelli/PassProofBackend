package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO carrying aggregated metrics, ROI calculations, and compliance statistics for a tenant.
 */
public class DashboardMetricsDto {

    @JsonProperty("time_saved_hours")
    private double timeSavedHours;

    @JsonProperty("total_questionnaires")
    private long totalQuestionnaires;

    @JsonProperty("current_month_questionnaires")
    private long currentMonthQuestionnaires;

    @JsonProperty("max_questionnaires_limit")
    private int maxQuestionnairesLimit;

    @JsonProperty("plan_tier")
    private String planTier;

    @JsonProperty("total_questions_answered")
    private long totalQuestionsAnswered;

    @JsonProperty("average_confidence")
    private double averageConfidence;

    @JsonProperty("total_knowledge_docs")
    private long totalKnowledgeDocs;

    @JsonProperty("total_knowledge_vectors")
    private long totalKnowledgeVectors;

    @JsonProperty("total_nda_signatures")
    private long totalNdaSignatures;

    @JsonProperty("trust_portal_published")
    private boolean trustPortalPublished;

    @JsonProperty("active_security_controls")
    private int activeSecurityControls;

    public DashboardMetricsDto() {
    }

    public double getTimeSavedHours() {
        return timeSavedHours;
    }

    public void setTimeSavedHours(double timeSavedHours) {
        this.timeSavedHours = timeSavedHours;
    }

    public long getTotalQuestionnaires() {
        return totalQuestionnaires;
    }

    public void setTotalQuestionnaires(long totalQuestionnaires) {
        this.totalQuestionnaires = totalQuestionnaires;
    }

    public long getCurrentMonthQuestionnaires() {
        return currentMonthQuestionnaires;
    }

    public void setCurrentMonthQuestionnaires(long currentMonthQuestionnaires) {
        this.currentMonthQuestionnaires = currentMonthQuestionnaires;
    }

    public int getMaxQuestionnairesLimit() {
        return maxQuestionnairesLimit;
    }

    public void setMaxQuestionnairesLimit(int maxQuestionnairesLimit) {
        this.maxQuestionnairesLimit = maxQuestionnairesLimit;
    }

    public String getPlanTier() {
        return planTier;
    }

    public void setPlanTier(String planTier) {
        this.planTier = planTier;
    }

    public long getTotalQuestionsAnswered() {
        return totalQuestionsAnswered;
    }

    public void setTotalQuestionsAnswered(long totalQuestionsAnswered) {
        this.totalQuestionsAnswered = totalQuestionsAnswered;
    }

    public double getAverageConfidence() {
        return averageConfidence;
    }

    public void setAverageConfidence(double averageConfidence) {
        this.averageConfidence = averageConfidence;
    }

    public long getTotalKnowledgeDocs() {
        return totalKnowledgeDocs;
    }

    public void setTotalKnowledgeDocs(long totalKnowledgeDocs) {
        this.totalKnowledgeDocs = totalKnowledgeDocs;
    }

    public long getTotalKnowledgeVectors() {
        return totalKnowledgeVectors;
    }

    public void setTotalKnowledgeVectors(long totalKnowledgeVectors) {
        this.totalKnowledgeVectors = totalKnowledgeVectors;
    }

    public long getTotalNdaSignatures() {
        return totalNdaSignatures;
    }

    public void setTotalNdaSignatures(long totalNdaSignatures) {
        this.totalNdaSignatures = totalNdaSignatures;
    }

    public boolean isTrustPortalPublished() {
        return trustPortalPublished;
    }

    public void setTrustPortalPublished(boolean trustPortalPublished) {
        this.trustPortalPublished = trustPortalPublished;
    }

    public int getActiveSecurityControls() {
        return activeSecurityControls;
    }

    public void setActiveSecurityControls(int activeSecurityControls) {
        this.activeSecurityControls = activeSecurityControls;
    }
}
