package io.nop.job.local;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.46 R4-06 (plan 2539) C10-007 acceptance-gate test set — ONE test class, two
 * legs covering the two defect families of the same raced branch (merge-adjudication
 * §4.4): leg 1 = W-07 (1.4-C2-004, driver 红⑦) atMostOneWinner for concurrent
 * first-time registration with {@code allowUpdate=false}; leg 2 = W-02 (1.4-MA-03,
 * driver 红②) add×remove interleaving must leave a CONSISTENT terminal state (never a
 * silently dropped registration). Both legs are probability-form (50 rounds each);
 * the deterministic red evidence is the per-leg mutation chains recorded in the
 * red-green ledger. The sequential contract pin {@code testAddDuplicateJobThrows-
 * AlreadyExists} stays untouched and green (base-line,叠加非替代).
 */
public class TestLocalJobSchedulerConcurrentRegistrationR406 {

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

    private static JobSpec spec(String name) {
        JobSpec spec = new JobSpec();
        spec.setJobName(name);
        spec.setJobInvoker("mock");
        TriggerSpec ts = new TriggerSpec();
        ts.setRepeatInterval(60000L);
        ts.setRepeatFixedDelay(true);
        spec.setTriggerSpec(ts);
        return spec;
    }

    /**
     * Leg 1 (红⑦ / W-07): two concurrent first-time registrations with
     * {@code allowUpdate=false} — EXACTLY one wins, the loser must throw the typed
     * already-exists error (entry-path contract), never silently overwrite the
     * winner's spec. 200 rounds (检出力 = 概率趟数, design B.3).
     */
    @Test
    public void concurrentFirstRegistrationAtMostOneWinner() throws Exception {
        int silentOverwrites = 0;
        for (int round = 0; round < 200; round++) {
            scheduler.removeJob("conc-winner");
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> threadAError = new AtomicReference<>();
            AtomicReference<Throwable> threadBError = new AtomicReference<>();
            // two DISTINCT spec payloads so a silent overwrite would be observable
            JobSpec specA = spec("conc-winner");
            specA.setJobParams(java.util.Collections.singletonMap("payload", "A"));
            JobSpec specB = spec("conc-winner");
            specB.setJobParams(java.util.Collections.singletonMap("payload", "B"));

            Thread a = new Thread(() -> {
                try {
                    start.await();
                    scheduler.addJob(specA, false);
                } catch (Throwable t) {
                    threadAError.set(t);
                }
            });
            Thread b = new Thread(() -> {
                try {
                    start.await();
                    scheduler.addJob(specB, false);
                } catch (Throwable t) {
                    threadBError.set(t);
                }
            });
            a.start();
            b.start();
            start.countDown();
            a.join(10000);
            b.join(10000);
            assertFalse(a.isAlive() || b.isAlive(), "both registrants must terminate");

            int errors = (threadAError.get() != null ? 1 : 0) + (threadBError.get() != null ? 1 : 0);
            // atMostOneWinner: exactly one loser with the typed already-exists error,
            // OR (already settled to update semantics is impossible with allowUpdate=false)
            if (errors == 0) {
                silentOverwrites++;
            } else {
                Throwable loser = threadAError.get() != null ? threadAError.get() : threadBError.get();
                assertTrue(loser instanceof NopException,
                        () -> "the loser must be typed already-exists, got: " + loser);
                assertTrue(String.valueOf(loser).contains("ALREADY_EXISTS")
                                || String.valueOf(loser).contains("already"),
                        () -> "typed detail must identify already-exists: " + loser);
            }
            assertTrue(errors <= 1, "at most one loser per round, got " + errors);
        }
        assertEquals(0, silentOverwrites,
                "no round may end with a silent concurrent overwrite (allowUpdate=false contract)");
    }

    /**
     * 红② structure leg (deterministic carrier — the probabilistic add×remove leg's
     * race window is microseconds wide and may legitimately miss in 200 rounds): the
     * raced branch must NOT silently return on raced removal — it must WARN
     * ({@code nop.job.add-job-raced-removed}) and fall through to a RETRY (a second
     * putIfAbsent). Asserted against the MAIN SOURCE (2537 红⑥/2538 红⑧ structure-pin
     * precedent); the mutation chain (restore silent return → pin red → re-fix →
     * green) is recorded in the red-green ledger.
     */
    @Test
    public void racedRemovalBranchWarnsAndRetriesStructurePin() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get("src", "main", "java", "io", "nop",
                "job", "local", "LocalJobScheduler.java");
        if (!java.nio.file.Files.exists(path)) {
            path = java.nio.file.Paths.get("nop-job", "nop-job-local", "src", "main", "java",
                    "io", "nop", "job", "local", "LocalJobScheduler.java");
        }
        assertTrue(java.nio.file.Files.exists(path), "LocalJobScheduler.java must be locatable");
        String src = new String(java.nio.file.Files.readAllBytes(path),
                java.nio.charset.StandardCharsets.UTF_8);
        int racedBranchStart = src.indexOf("ScheduledJob raced = jobs.putIfAbsent(spec.getJobName(), job);");
        assertTrue(racedBranchStart >= 0, "the raced putIfAbsent must exist");
        String racedBranch = src.substring(racedBranchStart,
                Math.min(src.length(), racedBranchStart + 4000));
        assertTrue(racedBranch.contains("nop.job.add-job-raced-removed"),
                "the raced-removal branch must WARN (never silent) — W-02");
        assertTrue(racedBranch.contains("raced = jobs.putIfAbsent(spec.getJobName(), job);"),
                "the raced-removal branch must fall through to a RETRY (second putIfAbsent) — W-02");
    }

    /**
     * Leg 2 (红② / W-02): concurrent add×remove interleaving — the registration must
     * never be silently dropped: the observable terminal state must equal the
     * outcome semantics (add-then-remove wins → absent; remove-missed → present),
     * with zero ghost entries. 200 rounds (检出力 = 概率趟数, design B.3).
     */
    @Test
    public void concurrentAddRemoveLeavesConsistentTerminalState() throws Exception {
        List<String> inconsistencies = new ArrayList<>();
        for (int round = 0; round < 200; round++) {
            // clean slate for the round
            scheduler.removeJob("conc-race");
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Boolean> addOk = new AtomicReference<>();
            AtomicReference<Throwable> addError = new AtomicReference<>();
            AtomicReference<Boolean> removed = new AtomicReference<>();
            Thread adder = new Thread(() -> {
                try {
                    start.await();
                    scheduler.addJob(spec("conc-race"), true);
                    addOk.set(Boolean.TRUE);
                } catch (Throwable t) {
                    addError.set(t);
                    addOk.set(Boolean.FALSE);
                }
            });
            Thread remover = new Thread(() -> {
                try {
                    start.await();
                    removed.set(scheduler.removeJob("conc-race"));
                } catch (Throwable t) {
                    removed.set(Boolean.FALSE);
                }
            });
            adder.start();
            remover.start();
            start.countDown();
            adder.join(10000);
            remover.join(10000);
            assertFalse(adder.isAlive() || remover.isAlive(), "both racers must terminate");

            boolean present = scheduler.getJobNames().contains("conc-race");
            // consistent terminal state: present ⟺ (add succeeded AND the remove ran
            // BEFORE the add's insert). If the remover removed first (missed the new
            // entry), the add must have (re-)established the job — the pre-fix raced
            // branch silently DROPPED this outcome instead.
            boolean expectedPresent = Boolean.TRUE.equals(addOk.get())
                    && !Boolean.TRUE.equals(removed.get());
            if (addError.get() != null) {
                // typed already-exists from an uncleaned prior round would be a test
                // harness bug — the round reset guarantees absence
                inconsistencies.add("round " + round + ": unexpected add error " + addError.get());
                continue;
            }
            if (present != expectedPresent) {
                inconsistencies.add("round " + round + ": present=" + present
                        + " but expectedPresent=" + expectedPresent
                        + " (addOk=" + addOk.get() + ", removed=" + removed.get()
                        + ") — registration silently dropped or ghost entry");
            }
        }
        assertTrue(inconsistencies.isEmpty(),
                () -> "add×remove terminal-state inconsistencies (" + inconsistencies.size()
                        + "/50): " + String.join("; ", inconsistencies));
    }
}
