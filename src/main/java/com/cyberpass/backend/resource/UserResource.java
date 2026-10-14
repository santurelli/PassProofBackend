package com.cyberpass.backend.resource;

import com.cyberpass.backend.dto.TenantDto;
import com.cyberpass.backend.service.AuthService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;

/**
 * REST controller for user-specific queries.
 */
@Path("/api/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject
    AuthService authService;

    @Inject
    JsonWebToken jwt;

    /**
     * Retrieves the list of all tenants (companies) the authenticated user belongs to.
     */
    @GET
    @Path("/me/tenants")
    public Uni<Response> getMyTenants() {
        // Resolve authenticated user ID from validated JWT subject claim
        String userId = jwt.getSubject();
        if (userId == null || userId.trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "Utente non autenticato o token non valido."))
                .build());
        }

        return authService.getUserTenants(userId)
            .map(tenantsList -> Response.ok(tenantsList).build());
    }
}
