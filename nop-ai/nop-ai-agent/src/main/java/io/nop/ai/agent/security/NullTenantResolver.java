package io.nop.ai.agent.security;

/**
 * {@link ITenantResolver} that always returns {@code null} — the shipped
 * default for DB stores and the {@code ActorRegistry} (plan 232 / L4-multi-
 * tenant-isolation).
 *
 * <p>When a store is constructed without an explicit tenant resolver, it uses
 * this implementation. Because {@link #resolveTenantId()} always returns
 * {@code null}, no tenantId {@code WHERE} injection and no {@code TENANT_ID}
 * column writes occur — every persisted row is visible and the SQL is byte-for-
 * byte identical to the pre-tenant behaviour. This is the explicit
 * backward-compatible / single-tenant / existing-test path (plan Design
 * Decision 2: "null tenant = 全部数据可见").
 *
 * <p>This is not a silent no-op (Minimum Rules #24): a {@code null} return is
 * the documented "no tenant context" signal that triggers the legitimate
 * "all data visible" code path.
 */
public final class NullTenantResolver implements ITenantResolver {

    /** Singleton instance — stateless, safe to share. */
    public static final NullTenantResolver INSTANCE = new NullTenantResolver();

    private NullTenantResolver() {
    }

    @Override
    public String resolveTenantId() {
        return null;
    }
}
