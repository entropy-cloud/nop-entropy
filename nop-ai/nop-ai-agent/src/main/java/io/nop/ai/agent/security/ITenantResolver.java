package io.nop.ai.agent.security;

/**
 * Contextual resolver for the current multi-tenant identifier
 * (vision §5.1 Tenant 隔离).
 *
 * <p>DB-backed stores and the {@code ActorRegistry} consult an
 * {@code ITenantResolver} to obtain the active tenantId at operation time.
 * Resolving tenant contextually (rather than as a method parameter on every
 * store interface) avoids a massive breaking change to every store contract
 * and aligns with the Nop platform {@code IContext} request-scoped model
 * (architecture baseline §九 — "Nop 平台的 {@code IContext}
 * (tenantId/userId) 天然支持租户标识").
 *
 * <p><b>Resolution semantics</b>:
 * <ul>
 *   <li>A {@code null} return means <em>no tenant context</em>: the caller
 *       treats all persisted data as visible (backward compatible, single
 *       tenant, NoOp shipped default, existing tests). No tenantId
 *       {@code WHERE} injection happens and no {@code TENANT_ID} value is
 *       written (see plan 232 Design Decisions 1 / 2).</li>
 *   <li>A non-null return is the active tenant identifier: the caller
 *       injects {@code AND (TENANT_ID = ? OR TENANT_ID IS NULL)} on
 *       SELECT/UPDATE/DELETE and writes the resolved value to the
 *       {@code TENANT_ID} column on INSERT.</li>
 * </ul>
 *
 * <p>Implementations <strong>must not</strong> return placeholder values
 * (e.g. {@code "unknown"}) to signal absence of context — return {@code null}
 * explicitly (Minimum Rules #24: no silent placeholder).
 *
 * <p>Shipped implementations:
 * {@link NullTenantResolver} (always-null, backward-compatible default) and
 * {@link ThreadLocalTenantResolver} (thread-local backed, used by
 * {@code DefaultAgentEngine} to propagate {@code Principal.tenantId} across
 * the {@code supplyAsync} worker-thread boundary).
 *
 * <p>See plan 232 (L4-multi-tenant-isolation) and vision §5.1.
 */
public interface ITenantResolver {

    /**
     * Resolve the active tenant identifier for the current thread / context.
     *
     * @return the tenantId, or {@code null} when no tenant context is active
     *         (all data visible — explicit backward-compatible semantics)
     */
    String resolveTenantId();
}
