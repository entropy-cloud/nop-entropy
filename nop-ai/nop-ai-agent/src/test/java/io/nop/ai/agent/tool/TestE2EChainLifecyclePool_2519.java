package io.nop.ai.agent.tool;

import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.InjectionSemantics;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 Phase 2 rule 22 chain one (concurrency / lifecycle chain, 裁定
 * ①②③④) — one composite end-to-end scenario over a real engine context:
 *
 * <ol>
 *   <li>裁定① directional interleave: an injection staged while the session is
 *       live lands in the drain (persisted with the terminal state); an
 *       injection arriving between the in-lock drain and the remove is
 *       deterministically rejected with an observable counter — the terminal
 *       persisted state never contains it (C3-014 lost-write closure).</li>
 *   <li>裁定② store terminal eviction + restore compensation: the real engine
 *       terminal path (cancelSession convergence point) removes the session
 *       from the store and the restore path compensates with an explicit typed
 *       rejection ("no persistent state").</li>
 *   <li>裁定③ the TEE resolves ONE shared spawn pool across invocations
 *       (rule 23 wiring: thread-name / identity evidence).</li>
 *   <li>裁定④ close() two-state invariant: running tasks settle (interrupted
 *       or released) and queued tasks settle observably — no future is left
 *       never-settling, no task silently executes after close.</li>
 * </ol>
 *
 * <p>Maps the focused pins TestDefaultAgentEngineInjectionWindow (①),
 * TestInvariantGate3EntryPointCleanup.runtimeTerminalCleanup (②),
 * TestTeamExecuteFlowExecutorPoolLifecycle (③④), TestSpawnPoolDrain (④) into
 * one sequential chain (rule 22 composition duty).
 */
public class TestE2EChainLifecyclePool_2519 {

    /** Store that records real remove invocations (rule 23/裁定② evidence). */
    static final class CountingStore extends InMemorySessionStore {
        final AtomicInteger removeCount = new AtomicInteger();

        @Override
        public void remove(String sessionId) {
            removeCount.incrementAndGet();
            super.remove(sessionId);
        }
    }

    // =====================================================================
    // Stage A+B (裁定①②): injection window → terminal → eviction → restore
    // =====================================================================

    @Test
    void chain1_injectionWindowToTerminalEvictionToRestoreRejection() throws Exception {
        CountingStore store = new CountingStore();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(null, null)
                .sessionStore(store).build();
        try {
            AgentSession session = store.getOrCreate("e2e-chain1", "chain1-agent");

            // (1) live session: a pre-terminal injection stages through the
            // public injectInput channel (staged branch — session not running).
            engine.injectInput("e2e-chain1",
                    new io.nop.ai.api.chat.messages.ChatUserMessage("early"),
                    InjectionSemantics.FOLLOWUP);
            assertEquals(1, session.getPendingInjections().size());

            // (2) directional interleave — hold the session monitor exactly
            // like the terminal path's in-lock step (1): drain + terminal
            // marker; an injectInput released AFTER the marker observes it
            // under the same monitor and must be rejected (never staged).
            final long before = DefaultAgentEngine.injectAfterTerminalCount();
            final CountDownLatch rejected = new CountDownLatch(1);
            final AtomicInteger stagedAfter = new AtomicInteger(-1);
            List<AgentSession.PendingInjection> drained;
            synchronized (session) {
                drained = session.drainPendingInjections();
                session.markTerminal();
                Thread injector = new Thread(() -> {
                    try {
                        engine.injectInput("e2e-chain1",
                                new io.nop.ai.api.chat.messages.ChatUserMessage("late"),
                                InjectionSemantics.FOLLOWUP);
                    } finally {
                        stagedAfter.set(session.getPendingInjections().size());
                        rejected.countDown();
                    }
                });
                injector.start();
                // keep the monitor held briefly so the injector deterministically
                // parks on it before observing the marker
                Thread.sleep(100);
            }
            assertTrue(rejected.await(10, TimeUnit.SECONDS), "injector must finish");
            assertEquals(1, drained.size(), "the pre-terminal injection lands in the drain");
            assertTrue(drained.get(0).getMessageJson().contains("early"),
                    "the drained entry is the pre-terminal injection");
            assertEquals(0, stagedAfter.get(),
                    "the post-drain injection must NOT be staged (终态持久化不含该注入)");
            assertEquals(before + 1, DefaultAgentEngine.injectAfterTerminalCount(),
                    "the post-drain rejection is observable (nop.ai.agent.inject-after-terminal counter)");

            // (3) 裁定② — real engine terminal path (cancel convergence point)
            // removes a live session from the store; the wrapper records the
            // real remove call (rule 23② evidence, not method-existence).
            store.getOrCreate("e2e-chain1-live", "chain1-agent");
            int removesBefore = store.removeCount.get();
            engine.cancelSession("e2e-chain1-live", "chain1 terminal probe", false)
                    .toCompletableFuture().join();
            assertNull(store.get("e2e-chain1-live"),
                    "terminal execution must evict the session from the store (裁定②)");
            assertTrue(store.removeCount.get() > removesBefore,
                    "sessionStore.remove must be REALLY invoked by the engine terminal path");

            // (4) restore compensation: the evicted session is no longer
            // restorable — explicit typed rejection (design 终态语义矩阵,
            // InMemory row: 终态会话不可 restore).
            NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                    () -> engine.restoreSession("e2e-chain1-live", "approver", "chain1"),
                    "restore of an evicted terminal session must be explicitly rejected");
            assertTrue(String.valueOf(ex.getMessage()).contains("no persistent state"),
                    "typed restore rejection expected, got: " + ex.getMessage());
        } finally {
            engine.close();
        }
    }

    // =====================================================================
    // Stage C (裁定③④): TEE shared pool identity + close two-state invariant
    // =====================================================================

    @Test
    void chain1_teeSharedPoolAndCloseDrainTwoStateInvariant() throws Exception {
        TeamExecuteFlowExecutor tee = new TeamExecuteFlowExecutor();
        try {
            // 裁定③ (rule 23③): every resolve returns the SAME TEE-owned pool
            // — the per-invocation leak defect would return distinct pools.
            ExecutorService first = (ExecutorService) tee.resolveSpawnExecutor();
            for (int i = 0; i < 4; i++) {
                assertSame(first, tee.resolveSpawnExecutor(),
                        "resolveSpawnExecutor must return the shared TEE pool on every invocation");
            }

            int capacity = Math.max(2, Runtime.getRuntime().availableProcessors());
            CountDownLatch hold = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            // saturating running tasks + queued tasks beyond capacity
            for (int i = 0; i < capacity + 3; i++) {
                futures.add(first.submit(() -> {
                    try {
                        hold.await(8, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
            }
            Thread.sleep(200); // let the saturating workers start

            tee.close(); // bounded drain against the held latch

            // two-state invariant (state 1): RUNNING tasks settle at close —
            // the bounded drain interrupts them and each future is done.
            long deadline = System.currentTimeMillis() + 8_000;
            boolean runningSettled;
            do {
                runningSettled = true;
                for (Future<?> f : futures.subList(0, capacity)) {
                    if (!f.isDone()) {
                        runningSettled = false;
                        break;
                    }
                }
                if (!runningSettled) {
                    Thread.sleep(10);
                }
            } while (!runningSettled && System.currentTimeMillis() < deadline);
            assertTrue(runningSettled,
                    "裁定④ two-state invariant state 1: every RUNNING spawn future settles at close");

            long terminalDeadline = System.currentTimeMillis() + 5_000;
            while (!first.isTerminated() && System.currentTimeMillis() < terminalDeadline) {
                Thread.sleep(10);
            }
            assertTrue(first.isTerminated(), "close() must leave the owned pool terminated");
            assertTrue(first.isShutdown(), "close() must shut the owned pool down");

            // two-state invariant (state 2): QUEUED tasks are discarded
            // observably — never executed after close (the discard is WARNed
            // under nop.ai.agent.spawn-drain-discard, rule 24; the
            // dispatch-future settle face for the coordinator-owned pool is
            // pinned by TestSpawnPoolDrain).
            for (Future<?> f : futures.subList(capacity, futures.size())) {
                assertTrue(!(f.isDone() && !f.isCancelled()),
                        "queued future must not complete normally after close");
            }
        } finally {
            // idempotent with the close above
            tee.close();
        }
    }

    // sanity: PendingInjection accessor shape used above stays compile-visible
    @Test
    void chain1_drainedEntryCarriesPayload() {
        AgentSession s = AgentSession.create("e2e-chain1-payload", "chain1-agent");
        s.addPendingInjection("FOLLOWUP", "{\"content\":\"early\"}");
        List<AgentSession.PendingInjection> drained = s.drainPendingInjections();
        assertEquals(1, drained.size());
        assertNotNull(drained.get(0).getMessageJson());
        assertTrue(drained.get(0).getMessageJson().contains("early"));
    }
}
