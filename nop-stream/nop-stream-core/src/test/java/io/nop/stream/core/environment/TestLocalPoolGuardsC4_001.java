package io.nop.stream.core.environment;

import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * C4-001 / C10-002 (plan 2510 R2-07, adjudication 2): LOCAL pool mutual-wait
 * deadlock elimination guards.
 *
 * <ul>
 *   <li>Guard a (submission-time): a topology whose Σsubtask exceeds the
 *       TaskExecutor pool size fails fast with a typed error
 *       ({@code ERR_STREAM_LOCAL_POOL_EXCEEDED}) BEFORE any task is submitted —
 *       it must never hang until timeout.</li>
 *   <li>Guard b (runtime): {@code runLocal}'s single {@code awaitCompletion}
 *       point waits at most {@code nop.stream.local.await-completion-timeout-ms}
 *       (default 300000, 0 = explicitly disabled); on timeout the execution is
 *       aborted (pool interrupted + typed failure
 *       {@code ERR_STREAM_LOCAL_AWAIT_TIMEOUT}) instead of blocking forever.</li>
 * </ul>
 *
 * <p>Red drivers (design 3.11 seam 2): (a) Σsubtask &gt; poolSize topology backed by
 * an endless source so the unfixed code hangs forever inside
 * {@code awaitCompletion} (test observes a bounded-window timeout = red); the fixed
 * code fails fast (green). (b) small topology + short configured await timeout —
 * the unfixed code ignores the configuration and hangs (red); the fixed code aborts
 * typed (green).
 */
class TestLocalPoolGuardsC4_001 {

    private static final String AWAIT_TIMEOUT_CONFIG =
            "nop.stream.local.await-completion-timeout-ms";

    /** Expected typed error codes (error constants land in NopStreamErrors with the fix). */
    private static final String CODE_POOL_EXCEEDED = "nop.err.stream.local.pool-exceeded";
    private static final String CODE_AWAIT_TIMEOUT = "nop.err.stream.local.await-timeout";
    private static final String CODE_INVALID_ARG = "nop.err.stream.invalid-arg";

    /** Endless source: keeps every subtask busy until interrupted/cancelled. */
    static final class EndlessSource implements io.nop.stream.core.common.functions.source.SourceFunction<Integer> {
        private static final long serialVersionUID = 1L;
        private volatile boolean running = true;

        @Override
        public void run(SourceContext<Integer> ctx) throws Exception {
            int i = 0;
            while (running && !Thread.currentThread().isInterrupted()) {
                ctx.collect(i++);
                // The downstream queue fills; the put blocks this task — the
                // C4-001 mutual-wait shape (all pool threads are producers).
                Thread.yield();
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /**
     * Walks the causal chain looking for a {@link StreamException} with the given
     * error code (execute() wraps failures in ERR_STREAM_JOB_EXECUTE_FAILED).
     */
    private static boolean hasErrorCode(Throwable t, String code) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof StreamException
                    && code.equals(((StreamException) c).getErrorCode())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void c4_001_subtaskTotalExceedingPoolSizeFailsFastTyped() throws Exception {
        // Σsubtask = parallelism(source) + parallelism(sink) = 2 * (cores + 2)
        // which exceeds the fixed pool size (= CPU core count) on any machine.
        int par = Runtime.getRuntime().availableProcessors() + 2;
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(par);
        env.addSource(new EndlessSource(), "c4-001-endless-source")
                .sink(value -> {
                    // consume nothing; the point is that tasks fill the queues
                });

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                env.execute("c4-001-guard-a");
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        }, "c4-001-guard-a-runner");
        runner.start();

        // The fixed code fails fast (well below this window). The unfixed code
        // hangs forever inside awaitCompletion — the bounded window elapses = red.
        assertTrue(done.await(20, TimeUnit.SECONDS),
                "C4-001 red driver: execute() must terminate quickly — Σsubtask > poolSize "
                        + "must fail fast at submission (guard a), never hang in awaitCompletion");

        Throwable t = error.get();
        assertNotNull(t, "guard a must reject the oversized topology");
        assertTrue(hasErrorCode(t, CODE_POOL_EXCEEDED),
                "expected typed ERR_STREAM_LOCAL_POOL_EXCEEDED in the causal chain, got: " + t);
    }

    @Test
    void c4_001_awaitCompletionBoundedWaitAbortsOnTimeout() throws Exception {
        System.setProperty(AWAIT_TIMEOUT_CONFIG, "800");
        try {
            // Σsubtask = 2 <= poolSize: guard a passes; the endless source fills the
            // sink queue so awaitCompletion would block forever without guard b.
            StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
            env.setParallelism(1);
            env.addSource(new EndlessSource(), "c4-001-endless-source-b")
                    .sink(value -> {
                    });

            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread runner = new Thread(() -> {
                try {
                    env.execute("c4-001-guard-b");
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    done.countDown();
                }
            }, "c4-001-guard-b-runner");
            runner.start();

            // Fixed code: aborts typed after ~800ms. Unfixed code: ignores the
            // configuration and hangs forever = the window elapses (red).
            assertTrue(done.await(20, TimeUnit.SECONDS),
                    "C4-001 red driver: bounded await timeout must abort the execution "
                            + "(typed ERR_STREAM_LOCAL_AWAIT_TIMEOUT) instead of hanging forever");

            Throwable t = error.get();
            assertNotNull(t, "guard b must abort with a typed failure on timeout");
            assertTrue(hasErrorCode(t, CODE_AWAIT_TIMEOUT),
                    "expected typed ERR_STREAM_LOCAL_AWAIT_TIMEOUT in the causal chain, got: " + t);
        } finally {
            System.clearProperty(AWAIT_TIMEOUT_CONFIG);
        }
    }

    @Test
    void c4_001_negativeAwaitTimeoutConfigFailsFast() {
        System.setProperty(AWAIT_TIMEOUT_CONFIG, "-1");
        try {
            StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
            env.fromElements("x").sink(value -> {
            });
            try {
                env.execute("c4-001-negative-config");
                fail("negative await-completion timeout must fail fast at assembly");
            } catch (StreamException e) {
                assertTrue(hasErrorCode(e, CODE_INVALID_ARG),
                        "expected typed ERR_STREAM_INVALID_ARG for negative config, got: " + e);
            } catch (Exception e) {
                fail("expected StreamException, got: " + e);
            }
        } finally {
            System.clearProperty(AWAIT_TIMEOUT_CONFIG);
        }
    }
}
