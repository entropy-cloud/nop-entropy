package io.nop.ai.agent.runtime;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.quota.CountingResourceGuard;
import io.nop.ai.agent.quota.DefaultResourceGuard;
import io.nop.ai.agent.quota.NoOpResourceGuard;
import io.nop.ai.agent.quota.QuotaConfig;
import io.nop.ai.agent.quota.QuotaDimension;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 focused tests for the {@link IResourceGuard} wiring into
 * {@link InMemoryActorRuntime} (plan 234).
 *
 * <p>Verifies the {@link QuotaDimension#CONCURRENT_ACTORS_PER_TENANT}
 * dimension:
 * <ul>
 *   <li>NoOp default → zero regression (no enforcement, all createActor
 *       proceed).</li>
 *   <li>Functional {@link DefaultResourceGuard} + low limit → the Nth
 *       createActor beyond the limit is denied with
 *       {@link NopAiAgentException}.</li>
 *   <li>Per-tenant scope isolation — tenant-A's quota is independent of
 *       tenant-B's (Design Decision §5).</li>
 *   <li>Wiring verification — a {@link CountingResourceGuard} asserts
 *       {@code checkConcurrent} was actually invoked at runtime (Minimum
 *       Rules #23).</li>
 *   <li>Anti-Hollow — denial does not register the actor (state unchanged,
 *       Minimum Rules #24).</li>
 * </ul>
 */
public class TestActorRuntimeQuotaEnforcement {

    private InMemoryActorRuntime runtime;

    private void destroyQuietly() {
        if (runtime != null) {
            runtime.destroyAll();
        }
    }

    @AfterEach
    void tearDown() {
        destroyQuietly();
        ThreadLocalTenantResolver.clear();
    }

    /**
     * Mailbox lookup that returns null (no mailbox) — sufficient for quota
     * tests which do not depend on mailbox consumption.
     */
    private Function<String, IMailbox> noMailbox() {
        return sessionId -> null;
    }

    @Test
    void noOpDefaultIsZeroRegression() {
        runtime = new InMemoryActorRuntime(noMailbox(),
                () -> "tenant-A",
                10L, 1000L,
                NoOpResourceGuard.noOp());
        // NoOp guard → no enforcement: create 5 actors despite a (would-be)
        // functional DefaultResourceGuard config that is bypassed.
        for (int i = 0; i < 5; i++) {
            AgentActor actor = runtime.createActor("sess-" + i, "agent-" + i);
            assertTrue(runtime.getActor(actor.getActorId()).isPresent());
        }
        assertEquals(5, runtime.getActiveActors().size());
    }

    @Test
    void concurrentActorsPerTenantEnforced() {
        // tenantMaxConcurrentActors=2.
        runtime = new InMemoryActorRuntime(noMailbox(),
                () -> "tenant-A",
                10L, 1000L,
                new DefaultResourceGuard(new QuotaConfig(8, 2)));

        // 1st and 2nd actors succeed (projected 1, 2 <= 2).
        AgentActor a1 = runtime.createActor("sess-1", "agent-1");
        runtime.createActor("sess-2", "agent-2");

        // 3rd actor denied (projected 3 > 2).
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> runtime.createActor("sess-3", "agent-3"));
        assertTrue(ex.getMessage().contains("CONCURRENT_ACTORS_PER_TENANT"));
        assertTrue(ex.getMessage().contains("tenant-A"));

        // Anti-Hollow: 3rd actor NOT registered (state unchanged).
        assertEquals(2, runtime.getActiveActors().size(),
                "denial must not have registered the 3rd actor");
        assertTrue(runtime.getActorBySession("sess-3").isEmpty());
        // a1 still active.
        assertTrue(runtime.getActor(a1.getActorId()).isPresent());
    }

    @Test
    void perTenantScopeIsIndependent() {
        // tenantMaxConcurrentActors=2.
        DefaultResourceGuard guard = new DefaultResourceGuard(new QuotaConfig(8, 2));
        ThreadLocalTenantResolver tenantResolver = ThreadLocalTenantResolver.INSTANCE;

        runtime = new InMemoryActorRuntime(noMailbox(),
                tenantResolver, 10L, 1000L, guard);

        // tenant-A: create 2 (limit).
        ThreadLocalTenantResolver.set("tenant-A");
        runtime.createActor("a-sess-1", "agent");
        runtime.createActor("a-sess-2", "agent");
        assertEquals(2, runtime.getActiveActors().size());

        // tenant-A 3rd denied.
        assertThrows(NopAiAgentException.class,
                () -> runtime.createActor("a-sess-3", "agent"));

        // Switch to tenant-B: independent bucket — 2 more succeed even though
        // tenant-A is at its limit.
        ThreadLocalTenantResolver.set("tenant-B");
        runtime.createActor("b-sess-1", "agent");
        runtime.createActor("b-sess-2", "agent");
        // From tenant-B's view, only tenant-B's 2 actors are visible.
        assertEquals(2, runtime.getActiveActors().size(),
                "tenant-B sees only its own actors (registry tenant filtering)");

        // tenant-B 3rd denied too (independent limit).
        assertThrows(NopAiAgentException.class,
                () -> runtime.createActor("b-sess-3", "agent"));

        // Back to tenant-A: still sees its 2.
        ThreadLocalTenantResolver.set("tenant-A");
        assertEquals(2, runtime.getActiveActors().size());
    }

    @Test
    void wiringGuardActuallyInvoked() {
        CountingResourceGuard spy = new CountingResourceGuard(
                new DefaultResourceGuard(new QuotaConfig(8, 10)));
        runtime = new InMemoryActorRuntime(noMailbox(),
                () -> "tenant-X",
                10L, 1000L, spy);

        runtime.createActor("sess-1", "agent");

        // Minimum Rules #23: guard was actually called.
        assertTrue(spy.wasCalled(), "createActor must call the guard at runtime");
        assertEquals(QuotaDimension.CONCURRENT_ACTORS_PER_TENANT,
                spy.getDimensions().get(0));
        // scopeKey = tenant-X.
        assertTrue(spy.getDecisions().get(0).getScopeKey().contains("tenant-X"));
    }

    @Test
    void globalScopeUsedWhenNoTenantContext() {
        CountingResourceGuard spy = new CountingResourceGuard(NoOpResourceGuard.noOp());
        // NullTenantResolver → no tenant context → global bucket scopeKey.
        runtime = new InMemoryActorRuntime(noMailbox(),
                io.nop.ai.agent.security.NullTenantResolver.INSTANCE,
                10L, 1000L, spy);

        runtime.createActor("sess-1", "agent");
        assertTrue(spy.wasCalled());
        assertEquals("__global__", spy.getDecisions().get(0).getScopeKey());
    }

    @Test
    void idempotentCreateActorForSameSessionDoesNotConsumeExtraQuota() {
        CountingResourceGuard spy = new CountingResourceGuard(
                new DefaultResourceGuard(new QuotaConfig(8, 1)));
        runtime = new InMemoryActorRuntime(noMailbox(),
                () -> "tenant-1",
                10L, 1000L, spy);

        // 1st create succeeds.
        AgentActor first = runtime.createActor("sess-1", "agent");
        // Idempotent: 2nd create for same active session returns existing,
        // does NOT call the guard again (does not consume a 2nd quota slot).
        AgentActor same = runtime.createActor("sess-1", "agent");
        assertEquals(first.getActorId(), same.getActorId());
        assertEquals(1, spy.getCallCount(),
                "idempotent re-create must not invoke the guard a second time");
        assertEquals(1, runtime.getActiveActors().size());
    }

    @Test
    void nullGuardFallsBackToNoOp() {
        // null guard → NoOp fallback (zero regression).
        runtime = new InMemoryActorRuntime(noMailbox(),
                () -> "tenant-1",
                10L, 1000L, null);
        AgentActor actor = runtime.createActor("sess-1", "agent");
        assertTrue(runtime.getActor(actor.getActorId()).isPresent());
        assertNotEquals(AgentActorStatus.FAILED, actor.getStatus());
    }
}
