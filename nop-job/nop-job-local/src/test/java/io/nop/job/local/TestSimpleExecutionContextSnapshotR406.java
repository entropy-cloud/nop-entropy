package io.nop.job.local;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.46 R4-06 (plan 2539) W-03 / 1.4-MA-04 — Proof-type closure-verification pin
 * (design adjudication A.2): the 2513 ctx true-snapshot refactor CLOSED the audited
 * escape (constructor-time defensive params copy + final state counters + zero live
 * references). This pin freezes that closure as a contract:
 * <ul>
 *   <li>structure leg (缝5 formalized): reflection asserts SimpleExecutionContext
 *       declares NO field of type ScheduledJob / SimpleJobState / JobSpec — a future
 *       regression back to live references goes red;</li>
 *   <li>behavior leg: mutating the snapshot's params Map must NOT tear through to
 *       the spec, and a second trigger's snapshot must not see the first trigger's
 *       mutation.</li>
 * </ul>
 * Current-as-green by construction (2513 already closed it); the red evidence is the
 * mutation trial recorded in the red-green ledger (scratch trial restoring the
 * live-reference shape → both legs red → re-fix → green, not committed to the repo).
 */
public class TestSimpleExecutionContextSnapshotR406 {

    private LocalJobScheduler scheduler;
    private final AtomicReference<IJobExecutionContext> lastCtx = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        scheduler = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                lastCtx.set(ctx);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        });
        scheduler.activate();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.deactivate();
        }
    }

    /** 结构腿（缝5 formalized）: zero live-reference fields on the snapshot class. */
    @Test
    public void snapshotClassDeclaresNoLiveReferenceFields() throws Exception {
        Class<?> snapshotClass = Class.forName("io.nop.job.local.LocalJobScheduler$SimpleExecutionContext");
        int liveRefs = 0;
        for (Field field : snapshotClass.getDeclaredFields()) {
            Class<?> type = field.getType();
            boolean isLiveRef = type.getSimpleName().equals("ScheduledJob")
                    || type.getSimpleName().equals("SimpleJobState")
                    || type.getSimpleName().equals("JobSpec");
            assertTrue(!isLiveRef,
                    "SimpleExecutionContext must not hold live references (缝5, 2513 裁定③) — found: "
                            + field.getName() + " of type " + type.getSimpleName());
            if (!isLiveRef) {
                continue;
            }
            liveRefs++;
        }
        assertEquals(0, liveRefs, "zero live-reference fields expected");
    }

    /** 行为腿: params snapshot isolates from the spec and across triggers. */
    @Test
    public void paramsSnapshotIsolatesFromSpecAndAcrossTriggers() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("k1", "v1");
        JobSpec spec = new JobSpec();
        spec.setJobName("snap-params");
        spec.setJobInvoker("mock");
        spec.setJobParams(params);
        TriggerSpec ts = new TriggerSpec();
        ts.setRepeatInterval(60000L);
        ts.setRepeatFixedDelay(true);
        spec.setTriggerSpec(ts);
        scheduler.addJob(spec, false);

        // first trigger: PeriodicTrigger fires ~immediately on add — await the
        // COMPLETED execution (awaitIdle alone races the 1ms timer delay)
        assertTrue(scheduler.awaitFireCount("snap-params", 1, 10, TimeUnit.SECONDS),
                "the first (timer-driven) execution must complete");
        assertTrue(scheduler.awaitIdle("snap-params", 10, TimeUnit.SECONDS));
        IJobExecutionContext first = lastCtx.get();
        assertNotNull(first, "first execution must capture the ctx snapshot");
        assertEquals("v1", first.getJobParams().get("k1"));

        // mutate the SNAPSHOT's params map — the SPEC must stay untouched
        first.getJobParams().put("injected", "x");

        // second trigger: its snapshot must not see the first trigger's mutation,
        // and the spec must not see it either
        assertTrue(scheduler.fireNow("snap-params"));
        assertTrue(scheduler.awaitIdle("snap-params", 10, TimeUnit.SECONDS));
        IJobExecutionContext second = lastCtx.get();
        assertNotNull(second);
        assertFalse(second.getJobParams().containsKey("injected"),
                "the second trigger's snapshot must not inherit the first trigger's mutation");
        assertFalse(spec.getJobParams().containsKey("injected"),
                "mutation of the snapshot map must not tear through to the spec params");
        assertEquals("v1", second.getJobParams().get("k1"),
                "the snapshot still carries the declared params");
    }
}
