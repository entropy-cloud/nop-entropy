package io.nop.lint.core.xscript;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The install-ownership contract of the lint deadline wrapper (plan 2531
 * W-02, red② — M-P1-2): a lint run owns the global executor slot only for
 * its duration (red②② — the deterministic consequence-face pin), a foreign
 * writer that moved onto the slot during the run is never clobbered by the
 * restore and the residual is logged loudly (red②③, both attach shapes),
 * and interleaved runs against a second writer never stack or lose the
 * ledger (red②①, bounded-round loop).
 */
public class TestLintDeadlineUninstall_W_02 {

    @Test
    public void testW_02_uninstallRestoresThePreInstallSnapshotAndLeavesNoResidue() {
        // red②②: the deterministic consequence-face pin — before the fix the
        // wrapper had no uninstall path at all and stayed installed for the
        // whole JVM lifetime (CL-401: global rerouting, ordering races, a
        // permanent strong reference).
        IExpressionExecutor prior = EvalExprProvider.getGlobalExecutor();
        assertFalse(prior instanceof LintDeadlineExecutor,
                "test precondition: the slot starts wrapper-free");

        LintDeadlineExecutor.install();
        try {
            assertInstanceOfInstalled(EvalExprProvider.getGlobalExecutor());
        } finally {
            LintDeadlineExecutor.uninstall();
        }

        assertSame(prior, EvalExprProvider.getGlobalExecutor(),
                "uninstall must restore the exact pre-install snapshot");
        assertFalse(EvalExprProvider.getGlobalExecutor() instanceof LintDeadlineExecutor,
                "no wrapper residue may survive the run");
    }

    @Test
    public void testW_02_uninstallIsExplicitOnAnUnpairedCall() {
        // rule 24: a caller bug must not pass silently
        assertThrows(NopLintException.class, LintDeadlineExecutor::uninstall,
                "an unpaired uninstall is a caller bug and must fail loudly");
    }

    @Test
    public void testW_02_attachWrappedVariantIsKeptAndTheResidualIsLoud() throws Exception {
        // red②③ variant 1 — attach wraps the current chain top and writes the
        // slot: the restore must keep the foreign owner (never clobber it
        // with the stale snapshot) and log the residual loudly.
        IExpressionExecutor prior = EvalExprProvider.getGlobalExecutor();
        LintDeadlineExecutor.install();
        {
            IExpressionExecutor installed = EvalExprProvider.getGlobalExecutor();
            ForeignExecutor foreign = new ForeignExecutor(installed);
            EvalExprProvider.registerGlobalExecutor(foreign);
            ListAppender<ILoggingEvent> events = attachLogCaptor();
            try {
                LintDeadlineExecutor.uninstall();
                assertSame(foreign, EvalExprProvider.getGlobalExecutor(),
                        "uninstall must never clobber the foreign writer that owns the slot top");
            } finally {
                detachLogCaptor(events);
                EvalExprProvider.registerGlobalExecutor(prior);
            }
            assertTrue(events.list.stream()
                            .anyMatch(e -> e.getFormattedMessage().contains("deadline-executor-residual")),
                    "the residual must be logged loudly, never silently kept");
        }
        // ownership fully released: a fresh generation installs and unwinds
        LintDeadlineExecutor.install();
        try {
            assertInstanceOfInstalled(EvalExprProvider.getGlobalExecutor());
        } finally {
            LintDeadlineExecutor.uninstall();
        }
        assertSame(prior, EvalExprProvider.getGlobalExecutor());
    }

    @Test
    public void testW_02_attachReplacedVariantIsKeptAndTheResidualIsLoud() throws Exception {
        // red②③ variant 2 — the real attach shape: a foreign executor
        // replaces the slot wholesale (the debugger executor has no delegate
        // accessor and wraps nothing).
        IExpressionExecutor prior = EvalExprProvider.getGlobalExecutor();
        LintDeadlineExecutor.install();
        {
            ForeignExecutor foreign = new ForeignExecutor(prior);
            EvalExprProvider.registerGlobalExecutor(foreign);
            ListAppender<ILoggingEvent> events = attachLogCaptor();
            try {
                LintDeadlineExecutor.uninstall();
                assertSame(foreign, EvalExprProvider.getGlobalExecutor(),
                        "the attach-replaced slot must keep the foreign executor");
            } finally {
                detachLogCaptor(events);
                EvalExprProvider.registerGlobalExecutor(prior);
            }
            assertTrue(events.list.stream()
                            .anyMatch(e -> e.getFormattedMessage().contains("deadline-executor-residual")),
                    "the residual must be logged loudly, never silently kept");
        }
        LintDeadlineExecutor.install();
        try {
            assertInstanceOfInstalled(EvalExprProvider.getGlobalExecutor());
        } finally {
            LintDeadlineExecutor.uninstall();
        }
        assertSame(prior, EvalExprProvider.getGlobalExecutor());
    }

    @Test
    public void testW_02_interleavedInstallUninstallWithForeignWriterNeverStacks() throws Exception {
        // red②①: bounded-round loop against a second writer hammering the
        // slot attach-style between the run's install and uninstall. The
        // per-round invariants: the wrapper disappears only through its
        // uninstall exit, a fresh install never stacks, and the ownership
        // ledger stays balanced across every round.
        IExpressionExecutor prior = EvalExprProvider.getGlobalExecutor();
        int rounds = 300;
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < rounds; i++) {
                    barrier.await(10, TimeUnit.SECONDS);
                    // the attach-shaped write lands between T1's install and
                    // its uninstall
                    EvalExprProvider.registerGlobalExecutor(new ForeignExecutor(prior));
                    barrier.await(10, TimeUnit.SECONDS);
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "w02-foreign-writer");
        writer.start();
        try {
            for (int i = 0; i < rounds; i++) {
                LintDeadlineExecutor.install();
                barrier.await(10, TimeUnit.SECONDS);
                barrier.await(10, TimeUnit.SECONDS);
                LintDeadlineExecutor.uninstall();
                assertFalse(EvalExprProvider.getGlobalExecutor() instanceof LintDeadlineExecutor,
                        "round " + i + ": a wrapper survived the run outside its uninstall exit");
                // the ledger must be reusable: a fresh generation installs,
                // never stacks, and unwinds to the current foreign top
                LintDeadlineExecutor.install();
                IExpressionExecutor top = EvalExprProvider.getGlobalExecutor();
                if (top instanceof LintDeadlineExecutor wrapper) {
                    assertFalse(wrapper.delegate() instanceof LintDeadlineExecutor,
                            "round " + i + ": install stacked a second wrapper");
                }
                LintDeadlineExecutor.uninstall();
                assertFalse(EvalExprProvider.getGlobalExecutor() instanceof LintDeadlineExecutor,
                        "round " + i + ": a wrapper survived the second run outside its uninstall exit");
            }
        } finally {
            writer.join(10_000);
            EvalExprProvider.registerGlobalExecutor(prior);
        }
        assertNullFailure(failure);
        assertFalse(EvalExprProvider.getGlobalExecutor() instanceof LintDeadlineExecutor,
                "the loop must end with no wrapper residue");
    }

    @Test
    public void testW_02_concurrentLintRunsNeverLoseTheLedger() throws Exception {
        // red②①: the lint-vs-lint interleaving face — two concurrent runs
        // install/uninstall against the same slot with no cross-thread
        // alignment. The class monitor must close the read-slot -> write-slot
        // window inside install and the release swap inside uninstall: a
        // dropped monitor loses a generation's wrapper (it survives its own
        // uninstall exit or unbalances the ledger) and the bounded loop
        // below catches it.
        IExpressionExecutor prior = EvalExprProvider.getGlobalExecutor();
        assertFalse(prior instanceof LintDeadlineExecutor,
                "test precondition: the slot starts wrapper-free");
        int rounds = 200;
        for (int i = 0; i < rounds; i++) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Runnable lintRun = () -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    spinRandomly();
                    LintDeadlineExecutor.install();
                    barrier.await(10, TimeUnit.SECONDS);
                    assertTrue(EvalExprProvider.getGlobalExecutor() instanceof LintDeadlineExecutor,
                            "the guard must be live inside the run window");
                    barrier.await(10, TimeUnit.SECONDS);
                    spinRandomly();
                    LintDeadlineExecutor.uninstall();
                } catch (Throwable t) {
                    failure.set(t);
                }
            };
            Thread runA = new Thread(lintRun, "w02-lint-run-a");
            Thread runB = new Thread(lintRun, "w02-lint-run-b");
            runA.start();
            runB.start();
            runA.join(10_000);
            runB.join(10_000);
            if (failure.get() != null) {
                throw new AssertionError("round " + i + ": the paired runs broke the ledger",
                        failure.get());
            }
            IExpressionExecutor top = EvalExprProvider.getGlobalExecutor();
            assertFalse(top instanceof LintDeadlineExecutor,
                    "round " + i + ": a wrapper survived the paired runs outside its uninstall exit");
            // the ledger must be reusable: a fresh generation installs and
            // unwinds to exactly the top it captured
            LintDeadlineExecutor.install();
            try {
                assertTrue(EvalExprProvider.getGlobalExecutor() instanceof LintDeadlineExecutor,
                        "round " + i + ": the ledger must accept a fresh generation");
            } finally {
                LintDeadlineExecutor.uninstall();
            }
            assertSame(top, EvalExprProvider.getGlobalExecutor(),
                    "round " + i + ": the ledger must unwind to the same slot top");
        }
    }

    /**
     * Busy-spins a random offset (0 to ~2000 iterations) so the two paired
     * runs sweep each other's read-slot -> write-slot windows across rounds
     * instead of entering them in lockstep.
     */
    private static void spinRandomly() {
        int iterations = java.util.concurrent.ThreadLocalRandom.current().nextInt(2000);
        int sink = 0;
        for (int i = 0; i < iterations; i++) {
            sink += i;
        }
        if (sink == Integer.MIN_VALUE) {
            throw new AssertionError("unreachable");
        }
    }

    // ==================== helpers ====================

    private static void assertInstanceOfInstalled(IExpressionExecutor executor) {
        assertTrue(executor instanceof LintDeadlineExecutor,
                "the run's install must hold the global executor slot (chain-scan visible)");
    }

    private static void assertNullFailure(AtomicReference<Throwable> failure) {
        if (failure.get() != null) {
            throw new AssertionError("the second writer failed", failure.get());
        }
    }

    private static ListAppender<ILoggingEvent> attachLogCaptor() {
        Logger log = (Logger) LoggerFactory.getLogger(LintDeadlineExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        log.addAppender(appender);
        return appender;
    }

    private static void detachLogCaptor(ListAppender<ILoggingEvent> appender) {
        Logger log = (Logger) LoggerFactory.getLogger(LintDeadlineExecutor.class);
        log.detachAppender(appender);
        appender.stop();
    }

    /**
     * The hand-rolled foreign executor carrier (zero mockito): models the
     * debugger attach's slot write.
     */
    static final class ForeignExecutor implements IExpressionExecutor {
        private final IExpressionExecutor delegate;

        ForeignExecutor(IExpressionExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object execute(IExecutableExpression expr, EvalRuntime rt) {
            return delegate.execute(expr, rt);
        }
    }
}
