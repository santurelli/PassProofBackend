package com.cyberpass.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for requesting document ingestion into the tenant's knowledge base.
 */
public class DocumentIngestionRequest {

    @JsonProperty("document_reference")
    private String documentReference;

    @JsonProperty("content")
    private String content;

    public DocumentIngestionRequest() {
    }

    public DocumentIngestionRequest(String documentReference, String content) {
        this.documentReference = documentReference;
        this.content = content;
    }

    public String getDocumentReference() {
        return documentReference;
    }

    public void setDocumentReference(String documentReference) {
        this.documentReference = documentReference;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
