package io.nop.stream.fraud.scenario;

import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.parseStreamXml;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.registerCheckpointExecutorFactory;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.unregisterCheckpointExecutorFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 (item 29) LOCAL e2e: the inline xpl source cancellation semantics,
 * driven end-to-end through a REAL runtime cancel surface.
 *
 * <p>Form chosen (per the plan's host adjudication): the LOCAL checkpoint-abort
 * path. The core {@code env.execute()} entry has no cancel API and the production
 * {@code signalCancel} callers all live in nop-stream-runtime, whose LOCAL
 * checkpoint executor reaches the same cooperative-cancel recipe
 * ({@code MailboxExecutor.signalCancel()} + task cancel) via
 * {@code GraphModelCheckpointExecutor.registerLocalAbortHandler} when a pending
 * checkpoint times out. This test deliberately makes the checkpoint time out (the
 * xpl source stops collecting, so the trigger-checkpoint mail is never drained)
 * so the abort handler raises the cancel flag.
 *
 * <p>The xpl body swallows the abort interrupt inside its sleep loop, so the only
 * way its {@code while (!ctx.isCancelled())} loop can end is the context cancel
 * accessor reflecting the mailbox flag. Assertions:
 * <ol>
 *   <li>{@code env.execute()} terminates within a bounded budget (graceful source
 *       exit → task terminal state → supervision loop returns — no hang);</li>
 *   <li>the surfaced failure is the checkpoint abort (the runtime cancel surface
 *       fired), NOT a source task failure (which is what an interrupt-only exit
 *       would produce);</li>
 *   <li>the data plane worked before the cancel (the sink observed the source's
 *       startup element).</li>
 * </ol>
 */
public class TestXplSourceCancelE2E {

    private static final String CANCEL_STREAM_PATH = "/nop/stream/test/fraud-cancel-xpl.stream.xml";

    /** Upper bound for the whole execute() cycle (trigger ~200ms + timeout 800ms + teardown). */
    private static final long EXECUTE_BUDGET_MS = 30_000L;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        // Route env.execute() through the runtime checkpoint executor (LOCAL path).
        registerCheckpointExecutorFactory();
    }

    @AfterAll
    public static void destroy() {
        unregisterCheckpointExecutorFactory();
        CoreInitialization.destroy();
    }

    /** Shared collecting sink bean (plain SinkFunctions stay shared across subtasks). */
    public static final class CollectingSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        private final List<String> collected = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void consume(String value) {
            collected.add(value);
        }

        public List<String> getCollected() {
            return collected;
        }
    }

    @TempDir
    Path tempDir;

    @Test
    @Timeout(120)
    public void xplSourceExitsGracefullyViaContextCancelPollOnCheckpointAbort() throws Exception {
        CollectingSink sink = new CollectingSink();
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("cancelSink", sink);

        StreamModel model = parseStreamXml(CANCEL_STREAM_PATH);
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        // Per-run temp storage: the abort e2e must not read or leave state under a
        // path shared with other runs (a stale stored fingerprint would fail restore).
        Path storagePath = Files.createDirectories(tempDir.resolve("cancel-e2e-storage"));
        env.getCheckpointConfig().setStorageProperty("path", storagePath.toString());

        long start = System.currentTimeMillis();
        StreamException ex = assertThrows(StreamException.class, () -> env.execute("fraud-cancel-xpl"));
        long elapsed = System.currentTimeMillis() - start;

        // (1) Bounded graceful termination: the supervision loop only returns once
        // every task reached a terminal state. If the body could not observe the
        // cancel flag (and it swallows interrupts on purpose), execute() would hang
        // until this test's timeout.
        assertTrue(elapsed < EXECUTE_BUDGET_MS,
                "execute() must terminate within the cancel budget, took " + elapsed + "ms");

        // (2) The surfaced failure is the checkpoint abort raised by the runtime
        // cancel surface — not a source task failure (an interrupt-driven exit would
        // surface ERR_STREAM_SUPERVISION_TASK_FAILED instead) and not a job failure
        // without an abort cause.
        assertNotNull(findErrorCause(ex, NopStreamErrors.ERR_STREAM_CHECKPOINT_ABORTED),
                "cause chain must carry the checkpoint-abort error (runtime cancel surface fired): "
                        + dumpChain(ex));
        assertEquals(NopStreamErrors.ERR_STREAM_JOB_EXECUTE_FAILED.getErrorCode(), ex.getErrorCode());

        // (3) Data plane proof before the cancel: the source's startup element
        // reached the sink through the chained operator pipeline.
        assertTrue(sink.getCollected().contains("started"),
                "sink must have observed the source startup element before cancel: " + sink.getCollected());
    }

    private static String dumpChain(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable t = error;
        while (t != null) {
            sb.append(t.getClass().getName()).append(": ")
                    .append(t.getMessage() == null ? "(no message)" : t.getMessage()).append("\n");
            t = t.getCause();
        }
        return sb.toString();
    }

    private static Throwable findErrorCause(Throwable error, io.nop.api.core.exceptions.ErrorCode errorCode) {
        Throwable t = error;
        while (t != null) {
            if (t instanceof StreamException se && errorCode.getErrorCode().equals(se.getErrorCode())) {
                return t;
            }
            t = t.getCause();
        }
        return null;
    }
}
