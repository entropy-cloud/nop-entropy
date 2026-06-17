package io.nop.ai.agent.security;

/**
 * Thread-local backed {@link ITenantResolver} — the mechanism
 * {@code DefaultAgentEngine} uses to propagate {@code Principal.tenantId}
 * across the {@code CompletableFuture.supplyAsync} worker-thread boundary
 * (plan 232 Design Decision 6).
 *
 * <p>Standard {@link ThreadLocal} does not propagate across
 * {@code supplyAsync} thread boundaries, so the engine must set the tenant
 * context <em>inside</em> the {@code supplyAsync} lambda body (and clear it in
 * the lambda's {@code finally}) for DB stores running on the worker thread to
 * observe it.
 *
 * <p><b>Usage</b>:
 * <ul>
 *   <li>Integrators construct DB stores / {@code ActorRegistry} with
 *       {@link #INSTANCE} (or a shared instance) so they read this thread-local.</li>
 *   <li>The engine captures tenantId from {@code Principal.getTenantId()}
 *       (null-safe) in the synchronous phase, then inside the
 *       {@code supplyAsync} lambda calls {@link #set} with the captured value
 *       before any DB operation and {@link #clear} in a {@code finally}.</li>
 * </ul>
 *
 * <p><b>Null = all visible</b> (plan Design Decision 2): a {@code null}
 * tenantId is the explicit backward-compatible "no tenant context" signal —
 * setting it to {@code null} is equivalent to not having set it.
 *
 * <p>See plan 232 (L4-multi-tenant-isolation) Phase 1 and vision §5.1.
 */
public final class ThreadLocalTenantResolver implements ITenantResolver {

    /** Singleton instance — backed by a static {@link ThreadLocal}. */
    public static final ThreadLocalTenantResolver INSTANCE = new ThreadLocalTenantResolver();

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ThreadLocalTenantResolver() {
    }

    /**
     * Set the active tenantId for the current thread. Pass {@code null} (or
     * call {@link #clear()}) to signal "no tenant context".
     *
     * @param tenantId the tenant identifier captured from {@code Principal}, or
     *                {@code null} when there is no tenant context
     */
    public static void set(String tenantId) {
        if (tenantId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantId);
        }
    }

    /**
     * Clear the tenant context for the current thread. Must be called in a
     * {@code finally} block so the worker thread (often pooled) does not leak
     * tenant context to the next task it executes.
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * @return the tenantId currently set for this thread, or {@code null} when
     *         none is set (all data visible — backward compatible)
     */
    public static String current() {
        return CURRENT.get();
    }

    @Override
    public String resolveTenantId() {
        return current();
    }
}
