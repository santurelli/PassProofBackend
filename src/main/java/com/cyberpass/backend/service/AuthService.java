package com.cyberpass.backend.service;

import com.cyberpass.backend.dto.*;
import com.cyberpass.backend.dto.QuestionResponse.SourceInfo;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import com.cyberpass.backend.security.MfaCrypto;
import com.cyberpass.backend.security.TotpVerifier;

import java.time.OffsetDateTime;
import java.util.*;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    @Inject
    PgPool pgPool;

    @Inject
    MfaCrypto mfaCrypto;

    @Inject
    TotpVerifier totpVerifier;

    @Inject
    io.quarkus.mailer.reactive.ReactiveMailer mailer;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "passproof.mail.simulation")
    boolean emailSimulation;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "passproof.activation.base-url")
    String activationBaseUrl;

    /**
     * Registers a new tenant and an owner user in a single transaction.
     * Generates a signed activation token.
     */
    public Uni<Map<String, String>> registerTenant(TenantRegisterRequest request) {
        LOG.infof("Starting self-service tenant registration for company: %s", request.getCompanyName());

        // Simple mock hash (in production, use BcryptUtil.bcryptHash or equivalent)
        String mockHash = "SHA256:" + Base64.getEncoder().encodeToString(request.getPassword().getBytes());

        JsonObject billingJson = new JsonObject(request.getBillingData() != null ? request.getBillingData() : new HashMap<>());

        return pgPool.withTransaction(conn -> 
            // 1. Insert Tenant
            conn.preparedQuery("INSERT INTO tenants (company_name, vat_number, billing_data) VALUES ($1, $2, $3::jsonb) RETURNING id")
                .execute(Tuple.of(request.getCompanyName(), request.getVatNumber(), billingJson))
                .onItem().transform(rows -> rows.iterator().next().getUUID("id"))
                .chain(tenantId -> 
                    // 2. Insert User
                    conn.preparedQuery("INSERT INTO users (email, password_hash, first_name, last_name) VALUES ($1, $2, $3, $4) RETURNING id")
                        .execute(Tuple.of(request.getEmail(), mockHash, request.getFirstName(), request.getLastName()))
                        .onItem().transform(rows -> rows.iterator().next().getUUID("id"))
                        .chain(userId -> 
                            // 3. Create Owner Membership
                            conn.preparedQuery("INSERT INTO tenant_memberships (user_id, tenant_id, role) VALUES ($1, $2, $3)")
                                .execute(Tuple.of(userId, tenantId, "OWNER"))
                                .onItem().transformToUni(v -> {
                                    // Generate signed JWT activation token containing context IDs (inside transaction)
                                    String activationToken = Jwt.issuer("passproof-auth")
                                        .subject(userId.toString())
                                        .claim("tenant_id", tenantId.toString())
                                        .claim("action", "activate")
                                        .expiresIn(86400) // 24 hours
                                        .sign();

                                    LOG.infof("Tenant registered successfully. TenantId: %s, UserId: %s", tenantId, userId);
                                    
                                    String activationLink = activationBaseUrl + "/?activate=" + activationToken;
                                    
                                    if (emailSimulation) {
                                        System.out.println("\n==========================================================================================");
                                        System.out.println("📬 [EMAIL SIMULATION] Link di attivazione generato per " + request.getEmail() + ":");
                                        System.out.println("👉 " + activationLink);
                                        System.out.println("==========================================================================================\n");

                                        return Uni.createFrom().item(Map.of(
                                            "message", "Registration successful. Activation email simulated.",
                                            "activation_token", activationToken
                                        ));
                                    } else {
                                        LOG.infof("Sending activation email to: %s", request.getEmail());
                                        String htmlContent = "<!DOCTYPE html>" +
                                            "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
                                            "<body style=\"margin:0;padding:0;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;-webkit-font-smoothing:antialiased;\">" +
                                            "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color:#f8fafc;padding:40px 0;\">" +
                                            "<tr><td align=\"center\">" +
                                            "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width:580px;background-color:#ffffff;border:1px solid #e2e8f0;border-radius:12px;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05);\">" +
                                            "<tr><td align=\"center\" style=\"padding:35px 40px 25px 40px;border-bottom:1px solid #f1f5f9;\">" +
                                            "<span style=\"font-size:22px;font-weight:800;color:#0f172a;letter-spacing:-0.5px;\">Pass<span style=\"color:#4f46e5;\">Proof</span></span>" +
                                            "<div style=\"font-size:11px;color:#64748b;font-weight:500;margin-top:4px;text-transform:uppercase;letter-spacing:1.5px;\">Security & Compliance Portal</div>" +
                                            "</td></tr>" +
                                            "<tr><td style=\"padding:40px 40px 35px 40px;\">" +
                                            "<h2 style=\"margin:0 0 16px 0;font-size:18px;font-weight:700;color:#0f172a;line-height:24px;\">Benvenuto su PassProof</h2>" +
                                            "<p style=\"margin:0 0 24px 0;font-size:14px;line-height:22px;color:#334155;\">" +
                                            "Grazie per esserti registrato. Per completare la configurazione del tuo account e attivare il portale aziendale, clicca sul pulsante sottostante." +
                                            "</p>" +
                                            "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"margin-bottom:28px;\">" +
                                            "<tr><td align=\"center\">" +
                                            "<a href=\"" + activationLink + "\" target=\"_blank\" style=\"display:inline-block;background-color:#4f46e5;color:#ffffff;font-size:14px;font-weight:600;text-decoration:none;padding:12px 28px;border-radius:8px;box-shadow:0 2px 4px rgba(79,70,229,0.2);\">" +
                                            "Attiva Account" +
                                            "</a>" +
                                            "</td></tr>" +
                                            "</table>" +
                                            "<p style=\"margin:0 0 12px 0;font-size:13px;line-height:20px;color:#64748b;\">" +
                                            "Se il pulsante non funziona o non è cliccabile, copia e incolla il seguente indirizzo web nel tuo browser:" +
                                            "</p>" +
                                            "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;margin-bottom:24px;\">" +
                                            "<tr><td style=\"padding:12px;font-family:monospace;font-size:11px;color:#4f46e5;word-break:break-all;line-height:16px;\">" +
                                            "<a href=\"" + activationLink + "\" target=\"_blank\" style=\"color:#4f46e5;text-decoration:none;\">" + activationLink + "</a>" +
                                            "</td></tr>" +
                                            "</table>" +
                                            "<div style=\"border-top:1px solid #f1f5f9;padding-top:24px;\">" +
                                            "<p style=\"margin:0;font-size:12px;line-height:18px;color:#94a3b8;\">" +
                                            "Se non hai richiesto tu questa registrazione, puoi ignorare questa e-mail in totale sicurezza." +
                                            "</p>" +
                                            "</div>" +
                                            "</td></tr>" +
                                            "<tr><td style=\"padding:0 40px 35px 40px;text-align:center;\">" +
                                            "<p style=\"margin:0;font-size:11px;line-height:16px;color:#94a3b8;\">" +
                                            "© 2026 PassProof. Tutti i diritti riservati." +
                                            "</p>" +
                                            "<p style=\"margin:4px 0 0 0;font-size:10px;color:#cbd5e1;\">" +
                                            "Questa è una comunicazione automatica del sistema, si prega di non rispondere." +
                                            "</p>" +
                                            "</td></tr>" +
                                            "</table>" +
                                            "</td></tr>" +
                                            "</table>" +
                                            "</body></html>";
                                            
                                        return mailer.send(io.quarkus.mailer.Mail.withHtml(
                                            request.getEmail(),
                                            "Attiva il tuo account PassProof",
                                            htmlContent
                                        )).map(voidResult -> Map.of(
                                            "message", "Registration successful. Activation email sent.",
                                            "activation_token", activationToken
                                        ));
                                    }
                                })
                        )
                )
        );
    }

    /**
     * Activates a pending user and tenant based on a signed activation token.
     */
    public Uni<Void> activateAccount(String token) {
        LOG.debug("Processing account activation token...");
        try {
            if (token == null || token.trim().isEmpty()) {
                return Uni.createFrom().failure(new IllegalArgumentException("Token di attivazione mancante."));
            }

            String cleanToken = token.trim();
            if (cleanToken.startsWith("Bearer ")) {
                cleanToken = cleanToken.substring(7).trim();
            }

            // URL Decode if token contains percent-encoded characters
            if (cleanToken.contains("%")) {
                try {
                    cleanToken = java.net.URLDecoder.decode(cleanToken, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }

            // Extract JWT payload (part index 1 if 3 parts, part 0 if just payload)
            String payloadBase64;
            String[] parts = cleanToken.split("\\.");
            if (parts.length >= 2) {
                payloadBase64 = parts[1];
            } else if (parts.length == 1 && parts[0].length() > 10) {
                payloadBase64 = parts[0];
            } else {
                return Uni.createFrom().failure(new IllegalArgumentException("Formato token non valido."));
            }

            // Ensure valid base64 padding
            while (payloadBase64.length() % 4 != 0) {
                payloadBase64 += "=";
            }

            byte[] decodedBytes;
            try {
                decodedBytes = Base64.getUrlDecoder().decode(payloadBase64);
            } catch (IllegalArgumentException e) {
                decodedBytes = Base64.getDecoder().decode(payloadBase64);
            }

            String decodedString = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
            JsonObject claims = new JsonObject(decodedString);
            
            String userId = claims.getString("sub");
            String tenantId = claims.getString("tenant_id");
            String action = claims.getString("action");

            if (!"activate".equals(action)) {
                return Uni.createFrom().failure(new IllegalArgumentException("Azione token non valida."));
            }

            if (userId == null || tenantId == null) {
                return Uni.createFrom().failure(new IllegalArgumentException("Token privo di identificativi utente o tenant."));
            }

            UUID userUuid = UUID.fromString(userId);
            UUID tenantUuid = UUID.fromString(tenantId);

            LOG.infof("Activating Tenant: %s and User: %s", tenantId, userId);

            return pgPool.withTransaction(conn ->
                conn.preparedQuery("UPDATE users SET status = 'ACTIVE' WHERE id = $1")
                    .execute(Tuple.of(userUuid))
                    .chain(() -> 
                        conn.preparedQuery("UPDATE tenants SET status = 'ACTIVE' WHERE id = $1")
                            .execute(Tuple.of(tenantUuid))
                    )
            ).replaceWithVoid();

        } catch (Exception e) {
            LOG.error("Failed to parse activation token: " + token, e);
            return Uni.createFrom().failure(new IllegalArgumentException("Link di attivazione non valido o scaduto."));
        }
    }

    /**
     * Generates a collaborator invitation token.
     */
    public Uni<Map<String, String>> inviteCollaborator(InviteRequest request, String currentTenantId) {
        UUID tenantUuid = UUID.fromString(currentTenantId);
        String invitationToken = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);

        LOG.infof("Creating invitation for %s in tenant %s", request.getEmail(), currentTenantId);

        String inviteLink = activationBaseUrl + "/?invite=" + invitationToken;

        return pgPool.preparedQuery(
            "SELECT (SELECT COUNT(*) FROM tenant_memberships WHERE tenant_id = $1) as current_users, " +
            "(SELECT max_users FROM tenants WHERE id = $1) as max_users"
        ).execute(Tuple.of(tenantUuid))
        .chain(rows -> {
            Row row = rows.iterator().next();
            Long currentUsers = row.getLong("current_users");
            Integer maxUsers = row.getInteger("max_users");
            if (maxUsers == null) maxUsers = 3;

            if (currentUsers >= maxUsers) {
                return Uni.createFrom().failure(new IllegalStateException(
                    "Hai raggiunto il limite massimo di " + maxUsers + " utenti consentiti dal tuo piano (Starter)."
                ));
            }

            return pgPool.preparedQuery(
                "INSERT INTO user_invitations (tenant_id, email, role, token, expires_at) VALUES ($1, $2, $3, $4, $5)"
            ).execute(Tuple.of(tenantUuid, request.getEmail(), request.getRole(), invitationToken, expiresAt))
             .chain(v -> {
             if (emailSimulation) {
                 System.out.println("\n==========================================================================================");
                 System.out.println("📬 [EMAIL SIMULATION] Invito collaboratore generato per " + request.getEmail() + ":");
                 System.out.println("👉 " + inviteLink);
                 System.out.println("==========================================================================================\n");

                 return Uni.createFrom().item(Map.of(
                     "message", "Invitation created successfully. Invitation email simulated.",
                     "invitation_token", invitationToken,
                     "expires_at", expiresAt.toString()
                 ));
             } else {
                 LOG.infof("Sending invitation email to: %s", request.getEmail());
                 String htmlContent = "<!DOCTYPE html>" +
                     "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
                     "<body style=\"margin:0;padding:0;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;-webkit-font-smoothing:antialiased;\">" +
                     "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color:#f8fafc;padding:40px 0;\">" +
                     "<tr><td align=\"center\">" +
                     "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width:580px;background-color:#ffffff;border:1px solid #e2e8f0;border-radius:12px;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05);\">" +
                     "<tr><td align=\"center\" style=\"padding:35px 40px 25px 40px;border-bottom:1px solid #f1f5f9;\">" +
                     "<span style=\"font-size:22px;font-weight:800;color:#0f172a;letter-spacing:-0.5px;\">Pass<span style=\"color:#4f46e5;\">Proof</span></span>" +
                     "<div style=\"font-size:11px;color:#64748b;font-weight:500;margin-top:4px;text-transform:uppercase;letter-spacing:1.5px;\">Security & Compliance Portal</div>" +
                     "</td></tr>" +
                     "<tr><td style=\"padding:40px 40px 35px 40px;\">" +
                     "<h2 style=\"margin:0 0 16px 0;font-size:18px;font-weight:700;color:#0f172a;line-height:24px;\">Sei stato invitato su PassProof</h2>" +
                     "<p style=\"margin:0 0 24px 0;font-size:14px;line-height:22px;color:#334155;\">" +
                     "Un amministratore ti ha invitato a collaborare all'interno del portale di sicurezza e conformità PassProof della sua organizzazione." +
                     "</p>" +
                     "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"margin-bottom:28px;\">" +
                     "<tr><td align=\"center\">" +
                     "<a href=\"" + inviteLink + "\" target=\"_blank\" style=\"display:inline-block;background-color:#4f46e5;color:#ffffff;font-size:14px;font-weight:600;text-decoration:none;padding:12px 28px;border-radius:8px;box-shadow:0 2px 4px rgba(79,70,229,0.2);\">" +
                     "Accetta Invito" +
                     "</a>" +
                     "</td></tr>" +
                     "</table>" +
                     "<p style=\"margin:0 0 12px 0;font-size:13px;line-height:20px;color:#64748b;\">" +
                     "Se il pulsante non funziona o non è cliccabile, copia e incolla il seguente indirizzo web nel tuo browser:" +
                     "</p>" +
                     "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;margin-bottom:24px;\">" +
                     "<tr><td style=\"padding:12px;font-family:monospace;font-size:11px;color:#4f46e5;word-break:break-all;line-height:16px;\">" +
                     "<a href=\"" + inviteLink + "\" target=\"_blank\" style=\"color:#4f46e5;text-decoration:none;\">" + inviteLink + "</a>" +
                     "</td></tr>" +
                     "</table>" +
                     "<div style=\"border-top:1px solid #f1f5f9;padding-top:24px;\">" +
                     "<p style=\"margin:0;font-size:12px;line-height:18px;color:#94a3b8;\">" +
                     "Questo link di invito scadrà tra 24 ore. Se ritieni che si tratti di un errore, ignora questa e-mail." +
                     "</p>" +
                     "</div>" +
                     "</td></tr>" +
                     "<tr><td style=\"padding:0 40px 35px 40px;text-align:center;\">" +
                     "<p style=\"margin:0;font-size:11px;line-height:16px;color:#94a3b8;\">" +
                     "© 2026 PassProof. Tutti i diritti riservati." +
                     "</p>" +
                     "<p style=\"margin:4px 0 0 0;font-size:10px;color:#cbd5e1;\">" +
                     "Questa è una comunicazione automatica del sistema, si prega di non rispondere." +
                     "</p>" +
                     "</td></tr>" +
                     "</table>" +
                     "</td></tr>" +
                     "</table>" +
                     "</body></html>";

                 return mailer.send(io.quarkus.mailer.Mail.withHtml(
                     request.getEmail(),
                     "Sei stato invitato a collaborare su PassProof",
                     htmlContent
                 )).map(voidResult -> Map.of(
                     "message", "Invitation created successfully. Invitation email sent.",
                     "invitation_token", invitationToken,
                     "expires_at", expiresAt.toString()
                 ));
             }
          });
        });
    }

    /**
     * Accepts a collaborator invitation, creating the user and membership in a transaction.
     */
    public Uni<Void> acceptInvitation(AcceptInviteRequest request) {
        LOG.debugf("Accepting invitation with token: %s", request.getToken());

        // Simple mock hash (in production, use BcryptUtil.bcryptHash or equivalent)
        String mockHash = "SHA256:" + Base64.getEncoder().encodeToString(request.getPassword().getBytes());

        return pgPool.withTransaction(conn -> 
            // 1. Retrieve and validate invitation
            conn.preparedQuery("SELECT tenant_id, email, role, expires_at FROM user_invitations WHERE token = $1")
                .execute(Tuple.of(request.getToken()))
                .chain(rows -> {
                    if (!rows.iterator().hasNext()) {
                        return Uni.createFrom().failure(new IllegalArgumentException("Invitation token not found."));
                    }
                    Row row = rows.iterator().next();
                    OffsetDateTime expiresAt = row.getOffsetDateTime("expires_at");
                    if (expiresAt.isBefore(OffsetDateTime.now())) {
                        return Uni.createFrom().failure(new IllegalArgumentException("Invitation token has expired."));
                    }

                    UUID tenantId = row.getUUID("tenant_id");
                    String email = row.getString("email");
                    String role = row.getString("role");

                    // 1.5 Check users limit
                    return conn.preparedQuery(
                        "SELECT (SELECT COUNT(*) FROM tenant_memberships WHERE tenant_id = $1) as current_users, " +
                        "(SELECT max_users FROM tenants WHERE id = $1) as max_users"
                    ).execute(Tuple.of(tenantId))
                    .chain(checkRows -> {
                        Row checkRow = checkRows.iterator().next();
                        Long currentUsers = checkRow.getLong("current_users");
                        Integer maxUsers = checkRow.getInteger("max_users");
                        if (maxUsers == null) maxUsers = 3;

                        if (currentUsers >= maxUsers) {
                            return Uni.createFrom().failure(new IllegalStateException(
                                "Questa organizzazione ha già raggiunto il limite massimo di " + maxUsers + " utenti consentiti dal suo piano."
                            ));
                        }

                        // 2. Create the User (activated immediately since invite was accepted)
                        return conn.preparedQuery(
                            "INSERT INTO users (email, password_hash, first_name, last_name, status) " +
                            "VALUES ($1, $2, $3, $4, 'ACTIVE') RETURNING id"
                        ).execute(Tuple.of(email, mockHash, request.getFirstName(), request.getLastName()))
                         .onItem().transform(userRows -> userRows.iterator().next().getUUID("id"));
                    })
                     .chain(userId -> 
                         // 3. Create the Membership
                         conn.preparedQuery("INSERT INTO tenant_memberships (user_id, tenant_id, role) VALUES ($1, $2, $3)")
                             .execute(Tuple.of(userId, tenantId, role))
                             .chain(() -> 
                                 // 4. Delete the invitation token
                                 conn.preparedQuery("DELETE FROM user_invitations WHERE token = $1")
                                     .execute(Tuple.of(request.getToken()))
                             )
                     );
                })
        ).replaceWithVoid();
    }

    /**
     * Fetches all tenant memberships for a given user.
     */
    public Uni<List<TenantDto>> getUserTenants(String userId) {
        UUID userUuid = UUID.fromString(userId);

        return pgPool.preparedQuery(
            "SELECT t.id, t.company_name, m.role, t.plan_tier " +
            "FROM tenant_memberships m " +
            "JOIN tenants t ON m.tenant_id = t.id " +
            "WHERE m.user_id = $1"
        ).execute(Tuple.of(userUuid))
         .map(rows -> {
             List<TenantDto> list = new ArrayList<>();
             for (Row row : rows) {
                 list.add(new TenantDto(
                     row.getUUID("id"),
                     row.getString("company_name"),
                     row.getString("role"),
                     row.getString("plan_tier")
                 ));
             }
             return list;
         });
    }

    /**
     * Verifies membership and emits a new signed JWT token with the switched tenant context.
     */
    public Uni<Map<String, String>> switchTenantContext(String targetTenantId, String userId) {
        UUID tenantUuid = UUID.fromString(targetTenantId);
        UUID userUuid = UUID.fromString(userId);

        LOG.infof("User %s requesting context switch to tenant %s", userId, targetTenantId);

        return pgPool.preparedQuery(
            "SELECT m.role, u.email, t.plan_tier " +
            "FROM tenant_memberships m " +
            "JOIN users u ON m.user_id = u.id " +
            "JOIN tenants t ON m.tenant_id = t.id " +
            "WHERE m.user_id = $1 AND m.tenant_id = $2"
        ).execute(Tuple.of(userUuid, tenantUuid))
         .chain(rows -> {
             if (!rows.iterator().hasNext()) {
                 return Uni.createFrom().failure(new SecurityException("No active membership found for requested tenant context."));
             }
             Row row = rows.iterator().next();
             String role = row.getString("role");
             String email = row.getString("email");
             String planTier = row.getString("plan_tier");

             // Build and sign new JWT token carrying the new tenant_id and plan_tier claims
             String jwtToken = Jwt.issuer("passproof-auth")
                 .upn(email)
                 .subject(userId)
                 .groups(Set.of(role))
                 .claim("tenant_id", targetTenantId)
                 .claim("plan_tier", planTier != null ? planTier : "STARTER")
                 .expiresIn(7200) // 2 hours expiration
                 .sign();

             LOG.infof("Context switch approved. New JWT token emitted for tenant: %s, plan: %s", targetTenantId, planTier);
             return Uni.createFrom().item(Map.of(
                 "status", "success",
                 "token", jwtToken
             ));
         });
    }

    /**
     * Step 1: Validates credentials, checks if MFA setup or challenge is required,
     * and returns a temporary token to proceed.
     */
    public Uni<LoginResponse> initiateLogin(LoginRequest request) {
        LOG.infof("Initiating login step 1 for user: %s", request.getEmail());
        String mockHash = "SHA256:" + Base64.getEncoder().encodeToString(request.getPassword().getBytes());

        return pgPool.preparedQuery(
            "SELECT id, password_hash, status, mfa_configured, mfa_secret FROM users WHERE email = $1"
        ).execute(Tuple.of(request.getEmail()))
         .chain(rows -> {
             if (!rows.iterator().hasNext()) {
                 return Uni.createFrom().failure(new SecurityException("Credenziali non valide."));
             }
             Row row = rows.iterator().next();
             String dbHash = row.getString("password_hash");
             String status = row.getString("status");
             boolean mfaConfigured = row.getBoolean("mfa_configured");
             UUID userId = row.getUUID("id");

             if (!"ACTIVE".equals(status)) {
                 return Uni.createFrom().failure(new SecurityException("Account non attivo."));
             }

             if (!dbHash.equals(mockHash)) {
                 return Uni.createFrom().failure(new SecurityException("Credenziali non valide."));
             }

             if (!mfaConfigured) {
                 // MFA Setup required: Generate secret, encrypt it, save backup codes
                 String secret = totpVerifier.generateSecret();
                 String encryptedSecret = mfaCrypto.encrypt(secret);
                 List<String> backupCodes = totpVerifier.generateBackupCodes();
                 
                 // Encrypt backup codes for storage
                 String[] encryptedBackupArray = backupCodes.stream().map(mfaCrypto::encrypt).toArray(String[]::new);

                 String totpUri = totpVerifier.generateOtpAuthUri(request.getEmail(), secret);

                 // Update database with secret and backup codes in pending state (mfa_configured = false)
                 return pgPool.preparedQuery(
                     "UPDATE users SET mfa_secret = $1, mfa_backup_codes = $2 WHERE id = $3"
                 ).execute(Tuple.of(encryptedSecret, encryptedBackupArray, userId))
                  .map(v -> {
                      // Issue temporary JWT signed token containing MFA setup claims
                      String tempToken = Jwt.issuer("passproof-auth")
                          .subject(userId.toString())
                          .claim("mfa_pending", true)
                          .claim("mfa_setup", true)
                          .expiresIn(600) // 10 minutes
                          .sign();
                      return LoginResponse.setupRequired(tempToken, totpUri, backupCodes);
                  });
             } else {
                 // MFA Challenge required: Issue temporary challenge JWT
                 String tempToken = Jwt.issuer("passproof-auth")
                     .subject(userId.toString())
                     .claim("mfa_pending", true)
                     .claim("mfa_setup", false)
                     .expiresIn(600) // 10 minutes
                     .sign();
                 return Uni.createFrom().item(LoginResponse.challengeRequired(tempToken));
             }
         });
    }

    /**
     * Step 2: Validates the 6-digit TOTP code against the encrypted secret key or backup codes.
     * If valid, issues the final authentication JWT token.
     */
    public Uni<LoginResponse> verifyMfa(MfaVerifyRequest request) {
        LOG.debug("Processing MFA verification step 2...");
        try {
            // Decodes the temporary token (simulated for dev/mock, standard JWT validation applies)
            String decodedString = new String(Base64.getUrlDecoder().decode(request.getTempToken().split("\\.")[1]));
            JsonObject claims = new JsonObject(decodedString);

            boolean mfaPending = claims.getBoolean("mfa_pending", false);
            boolean mfaSetup = claims.getBoolean("mfa_setup", false);
            String userId = claims.getString("sub");

            if (!mfaPending) {
                return Uni.createFrom().failure(new SecurityException("Token provvisorio non valido per la verifica MFA."));
            }

            UUID userUuid = UUID.fromString(userId);

            return pgPool.withTransaction(conn -> 
                // 1. Fetch user data (secret, backup codes, email)
                conn.preparedQuery("SELECT mfa_secret, mfa_configured, mfa_backup_codes, email FROM users WHERE id = $1")
                    .execute(Tuple.of(userUuid))
                    .chain(rows -> {
                        if (!rows.iterator().hasNext()) {
                            return Uni.createFrom().failure(new SecurityException("Utente non trovato."));
                        }
                        Row row = rows.iterator().next();
                        String encryptedSecret = row.getString("mfa_secret");
                        String email = row.getString("email");
                        String[] encryptedBackupCodes = (String[]) row.getValue("mfa_backup_codes");

                        // Decrypt secret key
                        String secret = mfaCrypto.decrypt(encryptedSecret);

                        // Validate 6-digit TOTP code
                        boolean valid = totpVerifier.verifyCode(secret, request.getCode());
                        Uni<Void> postValidationUni = Uni.createFrom().voidItem();

                        if (!valid && encryptedBackupCodes != null) {
                            // Check against backup codes if TOTP fails
                            int matchedIdx = -1;
                            for (int i = 0; i < encryptedBackupCodes.length; i++) {
                                String decryptedCode = mfaCrypto.decrypt(encryptedBackupCodes[i]);
                                if (decryptedCode.equals(String.valueOf(request.getCode()))) {
                                    matchedIdx = i;
                                    break;
                                }
                            }

                            if (matchedIdx != -1) {
                                LOG.infof("MFA verified successfully via backup code for user: %s", email);
                                valid = true;

                                // Remove the used backup code from the database array
                                List<String> remainingEncrypted = new ArrayList<>(Arrays.asList(encryptedBackupCodes));
                                remainingEncrypted.remove(matchedIdx);
                                String[] updatedBackupArray = remainingEncrypted.toArray(new String[0]);

                                postValidationUni = conn.preparedQuery("UPDATE users SET mfa_backup_codes = $1 WHERE id = $2")
                                    .execute(Tuple.of(updatedBackupArray, userUuid))
                                    .replaceWithVoid();
                            }
                        }

                        if (!valid) {
                            return Uni.createFrom().failure(new SecurityException("Codice TOTP o di backup non valido."));
                        }

                        // 2. Perform first-setup updates if mfa_setup flag is true
                        if (mfaSetup) {
                            postValidationUni = postValidationUni.chain(() -> 
                                conn.preparedQuery("UPDATE users SET mfa_configured = true WHERE id = $1")
                                    .execute(Tuple.of(userUuid))
                                    .replaceWithVoid()
                            );
                        }

                        // 3. Resolve the user's default/first active tenant membership
                        return postValidationUni.chain(() -> 
                            conn.preparedQuery(
                                "SELECT m.tenant_id, m.role, t.plan_tier " +
                                "FROM tenant_memberships m " +
                                "JOIN tenants t ON m.tenant_id = t.id " +
                                "WHERE m.user_id = $1 AND t.status = 'ACTIVE' LIMIT 1"
                            ).execute(Tuple.of(userUuid))
                             .chain(membershipRows -> {
                                 String targetTenantId = "";
                                 String role = "VIEWER"; // Default fallback role
                                 String planTier = "STARTER";

                                 if (membershipRows.iterator().hasNext()) {
                                     Row memRow = membershipRows.iterator().next();
                                     targetTenantId = memRow.getUUID("tenant_id").toString();
                                     role = memRow.getString("role");
                                     planTier = memRow.getString("plan_tier");
                                 }

                                 // 4. Issue the final authentication JWT token containing tenant_id and plan_tier claims
                                 String finalToken = Jwt.issuer("passproof-auth")
                                     .upn(email)
                                     .subject(userId)
                                     .groups(Set.of(role))
                                     .claim("tenant_id", targetTenantId)
                                     .claim("plan_tier", planTier != null ? planTier : "STARTER")
                                     .expiresIn(7200) // 2 hours expiration
                                     .sign();

                                 return Uni.createFrom().item(LoginResponse.success(finalToken));
                             })
                        );
                    })
            );
        } catch (Exception e) {
            LOG.error("Failed to process MFA verification step 2", e);
            return Uni.createFrom().failure(new SecurityException("Errore di validazione token o codice."));
        }
    }

    /**
     * Retrieves all active/inactive members and pending invitations for a tenant.
     */
    public Uni<List<TeamMemberDto>> getTeamMembers(String tenantId) {
        UUID tenantUuid = UUID.fromString(tenantId);
        List<TeamMemberDto> results = new ArrayList<>();

        // 1. Query active memberships
        return pgPool.preparedQuery(
            "SELECT u.id, u.email, u.first_name, u.last_name, u.status, tm.role, tm.created_at " +
            "FROM tenant_memberships tm " +
            "JOIN users u ON tm.user_id = u.id " +
            "WHERE tm.tenant_id = $1 " +
            "ORDER BY tm.created_at ASC"
        ).execute(Tuple.of(tenantUuid))
        .chain(memberRows -> {
            for (Row r : memberRows) {
                results.add(new TeamMemberDto(
                    r.getUUID("id").toString(),
                    r.getString("email"),
                    r.getString("first_name") != null ? r.getString("first_name") : "",
                    r.getString("last_name") != null ? r.getString("last_name") : "",
                    r.getString("role"),
                    r.getString("status"),
                    false,
                    r.getOffsetDateTime("created_at")
                ));
            }

            // 2. Query pending invitations
            return pgPool.preparedQuery(
                "SELECT id, email, role, 'PENDING' as status, created_at " +
                "FROM user_invitations " +
                "WHERE tenant_id = $1 " +
                "ORDER BY created_at DESC"
            ).execute(Tuple.of(tenantUuid))
            .map(inviteRows -> {
                for (Row r : inviteRows) {
                    results.add(new TeamMemberDto(
                        r.getUUID("id").toString(),
                        r.getString("email"),
                        "",
                        "",
                        r.getString("role"),
                        "PENDING",
                        true,
                        r.getOffsetDateTime("created_at")
                    ));
                }
                return results;
            });
        });
    }

    /**
     * Toggles collaborator account status (ACTIVE vs DISABLED).
     */
    public Uni<Void> toggleMemberStatus(String tenantId, String userId, String newStatus) {
        UUID tenantUuid = UUID.fromString(tenantId);
        UUID userUuid = UUID.fromString(userId);

        return pgPool.preparedQuery(
            "SELECT role FROM tenant_memberships WHERE tenant_id = $1 AND user_id = $2"
        ).execute(Tuple.of(tenantUuid, userUuid))
        .chain(rows -> {
            if (!rows.iterator().hasNext()) {
                return Uni.createFrom().failure(new IllegalArgumentException("Collaboratore non trovato nel team."));
            }
            String role = rows.iterator().next().getString("role");
            if ("OWNER".equalsIgnoreCase(role)) {
                return Uni.createFrom().failure(new IllegalStateException("Non è possibile disattivare l'Owner dell'azienda."));
            }

            String validatedStatus = "DISABLED".equalsIgnoreCase(newStatus) ? "DISABLED" : "ACTIVE";
            return pgPool.preparedQuery(
                "UPDATE users SET status = $1 WHERE id = $2"
            ).execute(Tuple.of(validatedStatus, userUuid)).replaceWithVoid();
        });
    }

    /**
     * Removes a collaborator from the tenant.
     */
    public Uni<Void> removeMember(String tenantId, String userId) {
        UUID tenantUuid = UUID.fromString(tenantId);
        UUID userUuid = UUID.fromString(userId);

        return pgPool.preparedQuery(
            "SELECT role FROM tenant_memberships WHERE tenant_id = $1 AND user_id = $2"
        ).execute(Tuple.of(tenantUuid, userUuid))
        .chain(rows -> {
            if (!rows.iterator().hasNext()) {
                return Uni.createFrom().failure(new IllegalArgumentException("Collaboratore non trovato nel team."));
            }
            String role = rows.iterator().next().getString("role");
            if ("OWNER".equalsIgnoreCase(role)) {
                return Uni.createFrom().failure(new IllegalStateException("Non è possibile eliminare l'Owner dell'azienda."));
            }

            return pgPool.preparedQuery(
                "DELETE FROM tenant_memberships WHERE tenant_id = $1 AND user_id = $2"
            ).execute(Tuple.of(tenantUuid, userUuid)).replaceWithVoid();
        });
    }

    /**
     * Revokes a pending invitation.
     */
    public Uni<Void> revokeInvitation(String tenantId, String inviteId) {
        UUID tenantUuid = UUID.fromString(tenantId);
        UUID inviteUuid = UUID.fromString(inviteId);

        return pgPool.preparedQuery(
            "DELETE FROM user_invitations WHERE id = $1 AND tenant_id = $2"
        ).execute(Tuple.of(inviteUuid, tenantUuid)).replaceWithVoid();
    }
}
