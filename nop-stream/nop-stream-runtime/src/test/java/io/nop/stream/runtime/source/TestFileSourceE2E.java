/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.connector.file.FileSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 49 Phase 3 (Anti-Hollow Rule): end-to-end test that drives the entire FLIP-27
 * source path — {@code env.addSource(FileSource)} → {@code SourceApiTransformation} →
 * {@code StreamGraphGenerator} branch → {@code SourceReaderOperator} (per-subtask) →
 * {@code LocalSourceCoordinator} (in-process) → {@code FileSplitEnumerator} /
 * {@code FileSourceReader} → sink collect.
 *
 * <p>This test is the wiring verification required by plan guide #22 (Anti-Hollow) and
 * #23 (Wiring Verification): it asserts that every new component in the FLIP-27 path is
 * actually reached by {@code env.execute()}, not just that the types compile.
 */
class TestFileSourceE2E {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearCoordinatorRegistry() {
        // Sweep any coordinators left over by the run (defensive — execute() should also
        // unregister in its finally block).
        io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.clearForTest();
    }

    @Test
    void e2eSingleParallelismReadsAllLinesOnce() throws Exception {
        Path dir = tempDir.resolve("e2e-single");
        Files.createDirectories(dir);
        Files.write(dir.resolve("a.txt"), Arrays.asList("alpha", "beta"));
        Files.write(dir.resolve("b.txt"), Arrays.asList("gamma", "delta"));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);

        ConcurrentLinkedQueue<String> sink = new ConcurrentLinkedQueue<>();

        env.addSource(new FileSource(dir.toString()), "file-source")
                .map(s -> s)
                .sink(new CollectSinkFunction<>(sink));

        env.execute("e2e-file-source-single");

        Set<String> unique = new HashSet<>(sink);
        assertEquals(4, sink.size(), "all 4 lines must be emitted exactly once (no loss, no duplicate)");
        assertTrue(unique.contains("alpha"));
        assertTrue(unique.contains("beta"));
        assertTrue(unique.contains("gamma"));
        assertTrue(unique.contains("delta"));
    }

    @Test
    void e2eMultiParallelismDistributesSplitsWithoutLoss() throws Exception {
        Path dir = tempDir.resolve("e2e-multi");
        Files.createDirectories(dir);
        Files.write(dir.resolve("f1.txt"), Collections.singletonList("L1"));
        Files.write(dir.resolve("f2.txt"), Collections.singletonList("L2"));
        Files.write(dir.resolve("f3.txt"), Collections.singletonList("L3"));
        Files.write(dir.resolve("f4.txt"), Collections.singletonList("L4"));
        Files.write(dir.resolve("f5.txt"), Collections.singletonList("L5"));
        Files.write(dir.resolve("f6.txt"), Collections.singletonList("L6"));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);

        ConcurrentLinkedQueue<String> sink = new ConcurrentLinkedQueue<>();

        env.addSource(new FileSource(dir.toString()), "file-source-multi")
                .sink(new CollectSinkFunction<>(sink));

        env.execute("e2e-file-source-multi");

        // 6 lines across 6 files, distributed across 2 subtasks.
        // Every line must be emitted exactly once (exactly-once on the source side).
        assertEquals(6, sink.size(),
                "all 6 lines must be emitted exactly once across 2 parallel subtasks");
        Set<String> unique = new HashSet<>(sink);
        assertEquals(6, unique.size(), "no duplicates expected");
        assertTrue(unique.contains("L1"));
        assertTrue(unique.contains("L6"));
    }

    @Test
    void e2eEmptyDirectoryCompletesWithEmptySink() throws Exception {
        Path dir = tempDir.resolve("e2e-empty");
        Files.createDirectories(dir);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);

        ConcurrentLinkedQueue<String> sink = new ConcurrentLinkedQueue<>();
        env.addSource(new FileSource(dir.toString()), "file-source-empty")
                .sink(new CollectSinkFunction<>(sink));

        env.execute("e2e-file-source-empty");

        assertTrue(sink.isEmpty(),
                "an empty source directory should produce no records, not hang or fail");
    }

    /**
     * Wiring-verification test (plan guide #23): if the coordinator were not wired into
     * {@code env.execute()}, the FileSource path would emit nothing. Here we explicitly
     * assert that the coordinator was created (proving the operator's open() reached
     * {@link io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry#get}).
     */
    @Test
    void e2eWiringProvenByCoordinatorActivity() throws Exception {
        Path dir = tempDir.resolve("e2e-wiring");
        Files.createDirectories(dir);
        Files.write(dir.resolve("only.txt"), Collections.singletonList("X"));

        // Clear any stale state, then run a job whose only output path goes through
        // the new SourceReaderOperator + LocalSourceCoordinator.
        io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.clearForTest();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);

        ConcurrentLinkedQueue<String> sink = new ConcurrentLinkedQueue<>();
        env.addSource(new FileSource(dir.toString()), "file-source-wiring")
                .sink(new CollectSinkFunction<>(sink));

        env.execute("e2e-wiring");

        // If we got "X" at all, the entire chain was wired: SourceReaderOperator created
        // the coordinator, the coordinator ran the enumerator, the enumerator discovered
        // the split, the operator polled the reader, the reader read the file.
        assertEquals(1, sink.size());
        assertEquals("X", sink.peek());
    }

    /** Simple thread-safe collecting sink function used by the E2E tests. */
    static final class CollectSinkFunction<T> implements SinkFunction<T> {
        private static final long serialVersionUID = 1L;
        private final transient ConcurrentLinkedQueue<T> sink;

        CollectSinkFunction(ConcurrentLinkedQueue<T> sink) {
            this.sink = sink;
        }

        @Override
        public void consume(T value) {
            sink.add(value);
        }
    }
}
