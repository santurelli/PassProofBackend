package com.cyberpass.backend.service;

import com.cyberpass.backend.dto.QuestionRequest;
import com.cyberpass.backend.dto.QuestionResponse;
import com.cyberpass.backend.dto.QuestionResponse.SourceInfo;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrator service to process questionnaire queries.
 * Coordinates similarity searches, AI Service context injections, and confidence score evaluations.
 */
@ApplicationScoped
public class QuestionnaireProcessingService {

    private static final Logger LOG = Logger.getLogger(QuestionnaireProcessingService.class);
    private static final int MAX_RESULTS = 3;

    @Inject
    KnowledgeBaseService knowledgeBaseService;

    @Inject
    QuestionAnsweringAiService aiService;

    /**
     * Processes a single question through the RAG pipeline.
     *
     * @param request The question payload request.
     * @return A Uni containing the QuestionResponse with suggested answer and sources.
     */
    public Uni<QuestionResponse> processQuestion(QuestionRequest request) {
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Question text cannot be empty."));
        }

        LOG.infof("Processing RAG query: '%s'", request.getQuestion());

        // 1. Perform similarity search in the tenant knowledge base
        return knowledgeBaseService.similaritySearch(request.getQuestion(), MAX_RESULTS)
            .chain(sources -> {
                if (sources.isEmpty()) {
                    LOG.debug("No context matches found in the knowledge base. Skipping LLM execution.");
                    return Uni.createFrom().item(new QuestionResponse(
                        request.getQuestion(),
                        "Non ho abbastanza informazioni per rispondere.",
                        new ArrayList<>(),
                        0.0
                    ));
                }

                // 2. Format matches into a single context string
                String context = sources.stream()
                    .map(SourceInfo::getContentExcerpt)
                    .collect(Collectors.joining("\n---\n"));

                // 3. Invoke LLM generation on the worker pool thread since LLM API calls are blocking
                return Uni.createFrom().item(() -> {
                    LOG.debug("Invoking LangChain4j AI Service for answer generation...");
                    return aiService.answer(request.getQuestion(), context);
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .map(answer -> {
                    // 4. Compute confidence score
                    double confidence = evaluateConfidence(answer, sources);
                    LOG.infof("RAG generation completed. Confidence score: %.2f", confidence);

                    return new QuestionResponse(
                        request.getQuestion(),
                        answer.trim(),
                        sources,
                        confidence
                    );
                });
            });
    }

    /**
     * Evaluates the response confidence based on pgvector similarity and LLM output.
     */
    private double evaluateConfidence(String answer, List<SourceInfo> sources) {
        if (answer == null || answer.trim().isEmpty()) {
            return 0.0;
        }

        String normalizedAnswer = answer.toLowerCase().trim();
        // If LLM states it doesn't have enough information, confidence is 0.0
        if (normalizedAnswer.contains("non ho abbastanza informazioni") 
            || normalizedAnswer.startsWith("non ho abbastanza informazioni per rispondere")
            || normalizedAnswer.equals("non ho abbastanza informazioni per rispondere.")) {
            return 0.0;
        }

        // Otherwise, base confidence on the maximum cosine similarity score retrieved
        double maxSimilarity = sources.stream()
            .mapToDouble(SourceInfo::getSimilarityScore)
            .max()
            .orElse(0.0);

        // Normalize / clip between 0.0 and 1.0 just in case
        return Math.max(0.0, Math.min(1.0, maxSimilarity));
    }
}
