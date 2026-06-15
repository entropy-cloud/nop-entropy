package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 functional tests for {@link FileBackedCheckpointManager}: verifies
 * the core value — save→persist→reload→retrieve round-trip across instances
 * (simulating process restart), watermark recovery with/without snapshot,
 * per-session file isolation, and snapshot generation.
 */
public class TestFileBackedCheckpointManager {

    @TempDir
    Path tempDir;

    // ========================================================================
    // save → persist → reload → retrieve (core value — cross-instance survival)
    // ========================================================================

    @Test
    void savePersistReloadRetrieveSurvivesNewInstance() {
        Path root = tempDir.resolve("ckpt");

        // Instance 1: save checkpoints
        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-0", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", "in0", "out-0", 1, 10L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-1", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "ls", "c1", "in1", "out-1", 2, 20L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-2", 2, 1002L,
                CheckpointType.TOOL_EXECUTION, "pwd", "c2", "in2", "out-2", 3, 30L));

        // Instance 2: simulate process restart — new manager, same root
        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);

        Checkpoint latest = mgr2.getLatestCheckpoint("sess");
        assertNotNull(latest, "After reload, getLatestCheckpoint must return the last saved checkpoint");
        assertEquals("wm-2", latest.getWatermark());
        assertEquals("pwd", latest.getToolName());
        assertEquals(2, latest.getSeq());
        assertEquals("out-2", latest.getOutputSummary());

        // getCheckpoint by watermark must also work across instances
        Checkpoint byWm = mgr2.getCheckpoint("wm-0");
        assertNotNull(byWm, "getCheckpoint(wm-0) must find the checkpoint from the journal after reload");
        assertEquals("echo", byWm.getToolName());

        Checkpoint byWm1 = mgr2.getCheckpoint("wm-1");
        assertNotNull(byWm1);
        assertEquals("ls", byWm1.getToolName());
    }

    @Test
    void reloadWithNoCheckpointsReturnsNull() {
        Path root = tempDir.resolve("empty");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root);

        assertNull(mgr.getLatestCheckpoint("never-saved"),
                "Session with no journal file must return null");
        assertNull(mgr.getLatestCheckpoint(null),
                "getLatestCheckpoint(null) must return null");
    }

    // ========================================================================
    // Watermark recovery acceleration (snapshot present)
    // ========================================================================

    @Test
    void snapshotAcceleratedRecoveryReturnsCorrectLatestAfterReload() {
        Path root = tempDir.resolve("snap-recovery");

        // Instance 1: save 5 checkpoints + flush snapshot at the end
        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        for (int i = 0; i < 5; i++) {
            mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-" + i, i, 1000L + i,
                    CheckpointType.TOOL_EXECUTION, "tool" + i, "c" + i, null, "out" + i, i + 1, (long) (10 * (i + 1))));
        }
        mgr1.flushSnapshot("sess");

        // Verify snapshot file was written
        java.nio.file.Path snapshotFile = root.resolve("sess").resolve(FileBackedCheckpointManager.SNAPSHOT_FILE_NAME);
        assertEquals(true, java.nio.file.Files.exists(snapshotFile),
                "flushSnapshot must write snapshot.json");

        // Instance 2: reload — snapshot exists, latest checkpoint must be correct
        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);
        Checkpoint latest = mgr2.getLatestCheckpoint("sess");
        assertNotNull(latest);
        assertEquals("wm-4", latest.getWatermark());
        assertEquals("tool4", latest.getToolName());

        // All checkpoints must be retrievable by watermark
        for (int i = 0; i < 5; i++) {
            assertNotNull(mgr2.getCheckpoint("wm-" + i),
                    "getCheckpoint(wm-" + i + ") must work after snapshot-accelerated reload");
        }
    }

    // ========================================================================
    // Degraded recovery (no snapshot — full journal scan)
    // ========================================================================

    @Test
    void degradedRecoveryWithoutSnapshotFullScanIsCorrect() {
        Path root = tempDir.resolve("degraded");

        // Instance 1: save checkpoints WITHOUT flushing snapshot
        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-a", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "ca", null, "out-a", 1, 10L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-b", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "ls", "cb", null, "out-b", 2, 20L));

        // Verify no snapshot file exists
        java.nio.file.Path snapshotFile = root.resolve("sess").resolve(FileBackedCheckpointManager.SNAPSHOT_FILE_NAME);
        assertEquals(false, java.nio.file.Files.exists(snapshotFile),
                "No snapshot should have been written (interval not reached, no flush)");

        // Instance 2: full-scan recovery — must still be correct
        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);
        Checkpoint latest = mgr2.getLatestCheckpoint("sess");
        assertNotNull(latest, "Degraded recovery (no snapshot) must still find the latest checkpoint");
        assertEquals("wm-b", latest.getWatermark());
        assertEquals("ls", latest.getToolName());

        assertNotNull(mgr2.getCheckpoint("wm-a"));
        assertNotNull(mgr2.getCheckpoint("wm-b"));
    }

    // ========================================================================
    // Per-session isolation
    // ========================================================================

    @Test
    void perSessionCheckpointsAreIsolatedAcrossInstances() {
        Path root = tempDir.resolve("iso");

        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        mgr1.saveCheckpoint(Checkpoint.of("sessA", "wm-a0", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "ca", null, "out-a0", 1, 10L));
        mgr1.saveCheckpoint(Checkpoint.of("sessB", "wm-b0", 0, 1001L,
                CheckpointType.TOOL_EXECUTION, "ls", "cb", null, "out-b0", 1, 20L));

        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);

        Checkpoint latestA = mgr2.getLatestCheckpoint("sessA");
        Checkpoint latestB = mgr2.getLatestCheckpoint("sessB");

        assertNotNull(latestA);
        assertNotNull(latestB);
        assertEquals("echo", latestA.getToolName(),
                "Session A's latest must be its own checkpoint, not session B's");
        assertEquals("ls", latestB.getToolName(),
                "Session B's latest must be its own checkpoint, not session A's");
        assertEquals("wm-a0", latestA.getWatermark());
        assertEquals("wm-b0", latestB.getWatermark());
    }

    // ========================================================================
    // Snapshot generation at interval
    // ========================================================================

    @Test
    void snapshotGeneratedAutomaticallyAtInterval() {
        Path root = tempDir.resolve("interval");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root, 3);

        // Save 3 checkpoints — snapshot should be written at the 3rd
        mgr.saveCheckpoint(Checkpoint.of("sess", "wm-0", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "t", "c0", null, "o0", 1, 10L));
        mgr.saveCheckpoint(Checkpoint.of("sess", "wm-1", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "t", "c1", null, "o1", 2, 20L));

        java.nio.file.Path snapFile = root.resolve("sess").resolve(FileBackedCheckpointManager.SNAPSHOT_FILE_NAME);
        assertEquals(false, java.nio.file.Files.exists(snapFile),
                "No snapshot before interval threshold (3)");

        mgr.saveCheckpoint(Checkpoint.of("sess", "wm-2", 2, 1002L,
                CheckpointType.TOOL_EXECUTION, "t", "c2", null, "o2", 3, 30L));

        assertEquals(true, java.nio.file.Files.exists(snapFile),
                "Snapshot must be written when interval threshold is reached");
    }

    // ========================================================================
    // Append across instances (incremental persistence)
    // ========================================================================

    @Test
    void appendAcrossInstancesJournalGrows() {
        Path root = tempDir.resolve("append-cross");

        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-0", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", null, "out-0", 1, 10L));

        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);
        mgr2.saveCheckpoint(Checkpoint.of("sess", "wm-1", 1, 1001L,
                CheckpointType.TOOL_EXECUTION, "ls", "c1", null, "out-1", 2, 20L));

        FileBackedCheckpointManager mgr3 = new FileBackedCheckpointManager(root);
        Checkpoint latest = mgr3.getLatestCheckpoint("sess");
        assertNotNull(latest);
        assertEquals("wm-1", latest.getWatermark());
        assertEquals("ls", latest.getToolName());

        assertNotNull(mgr3.getCheckpoint("wm-0"), "First checkpoint from mgr1 must survive");
        assertNotNull(mgr3.getCheckpoint("wm-1"), "Second checkpoint from mgr2 must survive");
    }

    // ========================================================================
    // Plan 188: compaction-aware truncation on load
    // ========================================================================

    @Test
    void noCompactionNoTruncationBackwardCompat() {
        Path root = tempDir.resolve("no-compaction");

        // Write a journal with ONLY TOOL_EXECUTION + LLM_TURN (no COMPACTION).
        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-te-0", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", null, "out-0", 1, 10L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-ll-1", 1, 1001L,
                CheckpointType.LLM_TURN, null, null, null, "llm-1", 2, 20L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-te-2", 2, 1002L,
                CheckpointType.TOOL_EXECUTION, "ls", "c2", null, "out-2", 3, 30L));

        // New instance — triggers load.
        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);

        List<Checkpoint> loaded = mgr2.getCheckpoints("sess");
        assertEquals(3, loaded.size(), "No COMPACTION → no truncation (backward compat, full list)");

        // All watermarks must still resolve (byWatermark full mapping).
        assertNotNull(mgr2.getCheckpoint("wm-te-0"));
        assertNotNull(mgr2.getCheckpoint("wm-ll-1"));
        assertNotNull(mgr2.getCheckpoint("wm-te-2"));
    }

    @Test
    void singleCompactionTruncatesInclusive() {
        Path root = tempDir.resolve("single-compaction");

        // Journal: [TOOL_EXECUTION, LLM_TURN, COMPACTION, LLM_TURN, TOOL_EXECUTION]
        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-pre-te", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", null, "out-pre", 50, 500L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-pre-ll", 1, 1001L,
                CheckpointType.LLM_TURN, null, null, null, "ll-pre", 51, 510L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-compaction", 2, 1002L,
                CheckpointType.COMPACTION, null, null, null, "compaction-summary", 5, 50L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-post-ll", 3, 1003L,
                CheckpointType.LLM_TURN, null, null, null, "ll-post", 6, 60L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-post-te", 4, 1004L,
                CheckpointType.TOOL_EXECUTION, "ls", "c4", null, "out-post", 7, 70L));

        // New instance — triggers load.
        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);

        List<Checkpoint> loaded = mgr2.getCheckpoints("sess");
        assertEquals(3, loaded.size(), "Truncated list must start from COMPACTION inclusive");
        assertEquals(CheckpointType.COMPACTION, loaded.get(0).getType());
        assertEquals("wm-compaction", loaded.get(0).getWatermark());
        assertEquals("wm-post-ll", loaded.get(1).getWatermark());
        assertEquals("wm-post-te", loaded.get(2).getWatermark());

        // getLatestCheckpoint must return the last post-compaction checkpoint.
        Checkpoint latest = mgr2.getLatestCheckpoint("sess");
        assertNotNull(latest);
        assertEquals("wm-post-te", latest.getWatermark());

        // byWatermark preserves pre-compaction checkpoints (audit capability).
        assertNotNull(mgr2.getCheckpoint("wm-pre-te"),
                "byWatermark must still resolve pre-compaction checkpoint (full history index)");
        assertNotNull(mgr2.getCheckpoint("wm-pre-ll"));
        assertNotNull(mgr2.getCheckpoint("wm-compaction"));
        assertNotNull(mgr2.getCheckpoint("wm-post-ll"));
        assertNotNull(mgr2.getCheckpoint("wm-post-te"));
    }

    @Test
    void multipleCompactionTruncatesToLatest() {
        Path root = tempDir.resolve("multi-compaction");

        // Journal with 2 COMPACTION checkpoints.
        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-pre", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "t", "c0", null, "out-pre", 50, 500L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-comp1", 1, 1001L,
                CheckpointType.COMPACTION, null, null, null, "comp1", 5, 50L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-mid", 2, 1002L,
                CheckpointType.LLM_TURN, null, null, null, "ll-mid", 6, 60L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-comp2", 3, 1003L,
                CheckpointType.COMPACTION, null, null, null, "comp2", 4, 40L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-after", 4, 1004L,
                CheckpointType.LLM_TURN, null, null, null, "ll-after", 5, 50L));

        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);

        List<Checkpoint> loaded = mgr2.getCheckpoints("sess");
        // Truncated to the 2nd (latest) COMPACTION inclusive.
        assertEquals(2, loaded.size(), "Truncated to the latest COMPACTION inclusive");
        assertEquals("wm-comp2", loaded.get(0).getWatermark());
        assertEquals(CheckpointType.COMPACTION, loaded.get(0).getType());
        assertEquals("wm-after", loaded.get(1).getWatermark());

        // Both COMPACTION watermarks still resolve via byWatermark.
        assertNotNull(mgr2.getCheckpoint("wm-comp1"),
                "First COMPACTION watermark must still resolve via byWatermark");
        assertNotNull(mgr2.getCheckpoint("wm-pre"),
                "Pre-compaction watermark must still resolve via byWatermark");
    }

    @Test
    void compactionAsLastCheckpointReturnsJustIt() {
        Path root = tempDir.resolve("compaction-last");

        // Crash right after compaction — COMPACTION is the last checkpoint.
        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-pre", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "t", "c0", null, "out-pre", 50, 500L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-compaction", 1, 1001L,
                CheckpointType.COMPACTION, null, null, null, "compaction", 5, 50L));

        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);

        List<Checkpoint> loaded = mgr2.getCheckpoints("sess");
        assertEquals(1, loaded.size(), "Only COMPACTION survives truncation");
        assertEquals(CheckpointType.COMPACTION, loaded.get(0).getType());
        assertEquals("wm-compaction", loaded.get(0).getWatermark());

        Checkpoint latest = mgr2.getLatestCheckpoint("sess");
        assertNotNull(latest, "getLatestCheckpoint must return the COMPACTION checkpoint");
        assertEquals("wm-compaction", latest.getWatermark());
    }

    @Test
    void allLoadedCheckpointsSatisfyInvariant() {
        Path root = tempDir.resolve("invariant");

        // pre-compaction: messageCount 50; COMPACTION: 5 (post-compaction baseline);
        // post: 6, 7 (post-compaction growth). Session holds 7 compacted messages.
        FileBackedCheckpointManager mgr1 = new FileBackedCheckpointManager(root);
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-pre", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "t", "c0", null, "out", 50, 500L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-compaction", 1, 1001L,
                CheckpointType.COMPACTION, null, null, null, "compaction", 5, 50L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-post-6", 2, 1002L,
                CheckpointType.LLM_TURN, null, null, null, "ll", 6, 60L));
        mgr1.saveCheckpoint(Checkpoint.of("sess", "wm-post-7", 3, 1003L,
                CheckpointType.TOOL_EXECUTION, "t", "c3", null, "out", 7, 70L));

        FileBackedCheckpointManager mgr2 = new FileBackedCheckpointManager(root);

        int sessionMessageCount = 7;
        List<Checkpoint> loaded = mgr2.getCheckpoints("sess");
        assertFalse(loaded.isEmpty());

        for (Checkpoint cp : loaded) {
            assertTrue(cp.getMessageCount() <= sessionMessageCount,
                    "Invariant violated: checkpoint.messageCount=" + cp.getMessageCount()
                            + " > session.messageCount=" + sessionMessageCount
                            + " for watermark=" + cp.getWatermark()
                            + " — truncation must drop stale pre-compaction checkpoints");
        }
    }

    // ========================================================================
    // P0 path-traversal guard (plan 190, finding [13-15]): traversal-shaped
    // sessionIds are rejected at every checkpoint entry point AND never touch
    // a file outside the root (temp-dir probe).
    // ========================================================================

    @Test
    void saveCheckpointRejectsTraversalSessionIdAndWritesNoFile() {
        Path root = tempDir.resolve("trav-save");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root);

        Checkpoint cp = Checkpoint.of("../escape", "wm-0", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", null, "out", 1, 10L);
        assertThrows(NopAiAgentException.class, () -> mgr.saveCheckpoint(cp),
                "saveCheckpoint with a traversal sessionId must throw (fail-closed)");
        assertFalse(Files.exists(tempDir.resolve("escape")),
                "saveCheckpoint must not create a file outside the root via traversal");
        assertRootHasNoSessionArtifacts(root);
    }

    @Test
    void getLatestCheckpointRejectsTraversalSessionIdAndTouchesNoFile() {
        Path root = tempDir.resolve("trav-get");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root);

        assertThrows(NopAiAgentException.class,
                () -> mgr.getLatestCheckpoint("../../etc/exploit"),
                "getLatestCheckpoint with a traversal sessionId must throw (fail-closed)");
        assertFalse(Files.exists(tempDir.resolve("etc")),
                "getLatestCheckpoint must not touch a path outside the root via traversal");
    }

    @Test
    void getCheckpointsRejectsTraversalSessionId() {
        Path root = tempDir.resolve("trav-list");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root);

        assertThrows(NopAiAgentException.class,
                () -> mgr.getCheckpoints("../escape"),
                "getCheckpoints with a traversal sessionId must throw (fail-closed)");
    }

    @Test
    void flushSnapshotRejectsTraversalSessionId() {
        Path root = tempDir.resolve("trav-flush");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root);

        assertThrows(NopAiAgentException.class,
                () -> mgr.flushSnapshot("../escape"),
                "flushSnapshot with a traversal sessionId must throw (fail-closed)");
    }

    @Test
    void absolutePathSessionIdRejected() {
        Path root = tempDir.resolve("trav-abs");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root);

        assertThrows(NopAiAgentException.class,
                () -> mgr.getLatestCheckpoint("/etc/passwd"),
                "Absolute-path sessionId must be rejected");
        assertRootHasNoSessionArtifacts(root);
    }

    private void assertRootHasNoSessionArtifacts(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> entries = Files.list(root)) {
            assertFalse(entries.findAny().isPresent(),
                    "No checkpoint artifacts must be written under the root when the sessionId is rejected");
        } catch (Exception e) {
            throw new NopAiAgentException("Unexpected I/O failure", e);
        }
    }
}
