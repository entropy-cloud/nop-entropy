/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FileTwoPhaseCommitSink} (Stage 53 Phase 2).
 */
public class TestFileTwoPhaseCommitSink {

    private Path outputDir;
    private FileTwoPhaseCommitSink<String> sink;

    @BeforeEach
    void setUp() throws Exception {
        outputDir = Files.createTempDirectory("file-sink-test");
        sink = new FileTwoPhaseCommitSink<>(outputDir.toString(), StandardCharsets.UTF_8);
        sink.beginTransaction();
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(outputDir);
    }

    private void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception e) {
                        // ignore
                    }
                });
    }

    private List<String> readLines(Path file) throws Exception {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private int countLinesInFinalFiles() throws Exception {
        int total = 0;
        for (Path p : Files.newDirectoryStream(outputDir, "epoch-*.txt")) {
            total += readLines(p).size();
        }
        return total;
    }

    // ---- consistency / participant ----

    @Test
    void testGetSinkConsistencyReturnsTwoPhaseCommit() {
        assertEquals(SinkConsistencyCapability.TWO_PHASE_COMMIT, sink.getSinkConsistency());
    }

    @Test
    void testSinkIsCheckpointParticipant() {
        assertInstanceOf(CheckpointParticipant.class, sink);
        assertInstanceOf(TwoPhaseCommitSinkFunction.class, sink);
    }

    // ---- invoke buffers in memory ----

    @Test
    void testInvokeBuffersInMemory() throws Exception {
        sink.consume("a");
        sink.consume("b");
        List<String> snapshot = sink.getCurrentBufferSnapshot();
        assertEquals(2, snapshot.size());
        assertEquals("a", snapshot.get(0));
        assertEquals("b", snapshot.get(1));
    }

    // ---- saveState writes temp file + clears buffer ----

    @Test
    void testSaveStateWritesTempFileAndClearsBuffer() throws Exception {
        sink.consume("line-1");
        sink.consume("line-2");

        sink.saveState(1L);

        // Buffer should be cleared
        assertTrue(sink.getCurrentBufferSnapshot().isEmpty());

        // pendingCommits[1L] should hold a FilePendingCommit
        Object raw = sink.getPendingCommits().get(1L);
        assertNotNull(raw);
        assertInstanceOf(FilePendingCommit.class, raw);
        FilePendingCommit fpc = (FilePendingCommit) raw;
        assertEquals(2, fpc.getRecordCount());

        // Temp file should exist with 2 lines
        Path tempPath = sink.tempPath(1L);
        assertTrue(Files.exists(tempPath), "temp file should exist after saveState");
        assertEquals(2, readLines(tempPath).size());
    }

    // ---- commit: atomic rename + manifest update ----

    @Test
    void testCommitAtomicRenamesAndUpdatesManifest() throws Exception {
        sink.consume("x");
        sink.consume("y");
        sink.saveState(1L);

        Path tempPath = sink.tempPath(1L);
        assertTrue(Files.exists(tempPath));

        sink.commit(1L);

        // Temp file gone, final file exists
        assertFalse(Files.exists(tempPath), "temp file must be renamed away");
        Path finalPath = sink.finalPath(1L);
        assertTrue(Files.exists(finalPath), "final file must exist after commit");
        List<String> lines = readLines(finalPath);
        assertEquals(2, lines.size());
        assertEquals("x", lines.get(0));
        assertEquals("y", lines.get(1));

        // Manifest records the epoch
        assertTrue(sink.isEpochCommitted(1L));
        assertFalse(sink.getPendingCommits().containsKey(1L));

        // No manifest temp file left behind
        assertFalse(Files.exists(outputDir.resolve("manifest.properties.tmp")));
    }

    // ---- idempotent commit ----

    @Test
    void testIdempotentCommitNoDuplicateRename() throws Exception {
        sink.consume("only");
        sink.saveState(1L);
        sink.commit(1L);

        // Simulate recover-safe re-commit: put the pending back and commit again
        Path finalPath = sink.finalPath(1L);
        long sizeBefore = Files.size(finalPath);
        assertTrue(sink.isEpochCommitted(1L));

        sink.getPendingCommits().put(1L, new FilePendingCommit(sink.tempPath(1L).toString(), 1));
        sink.commit(1L); // idempotent skip (manifest already records epoch 1)

        long sizeAfter = Files.size(finalPath);
        assertEquals(sizeBefore, sizeAfter, "idempotent re-commit must not duplicate data");
        assertEquals(1, readLines(finalPath).size());
    }

    // ---- final-exists but manifest-missing: repair manifest ----

    @Test
    void testCommitFinalExistsManifestMissingRepairsManifest() throws Exception {
        sink.consume("recovered");
        sink.saveState(1L);
        // Simulate crash AFTER atomic rename but BEFORE manifest write:
        // manually do the rename, leave manifest empty
        Files.move(sink.tempPath(1L), sink.finalPath(1L),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        assertFalse(sink.isEpochCommitted(1L), "manifest should not yet record epoch 1");

        // commit should repair the manifest rather than throw
        sink.getPendingCommits().put(1L, new FilePendingCommit(
                sink.tempPath(1L).toString(), 1));
        assertDoesNotThrow(() -> sink.commit(1L));

        assertTrue(sink.isEpochCommitted(1L),
                "manifest must be repaired to record epoch 1");
        assertEquals(1, readLines(sink.finalPath(1L)).size(),
                "final file content must be preserved");
    }

    // ---- abort ----

    @Test
    void testAbortDeletesTempFile() throws Exception {
        sink.consume("doomed");
        sink.saveState(1L);
        Path tempPath = sink.tempPath(1L);
        assertTrue(Files.exists(tempPath));

        sink.abort(1L);

        assertFalse(Files.exists(tempPath), "abort must delete the temp file");
        assertFalse(sink.getPendingCommits().containsKey(1L));
        assertFalse(Files.exists(sink.finalPath(1L)), "no final file after abort");
    }

    @Test
    void testAbortOnNonExistentTempIsSafe() {
        // abort an epoch that has no temp file (already renamed or never written)
        assertDoesNotThrow(() -> sink.abort(999L),
                "abort on non-existent temp file must be a safe no-op");
    }

    // ---- FilePendingCommit serialization ----

    @Test
    void testFilePendingCommitSerializableRoundTrip() throws Exception {
        FilePendingCommit original = new FilePendingCommit("/tmp/.epoch-42.tmp", 100);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(original);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(bos.toByteArray()));
        FilePendingCommit restored = (FilePendingCommit) ois.readObject();

        assertEquals("/tmp/.epoch-42.tmp", restored.getTempPath());
        assertEquals(100, restored.getRecordCount());
    }

    // ---- E2E: kill/recover exactly-once ----

    @Test
    void testFileSinkKillRecoverExactlyOnce() throws Exception {
        // Epoch 1: 3 records, committed normally
        sink.consume("e1-a");
        sink.consume("e1-b");
        sink.consume("e1-c");
        sink.saveState(1L);
        sink.commit(1L);

        // Epoch 2: 2 records, saveState but CRASH before commit (durable-but-uncommitted)
        sink.consume("e2-a");
        sink.consume("e2-b");
        sink.saveState(2L);
        // simulate crash: do NOT call commit(2L). Capture the pending entry.
        Object epoch2Pending = sink.getPendingCommits().get(2L);
        assertNotNull(epoch2Pending);

        // Recovery: a fresh sink instance restores pendingCommits and re-commits durable epoch 2
        FileTwoPhaseCommitSink<String> recovered =
                new FileTwoPhaseCommitSink<>(outputDir.toString(), StandardCharsets.UTF_8);
        recovered.beginTransaction();
        recovered.getPendingCommits().put(2L, epoch2Pending);
        recovered.restoreFromEpoch(2L, null);

        // Assertions: no dupes, no loss
        // Epoch 1 final file: 3 lines (committed once, not re-committed by restore since ledger/manifest guards)
        // Epoch 2 final file: 2 lines (re-committed on restore)
        assertTrue(Files.exists(recovered.finalPath(1L)));
        assertTrue(Files.exists(recovered.finalPath(2L)));
        assertEquals(3, readLines(recovered.finalPath(1L)).size());
        assertEquals(2, readLines(recovered.finalPath(2L)).size());
        assertEquals(5, countLinesInFinalFiles(),
                "total final-file lines must equal total source records (3 + 2 = 5): no dupes, no loss");

        // No leftover temp files
        try (var stream = Files.newDirectoryStream(outputDir, "*.tmp")) {
            List<Path> temps = new ArrayList<>();
            stream.forEach(temps::add);
            assertTrue(temps.isEmpty(), "no temp files should remain after recovery");
        }
    }

    // ---- P0: per-subtask isolation under parallelism > 1 ----

    /**
     * P0 regression (parallelism &gt; 1 batch loss): the runtime deep-copies the operator
     * chain per subtask ({@code OperatorChain.deepCopy(taskIndex)}). The two copies below
     * simulate the deepCopy products for subtask 0 and subtask 1. Each copy must buffer,
     * snapshot and commit its own batch for the SAME epoch without the other copy
     * overwriting it (shared pendingCommits / identical temp paths previously left only
     * the last-written subtask's data committed — exactly-once broken).
     */
    @Test
    void testParallelSubtaskCopiesCommitIndependently() throws Exception {
        FileTwoPhaseCommitSink<String> template =
                new FileTwoPhaseCommitSink<>(outputDir.toString(), StandardCharsets.UTF_8);
        FileTwoPhaseCommitSink<String> subtask0 = template.copyForSubtask(0);
        FileTwoPhaseCommitSink<String> subtask1 = template.copyForSubtask(1);
        assertNotSame(subtask0, subtask1, "each subtask must get an independent sink copy");
        assertEquals(0, subtask0.getSubtaskIndex());
        assertEquals(1, subtask1.getSubtaskIndex());

        subtask0.beginTransaction();
        subtask1.beginTransaction();

        subtask0.consume("s0-a");
        subtask0.consume("s0-b");
        subtask1.consume("s1-a");
        subtask1.consume("s1-b");
        subtask1.consume("s1-c");

        // Both subtasks receive the SAME barrier epoch; each saveState must land in the
        // copy's OWN pendingCommits map with its OWN batch (no mutual overwrite).
        subtask0.saveState(1L);
        subtask0.preCommit(1L);
        subtask1.saveState(1L);
        subtask1.preCommit(1L);

        FilePendingCommit pending0 = (FilePendingCommit) subtask0.getPendingCommits().get(1L);
        FilePendingCommit pending1 = (FilePendingCommit) subtask1.getPendingCommits().get(1L);
        assertNotNull(pending0, "subtask 0's pending batch must survive subtask 1's saveState");
        assertNotNull(pending1, "subtask 1's pending batch must exist");
        assertNotSame(pending0, pending1);
        assertEquals(2, pending0.getRecordCount());
        assertEquals(3, pending1.getRecordCount());
        assertEquals(0, pending0.getSubtaskIndex());
        assertEquals(1, pending1.getSubtaskIndex());
        // Distinct temp files: subtask 1's write must not truncate subtask 0's temp file
        assertEquals(2, readLines(java.nio.file.Paths.get(pending0.getTempPath())).size(),
                "subtask 0's temp file must keep its own lines");
        assertEquals(3, readLines(java.nio.file.Paths.get(pending1.getTempPath())).size());

        // Commit from both subtasks: every record must be committed, none overwritten.
        subtask0.commit(1L);
        subtask1.commit(1L);

        assertEquals(java.util.Arrays.asList("s0-a", "s0-b"), readLines(subtask0.finalPath(1L)));
        assertEquals(java.util.Arrays.asList("s1-a", "s1-b", "s1-c"), readLines(subtask1.finalPath(1L)));
        assertEquals(5, countLinesInFinalFiles(),
                "all records from both subtasks must be committed — no loss, no overwrite");
        assertTrue(subtask0.getPendingCommits().isEmpty());
        assertTrue(subtask1.getPendingCommits().isEmpty());
    }

    /**
     * The operator-level wiring must route {@code copyForSubtask(int)} into the 2PC udf
     * copy: parallel operator copies wrap independent sink functions (this is what
     * protects {@code processBarrier → saveState} under parallelism &gt; 1).
     */
    @Test
    void testStreamSinkOperatorCopiesIsolateTwoPhaseCommitUdf() {
        FileTwoPhaseCommitSink<String> template =
                new FileTwoPhaseCommitSink<>(outputDir.toString(), StandardCharsets.UTF_8);
        io.nop.stream.core.operators.StreamSinkOperator<String> op =
                new io.nop.stream.core.operators.StreamSinkOperator<>(template);

        io.nop.stream.core.operators.StreamSinkOperator<String> copy0 = op.copyForSubtask(0);
        io.nop.stream.core.operators.StreamSinkOperator<String> copy1 = op.copyForSubtask(1);

        assertNotSame(copy0.getUserFunction(), copy1.getUserFunction(),
                "2PC sink udf must be copied per subtask, not shared");
        assertEquals(0, ((FileTwoPhaseCommitSink<?>) copy0.getUserFunction()).getSubtaskIndex());
        assertEquals(1, ((FileTwoPhaseCommitSink<?>) copy1.getUserFunction()).getSubtaskIndex());
    }

    // ---- wiring: coordinator finishCommit drives file commit ----

    @Test
    void testCoordinatorFinishCommitDrivesFileCommit() throws Exception {
        sink.consume("f1");
        sink.saveState(1L);
        sink.consume("f2");
        sink.saveState(2L);

        // finishCommit(2, true) should commit both epochs <= 2 (subsuming commit driven by coordinator)
        sink.finishCommit(2L, true);

        assertTrue(Files.exists(sink.finalPath(1L)), "finishCommit must commit epoch 1");
        assertTrue(Files.exists(sink.finalPath(2L)), "finishCommit must commit epoch 2");
        assertEquals(1, readLines(sink.finalPath(1L)).size());
        assertEquals(1, readLines(sink.finalPath(2L)).size());
        assertTrue(sink.getPendingCommits().isEmpty(),
                "all pending must be cleared after finishCommit");
    }
}
