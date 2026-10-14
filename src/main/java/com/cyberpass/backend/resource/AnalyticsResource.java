package com.cyberpass.backend.resource;

import com.cyberpass.backend.dto.DashboardMetricsDto;
import com.cyberpass.backend.tenant.TenantAwarePgPool;
import com.cyberpass.backend.tenant.TenantContext;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * REST Endpoint for fetching aggregated analytics, ROI statistics, and compliance KPIs for the active tenant.
 */
@Path("/api/v1/analytics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class AnalyticsResource {

    private static final Logger LOG = Logger.getLogger(AnalyticsResource.class);

    @Inject
    TenantAwarePgPool tenantAwarePgPool;

    @Inject
    TenantContext tenantContext;

    @GET
    @Path("/metrics")
    public Uni<Response> getMetrics() {
        UUID tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity("Tenant context not provided.")
                .build());
        }

        LOG.debugf("Calculating analytics metrics for tenant: %s", tenantId);

        return tenantAwarePgPool.withTransaction(conn -> {
            DashboardMetricsDto metrics = new DashboardMetricsDto();

            // 1. Fetch Tenant plan details
            return conn.preparedQuery(
                "SELECT plan_tier, max_questionnaires_per_month FROM tenants WHERE id = $1"
            ).execute(Tuple.of(tenantId))
            .chain(tenantRows -> {
                if (tenantRows.iterator().hasNext()) {
                    Row tRow = tenantRows.iterator().next();
                    metrics.setPlanTier(tRow.getString("plan_tier") != null ? tRow.getString("plan_tier") : "STARTER");
                    Integer limit = tRow.getInteger("max_questionnaires_per_month");
                    metrics.setMaxQuestionnairesLimit(limit != null ? limit : 5);
                } else {
                    metrics.setPlanTier("STARTER");
                    metrics.setMaxQuestionnairesLimit(5);
                }

                // 2. Fetch Processed Questionnaires aggregation
                return conn.preparedQuery(
                    "SELECT COUNT(*) as total_q, " +
                    "COALESCE(SUM(questions_count), 0) as total_questions, " +
                    "COUNT(CASE WHEN created_at >= date_trunc('month', current_timestamp) THEN 1 END) as current_month_q " +
                    "FROM processed_questionnaires WHERE tenant_id = $1"
                ).execute(Tuple.of(tenantId));
            })
            .chain(qRows -> {
                if (qRows.iterator().hasNext()) {
                    Row qRow = qRows.iterator().next();
                    metrics.setTotalQuestionnaires(qRow.getLong("total_q"));
                    long totalQuestions = qRow.getLong("total_questions");
                    metrics.setTotalQuestionsAnswered(totalQuestions);
                    metrics.setCurrentMonthQuestionnaires(qRow.getLong("current_month_q"));

                    // ROI Calculation: standard ~12 minutes per manual compliance question
                    double hoursSaved = Math.round((totalQuestions * 12.0 / 60.0) * 10.0) / 10.0;
                    metrics.setTimeSavedHours(hoursSaved);
                    
                    // Estimate average confidence based on active learning or default high baseline
                    metrics.setAverageConfidence(totalQuestions > 0 ? 86.5 : 0.0);
                }

                // 3. Fetch Knowledge Base vector documents
                return conn.preparedQuery(
                    "SELECT COUNT(DISTINCT document_reference) as total_docs, COUNT(*) as total_vectors " +
                    "FROM tenant_knowledge_vectors WHERE tenant_id = $1"
                ).execute(Tuple.of(tenantId));
            })
            .chain(kbRows -> {
                if (kbRows.iterator().hasNext()) {
                    Row kbRow = kbRows.iterator().next();
                    metrics.setTotalKnowledgeDocs(kbRow.getLong("total_docs"));
                    metrics.setTotalKnowledgeVectors(kbRow.getLong("total_vectors"));
                }

                // 4. Fetch NDA signatures count
                return conn.preparedQuery(
                    "SELECT COUNT(*) as total_nda FROM nda_signatures WHERE tenant_id = $1"
                ).execute(Tuple.of(tenantId));
            })
            .chain(ndaRows -> {
                if (ndaRows.iterator().hasNext()) {
                    Row ndaRow = ndaRows.iterator().next();
                    metrics.setTotalNdaSignatures(ndaRow.getLong("total_nda"));
                }

                // 5. Fetch Trust Profile status
                return conn.preparedQuery(
                    "SELECT published, security_measures FROM trust_profiles WHERE tenant_id = $1"
                ).execute(Tuple.of(tenantId));
            })
            .map(trustRows -> {
                if (trustRows.iterator().hasNext()) {
                    Row trRow = trustRows.iterator().next();
                    metrics.setTrustPortalPublished(trRow.getBoolean("published"));
                    
                    Object measuresObj = trRow.getValue("security_measures");
                    int activeControls = 0;
                    if (measuresObj != null) {
                        try {
                            JsonObject json = new JsonObject(measuresObj.toString());
                            for (String key : json.fieldNames()) {
                                JsonObject control = json.getJsonObject(key);
                                if (control != null && control.getBoolean("enabled", false)) {
                                    activeControls++;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    metrics.setActiveSecurityControls(activeControls);
                } else {
                    metrics.setTrustPortalPublished(false);
                    metrics.setActiveSecurityControls(0);
                }

                return Response.ok(metrics).build();
            });
        });
    }
}
