/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10 (plan 2426): MAINTAINER TOOL — regenerates the legacy checkpoint fixture
 * under {@code src/test/resources/checkpoint-legacy-fixture/}.
 *
 * <p>Gated behind {@code -Dnop.stream.test.fixture.regenerate=true} so it never
 * runs in the default suite: the fixture is the cross-version FORMAT pin
 * (roadmap WI10 hard constraint — stored bytes must stay restorable), and it may
 * only be regenerated DELIBERATELY when the format legitimately evolves, with
 * the evolution recorded in the roadmap/design docs (see the fixture README).
 */
class TestLegacyCheckpointFixtureGenerator {

    /**
     * Deterministic fixture source (plan 2510 时间确定化 rework of the legacy
     * pacing source). The fixture is the cross-version FORMAT pin: regeneration
     * must reproduce the same manifest/ledger family (structure, not a
     * byte-identical value multiset). Emission is driven by COMMIT EVIDENCE
     * instead of real time: the source streams the input cyclically until the
     * sink's shared committed list shows >= 2 committed epochs (each commit
     * rides a real completed checkpoint), then one final full pass guarantees
     * every input value landed (early sends before the downstream subscription
     * was live are dropped by the in-process transport and re-covered by the
     * cyclic re-emission + final pass). The legacy 750ms subscribe window,
     * 25ms/record pacing and 1500ms finish linger are all gone — the waits are
     * condition waits on the committed-evidence counter (bounded hang
     * protection only).
     */
    public static final class LegacyPacingInput<T>
            implements io.nop.stream.core.common.functions.source.SourceFunction<T> {
        private static final long serialVersionUID = 1L;

        private static final int MIN_COMMITTED_EPOCHS = 2;
        private static final int BATCH = 8;
        /** EOS-gate heartbeat sentinel; dropped by the pipeline filter before keyBy. */
        public static final int HEARTBEAT = -1;

        private final List<T> input;
        /**
         * Shared committed-evidence mirror (the sink fixture's committed list).
         * {@code null} = immediate single-pass mode (each input value emitted once,
         * no commit gating).
         */
        private final List<T> committedMirror;
        /**
         * Single-pass-with-commit-evidence mode (plan 2510 load-stability fix):
         * emit the input list once, in order, in batches of {@link #BATCH}; after
         * each batch wait until the mirror shows the whole batch committed before
         * emitting the next. Each value is emitted exactly once and is committed
         * before the next batch starts, so the committed sequence equals the
         * input order — the deterministic form of the legacy paced replay, used
         * by the fixture RESTORE test. Requires a non-null mirror.
         */
        private final boolean singlePass;
        private volatile boolean running = true;

        public LegacyPacingInput(List<T> input, List<T> committedMirror) {
            this(input, committedMirror, false);
        }

        public LegacyPacingInput(List<T> input, List<T> committedMirror, boolean singlePass) {
            this.input = input;
            this.committedMirror = committedMirror;
            this.singlePass = singlePass;
        }

        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<T> ctx)
                throws Exception {
            if (singlePass && committedMirror != null) {
                runSinglePassWithCommitEvidence(ctx);
                return;
            }
            if (committedMirror == null) {
                for (T value : input) {
                    if (!running) {
                        return;
                    }
                    ctx.collect(value);
                }
                return;
            }
            int baseline = committedMirror.size();
            int emitted = 0;
            while (running && committedMirror.size() - baseline < MIN_COMMITTED_EPOCHS) {
                ctx.collect(input.get(emitted % input.size()));
                emitted++;
                if (emitted % BATCH == 0) {
                    long before = committedMirror.size();
                    io.nop.stream.core.testsupport.TestAwait.until(
                            "next checkpoint epoch committed (fixture evidence)",
                            () -> committedMirror.size() > before, 30_000L);
                }
            }
            // final full pass: subscription is live by now — every input value lands
            for (T value : input) {
                if (!running) {
                    return;
                }
                ctx.collect(value);
            }
        }

        private void runSinglePassWithCommitEvidence(
                io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<T> ctx)
                throws Exception {
            for (int batchStart = 0; batchStart < input.size(); batchStart += BATCH) {
                int end = Math.min(batchStart + BATCH, input.size());
                for (int i = batchStart; i < end; i++) {
                    if (!running) {
                        return;
                    }
                    ctx.collect(input.get(i));
                }
                // 邮箱排水型证据等待（负载稳定化修正）：barrier 注入是任务控制邮箱里的
                // 控制邮件，只在 collect 点被处理——阻塞式等待会让下一个周期 barrier
                // 无法注入、提交证据永不出现（30s 挂死实测）。心跳哨兵（HEARTBEAT）
                // 经管线 filter 在 keyBy 前丢弃，不进入提交流；100µs park 为邮箱服务
                // 节拍。30s 上限仅挂死保护。
                long deadline = System.currentTimeMillis() + 30_000L;
                while (running && committedMirror.size() < end) {
                    if (System.currentTimeMillis() >= deadline) {
                        throw new IllegalStateException(
                                "batch commit evidence did not converge within 30s (batchEnd="
                                        + end + ", committed=" + committedMirror.size() + ")");
                    }
                    ctx.collect(heartbeat());
                    java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private T heartbeat() {
            return (T) (Integer) HEARTBEAT;
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    @TempDir
    Path tempDir;

    @Test
    void regenerateFixture() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("nop.stream.test.fixture.regenerate"),
                "fixture regeneration is an explicit maintainer action "
                        + "(-Dnop.stream.test.fixture.regenerate=true)");

        String jobId = "fixture-source-job";
        Path storageDir = tempDir.resolve("storage");
        List<Integer> committed = new ArrayList<>();

        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        config.setCheckpointInterval(150L);
        config.setCheckpointTimeout(30_000L);
        config.setMaxRetainedCheckpoints(5);
        config.setStorageProperty("path", storageDir.toFile().getAbsolutePath());
        config.setJobId(jobId);
        config.setPipelineId("pipeline-0");

        io.nop.stream.core.environment.StreamExecutionEnvironment environment =
                io.nop.stream.core.environment.StreamExecutionEnvironment.createTestEnvironment();
        environment.setParallelism(1);
        environment.enableCheckpointing(150L);
        environment.getCheckpointConfig().setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        environment.addSource(
                new LegacyPacingInput<>(
                        TestCheckpointDualRunDifferential.firstInput(), committed), "paced-source")
                .map(v -> v)
                .keyBy(v -> v % 2)
                .reduce((a, b) -> a + b)
                .sink(new TestCheckpointDualRunDifferential.RecordingTwoPhaseSink(jobId, committed));
        JobGraph jobGraph = environment.buildJobGraph(jobId);
        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, jobId, config);

        assertTrue(!committed.isEmpty(), "generator run must commit outputs");
        assertTrue(TestCheckpointDualRunDifferential.countManifests(
                storageDir.toFile().getAbsolutePath()) >= 2, "fixture must contain >= 2 epochs");

        // Copy the storage tree into test resources.
        Path target = Path.of("src/test/resources/checkpoint-legacy-fixture/storage");
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(target),
                "refusing to overwrite an existing fixture — delete it explicitly first "
                        + "(format evolution must be a recorded decision)");
        copyTree(storageDir, target);
        System.out.println("Fixture written to " + target.toAbsolutePath()
                + " (" + TestCheckpointDualRunDifferential.countManifests(target.toString()) + " manifests)");
    }

    static void copyTree(Path from, Path to) throws Exception {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path src : walk.toList()) {
                Path rel = from.relativize(src);
                Path dst = to.resolve(rel);
                if (src.toFile().isDirectory()) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
