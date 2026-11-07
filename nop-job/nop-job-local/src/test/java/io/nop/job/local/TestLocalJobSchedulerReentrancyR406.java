package io.nop.job.local;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.job.api.JobState;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.46 R4-06 (plan 2539) W-08 / 1.4-C2-007 residual + C10-004 acceptance gate —
 * the three-anchor set (design adjudication C.2):
 * <ul>
 *   <li>(i) self-reentry rejection regression anchor (green on landing — guards the
 *       2513 placeholder-first face): {@code fireNow} on the SAME job from INSIDE its
 *       own execution must be rejected (running placeholder), never recurse;</li>
 *   <li>(ii) execution-time RUNNING visibility anchor (green on landing): the job
 *       state reads RUNNING from within its own execution and awaitIdle correctly
 *       waits (occupied by self);</li>
 *   <li>(iii) Error injection leg (THE red→green pin, driver 红⑧): a synchronous
 *       {@link TestLocalJobSchedulerReentrancyR406.TestInjectedError} from
 *       {@code invokeAsync} must terminate the job FAILED through the existing
 *       settleExecution channel — pre-fix it escaped the two-phase handoff and the
 *       job was silently stuck RUNNING (no FAILED, awaitIdle never idled, no
 *       rescheduling, zero module log).</li>
 * </ul>
 * MJ-1 green-leg predicate: FAILED is TERMINAL in local mode — the failed job must
 * NOT self-heal (fireCount constant, fireNow always refuses); self-heal belongs to
 * C7-006/R4-07. The scheduling CHAIN must stay alive (an independent control job
 * keeps firing).
 */
public class TestLocalJobSchedulerReentrancyR406 {

    /** controlled injection Error (design C.2: custom Error, NOT StackOverflowError) */
    static final class TestInjectedError extends Error {
        TestInjectedError(String message) {
            super(message);
        }
    }

    private LocalJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> null);
        scheduler.activate();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.deactivate();
        }
    }

    private static JobSpec periodicSpec(String name, long interval) {
        JobSpec spec = new JobSpec();
        spec.setJobName(name);
        spec.setJobInvoker("mock");
        TriggerSpec ts = new TriggerSpec();
        ts.setRepeatInterval(interval);
        ts.setRepeatFixedDelay(true);
        spec.setTriggerSpec(ts);
        return spec;
    }

    /**
     * Anchor (i): fireNow on the SAME job from inside its own execution → rejected
     * (false), no recursion/SOE; the job stays alive for later manual fires.
     */
    @Test
    public void anchorI_selfReentryRejectedNoRecursion() throws Exception {
        AtomicBoolean inExecution = new AtomicBoolean(false);
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<Boolean> reentryResult = new AtomicReference<>();
        AtomicInteger executionsAtReentry = new AtomicInteger();

        scheduler = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                if (inExecution.compareAndSet(false, true)) {
                    executions.incrementAndGet();
                    try {
                        // THE reentry probe: fire the SAME job from inside itself
                        executionsAtReentry.set(executions.get());
                        reentryResult.set(scheduler.fireNow(ctx.getJobName()));
                    } finally {
                        inExecution.set(false);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        });
        scheduler.activate();

        scheduler.addJob(periodicSpec("reent", 3600000L), false);
        // PeriodicTrigger fires the FIRST execution ~immediately on add — that timer
        // execution runs the reentry probe; wait for it to complete (avoid racing the
        // immediate timer fire with the manual fires below)
        assertTrue(scheduler.awaitFireCount("reent", 1, 10, TimeUnit.SECONDS),
                "the first (timer-driven) execution must complete");
        assertTrue(scheduler.awaitIdle("reent", 10, TimeUnit.SECONDS), "first execution must settle");

        assertEquals(1, executionsAtReentry.get(), "reentry probe ran inside the FIRST execution");
        assertEquals(Boolean.FALSE, reentryResult.get(),
                "self-reentry fireNow must be rejected by the running placeholder");
        assertEquals(1, executions.get(),
                "the rejected reentry must NOT have executed a nested invocation");

        // the job is still alive: a LATER manual fire is accepted again
        assertTrue(scheduler.fireNow("reent"), "post-completion fireNow must be accepted");
        assertTrue(scheduler.awaitIdle("reent", 10, TimeUnit.SECONDS));
        assertEquals(2, executions.get(), "second manual fire executes");
    }

    /**
     * Anchor (ii): execution-time RUNNING visibility + awaitIdle correctly waits
     * while the job occupies its own running placeholder.
     */
    @Test
    public void anchorII_executionTimeRunningVisibleAwaitIdleWaits() throws Exception {
        AtomicReference<JobState> observedState = new AtomicReference<>();
        AtomicReference<Boolean> selfAwaitIdle = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch observed = new CountDownLatch(1);

        scheduler = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                started.countDown();
                try {
                    observedState.set(scheduler.getJobState(ctx.getJobName()));
                    // awaitIdle from INSIDE the execution: correctly WAITING (timeout),
                    // never immediately idle — the running placeholder occupies the job
                    selfAwaitIdle.set(scheduler.awaitIdle(ctx.getJobName(), 300, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    selfAwaitIdle.set(Boolean.FALSE);
                } finally {
                    observed.countDown();
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        });
        scheduler.activate();

        scheduler.addJob(periodicSpec("vis", 3600000L), false);
        // the immediate timer-driven first execution IS the probe driver — no manual
        // fireNow (it would race the immediate fire on the shared scheduler)
        assertTrue(observed.await(10, TimeUnit.SECONDS), "invoker must run");
        assertTrue(scheduler.awaitIdle("vis", 10, TimeUnit.SECONDS), "execution must settle");

        assertEquals(JobState.RUNNING, observedState.get(),
                "the running placeholder must be visible DURING execution (2513 anchor)");
        assertEquals(Boolean.FALSE, selfAwaitIdle.get(),
                "awaitIdle from within the execution must correctly wait (timeout), not false-idle");
    }

    /**
     * Anchor (iii) — 红⑧ body (C10-004 same diff): synchronous injected Error from
     * invokeAsync → FAILED terminal state through the existing settleExecution
     * channel; scheduling chain alive; no self-heal (MJ-1 predicate).
     */
    @Test
    public void anchorIII_injectedErrorTerminatesFailedChainAliveNoSelfHeal() throws Exception {
        AtomicInteger errorJobExecutions = new AtomicInteger();
        AtomicInteger controlExecutions = new AtomicInteger();

        scheduler = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> {
            if ("errInvoker".equals(name)) {
                return new IJobInvoker() {
                    @Override
                    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                        errorJobExecutions.incrementAndGet();
                        // synchronous throw BEFORE returning any stage
                        throw new TestInjectedError("r406 controlled injection");
                    }

                    @Override
                    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                        return CompletableFuture.completedFuture(true);
                    }
                };
            }
            return new IJobInvoker() {
                @Override
                public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                    controlExecutions.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                    return CompletableFuture.completedFuture(true);
                }
            };
        });
        scheduler.activate();

        JobSpec errSpec = periodicSpec("errjob", 3600000L);
        errSpec.setJobInvoker("errInvoker");
        scheduler.addJob(errSpec, false);
        scheduler.addJob(periodicSpec("healthy", 20L), false);     // chain-alive control

        // the immediate timer-driven first execution hits the injected Error.
        // pre-fix the Error escapes invokeAsync into the executor insulation (job
        // stuck RUNNING: placeholder never cleared, awaitIdle never idles, fireCount
        // never advances); post-fix it lands FAILED through the settleExecution
        // channel and the placeholder clears.
        assertTrue(scheduler.awaitFireCount("errjob", 1, 10, TimeUnit.SECONDS),
                "the execution must settle (post-fix: fireCount advances even on Error)");
        assertTrue(scheduler.awaitIdle("errjob", 10, TimeUnit.SECONDS),
                "the running placeholder must be cleared (awaitIdle idles post-fix)");

        // MJ-1 predicate: FAILED terminal state, NOT a disguised anything
        assertEquals(JobState.FAILED, scheduler.getJobState("errjob"),
                "an injected Error must terminate the job FAILED (not stuck RUNNING)");
        assertEquals(1, errorJobExecutions.get(), "exactly one attempted execution");

        // no self-heal (MJ-1): fireCount constant, fireNow always refuses
        Thread.sleep(300);
        assertTrue(!scheduler.fireNow("errjob"),
                "fireNow on a FAILED (terminal) job must refuse — no self-heal");
        assertEquals(1, errorJobExecutions.get(),
                "the FAILED job must not re-execute (no self-heal — C7-006/R4-07 owns that)");

        // scheduling chain alive: the independent control job keeps firing
        assertTrue(scheduler.awaitFireCount("healthy", 3, 10, TimeUnit.SECONDS),
                "the scheduling chain must stay alive (control job keeps triggering), got "
                        + controlExecutions.get());
    }

    /**
     * Direct variant of anchor (iii) observing the done future itself (MN-R2-1):
     * the invoker reflects the jobs map while executing, grabs the running
     * placeholder's done future, and completes after returning a failed stage —
     * asserting completeExceptionally was called (done observation).
     */
    @Test
    public void anchorIII_doneFutureCompletesExceptionally() throws Exception {
        AtomicReference<CompletableFuture<?>> doneRef = new AtomicReference<>();
        CountDownLatch doneGrabbed = new CountDownLatch(1);

        scheduler = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                // grab the done future via the jobs map reflection (TestLocalJobScheduler
                // :497 readJobsMap precedent) BEFORE throwing
                try {
                    Field jobsField = LocalJobScheduler.class.getDeclaredField("jobs");
                    jobsField.setAccessible(true);
                    Map<String, ?> jobs = (Map<String, ?>) jobsField.get(scheduler);
                    Object job = jobs.get(ctx.getJobName());
                    assertNotNull(job, "job must be registered during execution");
                    Field runningField = job.getClass().getDeclaredField("running");
                    runningField.setAccessible(true);
                    Object running = runningField.get(job);
                    if (running instanceof CompletableFuture) {
                        doneRef.set((CompletableFuture<?>) running);
                        doneGrabbed.countDown();
                    }
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("r406: done-future reflection failed", e);
                }
                throw new TestInjectedError("r406 controlled injection (done observation)");
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        });
        scheduler.activate();

        scheduler.addJob(periodicSpec("errdone", 3600000L), false);
        assertTrue(doneGrabbed.await(5, TimeUnit.SECONDS), "done future must be grabbed");
        assertTrue(scheduler.awaitIdle("errdone", 10, TimeUnit.SECONDS),
                "placeholder cleared (post-fix settleExecution completes done exceptionally)");

        CompletableFuture<?> done = doneRef.get();
        assertNotNull(done, "the running placeholder held the done future");
        assertTrue(done.isCompletedExceptionally(),
                "the done future must complete exceptionally (Error path)");
    }
}
