package com.cyberpass.backend.resource;

import com.cyberpass.backend.dto.ExportRowRequest;
import com.cyberpass.backend.dto.QuestionRequest;
import com.cyberpass.backend.dto.QuestionResponse;
import com.cyberpass.backend.service.ExcelQuestionnaireService;
import com.cyberpass.backend.service.QuestionnaireProcessingService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.util.List;

/**
 * REST Endpoint for questionnaire parsing and analysis (RAG engine).
 * Enforces PostgreSQL Row-Level Security automatically based on the 'X-Tenant-ID' request header.
 */
@Path("/api/v1/questionnaire")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class QuestionnaireResource {

    @Inject
    QuestionnaireProcessingService questionnaireProcessingService;

    @Inject
    ExcelQuestionnaireService excelQuestionnaireService;

    @Inject
    com.cyberpass.backend.service.DocxQuestionnaireService docxQuestionnaireService;

    @Inject
    com.cyberpass.backend.service.KnowledgeBaseService knowledgeBaseService;

    @Inject
    com.cyberpass.backend.tenant.TenantAwarePgPool tenantAwarePgPool;

    @Inject
    com.cyberpass.backend.tenant.TenantContext tenantContext;

    /**
     * Accepts a single question payload, retrieves corresponding vectors from the database,
     * injects them as context into LangChain4j, and returns the suggested answer and sources.
     *
     * @param request The question request payload.
     * @return A Uni containing the QuestionResponse.
     */
    @POST
    @Path("/analyze-question")
    public Uni<QuestionResponse> analyzeQuestion(QuestionRequest request) {
        return questionnaireProcessingService.processQuestion(request);
    }

    /**
     * Accepts an uploaded Excel file, parses it, processes all found questions through the RAG engine,
     * and returns the list of answers.
     */
    @POST
    @Path("/upload-excel")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<List<QuestionResponse>> uploadExcel(@RestForm("file") FileUpload file) {
        if (file == null) {
            return Uni.createFrom().failure(new BadRequestException("No Excel file was uploaded."));
        }
        return checkQuestionnaireLimit()
            .chain(() -> excelQuestionnaireService.parseAndProcessExcel(file.filePath()));
    }

    /**
     * Accepts an uploaded Word (.docx) file, parses questions from tables or paragraphs,
     * processes all found questions through the RAG engine, and returns the list of answers.
     */
    @POST
    @Path("/upload-docx")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<List<QuestionResponse>> uploadDocx(@RestForm("file") FileUpload file) {
        if (file == null) {
            return Uni.createFrom().failure(new BadRequestException("No Word (.docx) file was uploaded."));
        }
        return checkQuestionnaireLimit()
            .chain(() -> docxQuestionnaireService.parseAndProcessDocx(file.filePath()));
    }

    /**
     * Receives the list of finalized answers, compiles them back into a styled Excel file,
     * and returns the binary spreadsheet file for client download.
     */
    @POST
    @Path("/export-excel")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportExcel(List<ExportRowRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Export list cannot be empty.")
                .build();
        }

        try {
            byte[] fileBytes = excelQuestionnaireService.exportToExcel(rows);
            return Response.ok(fileBytes)
                .header("Content-Disposition", "attachment; filename=\"questionnaire_suggested.xlsx\"")
                .build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to compile Excel file: " + e.getMessage())
                .build();
        }
    }

    /**
     * Receives the list of finalized answers, compiles them into a styled Word (.docx) file,
     * and returns the binary document for client download.
     */
    @POST
    @Path("/export-docx")
    @Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public Response exportDocx(List<ExportRowRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Export list cannot be empty.")
                .build();
        }

        try {
            byte[] fileBytes = docxQuestionnaireService.exportToDocx(rows);
            return Response.ok(fileBytes)
                .header("Content-Disposition", "attachment; filename=\"questionnaire_suggested.docx\"")
                .build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to compile Word (.docx) file: " + e.getMessage())
                .build();
        }
    }

    @POST
    @Path("/history")
    public Uni<Response> saveHistory(com.cyberpass.backend.dto.QuestionnaireHistoryDto dto) {
        if (dto.getFileName() == null || dto.getFileName().trim().isEmpty()) {
            throw new BadRequestException("File name is required.");
        }
        if (dto.getData() == null || dto.getData().trim().isEmpty()) {
            throw new BadRequestException("Questionnaire data is required.");
        }

        final String status = dto.getStatus() != null ? dto.getStatus() : "COMPLETED";

        Uni<java.util.UUID> saveHistoryUni;
        if (dto.getId() == null) {
            // New record (draft or completed) - verify limit first
            saveHistoryUni = checkQuestionnaireLimit().chain(() ->
                tenantAwarePgPool.withTransaction(conn ->
                    conn.preparedQuery(
                        "INSERT INTO processed_questionnaires (tenant_id, file_name, questions_count, data, status) " +
                        "VALUES ($1, $2, $3, $4::jsonb, $5) RETURNING id"
                    ).execute(io.vertx.mutiny.sqlclient.Tuple.of(
                        tenantContext.getTenantId(),
                        dto.getFileName(),
                        dto.getQuestionsCount(),
                        dto.getData(),
                        status
                    )).map(rows -> rows.iterator().next().getUUID("id"))
                )
            );
        } else {
            // Update existing record
            saveHistoryUni = tenantAwarePgPool.withTransaction(conn ->
                conn.preparedQuery(
                    "UPDATE processed_questionnaires " +
                    "SET file_name = $1, questions_count = $2, data = $3::jsonb, status = $4 " +
                    "WHERE id = $5 AND tenant_id = $6"
                ).execute(io.vertx.mutiny.sqlclient.Tuple.of(
                    dto.getFileName(),
                    dto.getQuestionsCount(),
                    dto.getData(),
                    status,
                    dto.getId(),
                    tenantContext.getTenantId()
                )).map(rows -> dto.getId())
            );
        }

        // 2. Chain with auto-learning vector ingestion only if status is COMPLETED
        return saveHistoryUni.chain(generatedId -> {
            if ("COMPLETED".equals(status)) {
                return knowledgeBaseService.ingestApprovedQuestions(dto.getFileName(), dto.getData())
                    .replaceWith(Response.status(Response.Status.CREATED)
                        .entity(java.util.Map.of("status", "success", "id", generatedId.toString()))
                        .build()
                    );
            } else {
                return Uni.createFrom().item(Response.status(Response.Status.CREATED)
                    .entity(java.util.Map.of("status", "success", "id", generatedId.toString()))
                    .build()
                );
            }
        });
    }

    @GET
    @Path("/history")
    public Uni<List<com.cyberpass.backend.dto.QuestionnaireHistoryDto>> getHistory() {
        java.util.UUID tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            return Uni.createFrom().failure(new BadRequestException("Tenant context non valido o mancante."));
        }

        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery(
                "SELECT id, file_name, questions_count, status, created_at " +
                "FROM processed_questionnaires " +
                "WHERE tenant_id = $1 " +
                "ORDER BY created_at DESC"
            ).execute(io.vertx.mutiny.sqlclient.Tuple.of(tenantId))
             .map(rows -> {
                 List<com.cyberpass.backend.dto.QuestionnaireHistoryDto> list = new java.util.ArrayList<>();
                 for (io.vertx.mutiny.sqlclient.Row row : rows) {
                     com.cyberpass.backend.dto.QuestionnaireHistoryDto item = new com.cyberpass.backend.dto.QuestionnaireHistoryDto();
                     item.setId(row.getUUID("id"));
                     item.setFileName(row.getString("file_name"));
                     item.setQuestionsCount(row.getInteger("questions_count"));
                     item.setStatus(row.getString("status"));
                     item.setCreatedAt(row.getOffsetDateTime("created_at").toString());
                     list.add(item);
                 }
                 return list;
             })
        );
    }

    @GET
    @Path("/history/{id}")
    public Uni<com.cyberpass.backend.dto.QuestionnaireHistoryDto> getHistoryDetail(@PathParam("id") String id) {
        java.util.UUID historyUuid;
        try {
            historyUuid = java.util.UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid history ID format.");
        }

        java.util.UUID tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant context non valido o mancante.");
        }

        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery(
                "SELECT id, file_name, questions_count, data, status, created_at " +
                "FROM processed_questionnaires " +
                "WHERE id = $1 AND tenant_id = $2"
            ).execute(io.vertx.mutiny.sqlclient.Tuple.of(historyUuid, tenantId))
             .map(rows -> {
                 if (!rows.iterator().hasNext()) {
                     throw new NotFoundException("Questionnaire history record not found.");
                 }
                 io.vertx.mutiny.sqlclient.Row row = rows.iterator().next();
                 com.cyberpass.backend.dto.QuestionnaireHistoryDto item = new com.cyberpass.backend.dto.QuestionnaireHistoryDto();
                 item.setId(row.getUUID("id"));
                 item.setFileName(row.getString("file_name"));
                 item.setQuestionsCount(row.getInteger("questions_count"));
                 item.setData(row.getValue("data").toString());
                 item.setStatus(row.getString("status"));
                 item.setCreatedAt(row.getOffsetDateTime("created_at").toString());
                 return item;
             })
        );
    }

    private Uni<Void> checkQuestionnaireLimit() {
        java.util.UUID tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            return Uni.createFrom().voidItem();
        }

        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery(
                "SELECT " +
                "(SELECT COUNT(*) FROM processed_questionnaires WHERE tenant_id = $1 AND created_at >= date_trunc('month', current_timestamp)) as current_count, " +
                "(SELECT max_questionnaires_per_month FROM tenants WHERE id = $1) as max_count"
            ).execute(io.vertx.mutiny.sqlclient.Tuple.of(tenantId))
             .chain(rows -> {
                 if (rows.iterator().hasNext()) {
                     io.vertx.mutiny.sqlclient.Row row = rows.iterator().next();
                     Long currentCount = row.getLong("current_count");
                     Integer maxCount = row.getInteger("max_count");
                     if (maxCount == null) maxCount = 5; // default fallback

                     if (currentCount >= maxCount) {
                         return Uni.createFrom().failure(new ForbiddenException(
                             "Hai raggiunto il limite massimo di " + maxCount + " questionari mensili consentiti dal tuo piano (Starter)."
                         ));
                     }
                 }
                 return Uni.createFrom().voidItem();
             })
        );
    }
}
