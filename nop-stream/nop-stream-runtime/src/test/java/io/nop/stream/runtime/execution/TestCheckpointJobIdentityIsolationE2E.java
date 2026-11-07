/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StorageJobIds;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-1 (P0) regression + dual pinning (Plan 2026-09-04-1326-1 Phase 1).
 *
 * <ul>
 *   <li><b>Pollution regression</b>: two different-topology jobs run back-to-back on the
 *       SAME machine (same default storage base) — the second job must neither fail with
 *       {@code ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED} nor inherit the first job's
 *       state (outputs come from a fresh start).</li>
 *   <li><b>Identity rejection</b>: same jobId + DIFFERENT topology restart under an
 *       explicit storage path is rejected typed (fingerprint guard), never silently
 *       inherited.</li>
 *   <li><b>Legal-recovery dual</b>: same jobId + SAME topology restart under an explicit
 *       storage path auto-restores (CheckpointedSourceFunction observes restored state) —
 *       the kill/recover semantics this fix must not regress.</li>
 *   <li><b>D1b</b>: jobs named with spaces / CJK characters persist checkpoints without
 *       LocalFileCheckpointStorage.validateId failures.</li>
 * </ul>
 *
 * <p>Cleanup discipline: every run uses a JUnit {@code @TempDir} (explicit paths) or the
 * system-property-overridden default base — no machine-level global directory residue.
 */
public class TestCheckpointJobIdentityIsolationE2E {

    private static final String MARKER_KEY = "iso-restore-marker";

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void init() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(
                new CheckpointExecutorFactoryImpl());
    }

    @AfterAll
    public static void destroy() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(null);
        System.clearProperty("nop-stream.checkpoint.storage.dir");
    }

    // ------------------------------------------------------------------
    // AR-1 pollution regression: default path, two different topologies
    // ------------------------------------------------------------------

    @Test
    public void differentTopologyJobsOnDirtyDefaultBaseDoNotPolluteEachOther() throws Exception {
        Path defaultBase = tempDir.resolve("default-base");
        Files.createDirectories(defaultBase);
        System.setProperty("nop-stream.checkpoint.storage.dir", defaultBase.toString());
        try {
            // job 1: source -> map(x2) -> sink, leaves durable checkpoints behind
            List<Integer> out1 = Collections.synchronizedList(new ArrayList<>());
            AtomicReference<String> restored1 = new AtomicReference<>();
            StreamExecutionResult r1 = runJob("iso-alpha", 2, false, null, defaultBase, out1, restored1);
            assertNotNull(r1);
            assertEquals(listOf(2, 4, 6, 8, 10, 12), sorted(out1), "job 1 output complete");
            assertNull(restored1.get(), "job 1 must fresh-start on an empty default base");
            assertTrue(Files.exists(defaultBase.resolve(StorageJobIds.sanitizeJobId("iso-alpha"))),
                    "job 1 namespace exists under the default base");

            // job 2: DIFFERENT topology (adds a filter stage -> different fingerprint),
            // same default base with job 1's residue present
            List<Integer> out2 = Collections.synchronizedList(new ArrayList<>());
            AtomicReference<String> restored2 = new AtomicReference<>();
            StreamExecutionResult r2 = runJob("iso-beta", 3, true, null, defaultBase, out2, restored2);
            assertNotNull(r2, "job 2 must complete — no ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED pollution");
            assertEquals(listOf(3, 9, 15), sorted(out2), "job 2 output complete (no inherited state)");
            assertNull(restored2.get(), "job 2 must fresh-start (default path never auto-restores)");
            assertTrue(Files.exists(defaultBase.resolve(StorageJobIds.sanitizeJobId("iso-beta"))),
                    "job 2 namespace is isolated from job 1's namespace");
        } finally {
            System.clearProperty("nop-stream.checkpoint.storage.dir");
        }
    }

    // ------------------------------------------------------------------
    // identity guard: same jobId + different topology under explicit path
    // ------------------------------------------------------------------

    @Test
    public void sameJobIdDifferentTopologyUnderExplicitPathFailsTyped() throws Exception {
        Path storage = tempDir.resolve("twin-storage");

        List<Integer> out1 = Collections.synchronizedList(new ArrayList<>());
        runJob("twin-job", 2, false, storage.toString(), storage, out1, new AtomicReference<>());
        assertTrue(Files.exists(storage.resolve(StorageJobIds.sanitizeJobId("twin-job"))),
                "run 1 persisted checkpoints");

        // same job name, different topology -> fingerprint guard must reject typed
        List<Integer> out2 = Collections.synchronizedList(new ArrayList<>());
        StreamException ex = assertThrows(StreamException.class,
                () -> runJob("twin-job", 3, true, storage.toString(), storage, out2, new AtomicReference<>()),
                "same jobId + different topology must fail typed, not silently inherit");
        // env.execute() wraps execution failures in ERR_STREAM_JOB_EXECUTE_FAILED — the
        // typed restore rejection must be observable in the cause chain
        Throwable t = ex;
        boolean foundRestoreFailed = false;
        StringBuilder chain = new StringBuilder();
        while (t != null) {
            chain.append(t.getClass().getSimpleName()).append(":").append(t.getMessage()).append(" <- ");
            if (t instanceof NopException
                    && NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED
                            .getErrorCode().equals(((NopException) t).getErrorCode())) {
                foundRestoreFailed = true;
            }
            t = t.getCause();
        }
        assertTrue(foundRestoreFailed,
                "typed rejection carries ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED in the"
                        + " cause chain: " + chain);
    }

    // ------------------------------------------------------------------
    // legal-recovery dual: same jobId + same topology under explicit path
    // ------------------------------------------------------------------

    @Test
    public void sameJobIdSameTopologyUnderExplicitPathAutoRestores() throws Exception {
        Path storage = tempDir.resolve("recover-storage");

        // run 1: fresh start, leaves durable checkpointed state behind
        List<Integer> out1 = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<String> restored1 = new AtomicReference<>();
        runJob("recover-job", 2, false, storage.toString(), storage, out1, restored1);
        assertNull(restored1.get(), "run 1 fresh-starts");
        assertEquals(listOf(2, 4, 6, 8, 10, 12), sorted(out1));

        // run 2: same job name + same topology -> auto-restore from run 1's checkpoints
        List<Integer> out2 = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<String> restored2 = new AtomicReference<>();
        runJob("recover-job", 2, false, storage.toString(), storage, out2, restored2);
        assertEquals(MARKER_VALUE, restored2.get(),
                "run 2 must observe run 1's checkpointed operator state (auto-recovery preserved)");
        assertEquals(listOf(2, 4, 6, 8, 10, 12), sorted(out2), "run 2 output complete");
    }

    // ------------------------------------------------------------------
    // D1b: unsafe job names persist without validateId failures
    // ------------------------------------------------------------------

    @Test
    public void defaultAndCjkJobNamesPersistCheckpointsUnderExplicitPath() throws Exception {
        Path storage = tempDir.resolve("name-storage");

        List<Integer> out1 = Collections.synchronizedList(new ArrayList<>());
        runJob("Streaming Job", 2, false, storage.toString(), storage, out1, new AtomicReference<>());
        assertTrue(Files.exists(storage.resolve(StorageJobIds.sanitizeJobId("Streaming Job"))),
                "'Streaming Job' (no-arg execute() default) maps to a valid storage id");

        List<Integer> out2 = Collections.synchronizedList(new ArrayList<>());
        runJob("订单-监控 作业", 2, false, storage.toString(), storage, out2, new AtomicReference<>());
        assertTrue(Files.exists(storage.resolve(StorageJobIds.sanitizeJobId("订单-监控 作业"))),
                "CJK job name maps to a valid storage id");
    }

    // ------------------------------------------------------------------
    // scaffolding
    // ------------------------------------------------------------------

    private static final String MARKER_VALUE = "restored-v1";

    /**
     * Runs source→map(×multiplier)→[filter?]→sink with checkpointing on. The source
     * keeps making collect boundaries (pad values ≥ 1000, counted at the sink and
     * excluded from the output assertions — odd positive pads survive any odd-value
     * filter) until the job's first periodic checkpoint manifest is durably stored
     * under {@code gateBase} (observable evidence — the manifest is produced by the
     * production checkpoint pipeline, the gate only waits for it), and records whether
     * {@code initializeState} observed a restored operator-state marker.
     *
     * <p>时间确定化（plan 2510）：不再用固定 pacing 制造「checkpoint 有时间触发」的
     * 窗口——barrier 只能在 source 的 collect 边界注入，源以 pad 元素保持 collect
     * 边界（往返条件等待为节奏），EOS 由 manifest 落盘这一可观测条件门控；周期触发
     * 本身是 executor 内部真实调度器（不可注入），属被测语义的具名残余。上限仅作
     * 挂死保护，超时后正常 EOS 让最终的 manifest/恢复断言给出确定性 RED。
     */
    private StreamExecutionResult runJob(String jobName, int multiplier, boolean withFilter,
                                         String storagePath, Path gateBase, List<Integer> sink,
                                         AtomicReference<String> restoredMarker) throws Exception {
        java.util.concurrent.atomic.AtomicInteger padsSeen = new java.util.concurrent.atomic.AtomicInteger();
        MarkerSource source = new MarkerSource(restoredMarker, jobName, gateBase, padsSeen);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(50);
        if (storagePath != null) {
            env.getCheckpointConfig().setStorageProperty("path", storagePath);
        }
        var stream = env.addSource(source, "IsoSource")
                .map(i -> i * multiplier);
        if (withFilter) {
            stream = stream.filter(i -> i % 2 == 1);
        }
        stream.sink(i -> {
            if (i >= 1000) {
                padsSeen.incrementAndGet();
            } else {
                sink.add(i);
            }
        });
        return env.execute(jobName);
    }

    private static List<Integer> listOf(Integer... values) {
        List<Integer> list = new ArrayList<>();
        Collections.addAll(list, values);
        return list;
    }

    private static List<Integer> sorted(List<Integer> list) {
        synchronized (list) {
            return new ArrayList<>(new TreeSet<>(list));
        }
    }

    /**
     * Checkpointed source: emits 1..6, then keeps making collect boundaries (odd
     * positive pads ≥ 1001 — they survive the odd-value filter and are counted at
     * the sink) until the job's first checkpoint manifest is durably stored under
     * the gate base. Pads are paced by the round-trip signal (previous pad consumed
     * downstream), so the source is still collecting when the periodic trigger
     * fires and the barrier can inject at a collect boundary.
     */
    private static class MarkerSource implements CheckpointedSourceFunction<Integer> {
        private final AtomicReference<String> restoredMarker;
        private final String jobName;
        private final Path gateBase;
        private final java.util.concurrent.atomic.AtomicInteger padsSeen;
        private volatile boolean running = true;

        MarkerSource(AtomicReference<String> restoredMarker, String jobName, Path gateBase,
                     java.util.concurrent.atomic.AtomicInteger padsSeen) {
            this.restoredMarker = restoredMarker;
            this.jobName = jobName;
            this.gateBase = gateBase;
            this.padsSeen = padsSeen;
        }

        @Override
        public void run(SourceFunction.SourceContext<Integer> ctx) throws Exception {
            for (int i = 1; i <= 6 && running; i++) {
                ctx.collect(i);
            }
            // EOS 门：第一个周期 checkpoint manifest 落盘（生产 checkpoint 管线的可观测
            // 证据）。往返条件等待为节奏保持 collect 边界；上限仅作挂死保护，超时后
            // 正常 EOS —— 最终 manifest/恢复断言给出确定性 RED。
            for (int p = 0; running && !firstManifestStored() && p < 20_000; p++) {
                int before = padsSeen.get();
                ctx.collect(1001 + 2 * p);
                int expected = before + 1;
                io.nop.stream.core.testsupport.TestAwait.until(
                        "pad consumed downstream", () -> padsSeen.get() >= expected, 5000);
            }
        }

        private boolean firstManifestStored() {
            try {
                LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(gateBase.toString());
                return storage.loadLatestEpochManifest(
                        StorageJobIds.sanitizeJobId(jobName), "pipeline-0") != null;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void cancel() {
            running = false;
        }

        @Override
        public OperatorSnapshotResult snapshotState(long checkpointId) {
            OperatorSnapshotResult result = OperatorSnapshotResult.empty();
            result.putOperatorState(MARKER_KEY, MARKER_VALUE);
            return result;
        }

        @Override
        public void initializeState(TaskStateSnapshot state) {
            if (state != null && state.getOperatorStates() != null
                    && state.getOperatorStates().containsKey(MARKER_KEY)) {
                restoredMarker.set(String.valueOf(state.getOperatorStates().get(MARKER_KEY)));
            }
        }
    }
}
