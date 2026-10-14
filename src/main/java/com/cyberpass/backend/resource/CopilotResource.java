package com.cyberpass.backend.resource;

import com.cyberpass.backend.dto.CopilotRequest;
import com.cyberpass.backend.dto.CopilotResponse;
import com.cyberpass.backend.dto.QuestionRequest;
import com.cyberpass.backend.service.QuestionnaireProcessingService;
import com.cyberpass.backend.tenant.TenantContext;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

/**
 * REST Endpoint for the AI Security Copilot ("Chiedi alla Knowledge Base").
 * Enables conversational natural language querying against tenant-isolated security vectors.
 */
@Path("/api/v1/copilot")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class CopilotResource {

    private static final Logger LOG = Logger.getLogger(CopilotResource.class);

    @Inject
    QuestionnaireProcessingService questionnaireProcessingService;

    @Inject
    TenantContext tenantContext;

    /**
     * Receives a natural language question, executes vector similarity search in the tenant knowledge base,
     * injects retrieved context into LangChain4j, and returns the certified answer with cited sources.
     */
    @POST
    @Path("/ask")
    public Uni<CopilotResponse> askCopilot(CopilotRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Uni.createFrom().failure(new BadRequestException("La domanda non può essere vuota."));
        }

        if (tenantContext.getTenantId() == null) {
            return Uni.createFrom().failure(new BadRequestException("Contesto aziendale (Tenant) mancante o non valido."));
        }

        LOG.infof("AI Copilot request received for tenant %s: '%s'", tenantContext.getTenantId(), request.getQuestion());

        QuestionRequest qr = new QuestionRequest(request.getQuestion().trim(), request.getContextHint());

        return questionnaireProcessingService.processQuestion(qr)
            .map(questionResponse -> new CopilotResponse(
                questionResponse.getQuestion(),
                questionResponse.getSuggestedAnswer(),
                questionResponse.getSources(),
                questionResponse.getConfidence()
            ));
    }
}
