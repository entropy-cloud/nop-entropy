package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 round-trip tests for the journal.md + snapshot.json format layer.
 * Verifies write→read fidelity for both formats, incremental journal reading,
 * append-only semantics, and boundary cases (empty file, corrupted section).
 *
 * <p>These tests exercise the serializers independently of the
 * {@link ICheckpointManager} contract — they prove the format is correct before
 * Phase 2 wires it into the file-backed manager.
 */
public class TestCheckpointJournalSnapshotFormat {

    @TempDir
    Path tempDir;

    // ========================================================================
    // journal.md write→read round-trip (all fields)
    // ========================================================================

    @Test
    void journalWriteReadRoundTripAllFieldsPreserved() {
        Path journalFile = tempDir.resolve("sess-1/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        CheckpointJournalReader reader = new CheckpointJournalReader();

        Checkpoint original = Checkpoint.of(
                "sess-1", "wm-1", 0, 1718445600123L,
                CheckpointType.TOOL_EXECUTION, "echo", "call-1",
                "input line1\nline2", "output\ttabbed", 5, 99L);

        writer.appendCheckpoint(journalFile, "sess-1", original);

        List<Checkpoint> read = reader.readAll(journalFile);
        assertEquals(1, read.size(), "One checkpoint written, one read");
        Checkpoint cp = read.get(0);
        assertEquals(original, cp, "Round-trip must preserve all 11 fields exactly");
        assertEquals("sess-1", cp.getSessionId());
        assertEquals("wm-1", cp.getWatermark());
        assertEquals(0, cp.getSeq());
        assertEquals(1718445600123L, cp.getTimestamp());
        assertEquals(CheckpointType.TOOL_EXECUTION, cp.getType());
        assertEquals("echo", cp.getToolName());
        assertEquals("call-1", cp.getCallId());
        assertEquals("input line1\nline2", cp.getInputSummary(),
                "Multi-line input summary must round-trip exactly");
        assertEquals("output\ttabbed", cp.getOutputSummary());
        assertEquals(5, cp.getMessageCount());
        assertEquals(99L, cp.getTokenEstimate());
    }

    @Test
    void journalWriteMultipleCheckpointsReadBackInOrder() {
        Path journalFile = tempDir.resolve("sess-2/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        CheckpointJournalReader reader = new CheckpointJournalReader();

        Checkpoint cp0 = Checkpoint.of("sess-2", "wm-a", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", null, "out-0", 1, 10L);
        Checkpoint cp1 = Checkpoint.of("sess-2", "wm-b", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "ls", "c1", null, "out-1", 2, 20L);
        Checkpoint cp2 = Checkpoint.of("sess-2", "wm-c", 2, 1002L,
                CheckpointType.TOOL_EXECUTION, "pwd", "c2", "in-2", "out-2", 3, 30L);

        writer.appendCheckpoint(journalFile, "sess-2", cp0);
        writer.appendCheckpoint(journalFile, "sess-2", cp1);
        writer.appendCheckpoint(journalFile, "sess-2", cp2);

        List<Checkpoint> read = reader.readAll(journalFile);
        assertEquals(3, read.size());
        assertEquals(cp0, read.get(0));
        assertEquals(cp1, read.get(1));
        assertEquals(cp2, read.get(2));
    }

    @Test
    void journalNullStringFieldsRoundTripAsNull() {
        Path journalFile = tempDir.resolve("sess-3/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        CheckpointJournalReader reader = new CheckpointJournalReader();

        Checkpoint original = Checkpoint.of(null, "wm-anon", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, null, null, null, null, 0, 0L);

        writer.appendCheckpoint(journalFile, null, original);

        List<Checkpoint> read = reader.readAll(journalFile);
        assertEquals(1, read.size());
        Checkpoint cp = read.get(0);
        assertEquals(original, cp);
        assertNull(cp.getSessionId());
        assertNull(cp.getToolName());
        assertNull(cp.getCallId());
        assertNull(cp.getInputSummary());
        assertNull(cp.getOutputSummary());
    }

    // ========================================================================
    // journal.md incremental read (readAfter — for snapshot-accelerated recovery)
    // ========================================================================

    @Test
    void journalReadAfterReturnsOnlyEntriesAfterWatermark() {
        Path journalFile = tempDir.resolve("sess-4/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        CheckpointJournalReader reader = new CheckpointJournalReader();

        writer.appendCheckpoint(journalFile, "sess-4",
                Checkpoint.of("sess-4", "wm-0", 0, 1000L, CheckpointType.TOOL_EXECUTION, "t0", "c0", null, "o0", 1, 10L));
        writer.appendCheckpoint(journalFile, "sess-4",
                Checkpoint.of("sess-4", "wm-1", 1, 1001L, CheckpointType.TOOL_EXECUTION, "t1", "c1", null, "o1", 2, 20L));
        writer.appendCheckpoint(journalFile, "sess-4",
                Checkpoint.of("sess-4", "wm-2", 2, 1002L, CheckpointType.TOOL_EXECUTION, "t2", "c2", null, "o2", 3, 30L));

        List<Checkpoint> after = reader.readAfter(journalFile, "wm-1");
        assertEquals(1, after.size(), "readAfter(wm-1) must return only entries after wm-1");
        assertEquals("wm-2", after.get(0).getWatermark());
    }

    @Test
    void journalReadAfterLastWatermarkReturnsEmpty() {
        Path journalFile = tempDir.resolve("sess-5/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        CheckpointJournalReader reader = new CheckpointJournalReader();

        writer.appendCheckpoint(journalFile, "sess-5",
                Checkpoint.of("sess-5", "wm-only", 0, 1000L, CheckpointType.TOOL_EXECUTION, "t", "c", null, "o", 1, 10L));

        List<Checkpoint> after = reader.readAfter(journalFile, "wm-only");
        assertTrue(after.isEmpty(), "readAfter(last) must return empty list");
    }

    @Test
    void journalReadAfterUnknownWatermarkReturnsFullListAsFallback() {
        Path journalFile = tempDir.resolve("sess-6/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        CheckpointJournalReader reader = new CheckpointJournalReader();

        writer.appendCheckpoint(journalFile, "sess-6",
                Checkpoint.of("sess-6", "wm-a", 0, 1000L, CheckpointType.TOOL_EXECUTION, "t", "c", null, "o", 1, 10L));
        writer.appendCheckpoint(journalFile, "sess-6",
                Checkpoint.of("sess-6", "wm-b", 1, 1001L, CheckpointType.TOOL_EXECUTION, "t", "c", null, "o", 2, 20L));

        List<Checkpoint> after = reader.readAfter(journalFile, "nonexistent-wm");
        assertEquals(2, after.size(),
                "readAfter(unknown) must degrade to full list (no checkpoint silently dropped)");
    }

    // ========================================================================
    // journal.md append-only semantics
    // ========================================================================

    @Test
    void journalAppendOnlyFileGrowsWithEachWrite() {
        Path journalFile = tempDir.resolve("sess-7/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();

        writer.appendCheckpoint(journalFile, "sess-7",
                Checkpoint.of("sess-7", "wm-0", 0, 1000L, CheckpointType.TOOL_EXECUTION, "t", "c", null, "o", 1, 10L));
        long sizeAfter1;
        try {
            sizeAfter1 = Files.size(journalFile);
        } catch (Exception e) {
            throw new NopAiAgentException("Unexpected I/O failure", e);
        }

        writer.appendCheckpoint(journalFile, "sess-7",
                Checkpoint.of("sess-7", "wm-1", 1, 1001L, CheckpointType.TOOL_EXECUTION, "t", "c", null, "o", 2, 20L));
        long sizeAfter2;
        try {
            sizeAfter2 = Files.size(journalFile);
        } catch (Exception e) {
            throw new NopAiAgentException("Unexpected I/O failure", e);
        }

        assertTrue(sizeAfter2 > sizeAfter1,
                "Append-only: file must grow with each write (was " + sizeAfter1 + ", now " + sizeAfter2 + ")");

        CheckpointJournalReader reader = new CheckpointJournalReader();
        assertEquals(2, reader.readAll(journalFile).size(),
                "Both checkpoints must be present after append-only writes");
    }

    @Test
    void journalHeaderContainsSessionId() {
        Path journalFile = tempDir.resolve("sess-hdr/journal.md");
        CheckpointJournalWriter writer = new CheckpointJournalWriter();

        writer.appendCheckpoint(journalFile, "my-session",
                Checkpoint.of("my-session", "wm-1", 0, 1000L, CheckpointType.TOOL_EXECUTION, "t", "c", null, "o", 1, 10L));

        String content;
        try {
            content = Files.readString(journalFile);
        } catch (Exception e) {
            throw new NopAiAgentException("Unexpected I/O failure", e);
        }
        assertTrue(content.startsWith("# Checkpoint Journal - my-session\n"),
                "Journal must start with §5.4a header containing sessionId");
    }

    // ========================================================================
    // journal.md boundary cases
    // ========================================================================

    @Test
    void emptyOrMissingJournalReadsAsEmptyList() {
        CheckpointJournalReader reader = new CheckpointJournalReader();
        assertEquals(0, reader.readAll(tempDir.resolve("nonexistent/journal.md")).size(),
                "Missing journal file must read as empty list, not throw");
    }

    @Test
    void headerOnlyJournalReadsAsEmptyList() {
        Path journalFile = tempDir.resolve("sess-empty/journal.md");
        try {
            Files.createDirectories(journalFile.getParent());
            Files.writeString(journalFile, "# Checkpoint Journal - sess-empty\n\n");
        } catch (Exception e) {
            throw new NopAiAgentException("Unexpected I/O failure", e);
        }

        CheckpointJournalReader reader = new CheckpointJournalReader();
        assertEquals(0, reader.readAll(journalFile).size(),
                "Header-only journal (no sections) must read as empty list");
    }

    @Test
    void corruptedSectionSkippedWithRemainingParsed() {
        Path journalFile = tempDir.resolve("sess-bad/journal.md");
        // Write a good checkpoint, then a corrupted section, then a good one.
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        writer.appendCheckpoint(journalFile, "sess-bad",
                Checkpoint.of("sess-bad", "wm-good-0", 0, 1000L, CheckpointType.TOOL_EXECUTION, "t", "c", null, "o", 1, 10L));

        // Append a corrupted section (missing required fields).
        String corrupted = "## CP-999\ntype: TOOL_EXECUTION\nseq: 999\n\n";
        try {
            Files.writeString(journalFile, corrupted,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            throw new NopAiAgentException("Unexpected I/O failure", e);
        }

        // Append another good checkpoint.
        writer.appendCheckpoint(journalFile, "sess-bad",
                Checkpoint.of("sess-bad", "wm-good-1", 1, 1001L, CheckpointType.TOOL_EXECUTION, "t1", "c1", null, "o1", 2, 20L));

        CheckpointJournalReader reader = new CheckpointJournalReader();
        List<Checkpoint> read = reader.readAll(journalFile);
        assertEquals(2, read.size(),
                "Corrupted section must be skipped; the two good sections must parse");
        assertEquals("wm-good-0", read.get(0).getWatermark());
        assertEquals("wm-good-1", read.get(1).getWatermark());
    }

    // ========================================================================
    // snapshot.json write→read round-trip
    // ========================================================================

    @Test
    void snapshotWriteReadRoundTripAllFieldsPreserved() {
        Path snapshotFile = tempDir.resolve("sess-snap/snapshot.json");
        CheckpointSnapshotWriter writer = new CheckpointSnapshotWriter();
        CheckpointSnapshotReader reader = new CheckpointSnapshotReader();

        long ts = 1718445600456L;
        CheckpointSnapshot original = CheckpointSnapshot.of(
                "snap-001", "sess-snap", "wm-last", 14, 8500L, ts);

        writer.write(snapshotFile, original);

        CheckpointSnapshot read = reader.readIfExists(snapshotFile);
        assertNotNull(read, "Snapshot file was written, must be readable");
        assertEquals(original, read, "Round-trip must preserve all fields");
        assertEquals("snap-001", read.getSnapshotId());
        assertEquals("sess-snap", read.getSessionId());
        assertEquals("wm-last", read.getLastWatermark());
        assertEquals(14, read.getMessageCount());
        assertEquals(8500L, read.getTokenEstimate());
        assertEquals(ts, read.getCreatedAtEpochMillis());
        assertEquals(Instant.ofEpochMilli(ts).toString(), read.getCreatedAtIso());
    }

    @Test
    void snapshotMissingFileReturnsNull() {
        CheckpointSnapshotReader reader = new CheckpointSnapshotReader();
        assertNull(reader.readIfExists(tempDir.resolve("no-such/snapshot.json")),
                "Missing snapshot file must return null (legitimate — no snapshot generated yet)");
    }

    @Test
    void snapshotOverwriteReplacesPreviousContent() {
        Path snapshotFile = tempDir.resolve("sess-ovr/snapshot.json");
        CheckpointSnapshotWriter writer = new CheckpointSnapshotWriter();
        CheckpointSnapshotReader reader = new CheckpointSnapshotReader();

        writer.write(snapshotFile, CheckpointSnapshot.of("snap-old", "sess", "wm-1", 3, 30L, 1000L));
        writer.write(snapshotFile, CheckpointSnapshot.of("snap-new", "sess", "wm-2", 5, 50L, 2000L));

        CheckpointSnapshot read = reader.readIfExists(snapshotFile);
        assertEquals("snap-new", read.getSnapshotId(), "Overwrite must replace previous snapshot");
        assertEquals("wm-2", read.getLastWatermark());
        assertEquals(5, read.getMessageCount());
    }
}
