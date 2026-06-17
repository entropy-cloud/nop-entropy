package io.nop.ai.agent.security;

/**
 * Shared helpers for multi-tenant SQL fragment construction (plan 232 /
 * L4-multi-tenant-isolation).
 *
 * <p>Each persistent table has a nullable {@code TENANT_ID} column. When the
 * contextual {@link ITenantResolver} reports a non-null tenant, DB stores
 * append the tenant {@code WHERE} fragment to SELECT/UPDATE/DELETE statements
 * and write the tenant value to {@code TENANT_ID} on INSERT. When the resolver
 * reports {@code null} (no tenant context), stores keep their SQL byte-identical
 * to the pre-tenant behaviour — explicit backward-compatible / single-tenant /
 * existing-test path (zero regression).
 *
 * <p>The {@code WHERE} fragment is non-strict:
 * {@code AND (TENANT_ID = ? OR TENANT_ID IS NULL)} — current-tenant rows plus
 * legacy null-tenant rows stay visible during a migration window (plan Design
 * Decision 3). In a fresh DB with non-null tenants only, the
 * {@code OR TENANT_ID IS NULL} clause matches nothing extra and cross-tenant
 * isolation holds strictly.
 *
 * <p>This class deliberately holds only static helpers — no behaviour, no state.
 */
public final class TenantSql {

    private TenantSql() {
    }

    /**
     * @param tenantColumn the tenant column name (e.g. {@code "TENANT_ID"})
     * @return the {@code WHERE}-clause fragment to AND-onto an existing
     *         {@code WHERE} when the tenant is active:
     *         {@code " AND (<col> = ? OR <col> IS NULL)"}
     */
    public static String whereTenant(String tenantColumn) {
        return " AND (" + tenantColumn + " = ? OR " + tenantColumn + " IS NULL)";
    }
}
