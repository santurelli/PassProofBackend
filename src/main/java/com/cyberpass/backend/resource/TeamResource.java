package com.cyberpass.backend.resource;

import com.cyberpass.backend.dto.InviteRequest;
import com.cyberpass.backend.service.AuthService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;

/**
 * REST controller for team management operations (e.g., invites, status toggle, removal).
 */
@Path("/api/v1/team")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class TeamResource {

    @Inject
    AuthService authService;

    @Inject
    JsonWebToken jwt;

    private String resolveTenantId(HttpHeaders headers) {
        String tenantId = null;
        try {
            tenantId = jwt.getClaim("tenant_id");
        } catch (Exception ignored) {}
        
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = headers.getHeaderString("X-Tenant-ID");
        }
        return tenantId;
    }

    /**
     * Lists all team members and pending invitations for the current tenant.
     */
    @GET
    @Path("/members")
    public Uni<Response> getMembers(@Context HttpHeaders headers) {
        String tenantId = resolveTenantId(headers);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Tenant context mancante."))
                .build());
        }

        return authService.getTeamMembers(tenantId)
            .map(members -> Response.ok(members).build());
    }

    /**
     * Sends an invitation to a collaborator.
     */
    @POST
    @Path("/invite")
    public Uni<Response> inviteCollaborator(InviteRequest request, @Context HttpHeaders headers) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty() ||
            request.getRole() == null || request.getRole().trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Email e ruolo sono obbligatori."))
                .build());
        }

        String tenantId = resolveTenantId(headers);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Tenant context mancante."))
                .build());
        }

        return authService.inviteCollaborator(request, tenantId)
            .map(result -> Response.status(Response.Status.CREATED).entity(result).build())
            .onFailure().recoverWithItem(err ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", err.getMessage() != null ? err.getMessage() : "Invito fallito."))
                    .build()
            );
    }

    /**
     * Toggles collaborator active/disabled status.
     */
    @PUT
    @Path("/members/{userId}/status")
    public Uni<Response> toggleStatus(
        @PathParam("userId") String userId,
        @QueryParam("status") String status,
        @Context HttpHeaders headers
    ) {
        String tenantId = resolveTenantId(headers);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Tenant context mancante."))
                .build());
        }

        return authService.toggleMemberStatus(tenantId, userId, status)
            .map(v -> Response.ok(Map.of("status", "success", "message", "Stato collaboratore aggiornato.")).build())
            .onFailure().recoverWithItem(err ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", err.getMessage() != null ? err.getMessage() : "Modifica stato fallita."))
                    .build()
            );
    }

    /**
     * Removes a collaborator from the current tenant.
     */
    @DELETE
    @Path("/members/{userId}")
    public Uni<Response> removeMember(
        @PathParam("userId") String userId,
        @Context HttpHeaders headers
    ) {
        String tenantId = resolveTenantId(headers);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Tenant context mancante."))
                .build());
        }

        return authService.removeMember(tenantId, userId)
            .map(v -> Response.ok(Map.of("status", "success", "message", "Collaboratore rimosso dal team.")).build())
            .onFailure().recoverWithItem(err ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", err.getMessage() != null ? err.getMessage() : "Rimozione collaboratore fallita."))
                    .build()
            );
    }

    /**
     * Revokes a pending invitation.
     */
    @DELETE
    @Path("/invites/{inviteId}")
    public Uni<Response> revokeInvite(
        @PathParam("inviteId") String inviteId,
        @Context HttpHeaders headers
    ) {
        String tenantId = resolveTenantId(headers);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Tenant context mancante."))
                .build());
        }

        return authService.revokeInvitation(tenantId, inviteId)
            .map(v -> Response.ok(Map.of("status", "success", "message", "Invito revocato con successo.")).build())
            .onFailure().recoverWithItem(err ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", err.getMessage() != null ? err.getMessage() : "Revoca invito fallita."))
                    .build()
            );
    }
}
