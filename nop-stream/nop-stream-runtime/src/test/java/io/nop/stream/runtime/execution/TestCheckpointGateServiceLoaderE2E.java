/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.checkpoint.StorageJobIds;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * F-06 (Plan 2026-09-04-1326-1 Phase 2): the user-guide quick-start shape —
 * {@code env.enableCheckpointing(interval)} with NO static
 * {@code StreamExecutionEnvironment.setCheckpointExecutorFactory(...)} call — must
 * produce REAL checkpoints when the runtime implementation jar is on the classpath
 * (its {@code META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory}
 * entry is consumed via {@code java.util.ServiceLoader}, adjudication D2=(a)).
 */
public class TestCheckpointGateServiceLoaderE2E {

    @TempDir
    Path tempDir;

    @Test
    public void userGuideQuickStartPathWithoutStaticSetterProducesRealCheckpoints() throws Exception {
        // NO static setter call — the exact user-guide quick-start shape. The instance
        // factory field is seeded only from the static setter, so this probe proves the
        // static default is unset and the run really exercises the ServiceLoader path.
        assertNull(new StreamExecutionEnvironment().getCheckpointExecutorFactory(),
                "precondition: no statically registered factory in this JVM");

        Path storagePath = tempDir.resolve("f06-storage");
        List<Integer> sink = Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.atomic.AtomicInteger padsSeen = new java.util.concurrent.atomic.AtomicInteger();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(50);
        env.getCheckpointConfig().setStorageProperty("path", storagePath.toString());
        env.addSource(new LingerSource(storagePath.toString(), padsSeen), "F06Source")
                .map(i -> i * 2)
                .sink(i -> {
                    if (i >= 1000) {
                        padsSeen.incrementAndGet();
                    } else {
                        sink.add(i);
                    }
                });

        StreamExecutionResult result = env.execute("f06-guide-quickstart");
        assertNotNull(result, "job completes");
        // exact-once output: map(i -> i * 2) over 1..6 = 2+4+6+8+10+12 = 42 (the
        // offset-checkpointed source forbids replay duplication)
        assertEquals(42, sink.stream().mapToInt(Integer::intValue).sum(), "output complete");

        // real checkpoint evidence: durable artifacts exist under the storage namespace
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storagePath.toString());
        assertNotNull(storage.loadLatestEpochManifest(
                        StorageJobIds.sanitizeJobId("f06-guide-quickstart"), "pipeline-0"),
                "user-guide quick-start path must produce REAL checkpoints (count > 0),"
                        + " not a silently non-checkpointed LOCAL run");
    }

    /**
     * Source that keeps making collect boundaries (odd positive pads ≥ 1001, counted
     * at the sink and excluded from the output assertions) until the job's first
     * periodic checkpoint manifest is durably stored. 时间确定化（plan 2510）：barrier
     * 只能在 source 的 collect 边界注入，pads 以「上一个 pad 已被消费」为节奏（往返
     * 条件等待），EOS 由 manifest 落盘这一可观测条件门控；周期触发本身是 executor
     * 内部真实调度器，属被测语义的具名残余. The emit offset is checkpointed (and
     * restored), so any internal region-restart replays at most the post-checkpoint
     * tail — the exactly-once output assertion below is a real behavioral proof that
     * the checkpoints taken are durable AND consumed.
     */
    private static class LingerSource implements CheckpointedSourceFunction<Integer> {
        private static final String OFFSET_KEY = "f06-offset";

        private final String gateBase;
        private final java.util.concurrent.atomic.AtomicInteger padsSeen;
        private volatile boolean running = true;
        private int emitted;

        LingerSource(String gateBase, java.util.concurrent.atomic.AtomicInteger padsSeen) {
            this.gateBase = gateBase;
            this.padsSeen = padsSeen;
        }

        @Override
        public void run(SourceFunction.SourceContext<Integer> ctx) throws Exception {
            for (int i = emitted + 1; i <= 6 && running; i++) {
                ctx.collect(i);
                emitted = i;
            }
            // EOS 门：第一个周期 checkpoint manifest 落盘。往返条件等待为节奏保持
            // collect 边界；上限仅作挂死保护，超时后正常 EOS —— 最终 manifest 断言
            // 给出确定性 RED。
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
                LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(gateBase);
                return storage.loadLatestEpochManifest(
                        StorageJobIds.sanitizeJobId("f06-guide-quickstart"), "pipeline-0") != null;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void cancel() {
            running = false;
        }

        @Override
        public io.nop.stream.core.checkpoint.OperatorSnapshotResult snapshotState(long checkpointId) {
            io.nop.stream.core.checkpoint.OperatorSnapshotResult result =
                    io.nop.stream.core.checkpoint.OperatorSnapshotResult.empty();
            result.putOperatorState(OFFSET_KEY, emitted);
            return result;
        }

        @Override
        public void initializeState(io.nop.stream.core.checkpoint.TaskStateSnapshot state) {
            if (state != null && state.getOperatorStates() != null
                    && state.getOperatorStates().containsKey(OFFSET_KEY)) {
                emitted = ((Number) state.getOperatorStates().get(OFFSET_KEY)).intValue();
            }
        }
    }
}
