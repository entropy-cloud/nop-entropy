/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.source.AssignmentDeliveryService;
import io.nop.stream.core.source.SplitEnumeratorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 49 Phase 3: tests for the FileSource reference split-based source. Covers:
 * <ul>
 *   <li>split enumeration from a directory (round-robin distribution across subtasks),</li>
 *   <li>reader consumption of assigned splits (line emission + cursor advance),</li>
 *   <li>enumerator state snapshot/restore round-trip.</li>
 * </ul>
 *
 * <p>End-to-end execution through {@code env.addSource(FileSource)} is exercised in
 * {@code TestFileSourceE2E} (in nop-stream-runtime test sources).
 */
class TestFileSource {

    @TempDir
    Path tempDir;

    @Test
    void fileSplitIdentityIsFilePath() {
        FileSplit a = new FileSplit("/tmp/a.txt", 100);
        FileSplit b = new FileSplit("/tmp/a.txt", 0L, 100L, 50L);
        assertEquals(a, b, "FileSplit identity must be filePath only");
        assertEquals(a.splitId(), b.splitId());
        assertEquals("/tmp/a.txt", a.splitId());
    }

    @Test
    void fileSplitWithCurrentOffsetPreservesCursorForRestore() {
        FileSplit original = new FileSplit("/tmp/x.txt", 10L, 100L, 50L);
        FileSplit advanced = original.withCurrentOffset(75L);

        assertEquals(50L, original.getCurrentOffset(), "withCurrentOffset must not mutate original");
        assertEquals(75L, advanced.getCurrentOffset());
        assertEquals(original.splitId(), advanced.splitId());
        assertEquals(original.getStartOffset(), advanced.getStartOffset());
        assertEquals(original.getEndOffset(), advanced.getEndOffset());
    }

    @Test
    void enumeratorScansDirectoryAndDistributesRoundRobin() throws Exception {
        Path dir = tempDir.resolve("file-source-test");
        Files.createDirectories(dir);
        Files.write(dir.resolve("f1.txt"), Collections.singletonList("hello"));
        Files.write(dir.resolve("f2.txt"), Collections.singletonList("world"));
        Files.write(dir.resolve("f3.txt"), Collections.singletonList("!"));
        Files.write(dir.resolve("f4.txt"), Collections.singletonList("end"));

        CapturingDeliveryService delivery = new CapturingDeliveryService();
        SplitEnumeratorContext<FileSplit> ctx = new SplitEnumeratorContext<>(2, delivery);

        FileSplitEnumerator enumerator = new FileSplitEnumerator(dir.toString());
        enumerator.start(ctx);

        assertEquals(4, enumerator.getDiscoveredCount(),
                "all 4 files in directory must be discovered");

        // Register subtask 0 → it should get a share of splits
        enumerator.addReader(0);
        // Register subtask 1
        enumerator.addReader(1);

        // With round-robin across 2 subtasks, each should receive 2 of the 4 splits.
        int total = delivery.totalAssigned();
        assertEquals(4, total, "all discovered splits must be assigned");
        assertEquals(2, delivery.assignedToSubtask.get(0).size());
        assertEquals(2, delivery.assignedToSubtask.get(1).size());
    }

    @Test
    void readerConsumesAssignedSplitsAndEmitsLines() throws Exception {
        Path dir = tempDir.resolve("file-source-reader-test");
        Files.createDirectories(dir);
        Files.write(dir.resolve("file-a.txt"), Arrays.asList("alpha", "beta", "gamma"));
        Files.write(dir.resolve("file-b.txt"), Arrays.asList("one", "two"));

        io.nop.stream.core.source.SourceReaderContext readerCtx =
                new io.nop.stream.core.source.SourceReaderContext(0, 1, null);

        FileSourceReader reader = new FileSourceReader(readerCtx);
        reader.start();

        FileSplit splitA = new FileSplit(dir.resolve("file-a.txt").toString(), 0L,
                Files.size(dir.resolve("file-a.txt")));
        FileSplit splitB = new FileSplit(dir.resolve("file-b.txt").toString(), 0L,
                Files.size(dir.resolve("file-b.txt")));
        reader.addSplits(Arrays.asList(splitA, splitB));

        List<String> collected = new ArrayList<>();
        Optional<String> next;
        while ((next = reader.pollNext()).isPresent()) {
            collected.add(next.get());
        }
        // Drain one more poll after empty to let the reader close the active split
        reader.pollNext();

        // Order: splitA lines then splitB lines (FIFO assignment order)
        assertEquals(5, collected.size());
        assertTrue(collected.contains("alpha"));
        assertTrue(collected.contains("beta"));
        assertTrue(collected.contains("gamma"));
        assertTrue(collected.contains("one"));
        assertTrue(collected.contains("two"));

        reader.close();
    }

    @Test
    void readerSnapshotCapturesActiveSplitCursor() throws Exception {
        Path dir = tempDir.resolve("file-source-snapshot-test");
        Files.createDirectories(dir);
        Files.write(dir.resolve("lines.txt"), Arrays.asList("a", "b", "c", "d", "e"));

        io.nop.stream.core.source.SourceReaderContext readerCtx =
                new io.nop.stream.core.source.SourceReaderContext(0, 1, null);

        FileSourceReader reader = new FileSourceReader(readerCtx);
        reader.start();
        reader.addSplits(Collections.singletonList(
                new FileSplit(dir.resolve("lines.txt").toString(),
                        0L, Files.size(dir.resolve("lines.txt")))));

        // Consume 2 lines, then snapshot
        reader.pollNext(); // "a"
        reader.pollNext(); // "b"

        List<FileSplit> snapshot = reader.snapshotState(1L);
        assertEquals(1, snapshot.size());
        FileSplit snap = snapshot.get(0);
        // Cursor must be > 0 (we've consumed 2 lines worth of bytes)
        assertTrue(snap.getCurrentOffset() > 0,
                "snapshot cursor must reflect consumed bytes; was " + snap.getCurrentOffset());
        assertTrue(snap.getCurrentOffset() < snap.getEndOffset(),
                "snapshot cursor must be before end (not yet finished)");

        reader.close();
    }

    @Test
    void readerRestoreResumesFromCheckpointedCursor() throws Exception {
        Path dir = tempDir.resolve("file-source-restore-test");
        Files.createDirectories(dir);
        Files.write(dir.resolve("seq.txt"), Arrays.asList("L1", "L2", "L3", "L4", "L5"));

        io.nop.stream.core.source.SourceReaderContext readerCtx =
                new io.nop.stream.core.source.SourceReaderContext(0, 1, null);

        // First reader: consume L1, snapshot cursor, "crash"
        FileSourceReader r1 = new FileSourceReader(readerCtx);
        r1.start();
        r1.addSplits(Collections.singletonList(
                new FileSplit(dir.resolve("seq.txt").toString(),
                        0L, Files.size(dir.resolve("seq.txt")))));
        Optional<String> first = r1.pollNext();
        assertEquals("L1", first.orElseThrow());
        List<FileSplit> snap = r1.snapshotState(1L);
        r1.close();

        // New reader instance: restore from snapshot, consume the rest
        FileSourceReader r2 = new FileSourceReader(readerCtx);
        r2.restoreState(snap);

        List<String> rest = new ArrayList<>();
        Optional<String> next;
        while ((next = r2.pollNext()).isPresent()) {
            rest.add(next.get());
        }
        r2.pollNext();
        r2.close();

        // Cursor advances from after-L1, so we should see L2..L5 (4 lines), NOT L1 again
        assertEquals(4, rest.size(), "restored reader must skip already-consumed records");
        assertTrue(rest.contains("L2"));
        assertTrue(rest.contains("L5"));
    }

    @Test
    void enumeratorStateSnapshotRestoreRoundTrip() throws Exception {
        Path dir = tempDir.resolve("file-source-enum-state");
        Files.createDirectories(dir);
        Files.write(dir.resolve("x1.txt"), Collections.singletonList("a"));
        Files.write(dir.resolve("x2.txt"), Collections.singletonList("b"));

        CapturingDeliveryService delivery = new CapturingDeliveryService();
        SplitEnumeratorContext<FileSplit> ctx = new SplitEnumeratorContext<>(1, delivery);

        FileSplitEnumerator orig = new FileSplitEnumerator(dir.toString());
        orig.start(ctx);
        orig.addReader(0);
        // Mark one split finished
        orig.markSplitFinished(dir.resolve("x1.txt").toString());

        FileSplitEnumeratorState snap = orig.snapshotState(1L);
        assertEquals(2, snap.getDiscoveredFiles().size());
        assertEquals(1, snap.getFinishedFiles().size());
        assertTrue(snap.getFinishedFiles().contains(dir.resolve("x1.txt").toString()));

        // Restore into a fresh enumerator
        FileSplitEnumerator restored = new FileSplitEnumerator(dir.toString());
        restored.restoreState(snap);

        FileSplitEnumeratorState snap2 = restored.snapshotState(2L);
        assertEquals(snap.getDiscoveredFiles(), snap2.getDiscoveredFiles());
        assertEquals(snap.getFinishedFiles(), snap2.getFinishedFiles());
    }

    @Test
    void fileSourceSerializersRoundTrip() throws Exception {
        FileSource.FileSplitSerializer splitSer = new FileSource.FileSplitSerializer();
        FileSplit original = new FileSplit("/tmp/round.txt", 10L, 100L, 50L);
        byte[] bytes = splitSer.serialize(original);
        FileSplit restored = splitSer.deserialize(splitSer.getVersion(), bytes);
        assertEquals(original.getFilePath(), restored.getFilePath());
        assertEquals(original.getStartOffset(), restored.getStartOffset());
        assertEquals(original.getEndOffset(), restored.getEndOffset());
        assertEquals(original.getCurrentOffset(), restored.getCurrentOffset());

        FileSource.FileSplitEnumeratorStateSerializer stateSer =
                new FileSource.FileSplitEnumeratorStateSerializer();
        java.util.Set<String> disc = new java.util.LinkedHashSet<>(Arrays.asList("/tmp/a", "/tmp/b"));
        java.util.Set<String> asg = new java.util.LinkedHashSet<>(Collections.singletonList("/tmp/a"));
        java.util.Set<String> fin = new java.util.LinkedHashSet<>();
        java.util.Map<String, FileSplit> byId = new java.util.LinkedHashMap<>();
        byId.put("/tmp/a", new FileSplit("/tmp/a", 0L, 100L, 30L));
        byId.put("/tmp/b", new FileSplit("/tmp/b", 0L, 50L));
        FileSplitEnumeratorState state = new FileSplitEnumeratorState("/tmp", disc, asg, fin, byId, 7);
        byte[] stateBytes = stateSer.serialize(state);
        FileSplitEnumeratorState stateRestored = stateSer.deserialize(stateSer.getVersion(), stateBytes);
        assertEquals("/tmp", stateRestored.getDirectoryPath());
        assertEquals(7, stateRestored.getNextSubtaskIndex());
        assertEquals(2, stateRestored.getDiscoveredFiles().size());
        assertEquals(1, stateRestored.getAssignedFiles().size());
        assertEquals(2, stateRestored.getSplitById().size());
    }

    @Test
    void fileSourceContractMethodsReturnValidInstances() {
        FileSource source = new FileSource(tempDir.toString());
        assertNotNull(source.createEnumerator());
        assertNotNull(source.restoreEnumerator(null));
        assertNotNull(source.createReader(null));
        assertNotNull(source.getEnumeratorStateSerializer());
        assertNotNull(source.getSplitSerializer());
        assertEquals(io.nop.stream.core.source.Boundedness.BOUNDED, source.getBoundedness());
    }

    // ====================== Helpers ======================

    private static final class CapturingDeliveryService implements AssignmentDeliveryService<FileSplit> {
        final Map<Integer, List<FileSplit>> assignedToSubtask = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void assignSplits(int subtaskIndex, List<FileSplit> splits) {
            assignedToSubtask.computeIfAbsent(subtaskIndex, k -> Collections.synchronizedList(new ArrayList<>()))
                    .addAll(splits);
        }

        @Override
        public boolean isReaderRegistered(int subtaskIndex) {
            return assignedToSubtask.containsKey(subtaskIndex);
        }

        int totalAssigned() {
            return assignedToSubtask.values().stream().mapToInt(List::size).sum();
        }
    }
}
