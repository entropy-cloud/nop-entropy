/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.stream.connector.batch.BatchLoaderSource;
import io.nop.stream.connector.batch.BatchLoaderSourceConnectorFactory;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.source.Source;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 plan 2343 Phase 1: the batch-loader factory → FLIP-27 source combination end to
 * end — the connector factory produces the {@link BatchLoaderSource}, the engine runs it
 * through {@code SourceReaderOperator} with real checkpoints, and a mid-read failure +
 * same-jobId resubmit resumes the loader traversal from the checkpointed offset
 * (AR-13 client-side skip) instead of re-reading from scratch. Exactly-once is NOT
 * asserted (AT_LEAST_ONCE source): continuation within the cursor window + full
 * cross-run coverage are.
 */
public class TestBatchLoaderSourceE2E {

    private static final int TOTAL_ROWS = 300;
    private static final int FAIL_AT = 50;
    /** per-batch sleep: ~125 rows/s with batchSize=1 — the failing row arrives well
     *  after the first 100ms checkpoint. */
    private static final long LOAD_PACE_MILLIS = 8L;

    /** Serializable deterministic loader: fixed rows, paced, re-traversable per setup. */
    static class PacedLoaderProvider implements IBatchLoaderProvider<String>, Serializable {
        @Override
        public IBatchLoader<String> setup(IBatchTaskContext taskContext) {
            return new IBatchLoader<>() {
                int position = 0;

                @Override
                public List<String> load(int batchSize, IBatchChunkContext chunkContext) {
                    if (position >= TOTAL_ROWS) {
                        return List.of();
                    }
                    try {
                        Thread.sleep(LOAD_PACE_MILLIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return List.of();
                    }
                    int end = Math.min(TOTAL_ROWS, position + batchSize);
                    List<String> batch = new ArrayList<>(end - position);
                    for (int i = position; i < end; i++) {
                        batch.add("row-" + (i + 1));
                    }
                    position = end;
                    return batch;
                }
            };
        }
    }

    static final class CollectOrFailSink implements SinkFunction<String>, Serializable {
        private static final long serialVersionUID = 1L;
        private final CopyOnWriteArrayList<String> collected;
        private final String failAtRow;

        CollectOrFailSink(CopyOnWriteArrayList<String> collected, String failAtRow) {
            this.collected = collected;
            this.failAtRow = failAtRow;
        }

        @Override
        public void consume(String value) {
            if (failAtRow != null && failAtRow.equals(value)) {
                throw new IllegalStateException("injected failure at " + value);
            }
            collected.add(value);
        }
    }

    private StreamExecutionEnvironment newEnvironment(String jobId, java.nio.file.Path storageDir) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(100L);
        env.getCheckpointConfig().setJobId(jobId);
        env.getCheckpointConfig().setCheckpointTimeout(5000L);
        env.getCheckpointConfig().setMinPause(50L);
        env.getCheckpointConfig().setStorageProperty("path", storageDir.toString());
        return env;
    }

    private CopyOnWriteArrayList<String> runPipeline(StreamExecutionEnvironment env,
                                                     String failAtRow) {
        BatchLoaderSourceConnectorFactory factory = new BatchLoaderSourceConnectorFactory();
        Source<String, ?, ?> source = (Source<String, ?, ?>) factory.createSource(
                new StreamConnectorConfig("batch-loader",
                        Map.of("loaderProvider", new PacedLoaderProvider(), "batchSize", 1)));
        CopyOnWriteArrayList<String> collected = new CopyOnWriteArrayList<>();
        env.addSource(source, "batch-loader-src")
                .sink(new CollectOrFailSink(collected, failAtRow));
        return collected;
    }

    private static int rowIndex(String row) {
        return Integer.parseInt(row.substring("row-".length()));
    }

    private static void deleteRecursive(java.io.File file) {
        if (file == null || !file.exists()) {
            return;
        }
        java.io.File[] children = file.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }

    @Test
    void factorySourceRestoresTraversalFromCheckpointedOffset() throws Exception {
        java.nio.file.Path storageDir = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "wi2-batch-e2e-"
                        + System.nanoTime());
        java.nio.file.Files.createDirectories(storageDir);
        String jobId = "batch-loader-restore-e2e";
        try {
            // --- run 1: fail mid-read, after several committed checkpoints ---
            StreamExecutionEnvironment env1 = newEnvironment(jobId, storageDir);
            CopyOnWriteArrayList<String> run1List = runPipeline(env1, "row-" + FAIL_AT);
            assertThrows(Exception.class, () -> env1.execute(jobId),
                    "run 1 must fail at the injected row");
            List<String> run1 = run1List;
            assertTrue(!run1.isEmpty(), "run 1 must produce records");
            assertEquals("row-1", run1.get(0), "run 1 must start at row 1");
            for (String row : run1) {
                assertTrue(rowIndex(row) < FAIL_AT,
                        "run 1 must never emit at or beyond the failing row, saw " + row);
            }

            // --- run 2: same jobId + explicit storage path → auto-restore ---
            StreamExecutionEnvironment env2 = newEnvironment(jobId, storageDir);
            CopyOnWriteArrayList<String> run2List = runPipeline(env2, null);
            env2.execute(jobId); // BOUNDED natural EOS
            List<String> run2 = run2List;

            assertTrue(!run2.isEmpty(), "run 2 must produce records");
            assertEquals("row-" + TOTAL_ROWS, run2.get(run2.size() - 1),
                    "run 2 must end at the last row");
            // the AR-13 discriminator: run 2 continues from the restored offset — NOT a
            // full re-read from row 1 (the pre-migration legacy path would have had no
            // FLIP-27 split state to restore at all).
            int firstIndex = rowIndex(run2.get(0));
            assertTrue(firstIndex >= 2 && firstIndex <= FAIL_AT,
                    "run 2 must continue from the restored offset inside the failure window "
                            + "[2," + FAIL_AT + "], but started at row " + firstIndex);
            // full coverage across the two runs
            boolean[] seen = new boolean[TOTAL_ROWS + 1];
            for (String row : run1) seen[rowIndex(row)] = true;
            for (String row : run2) seen[rowIndex(row)] = true;
            for (int i = 1; i <= TOTAL_ROWS; i++) {
                assertTrue(seen[i], "row-" + i + " was never emitted across the two runs");
            }
        } finally {
            deleteRecursive(storageDir.toFile());
        }
    }
}
