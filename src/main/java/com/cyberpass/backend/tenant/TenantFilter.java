package com.cyberpass.backend.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * RESTEasy Reactive request filter that intercepts incoming requests,
 * extracts the 'X-Tenant-ID' HTTP header, and populates the RequestScoped TenantContext.
 */
@ApplicationScoped
public class TenantFilter {

    private static final Logger LOG = Logger.getLogger(TenantFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Inject
    TenantContext tenantContext;

    @ServerRequestFilter(preMatching = true)
    public void filter(ContainerRequestContext requestContext) {
        String tenantIdHeader = requestContext.getHeaderString(TENANT_HEADER);
        
        if (tenantIdHeader == null || tenantIdHeader.trim().isEmpty()) {
            LOG.debugf("No %s header provided in request to %s", TENANT_HEADER, requestContext.getUriInfo().getPath());
            return;
        }

        try {
            tenantContext.setTenantId(tenantIdHeader);
            LOG.debugf("Successfully bound tenant context: %s for request: %s", tenantIdHeader, requestContext.getUriInfo().getPath());
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid UUID format for %s header: %s", TENANT_HEADER, tenantIdHeader);
            requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                .entity("Invalid X-Tenant-ID format. Must be a valid UUID.")
                .build());
        }
    }
}
