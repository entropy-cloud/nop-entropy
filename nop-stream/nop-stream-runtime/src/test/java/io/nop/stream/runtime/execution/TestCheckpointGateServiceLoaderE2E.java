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
import java.util.concurrent.TimeUnit;

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

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(50);
        env.getCheckpointConfig().setStorageProperty("path", storagePath.toString());
        env.addSource(new LingerSource(), "F06Source")
                .map(i -> i * 2)
                .sink(sink::add);

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
     * Lingering source so periodic checkpoints fire during the bounded run. The emit
     * offset is checkpointed (and restored), so any internal region-restart replays at
     * most the post-checkpoint tail — the exactly-once output assertion below is a real
     * behavioral proof that the checkpoints taken are durable AND consumed.
     */
    private static class LingerSource implements CheckpointedSourceFunction<Integer> {
        private static final String OFFSET_KEY = "f06-offset";

        private volatile boolean running = true;
        private int emitted;

        @Override
        public void run(SourceFunction.SourceContext<Integer> ctx) throws Exception {
            try {
                for (int i = emitted + 1; i <= 6 && running; i++) {
                    ctx.collect(i);
                    emitted = i;
                    TimeUnit.MILLISECONDS.sleep(60);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
