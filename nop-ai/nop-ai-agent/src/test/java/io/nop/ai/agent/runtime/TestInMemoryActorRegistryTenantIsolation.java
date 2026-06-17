package io.nop.ai.agent.runtime;

import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 232 (L4-multi-tenant-isolation) Phase 1 focused tests for the
 * {@link InMemoryActorRegistry} tenant tag map + tenant-scoped query filtering
 * (vision §5.1 Tenant 隔离). The {@link AgentActor} constructor and the
 * {@code IActorRuntime.createActor} interface signature are NOT modified —
 * tenant is tracked internally via the registry's tag map (plan Design
 * Decision 5).
 */
public class TestInMemoryActorRegistryTenantIsolation {

    @AfterEach
    void clear() {
        ThreadLocalTenantResolver.clear();
    }

    private static AgentActor actor(String actorId, String sessionId) {
        return new AgentActor(actorId, sessionId, "agent", System.currentTimeMillis(), null);
    }

    @Test
    void nullTenantResolverShowsAllRegisteredActors() {
        InMemoryActorRegistry registry = new InMemoryActorRegistry();
        AgentActor a = actor("a1", "s1");
        AgentActor b = actor("a2", "s2");
        registry.register(a);
        registry.register(b);

        // No tenant context → all visible (backward compatible).
        assertTrue(registry.get("a1").isPresent());
        assertTrue(registry.get("a2").isPresent());
        assertEquals(2, registry.getAll().size());
        assertTrue(registry.getBySession("s1").isPresent());
    }

    @Test
    void tenantContextHidesActorsRegisteredUnderDifferentTenant() {
        InMemoryActorRegistry registry = new InMemoryActorRegistry(ThreadLocalTenantResolver.INSTANCE);

        // Register under tenant-A.
        ThreadLocalTenantResolver.set("tenant-A");
        AgentActor a = actor("a1", "s1");
        registry.register(a);

        // Register under tenant-B.
        ThreadLocalTenantResolver.set("tenant-B");
        AgentActor b = actor("a2", "s2");
        registry.register(b);

        // tenant-A sees only its own actor.
        ThreadLocalTenantResolver.set("tenant-A");
        assertTrue(registry.get("a1").isPresent(), "tenant-A must see its own actor");
        assertFalse(registry.get("a2").isPresent(), "tenant-A must NOT see tenant-B's actor");
        assertTrue(registry.getBySession("s1").isPresent());
        assertFalse(registry.getBySession("s2").isPresent());
        assertEquals(1, registry.getAll().size(), "tenant-A getAll must return only its own actor");

        // tenant-B sees only its own actor.
        ThreadLocalTenantResolver.set("tenant-B");
        assertFalse(registry.get("a1").isPresent(), "tenant-B must NOT see tenant-A's actor");
        assertTrue(registry.get("a2").isPresent());
        assertEquals(1, registry.getAll().size());
    }

    @Test
    void nullTenantContextShowsAllActorsRegardlessOfTags() {
        InMemoryActorRegistry registry = new InMemoryActorRegistry(ThreadLocalTenantResolver.INSTANCE);

        ThreadLocalTenantResolver.set("tenant-A");
        registry.register(actor("a1", "s1"));
        ThreadLocalTenantResolver.set("tenant-B");
        registry.register(actor("a2", "s2"));

        // No tenant context → all visible (backward compatible / single tenant).
        ThreadLocalTenantResolver.set(null);
        assertEquals(2, registry.getAll().size(), "null tenant context must see all actors");
        assertTrue(registry.get("a1").isPresent());
        assertTrue(registry.get("a2").isPresent());
    }

    @Test
    void unregisterRemovesFromAllTenants() {
        InMemoryActorRegistry registry = new InMemoryActorRegistry(ThreadLocalTenantResolver.INSTANCE);

        ThreadLocalTenantResolver.set("tenant-A");
        registry.register(actor("a1", "s1"));

        // Unregister under a null context (the tag is keyed by actorId).
        ThreadLocalTenantResolver.set(null);
        registry.unregister("a1");

        ThreadLocalTenantResolver.set("tenant-A");
        assertFalse(registry.get("a1").isPresent(), "unregistered actor must be invisible to its tenant");
    }

    @Test
    void explicitNullTenantResolverConstructorIsEquivalentToDefault() {
        // Both the no-arg ctor and NullTenantResolver ctor disable filtering.
        InMemoryActorRegistry registry = new InMemoryActorRegistry(NullTenantResolver.INSTANCE);
        registry.register(actor("a1", "s1"));
        Optional<AgentActor> got = registry.get("a1");
        assertTrue(got.isPresent());
        // Even when a thread-local tenant is set elsewhere, a NullTenantResolver
        // registry never filters.
        ThreadLocalTenantResolver.set("tenant-X");
        assertTrue(registry.get("a1").isPresent(),
                "NullTenantResolver-backed registry must ignore thread-local tenant");
        assertEquals(1, registry.getAll().size());
    }
}
