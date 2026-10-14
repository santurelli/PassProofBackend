package com.cyberpass.backend.resource;

import com.cyberpass.backend.dto.ContactRequest;
import io.smallrye.mutiny.Uni;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.mailer.Mail;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Public REST resource to handle contact form submissions.
 * This endpoint is public and does not require JWT authentication.
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicContactResource {

    private static final Logger LOG = Logger.getLogger(PublicContactResource.class);

    @Inject
    ReactiveMailer mailer;

    @ConfigProperty(name = "passproof.contact.recipient-email", defaultValue = "pietro.santurelli@newsoftitalia.com")
    String recipientEmail;

    @POST
    @Path("/contact")
    public Uni<Response> handleContactForm(ContactRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty() ||
            request.getEmail() == null || request.getEmail().trim().isEmpty() ||
            request.getCompany() == null || request.getCompany().trim().isEmpty() ||
            request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Tutti i campi sono obbligatori."))
                .build());
        }

        LOG.infof("New contact form submission from: %s (%s) at company %s", 
            request.getName(), request.getEmail(), request.getCompany());

        String emailSubject = "Nuovo Lead PassProof: " + request.getCompany();
        
        String htmlContent = "<!DOCTYPE html><html>" +
            "<body style=\"font-family: sans-serif; color: #1e293b; line-height: 1.5; padding: 20px;\">" +
            "<div style=\"max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 30px;\">" +
            "<h2 style=\"color: #4f46e5; margin-bottom: 20px;\">Nuova Richiesta Demo / Contatto</h2>" +
            "<hr style=\"border: none; border-top: 1px solid #f1f5f9; margin-bottom: 20px;\" />" +
            "<p>Hai ricevuto una nuova richiesta dal modulo di contatto del sito web di PassProof:</p>" +
            "<table style=\"width: 100%; border-collapse: collapse; margin-bottom: 20px;\">" +
            "<tr><td style=\"padding: 8px 0; font-weight: bold; color: #64748b; width: 150px;\">Nome:</td><td style=\"padding: 8px 0;\">" + request.getName() + "</td></tr>" +
            "<tr><td style=\"padding: 8px 0; font-weight: bold; color: #64748b;\">Email:</td><td style=\"padding: 8px 0;\"><a href=\"mailto:" + request.getEmail() + "\" style=\"color: #4f46e5; text-decoration: none;\">" + request.getEmail() + "</a></td></tr>" +
            "<tr><td style=\"padding: 8px 0; font-weight: bold; color: #64748b;\">Azienda:</td><td style=\"padding: 8px 0;\">" + request.getCompany() + "</td></tr>" +
            "</table>" +
            "<p style=\"font-weight: bold; color: #64748b; margin-bottom: 8px;\">Messaggio:</p>" +
            "<div style=\"background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 15px; font-style: italic; white-space: pre-wrap;\">" +
            request.getMessage() +
            "</div>" +
            "</div>" +
            "</body></html>";

        return mailer.send(Mail.withHtml(recipientEmail, emailSubject, htmlContent))
            .map(v -> Response.ok(Map.of("status", "success", "message", "Request received successfully.")).build())
            .onFailure().recoverWithItem(err -> {
                LOG.error("Failed to send contact notification email via SMTP", err);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Errore nell'invio della richiesta: " + err.getMessage()))
                    .build();
            });
    }
}
