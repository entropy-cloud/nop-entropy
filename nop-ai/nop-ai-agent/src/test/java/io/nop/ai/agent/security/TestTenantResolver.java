package io.nop.ai.agent.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan 232 (L4-multi-tenant-isolation) Phase 1 focused tests for the
 * contextual tenant resolver mechanism: {@link ITenantResolver},
 * {@link NullTenantResolver} (backward-compatible default) and
 * {@link ThreadLocalTenantResolver} (engine worker-thread propagation).
 */
public class TestTenantResolver {

    @AfterEach
    void clear() {
        // Defensive: never leak a thread-local tenant into another test.
        ThreadLocalTenantResolver.clear();
    }

    @Test
    void nullTenantResolverAlwaysReturnsNull() {
        assertNull(NullTenantResolver.INSTANCE.resolveTenantId(),
                "NullTenantResolver must always report null (no tenant context = all visible)");
    }

    @Test
    void nullTenantResolverIsSingleton() {
        assertSame(NullTenantResolver.INSTANCE, NullTenantResolver.INSTANCE);
    }

    @Test
    void threadLocalResolverDefaultsToNullWhenUnset() {
        ThreadLocalTenantResolver.clear();
        assertNull(ThreadLocalTenantResolver.current(),
                "Unset thread-local must resolve to null (all visible, backward compatible)");
        assertNull(ThreadLocalTenantResolver.INSTANCE.resolveTenantId());
    }

    @Test
    void threadLocalResolverSetAndClearOnSameThread() {
        ThreadLocalTenantResolver.set("tenant-A");
        assertEquals("tenant-A", ThreadLocalTenantResolver.current());
        assertEquals("tenant-A", ThreadLocalTenantResolver.INSTANCE.resolveTenantId());

        ThreadLocalTenantResolver.clear();
        assertNull(ThreadLocalTenantResolver.current(),
                "clear() must reset the resolver to null on the same thread");
    }

    @Test
    void setNullIsEquivalentToClear() {
        ThreadLocalTenantResolver.set("tenant-A");
        ThreadLocalTenantResolver.set(null);
        assertNull(ThreadLocalTenantResolver.current(),
                "set(null) must be equivalent to clear() (explicit no-context signal)");
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
