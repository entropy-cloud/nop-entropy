package io.nop.job.local;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.job.api.JobDetail;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.46 R4-06 (plan 2539) W-01 / 1.4-MA-02 — driver 红① (probability leg): the
 * observation read path {@code getJobDetail} must return a MONOTONIC, non-torn
 * snapshot. A writer drives real executions (fireNow + awaitIdle ≥ 200 rounds) while
 * a sampler thread concurrently samples {@code getJobDetail} (≥ 1000 samples);
 * {@code execCount} must never go backwards. Per the design adjudication this leg is
 * DECLARED NON-DETERMINISTIC (JMM stale-read windows are timing dependent) — the
 * deterministic red evidence is the mutation leg recorded in the red-green ledger
 * (restore the unlocked direct read → pin red → re-fix → green).
 */
public class TestLocalJobDetailConsistencyR406 {

    private LocalJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> noopInvoker());
        scheduler.activate();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.deactivate();
        }
    }

    private static IJobInvoker noopInvoker() {
        return new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };
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
     * 红① structure leg (deterministic carrier, 2538 红⑧ precedent): getJobDetail must
     * acquire the per-job monitor — asserted against the MAIN SOURCE (the behavioral
     * probability leg alone showed no检出力 even under mutation: 64-bit long reads do
     * not tear and the CHM volatile reference chain makes stale-read windows rare).
     */
    @Test
    public void getJobDetailAcquiresJobMonitorStructurePin() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get("src", "main", "java", "io", "nop",
                "job", "local", "LocalJobScheduler.java");
        if (!java.nio.file.Files.exists(path)) {
            path = java.nio.file.Paths.get("nop-job", "nop-job-local", "src", "main", "java",
                    "io", "nop", "job", "local", "LocalJobScheduler.java");
        }
        assertTrue(java.nio.file.Files.exists(path), "LocalJobScheduler.java must be locatable");
        String src = new String(java.nio.file.Files.readAllBytes(path),
                java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("public JobDetail getJobDetail\\(String jobName\\) \\{[\\s\\S]*?\\n    \\}")
                .matcher(src);
        assertTrue(m.find(), "getJobDetail method must exist");
        String body = m.group();
        assertTrue(body.contains("synchronized (job)"),
                "getJobDetail must read toJobDetail() under the per-job monitor (W-01) — "
                        + "body was: " + body);
    }

    @Test
    public void concurrentSamplingObservesMonotonicExecCount() throws Exception {
        scheduler.addJob(periodicSpec("snap-probe", 60000L), false);
        // the fixed-delay trigger fires IMMEDIATELY on add — first round settles
        assertTrue(scheduler.awaitIdle("snap-probe", 10, TimeUnit.SECONDS),
                "initial immediate execution must settle");

        CountDownLatch samplerDone = new CountDownLatch(1);
        AtomicReference<Throwable> samplerFailure = new AtomicReference<>();
        Thread sampler = new Thread(() -> {
            long lastExecCount = Long.MIN_VALUE;
            try {
                for (int i = 0; i < 1000; i++) {
                    JobDetail detail = scheduler.getJobDetail("snap-probe");
                    if (detail != null && detail.getInstanceState() != null) {
                        long execCount = detail.getInstanceState().getExecCount();
                        // consistency oracle: the counter snapshot must never go
                        // backwards (the spec+counters come from ONE monitor-critical
                        // section post-fix)
                        assertTrue(execCount >= lastExecCount,
                                "execCount regressed: " + lastExecCount + " -> " + execCount
                                        + " (torn/stale snapshot read)");
                        lastExecCount = execCount;
                    }
                }
            } catch (Throwable t) {
                samplerFailure.set(t);
            } finally {
                samplerDone.countDown();
            }
        });
        sampler.start();

        // writer: ≥200 REAL triggered rounds, each awaiting completion (fireCount++
        // happens under the per-job monitor in handleResult).
        // fireNow is rejected by design while the job is already running: under a
        // loaded -T 1C reactor the initial immediately-scheduled fire can start
        // between awaitIdle() and the first fireNow() call. Retry briefly rather
        // than turning that scheduling race into a false failure; each round still
        // performs exactly one accepted fire.
        for (int round = 0; round < 200; round++) {
            boolean accepted = false;
            for (int attempt = 0; attempt < 50 && !accepted; attempt++) {
                accepted = scheduler.fireNow("snap-probe");
                if (!accepted) {
                    assertTrue(scheduler.awaitIdle("snap-probe", 1, TimeUnit.SECONDS),
                            "job never settled while waiting to fire round " + round);
                }
            }
            assertTrue(accepted, "fireNow must be accepted at round " + round);
            assertTrue(scheduler.awaitIdle("snap-probe", 10, TimeUnit.SECONDS),
                    "round " + round + " did not settle");
        }

        assertTrue(samplerDone.await(30, TimeUnit.SECONDS), "sampler must finish");
        if (samplerFailure.get() != null) {
            throw new AssertionError("consistency oracle violated", samplerFailure.get());
        }
        // and the counter actually moved (the writer really wrote ≥200 increments)
        JobDetail detail = scheduler.getJobDetail("snap-probe");
        assertTrue(detail.getInstanceState().getExecCount() >= 200,
                "writer rounds must be reflected in execCount, got "
                        + detail.getInstanceState().getExecCount());
        sampler.join(5000);
        if (sampler.isAlive()) {
            fail("sampler thread must terminate");
        }
    }
}
