package com.cyberpass.backend.tenant;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.function.Function;

/**
 * A tenant-aware wrapper around the reactive PgPool.
 * It manages transactional execution of queries, ensuring the PostgreSQL Row-Level Security
 * session variable 'app.current_tenant_id' is set locally for each transaction.
 */
@ApplicationScoped
public class TenantAwarePgPool {

    private static final Logger LOG = Logger.getLogger(TenantAwarePgPool.class);

    @Inject
    PgPool pgPool;

    @Inject
    TenantContext tenantContext;

    /**
     * Executes the given database operations in a transaction scoped to the current tenant.
     * 
     * Inside this method:
     * 1. A new PostgreSQL connection is acquired and a transaction is started.
     * 2. The session variable 'app.current_tenant_id' is set locally via 'set_config(..., true)'.
     *    This is secure and scoped only to this transaction, preventing connection pool leakage.
     * 3. The business logic function is executed on the connection.
     * 4. The transaction is committed or rolled back automatically, which also discards the local setting.
     *
     * @param function The database operations to execute.
     * @param <T> The return type of the database operation.
     * @return A Uni containing the result of the operations.
     */
    public <T> Uni<T> withTransaction(Function<SqlConnection, Uni<T>> function) {
        if (!tenantContext.hasTenantId()) {
            LOG.warn("Database operation aborted: No tenant ID found in current request context.");
            return Uni.createFrom().failure(new IllegalStateException(
                "No tenant ID found in current request context. Row-Level Security cannot be enforced."
            ));
        }

        String tenantId = tenantContext.getTenantId().toString();
        LOG.debugf("Executing transaction for tenant: %s", tenantId);

        return pgPool.withTransaction(conn -> 
            conn.preparedQuery("SELECT set_config('app.current_tenant_id', $1, true)")
                .execute(Tuple.of(tenantId))
                .chain(() -> function.apply(conn))
        );
    }
}
