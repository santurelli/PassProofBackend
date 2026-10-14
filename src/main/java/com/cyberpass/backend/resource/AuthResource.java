package com.cyberpass.backend.resource;

import com.cyberpass.backend.dto.*;
import com.cyberpass.backend.service.AuthService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * REST controller for authentication, registration, activation, and context switches.
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    AuthService authService;

    @Inject
    JsonWebToken jwt;

    /**
     * Registers a new tenant and owner user.
     */
    @POST
    @Path("/register")
    @PermitAll
    public Uni<Response> register(TenantRegisterRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().trim().isEmpty() ||
            request.getCompanyName() == null || request.getCompanyName().trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Email, password e ragione sociale sono obbligatori."))
                .build());
        }

        return authService.registerTenant(request)
            .map(result -> Response.status(Response.Status.CREATED).entity(result).build())
            .onFailure().recoverWithItem(err -> {
                LOG.errorf(err, "Registration failed for email: %s", request.getEmail());
                String msg = err.getMessage();
                if (msg != null && (msg.contains("duplicate key") || msg.contains("users_email_key") || msg.contains("violates unique constraint"))) {
                    msg = "L'indirizzo email inserito è già registrato nel sistema. Prova ad accedere.";
                }
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", msg != null ? msg : "Errore durante la registrazione."))
                    .build();
            });
    }

    /**
     * Activates a pending account based on activation token.
     */
    @GET
    @Path("/activate")
    @PermitAll
    public Uni<Response> activate(@QueryParam("token") String token) {
        if (token == null || token.trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Parametro token mancante."))
                .build());
        }

        return authService.activateAccount(token)
            .map(v -> Response.ok(Map.of("status", "success", "message", "Account attivato con successo.")).build())
            .onFailure().recoverWithItem(err -> 
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", err.getMessage() != null ? err.getMessage() : "Attivazione fallita."))
                    .build()
            );
    }

    /**
     * Accepts a collaborator invitation.
     */
    @POST
    @Path("/accept-invite")
    @PermitAll
    public Uni<Response> acceptInvite(AcceptInviteRequest request) {
        return authService.acceptInvitation(request)
            .map(v -> Response.ok(Map.of("status", "success", "message", "Invito accettato e profilo registrato.")).build())
            .onFailure().recoverWithItem(err -> 
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", err.getMessage() != null ? err.getMessage() : "Accettazione invito fallita."))
                    .build()
            );
    }

    /**
     * Performs multi-company context switch, returning a new JWT token scoped to the target tenant.
     */
    @POST
    @Path("/switch-tenant")
    @Authenticated
    public Uni<Response> switchTenant(SwitchTenantRequest request) {
        if (request.getTenantId() == null || request.getTenantId().trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Missing tenant_id in request body."))
                .build());
        }

        // Retrieve user ID from validated JWT subject claim
        String userId = jwt.getSubject();
        if (userId == null || userId.trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "Utente non autenticato."))
                .build());
        }

        return authService.switchTenantContext(request.getTenantId(), userId)
            .map(result -> Response.ok(result).build())
            .onFailure().recoverWithItem(err -> 
                Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", err.getMessage()))
                    .build()
            );
    }

    /**
     * Step 1: Initiates authentication by validating credentials.
     */
    @POST
    @Path("/login")
    @PermitAll
    public Uni<Response> login(LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Email and password must be supplied in request body."))
                .build());
        }

        return authService.initiateLogin(request)
            .map(result -> Response.ok(result).build())
            .onFailure().recoverWithItem(err -> 
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", err.getMessage()))
                    .build()
            );
    }

    /**
     * Step 2: Validates the 6-digit verification code to complete MFA.
     */
    @POST
    @Path("/mfa/verify")
    @PermitAll
    public Uni<Response> verifyMfa(MfaVerifyRequest request) {
        if (request.getTempToken() == null || request.getTempToken().trim().isEmpty() ||
            request.getCode() <= 0) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Missing temp_token or invalid verification code."))
                .build());
        }

        return authService.verifyMfa(request)
            .map(result -> Response.ok(result).build())
            .onFailure().recoverWithItem(err -> 
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", err.getMessage()))
                    .build()
            );
    }
}
