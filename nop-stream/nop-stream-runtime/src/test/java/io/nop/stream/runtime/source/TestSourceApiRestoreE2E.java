/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import io.nop.stream.connector.file.FileSource;
import io.nop.stream.connector.file.FileSourceReader;
import io.nop.stream.connector.file.FileSplitEnumerator;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 plan 2343 Phase 0 (B-1): the FLIP-27 restore path end-to-end. The pre-fix engine
 * dropped restored reader splits ({@code restoreState} ran before {@code open()} created
 * the reader) and never fed {@code EpochManifest.sourceEnumeratorSnapshots} back into the
 * coordinator — a restored job silently re-read from scratch. This test drives the REAL
 * chain (FileSource → SourceReaderOperator → LocalSourceCoordinator → manifest stash)
 * through a mid-read failure and asserts the restored run CONTINUES from the checkpointed
 * cursor instead of restarting at line 1.
 *
 * <p>Exactly-once is NOT asserted: a plain AT_LEAST_ONCE source re-emits the records
 * emitted after the last barrier before the failure — the assertions are continuation
 * (run 2 resumes inside the failure window), per-run sequential order, and full
 * cross-run coverage.
 */
public class TestSourceApiRestoreE2E {

    private static final int TOTAL_LINES = 500;
    private static final int FAIL_AT = 50;
    /**
     * Rate-limit pace (rows/s): the failing line arrives ≈500ms in — well after the
     * first 100ms checkpoint, so the restored cursor sits past line 1.
     */
    private static final long RATE_ROWS_PER_SECOND = 100L;

    @TempDir
    Path tempDir;

    /** Failing collector: throws when the configured line arrives (mid-read kill). */
    static final class CollectOrFailSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;
        private final CopyOnWriteArrayList<String> collected;
        private final String failAtLine;

        CollectOrFailSink(CopyOnWriteArrayList<String> collected, String failAtLine) {
            this.collected = collected;
            this.failAtLine = failAtLine;
        }

        @Override
        public void consume(String value) {
            if (failAtLine != null && failAtLine.equals(value)) {
                throw new IllegalStateException("injected failure at " + value);
            }
            collected.add(value);
        }
    }

    private Path writeInputFile() throws IOException {
        Path dir = tempDir.resolve("input");
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= TOTAL_LINES; i++) {
            sb.append("line-").append(i).append('\n');
        }
        Files.write(dir.resolve("data.txt"), sb.toString().getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private StreamExecutionEnvironment newEnvironment(String jobId, Path storageDir) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        // enableCheckpointing flips checkpointEnabled AND declares the intent — without
        // it env.execute skips the checkpoint engine entirely (no manifest, no restore).
        env.enableCheckpointing(100L);
        env.getCheckpointConfig().setJobId(jobId);
        env.getCheckpointConfig().setCheckpointTimeout(5000L);
        env.getCheckpointConfig().setMinPause(50L);
        env.getCheckpointConfig().setStorageProperty("path", storageDir.toString());
        return env;
    }

    private CopyOnWriteArrayList<String> runPipeline(StreamExecutionEnvironment env, Path inputDir,
                                                     String failAtLine) {
        FileSource source = new FileSource(inputDir.toString(),
                FileSourceReader.DEFAULT_MAX_LINE_BYTES,
                RATE_ROWS_PER_SECOND, 0L, FileSplitEnumerator.POLICY_ROUND_ROBIN);
        CopyOnWriteArrayList<String> collected = new CopyOnWriteArrayList<>();
        env.addSource(source, "restore-src")
                .sink(new CollectOrFailSink(collected, failAtLine));
        return collected;
    }

    private static int lineIndex(String line) {
        return Integer.parseInt(line.substring("line-".length()));
    }

    @Test
    void restoredRunContinuesFromCheckpointedCursorInsteadOfRestarting() throws Exception {
        Path inputDir = writeInputFile();
        Path storageDir = tempDir.resolve("st-restore");
        String jobId = "source-restore-e2e";

        // --- run 1: fail mid-read, after several committed checkpoints ---
        StreamExecutionEnvironment env1 = newEnvironment(jobId, storageDir);
        CopyOnWriteArrayList<String> run1List = runPipeline(env1, inputDir, "line-" + FAIL_AT);
        assertThrows(Exception.class, () -> env1.execute(jobId),
                "run 1 must fail at the injected line");
        List<String> run1 = run1List;
        // The supervisor may restart the region within its budget, so run 1 can contain
        // repeated post-checkpoint segments; the deterministic facts are: it starts at
        // line 1, never progresses past the failing line, and never collects the
        // failing line itself (thrown before collect).
        assertTrue(!run1.isEmpty(), "run 1 must produce records");
        assertEquals("line-1", run1.get(0), "run 1 must start at line 1");
        for (String line : run1) {
            assertTrue(lineIndex(line) < FAIL_AT,
                    "run 1 must never emit at or beyond the failing line, saw " + line);
        }

        // --- run 2: same jobId + same explicit storage path → auto-restore ---
        StreamExecutionEnvironment env2 = newEnvironment(jobId, storageDir);
        CopyOnWriteArrayList<String> run2List = runPipeline(env2, inputDir, null);
        env2.execute(jobId); // must complete (BOUNDED, natural EOS)
        List<String> run2 = run2List;

        assertTrue(!run2.isEmpty(), "run 2 must produce records");
        assertEquals("line-" + TOTAL_LINES, run2.get(run2.size() - 1),
                "run 2 must end at the last line");
        // THE B-1 DISCRIMINATOR: run 2 must NOT restart at line 1 — a fresh reader plus
        // a fresh enumerator would re-read the whole file from the beginning.
        int firstIndex = lineIndex(run2.get(0));
        assertTrue(firstIndex >= 2 && firstIndex <= FAIL_AT,
                "run 2 must continue from the restored cursor inside the failure window "
                        + "[2," + FAIL_AT + "], but started at line " + firstIndex);
        for (int i = 1; i < run2.size(); i++) {
            assertEquals(lineIndex(run2.get(i - 1)) + 1, lineIndex(run2.get(i)),
                    "run 2 must read sequentially, broke at "
                            + run2.get(i - 1) + " -> " + run2.get(i));
        }
        // full coverage across the two runs (AT_LEAST_ONCE: duplicates allowed inside
        // the cursor window, but every line must be seen at least once)
        boolean[] seen = new boolean[TOTAL_LINES + 1];
        for (String line : run1) seen[lineIndex(line)] = true;
        for (String line : run2) seen[lineIndex(line)] = true;
        for (int i = 1; i <= TOTAL_LINES; i++) {
            assertTrue(seen[i], "line-" + i + " was never emitted across the two runs");
        }
    }
}
