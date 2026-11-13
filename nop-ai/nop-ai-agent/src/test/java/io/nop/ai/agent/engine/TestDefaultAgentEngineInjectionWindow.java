package io.nop.ai.agent.engine;

import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 red① driver (C1-008 / C10-005): injection registration window.
 *
 * <p>Covers the three defect facets:
 * <ul>
 *   <li>(a) concurrent staged-branch injectInput pressure — the bare
 *       ArrayList carrier allowed CME/torn state under parallel injectInput;
 *       post-fix the synchronizedList + per-session monitor carries the
 *       pressure with zero loss (压力窗 red → green).</li>
 *   <li>(b) inject-after-terminal deterministic rejection — after the terminal
 *       marker, an injection is explicitly rejected (WARN + counter), never
 *       silently staged into a session that is about to be evicted (B1 唯一
 *       形态 step 4; the defect state red = injection silently staged/lost).</li>
 *   <li>(c) registry window symmetric registration — resume/wake/restore share
 *       the doExecute registration semantics (declaration-level gate row
 *       injection-target-registration-window in TestInvariantGate3EntryPointCleanup).</li>
 * </ul>
 *
 * <p>Pre-fix (put-only / bare-ArrayList / no-terminal-guard) state: (a) may
 * drop entries or throw CME under pressure; (b) stages the injection into the
 * terminal session (lost write, C3-014) — red. Post-fix: green.
 */
public class TestDefaultAgentEngineInjectionWindow {

    private static DefaultAgentEngine newEngine(InMemorySessionStore store) {
        return new DefaultAgentEngine.Builder(null, null).sessionStore(store).build();
    }

    /**
     * red①(a): N-thread × injectInput pressure on the staged branch — no CME,
     * no lost injection (bounded by MAX_PENDING_INJECTIONS).
     */
    @Test
    void concurrentStagedInjections_noCmeNoLoss() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(store);
        try {
            store.getOrCreate("s-inj-pressure", "agent");
            // total must stay below AgentSession.MAX_PENDING_INJECTIONS (256):
            // the cap is a legitimate back-pressure bound, not a loss — the
            // pressure window stays inside it and asserts zero loss within bound
            int threads = 8;
            int perThread = 30;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int id = t;
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(30, TimeUnit.SECONDS));
                    for (int i = 0; i < perThread; i++) {
                        engine.injectInput("s-inj-pressure",
                                new io.nop.ai.api.chat.messages.ChatUserMessage("msg-" + id + "-" + i),
                                InjectionSemantics.FOLLOWUP);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
            pool.shutdown();
            AgentSession session = store.get("s-inj-pressure");
            assertEquals(threads * perThread, session.getPendingInjections().size(),
                    "every staged injection must land (defect state: bare ArrayList drops/torns under pressure)");
        } finally {
            engine.close();
        }
    }

    /**
     * red①(b): deterministic inject-after-terminal interleave — an injection
     * arriving after the terminal marker is explicitly rejected (counter
     * increments) and never staged (defect state red: silent lost write).
     */
    @Test
    void injectAfterTerminal_isExplicitlyRejected() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = newEngine(store);
        try {
            AgentSession session = store.getOrCreate("s-inj-terminal", "agent");
            // Simulate the terminal path's in-lock step (1): marker set.
            session.markTerminal();
            long before = DefaultAgentEngine.injectAfterTerminalCount();
            engine.injectInput("s-inj-terminal",
                    new io.nop.ai.api.chat.messages.ChatUserMessage("late"),
                    InjectionSemantics.FOLLOWUP);
            assertEquals(0, session.getPendingInjections().size(),
                    "post-terminal injection must NOT be staged (C3-014 lost-write closure)");
            assertEquals(before + 1, DefaultAgentEngine.injectAfterTerminalCount(),
                    "post-terminal rejection must be observable (nop.ai.agent.inject-after-terminal)");
        } finally {
            engine.close();
        }
    }

    /**
     * red①(c): atomic drain — drainPendingInjections snapshots and clears in
     * one critical section; a concurrent add either lands before the drain
     * (collected) or after (staged for the next execution) — never dropped.
     */
    @Test
    void drainIsAtomic_noInjectionFallsThroughDrain() throws Exception {
        AgentSession session = AgentSession.create("s-drain", "agent");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> adder = pool.submit(() -> {
            assertTrue(start.await(30, TimeUnit.SECONDS));
            int added = 0;
            for (int i = 0; i < 500; i++) {
                try {
                    session.addPendingInjection("FOLLOWUP", "{\"content\":\"x\"}");
                    added++;
                } catch (IllegalStateException overflow) {
                    // bounded carrier — overflow beyond MAX is the documented
                    // rejection, not a loss facet of this test
                }
            }
            return added;
        });
        Future<List<AgentSession.PendingInjection>> drainer = pool.submit(() -> {
            assertTrue(start.await(30, TimeUnit.SECONDS));
            List<AgentSession.PendingInjection> all = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                all.addAll(session.drainPendingInjections());
                Thread.yield();
            }
            return all;
        });
        start.countDown();
        int added = adder.get(60, TimeUnit.SECONDS);
        List<AgentSession.PendingInjection> drained = drainer.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        int remaining = session.getPendingInjections().size();
        assertEquals(added, drained.size() + remaining,
                "atomic drain: every added injection is either drained or still staged — never dropped");
    }
}
