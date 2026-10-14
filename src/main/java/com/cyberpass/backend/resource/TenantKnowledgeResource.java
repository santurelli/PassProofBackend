package com.cyberpass.backend.resource;

import com.cyberpass.backend.dto.DocumentIngestionRequest;
import com.cyberpass.backend.service.KnowledgeBaseService;
import com.cyberpass.backend.tenant.TenantAwarePgPool;
import com.cyberpass.backend.tenant.TenantContext;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Endpoint to manage the tenant knowledge base.
 * Automatically enforces PostgreSQL Row-Level Security based on the tenant context.
 */
@Path("/api/v1/knowledge")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class TenantKnowledgeResource {

    @Inject
    TenantAwarePgPool tenantAwarePgPool;

    @Inject
    TenantContext tenantContext;

    @Inject
    KnowledgeBaseService knowledgeBaseService;

    @GET
    public Uni<List<Map<String, Object>>> getKnowledgeBase() {
        UUID tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            return Uni.createFrom().failure(new BadRequestException("Tenant context mancante."));
        }

        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery("SELECT id, document_reference, content FROM tenant_knowledge_vectors WHERE tenant_id = $1 ORDER BY id DESC")
                .execute(Tuple.of(tenantId))
                .map(rows -> {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (Row row : rows) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", row.getLong("id"));
                        map.put("document_reference", row.getString("document_reference"));
                        map.put("content", row.getString("content"));
                        list.add(map);
                    }
                    return list;
                })
        );
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<Response> uploadDocument(
        @org.jboss.resteasy.reactive.RestForm("file") org.jboss.resteasy.reactive.multipart.FileUpload file
    ) {
        if (file == null) {
            return Uni.createFrom().failure(new BadRequestException("File is missing."));
        }

        String fileName = file.fileName();
        
        return Uni.createFrom().item(() -> {
            try {
                String content;
                String lowerName = fileName.toLowerCase();
                if (lowerName.endsWith(".docx")) {
                    try (java.io.InputStream is = java.nio.file.Files.newInputStream(file.uploadedFile());
                         org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
                        org.apache.poi.xwpf.extractor.XWPFWordExtractor extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc);
                        content = extractor.getText();
                    }
                } else if (lowerName.endsWith(".pdf")) {
                    try (java.io.InputStream is = java.nio.file.Files.newInputStream(file.uploadedFile());
                         org.apache.pdfbox.pdmodel.PDDocument pdfDocument = org.apache.pdfbox.pdmodel.PDDocument.load(is)) {
                        org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                        content = stripper.getText(pdfDocument);
                    }
                } else if (lowerName.endsWith(".txt")) {
                    content = java.nio.file.Files.readString(file.uploadedFile(), java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    throw new BadRequestException("Unsupported file type. Please upload a .pdf, .docx or .txt file.");
                }
                return new DocumentIngestionRequest(fileName, content);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read uploaded file: " + e.getMessage(), e);
            }
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool())
          .chain(ingestionRequest -> 
              knowledgeBaseService.ingestDocument(ingestionRequest)
          )
          .map(v -> Response.status(Response.Status.CREATED)
              .entity(Map.of(
                  "status", "success",
                  "message", "Document '" + fileName + "' successfully chunked, embedded, and indexed under tenant RLS context."
              ))
              .build()
          );
    }

    @DELETE
    @Path("/document/{reference}")
    public Uni<Response> deleteDocument(@PathParam("reference") String documentReference) {
        if (documentReference == null || documentReference.trim().isEmpty()) {
            throw new BadRequestException("Document reference is required.");
        }
        
        UUID tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant context mancante.");
        }

        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery("DELETE FROM tenant_knowledge_vectors WHERE tenant_id = $1 AND document_reference = $2")
                .execute(Tuple.of(tenantId, documentReference.trim()))
                .map(rows -> Response.ok(Map.of(
                    "status", "success",
                    "message", "Document '" + documentReference + "' successfully deleted from RLS context."
                )).build())
        );
    }
}
