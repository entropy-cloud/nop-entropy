package io.nop.ai.agent.tool;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 (C4-010 / 裁定③) focused regression: the spawn orchestration pool
 * lifecycle is owned by the {@link TeamExecuteFlowExecutor} (TEE) singleton —
 * the per-invocation orchestrator consumes the TEE-shared executor through the
 * existing {@code setSpawnStepExecutor} seam and never builds or closes its
 * own pool on this path. The pre-fix defect state (per-invocation pool build,
 * no close) leaked max(2, NCPU) threads per team-execute-flow invocation.
 *
 * <p>Pins (新增断言即新钉 — defect state red, fix green):
 * <ul>
 *   <li>{@link #sharedExecutorIdentityAcrossInvocations} — K successive
 *       resolve calls return the SAME pool instance (defect state: a fresh
 *       pool per resolve — RED by identity mismatch).</li>
 *   <li>{@link #spawnWorkerThreadCountConservedAcrossInvocations} — after K
 *       invocations each forcing a full parallel round, the number of distinct
 *       {@code ai-agent-flow-spawn-worker-*} threads stays within the single
 *       pool capacity (defect state: K independent pools → thread count grows
 *       linearly past one pool capacity — RED).</li>
 *   <li>{@link #closeDrainsOwnedPoolToTerminalState} — after {@code close()},
 *       the owned pool is shut down AND terminated (defect state: close wiring
 *       removed — RED: pool still running); repeated close is idempotent.</li>
 *   <li>{@link #closeLeavesInjectedExecutorUntouched} — an executor injected
 *       via {@code setSpawnStepExecutor} is owned by its injector; close()
 *       must NOT shut it down (ownership boundary pin).</li>
 * </ul>
 */
public class TestTeamExecuteFlowExecutorPoolLifecycle {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static int poolCapacity() {
        return Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    /**
     * The TEE resolves the SAME shared pool across K successive invocations —
     * the structural anti-leak invariant. Defect state (per-invocation pool
     * build, pre-C4-010): each resolve returns a DISTINCT pool → RED here.
     */
    @Test
    void sharedExecutorIdentityAcrossInvocations() {
        TeamExecuteFlowExecutor tee = new TeamExecuteFlowExecutor();
        int k = 8;
        Object first = tee.resolveSpawnExecutor();
        for (int i = 1; i < k; i++) {
            assertSame(first, tee.resolveSpawnExecutor(),
                    "resolveSpawnExecutor must return the TEE-shared pool on every invocation "
                            + "(per-invocation pool build is the C4-010 leak defect — RED if distinct)");
        }
    }

    /**
     * Thread-count conservation (red③ driver, design §5.1): K invocations
     * each submit one full parallel round of blocking work; the total number
     * of distinct spawn worker threads must stay within ONE pool capacity.
     * Defect state: K independent pools spin up K distinct workers — the
     * count grows linearly past the capacity bound → RED.
     */
    @Test
    void spawnWorkerThreadCountConservedAcrossInvocations() throws Exception {
        TeamExecuteFlowExecutor tee = new TeamExecuteFlowExecutor();
        int capacity = poolCapacity();
        int k = capacity * 3; // far beyond one pool capacity

        Set<String> workerThreads = ConcurrentHashMap.newKeySet();
        for (int round = 0; round < k; round++) {
            ExecutorService pool = (ExecutorService) tee.resolveSpawnExecutor();
            // Force this pool to actually spin up its worker: submit a task
            // that records its thread name and blocks until released.
            CountDownLatch roundHold = new CountDownLatch(1);
            Future<?> f = pool.submit(() -> {
                String name = Thread.currentThread().getName();
                if (name.startsWith("ai-agent-flow-spawn-worker-")) {
                    workerThreads.add(name);
                }
                try {
                    roundHold.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            roundHold.countDown();
            f.get(5, TimeUnit.SECONDS);
        }

        assertTrue(workerThreads.size() <= capacity,
                "spawn worker thread count must be conserved within one pool capacity ("
                        + capacity + ") across " + k + " invocations; observed "
                        + workerThreads.size() + " distinct workers " + workerThreads
                        + " — linear growth is the C4-010 per-invocation leak defect (RED)");
    }

    /**
     * close() drives the owned pool to its terminal state (shutdown +
     * terminated) — the C4-010 close wiring. Defect state (close wiring
     * removed): the pool keeps running → RED. Repeated close is idempotent.
     */
    @Test
    void closeDrainsOwnedPoolToTerminalState() {
        TeamExecuteFlowExecutor tee = new TeamExecuteFlowExecutor();
        ExecutorService pool = (ExecutorService) tee.resolveSpawnExecutor();
        assertFalse(pool.isShutdown(), "an open TEE must own a live pool");

        tee.close();
        assertTrue(pool.isShutdown(), "close() must shut down the owned spawn pool "
                + "(close wiring removed is the defect state — RED)");
        assertTrue(pool.isTerminated(), "close() must leave the owned pool TERMINATED "
                + "(bounded drain completed within the 2s budget)");

        // Idempotent close: a second close on a closed TEE is a no-op, no throw.
        tee.close();
        assertTrue(pool.isTerminated(), "state unchanged after idempotent close");
    }

    /**
     * Ownership boundary: an executor injected via setSpawnStepExecutor is
     * owned by its injector — close() must leave it untouched (orchestrator
     * contract "injected executor: lifecycle owned by the injector").
     */
    @Test
    void closeLeavesInjectedExecutorUntouched() throws Exception {
        TeamExecuteFlowExecutor tee = new TeamExecuteFlowExecutor();
        AtomicInteger counter = new AtomicInteger();
        ExecutorService injected = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "tee-pool-lifecycle-test-injected-"
                    + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        tee.setSpawnStepExecutor(injected);
        assertSame(injected, tee.resolveSpawnExecutor(),
                "an injected executor wins over the TEE-owned pool");

        tee.close();
        assertFalse(injected.isShutdown(),
                "close() must NOT shut down an injected executor (injector owns its lifecycle)");
        assertTrue(tee.resolveSpawnExecutor() != null,
                "the injected executor is still resolvable after close");

        // Clean up the test-owned executor.
        injected.shutdownNow();
        assertTrue(injected.awaitTermination(2, TimeUnit.SECONDS));
    }

    /**
     * Queued work at close is DISCARDED, never silently executed: tasks
     * queued behind the drain budget must NOT run after close() (the stop
     * semantics — discard is designed, silent EXECUTION after stop is the
     * defect), and the pool must reach its terminal state. The dispatch
     * future settle face of the discard lives at the coordinator drain point
     * (TestSpawnPoolDrain). The discard itself is explicitly recorded
     * (nop.ai.agent.spawn-drain-discard WARN, rule 24). Defect state (close
     * wiring removed): queued tasks run after close → RED.
     */
    @Test
    void queuedTasksAtCloseAreDiscardedObservablyNotSilently() throws Exception {
        TeamExecuteFlowExecutor tee = new TeamExecuteFlowExecutor();
        ExecutorService pool = (ExecutorService) tee.resolveSpawnExecutor();
        int capacity = poolCapacity();
        java.util.Set<String> ranAfterClose = ConcurrentHashMap.newKeySet();
        List<Future<?>> queued = new ArrayList<>();
        CountDownLatch hold = new CountDownLatch(1);
        for (int i = 0; i < capacity + 3; i++) {
            final boolean saturating = i < capacity;
            queued.add(pool.submit(() -> {
                try {
                    hold.await(saturating ? 8 : 0, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                // Only reachable when the latch opened AND the task was
                // allowed to run past the close — the silent-execution defect.
                ranAfterClose.add(Thread.currentThread().getName());
            }));
        }
        Thread.sleep(200); // let the saturating workers start

        tee.close(); // bounded drain exhausts the 2s budget against the latch
        hold.countDown();

        // The pool reached its terminal state despite the saturated workers
        // (shutdownNow interrupt path; termination is async after the budget
        // expired — poll within a bound).
        long terminalDeadline = System.currentTimeMillis() + 5_000;
        while (!pool.isTerminated() && System.currentTimeMillis() < terminalDeadline) {
            Thread.sleep(10);
        }
        assertTrue(pool.isTerminated(),
                "close() must drive the owned pool to termination even with queued work");

        // Queued (never-started) work was discarded: nothing executes after
        // the close. Defect state (close wiring removed): the queued tasks run
        // once the latch opens → RED.
        long observeDeadline = System.currentTimeMillis() + 1_000;
        while (ranAfterClose.isEmpty() && System.currentTimeMillis() < observeDeadline) {
            Thread.sleep(10);
        }
        assertTrue(ranAfterClose.isEmpty(),
                "queued spawn work must be discarded at close, never silently executed "
                        + "after the stop (executed after close: " + ranAfterClose + " — RED)");
        // The saturating workers settled (interrupted or released).
        long settleDeadline = System.currentTimeMillis() + 5_000;
        boolean allSettled;
        do {
            allSettled = true;
            for (Future<?> f : queued.subList(0, capacity)) {
                if (!f.isDone()) {
                    allSettled = false;
                    break;
                }
            }
            if (allSettled) {
                break;
            }
            Thread.sleep(10);
        } while (System.currentTimeMillis() < settleDeadline);
        for (int i = 0; i < capacity; i++) {
            assertTrue(queued.get(i).isDone(),
                    "the running workers must settle after close (task[" + i + "] pending — RED)");
        }
    }
}
