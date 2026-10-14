package com.cyberpass.backend.resource;

import com.cyberpass.backend.dto.NdaSignatureDto;
import com.cyberpass.backend.dto.TrustProfileDto;
import com.cyberpass.backend.tenant.TenantAwarePgPool;
import com.cyberpass.backend.tenant.TenantContext;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST Endpoint for Security Passport (Trust Portal) configurations and public views.
 * Supports public unauthenticated preview and digital NDA signatures.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TrustResource {

    @Inject
    TenantAwarePgPool tenantAwarePgPool;

    @Inject
    TenantContext tenantContext;

    /* =========================================================================
     * Authenticated Administrative Endpoints
     * ========================================================================= */

    @GET
    @Path("/trust")
    public Uni<TrustProfileDto> getProfile() {
        java.util.UUID tenantId = tenantContext.getTenantId();
        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery("SELECT id, tenant_id, public_name, company_description, security_measures, published FROM trust_profiles WHERE tenant_id = $1")
                .execute(Tuple.of(tenantId))
                .map(rows -> {
                    if (!rows.iterator().hasNext()) {
                        // Return default initial profile if it doesn't exist
                        TrustProfileDto defaultProfile = new TrustProfileDto();
                        defaultProfile.setPublicName("Security Trust Portal");
                        defaultProfile.setCompanyDescription("Descrizione delle misure di sicurezza e conformità della nostra azienda.");
                        defaultProfile.setSecurityMeasures("{}");
                        defaultProfile.setPublished(false);
                        return defaultProfile;
                    }
                    io.vertx.mutiny.sqlclient.Row row = rows.iterator().next();
                    TrustProfileDto profile = new TrustProfileDto();
                    profile.setId(row.getUUID("id"));
                    profile.setTenantId(row.getUUID("tenant_id"));
                    profile.setPublicName(row.getString("public_name"));
                    profile.setCompanyDescription(row.getString("company_description"));
                    profile.setSecurityMeasures(row.getValue("security_measures").toString());
                    profile.setPublished(row.getBoolean("published"));
                    return profile;
                })
        );
    }

    @PUT
    @Path("/trust")
    public Uni<Response> updateProfile(TrustProfileDto dto) {
        java.util.UUID tenantId = tenantContext.getTenantId();
        
        String publicName = dto.getPublicName() != null ? dto.getPublicName().trim() : "Security Portal";
        String description = dto.getCompanyDescription() != null ? dto.getCompanyDescription().trim() : "";
        String measures = dto.getSecurityMeasures() != null ? dto.getSecurityMeasures() : "{}";
        
        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery(
                "INSERT INTO trust_profiles (tenant_id, public_name, company_description, security_measures, published) " +
                "VALUES ($1, $2, $3, $4::jsonb, $5) " +
                "ON CONFLICT (tenant_id) DO UPDATE " +
                "SET public_name = EXCLUDED.public_name, " +
                "    company_description = EXCLUDED.company_description, " +
                "    security_measures = EXCLUDED.security_measures, " +
                "    published = EXCLUDED.published, " +
                "    updated_at = CURRENT_TIMESTAMP"
            ).execute(Tuple.of(
                tenantId,
                publicName,
                description,
                measures,
                dto.isPublished()
            )).map(rows -> Response.ok(java.util.Map.of("status", "success")).build())
        );
    }

    @GET
    @Path("/trust/signatures")
    public Uni<List<NdaSignatureDto>> getSignatures() {
        java.util.UUID tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            return Uni.createFrom().failure(new BadRequestException("Tenant context non valido o mancante."));
        }

        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery(
                "SELECT id, tenant_id, full_name, email, company_name, signed_at " +
                "FROM nda_signatures " +
                "WHERE tenant_id = $1 " +
                "ORDER BY signed_at DESC"
            ).execute(Tuple.of(tenantId))
             .map(rows -> {
                 List<NdaSignatureDto> list = new java.util.ArrayList<>();
                 for (io.vertx.mutiny.sqlclient.Row row : rows) {
                     NdaSignatureDto sig = new NdaSignatureDto();
                     sig.setId(row.getUUID("id"));
                     sig.setTenantId(row.getUUID("tenant_id"));
                     sig.setFullName(row.getString("full_name"));
                     sig.setEmail(row.getString("email"));
                     sig.setCompanyName(row.getString("company_name"));
                     sig.setSignedAt(row.getOffsetDateTime("signed_at").toString());
                     list.add(sig);
                 }
                 return list;
             })
        );
    }

    /* =========================================================================
     * Public Gated Endpoints (No JWT Authentication needed)
     * ========================================================================= */

    @GET
    @Path("/public/trust/{tenantId}")
    public Uni<TrustProfileDto> getPublicProfile(@PathParam("tenantId") String tenantIdStr) {
        java.util.UUID tenantId;
        try {
            tenantId = java.util.UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid Tenant ID format.");
        }

        // Set the request-scoped tenantContext dynamically for this public query
        tenantContext.setTenantId(tenantId);

        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery("SELECT id, tenant_id, public_name, company_description, published FROM trust_profiles WHERE tenant_id = $1")
                .execute(Tuple.of(tenantId))
                .map(rows -> {
                    if (!rows.iterator().hasNext()) {
                        throw new NotFoundException("This company has not configured a Trust Portal.");
                    }
                    io.vertx.mutiny.sqlclient.Row row = rows.iterator().next();
                    if (!row.getBoolean("published")) {
                        throw new ForbiddenException("This Trust Portal is currently private.");
                    }
                    TrustProfileDto profile = new TrustProfileDto();
                    profile.setId(row.getUUID("id"));
                    profile.setTenantId(row.getUUID("tenant_id"));
                    profile.setPublicName(row.getString("public_name"));
                    profile.setCompanyDescription(row.getString("company_description"));
                    profile.setPublished(row.getBoolean("published"));
                    return profile;
                })
        );
    }

    @POST
    @Path("/public/trust/{tenantId}/sign-nda")
    public Uni<TrustProfileDto> signNda(@PathParam("tenantId") String tenantIdStr, NdaSignatureDto signatureDto) {
        java.util.UUID tenantId;
        try {
            tenantId = java.util.UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid Tenant ID format.");
        }

        if (signatureDto.getFullName() == null || signatureDto.getFullName().trim().isEmpty()) {
            throw new BadRequestException("Full name is required.");
        }
        if (signatureDto.getEmail() == null || signatureDto.getEmail().trim().isEmpty()) {
            throw new BadRequestException("Email is required.");
        }
        if (signatureDto.getCompanyName() == null || signatureDto.getCompanyName().trim().isEmpty()) {
            throw new BadRequestException("Company name is required.");
        }

        // Set request-scoped tenantContext dynamically
        tenantContext.setTenantId(tenantId);
        String emailClean = signatureDto.getEmail().trim().toLowerCase();

        // Check if signature already exists for this tenant & email
        return tenantAwarePgPool.withTransaction(conn ->
            conn.preparedQuery("SELECT id FROM nda_signatures WHERE tenant_id = $1 AND LOWER(email) = $2 LIMIT 1")
                .execute(Tuple.of(tenantId, emailClean))
                .chain(rows -> {
                    if (rows.iterator().hasNext()) {
                        // Already signed. Skip insert!
                        return Uni.createFrom().item(rows);
                    } else {
                        // New signature. Insert it!
                        return conn.preparedQuery(
                            "INSERT INTO nda_signatures (tenant_id, full_name, email, company_name) " +
                            "VALUES ($1, $2, $3, $4)"
                        ).execute(Tuple.of(
                            tenantId,
                            signatureDto.getFullName().trim(),
                            signatureDto.getEmail().trim(),
                            signatureDto.getCompanyName().trim()
                        ));
                    }
                })
                .chain(() ->
                    conn.preparedQuery("SELECT id, tenant_id, public_name, company_description, security_measures, published FROM trust_profiles WHERE tenant_id = $1")
                        .execute(Tuple.of(tenantId))
                        .map(rows -> {
                            if (!rows.iterator().hasNext()) {
                                throw new NotFoundException("Trust profile not found.");
                            }
                            io.vertx.mutiny.sqlclient.Row row = rows.iterator().next();
                            if (!row.getBoolean("published")) {
                                throw new ForbiddenException("This Trust Portal is private.");
                            }
                            TrustProfileDto profile = new TrustProfileDto();
                            profile.setId(row.getUUID("id"));
                            profile.setTenantId(row.getUUID("tenant_id"));
                            profile.setPublicName(row.getString("public_name"));
                            profile.setCompanyDescription(row.getString("company_description"));
                            profile.setSecurityMeasures(row.getValue("security_measures").toString());
                            profile.setPublished(row.getBoolean("published"));
                            return profile;
                        })
                )
        );
    }
}
