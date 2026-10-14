package com.cyberpass.backend.service;

import com.cyberpass.backend.dto.DocumentIngestionRequest;
import com.cyberpass.backend.tenant.TenantAwarePgPool;
import com.cyberpass.backend.tenant.TenantContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.mutiny.sqlclient.Row;
import com.cyberpass.backend.dto.QuestionResponse.SourceInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service to handle document ingestion.
 * It manages document chunking, calls the OpenAI Embedding Model via LangChain4j,
 * and saves vectors inside a Row-Level Security postgres transaction.
 */
@ApplicationScoped
public class KnowledgeBaseService {

    private static final Logger LOG = Logger.getLogger(KnowledgeBaseService.class);
    private static final int CHUNK_SIZE = 1000;
    private static final int OVERLAP = 150;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    TenantContext tenantContext;

    @Inject
    TenantAwarePgPool tenantAwarePgPool;

    /**
     * Splits, embeds, and saves a document under the current tenant's Row-Level Security scope.
     *
     * @param request The ingestion request containing document reference and text content.
     * @return A Uni that completes when all vector segments are successfully saved.
     */
    public Uni<Void> ingestDocument(DocumentIngestionRequest request) {
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Document content cannot be empty."));
        }

        List<String> chunks = splitText(request.getContent(), CHUNK_SIZE, OVERLAP);
        LOG.infof("Document '%s' split into %d chunks. Starting vector ingestion for tenant %s.", 
            request.getDocumentReference(), chunks.size(), tenantContext.getTenantId());

        // Perform the operations within the tenant-isolated database transaction
        return tenantAwarePgPool.withTransaction(conn -> {
            List<Uni<Void>> chunkIngestions = new ArrayList<>();

            for (String chunk : chunks) {
                // 1. Generate the vector embedding using LangChain4j.
                // Since HttpClient calls in LangChain4j are blocking, we execute this step on the worker thread pool.
                Uni<float[]> embeddingUni = Uni.createFrom().item(() -> {
                    LOG.debugf("Generating embedding for chunk segment of size: %d", chunk.length());
                    Response<Embedding> response = embeddingModel.embed(chunk);
                    return response.content().vector();
                }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());

                // 2. Chain the embedding generation with the database insert.
                Uni<Void> insertUni = embeddingUni.chain(vector -> {
                    String vectorString = Arrays.toString(vector);
                    LOG.debugf("Inserting chunk into PostgreSQL under tenant: %s", tenantContext.getTenantId());
                    
                    return conn.preparedQuery(
                        "INSERT INTO tenant_knowledge_vectors (tenant_id, document_reference, content, embedding) " +
                        "VALUES ($1, $2, $3, $4::vector)"
                    ).execute(Tuple.of(
                        tenantContext.getTenantId(),
                        request.getDocumentReference(),
                        chunk,
                        vectorString
                    )).replaceWithVoid();
                });

                chunkIngestions.add(insertUni);
            }

            // Join all chunk insertions so they run in parallel within the same transaction.
            // If any chunk fails (e.g. OpenAI timeout or RLS validation error), the transaction is rolled back.
            return Uni.join().all(chunkIngestions).andFailFast().replaceWithVoid();
        });
    }

    /**
     * Performs a cosine similarity search on pgvector embeddings in the database.
     * This query is automatically isolated to the current tenant's context via RLS.
     *
     * @param question The user's query question.
     * @param maxResults The maximum number of relevant segments to retrieve (K).
     * @return A Uni containing the list of matching source chunks and their similarity scores.
     */
    public Uni<List<SourceInfo>> similaritySearch(String question, int maxResults) {
        if (question == null || question.trim().isEmpty()) {
            return Uni.createFrom().item(new ArrayList<>());
        }

        // 1. Generate the query embedding using OpenAI (blocking HTTP call run on worker pool)
        Uni<float[]> embeddingUni = Uni.createFrom().item(() -> {
            Response<Embedding> response = embeddingModel.embed(question);
            return response.content().vector();
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());

        // 2. Query Postgres pgvector for cosine similarity under RLS and explicit tenant filter
        return tenantAwarePgPool.withTransaction(conn ->
            embeddingUni.chain(vector -> {
                String vectorString = Arrays.toString(vector);
                return conn.preparedQuery(
                    "SELECT document_reference, content, (1 - (embedding <=> $1::vector)) AS similarity " +
                    "FROM tenant_knowledge_vectors " +
                    "WHERE tenant_id = $2 " +
                    "ORDER BY embedding <=> $1::vector " +
                    "LIMIT $3"
                ).execute(Tuple.of(vectorString, tenantContext.getTenantId(), maxResults))
                .map(rows -> {
                    List<SourceInfo> list = new ArrayList<>();
                    for (Row row : rows) {
                        list.add(new SourceInfo(
                            row.getString("document_reference"),
                            row.getString("content"),
                            row.getDouble("similarity")
                        ));
                    }
                    return list;
                });
            })
        );
    }

    /**
     * Ingests a list of approved Q&A pairs into the vector knowledge base for auto-learning.
     */
    public Uni<Void> ingestApprovedQuestions(String fileName, String dataJson) {
        if (dataJson == null || dataJson.trim().isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        io.vertx.core.json.JsonArray array;
        try {
            array = new io.vertx.core.json.JsonArray(dataJson);
        } catch (Exception e) {
            LOG.error("Failed to parse history data JSON for auto-learning", e);
            return Uni.createFrom().voidItem();
        }

        List<Uni<Void>> ingestions = new ArrayList<>();
        String docRef = "Approvato: " + fileName;

        for (int i = 0; i < array.size(); i++) {
            io.vertx.core.json.JsonObject obj = array.getJsonObject(i);
            String question = obj.getString("question");
            String answer = obj.getString("answer");
            if (question == null || answer == null) continue;

            String content = "Domanda: " + question.trim() + "\nRisposta Approvata: " + answer.trim();

            // Generate embedding and save (using RLS context pgPool transaction)
            Uni<float[]> embeddingUni = Uni.createFrom().item(() -> {
                Response<Embedding> response = embeddingModel.embed(content);
                return response.content().vector();
            }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());

            Uni<Void> insertUni = tenantAwarePgPool.withTransaction(conn ->
                embeddingUni.chain(vector -> {
                    String vectorString = Arrays.toString(vector);
                    return conn.preparedQuery(
                        "INSERT INTO tenant_knowledge_vectors (tenant_id, document_reference, content, embedding) " +
                        "VALUES ($1, $2, $3, $4::vector)"
                    ).execute(Tuple.of(
                        tenantContext.getTenantId(),
                        docRef,
                        content,
                        vectorString
                    )).replaceWithVoid();
                })
            );

            ingestions.add(insertUni);
        }

        if (ingestions.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        return Uni.join().all(ingestions).andFailFast().replaceWithVoid();
    }

    /**
     * Splits a given text into segments of a specified character size with overlap.
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = end - overlap;
            if (start >= end) {
                start = end - 1; // Prevent infinite loop
            }
        }
        return chunks;
    }
}
