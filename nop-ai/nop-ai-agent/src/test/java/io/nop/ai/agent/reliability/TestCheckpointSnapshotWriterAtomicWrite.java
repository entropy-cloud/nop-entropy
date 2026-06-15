package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 195 focused tests for the crash-safe (atomic) write path of
 * {@link CheckpointSnapshotWriter}. Mirrors {@code TestSessionFileWriterAtomicWrite}
 * to prove the snapshot writer shares the same write-to-tmp +
 * {@code Files.move(ATOMIC_MOVE, REPLACE_EXISTING)} crash-safety guarantees:
 *
 * <ol>
 *   <li><b>atomic-write-target-intact</b> + <b>tmp-cleanup</b></li>
 *   <li><b>stale-tmp-recovery</b></li>
 *   <li><b>pre-move-failure-isolation</b> (crash-safety core guarantee)</li>
 *   <li><b>overwrite-write</b></li>
 *   <li><b>smoke test through FileBackedCheckpointManager</b> (wiring)</li>
 * </ol>
 */
public class TestCheckpointSnapshotWriterAtomicWrite {

    @TempDir
    Path tempDir;

    private static CheckpointSnapshot sampleSnapshot(String id, String wm) {
        return CheckpointSnapshot.of("snap-" + id, "sess-" + id, wm, 3, 42L, 1718445600123L);
    }

    private Path targetFile(String id) {
        return tempDir.resolve(id).resolve("snapshot.json");
    }

    private Path tmpFile(String id) {
        return targetFile(id).resolveSibling(targetFile(id).getFileName() + ".tmp");
    }

    // ========================================================================
    // (1) atomic-write-target-intact + (2) tmp-cleanup
    // ========================================================================

    @Test
    void successfulWriteLeavesCompleteTargetAndNoTmp() {
        CheckpointSnapshotWriter writer = new CheckpointSnapshotWriter();
        Path target = targetFile("intact");
        CheckpointSnapshot snap = sampleSnapshot("intact", "wm-1");

        writer.write(target, snap);

        assertTrue(Files.exists(target), "target file must exist after write");
        assertFalse(Files.exists(tmpFile("intact")),
                "tmp file must not survive a successful write");

        CheckpointSnapshotReader reader = new CheckpointSnapshotReader();
        CheckpointSnapshot restored = reader.readIfExists(target);
        assertNotNull(restored, "written file must be readable as a snapshot");
        assertEquals("snap-intact", restored.getSnapshotId());
        assertEquals("wm-1", restored.getLastWatermark());
        assertEquals(3, restored.getMessageCount());
    }

    // ========================================================================
    // (3) stale-tmp-recovery
    // ========================================================================

    @Test
    void staleTmpIsOverwrittenAndRemovedAfterWrite() throws Exception {
        Path target = targetFile("stale");
        Path tmp = tmpFile("stale");

        Files.createDirectories(target.getParent());
        Files.writeString(tmp, "stale-tmp-content-from-previous-crashed-write");

        CheckpointSnapshotWriter writer = new CheckpointSnapshotWriter();
        writer.write(target, sampleSnapshot("stale", "wm-fresh"));

        assertFalse(Files.exists(tmp),
                "stale tmp must not survive the next successful write");
        CheckpointSnapshotReader reader = new CheckpointSnapshotReader();
        CheckpointSnapshot restored = reader.readIfExists(target);
        assertNotNull(restored);
        assertEquals("wm-fresh", restored.getLastWatermark());
    }

    // ========================================================================
    // (4) pre-move-failure-isolation (crash-safety core guarantee)
    // ========================================================================

    @Test
    void preMoveFailureLeavesExistingTargetUntouched() throws Exception {
        Path target = targetFile("iso");
        Path tmp = tmpFile("iso");

        // Pre-populate target with valid, complete snapshot JSON.
        Files.createDirectories(target.getParent());
        Files.writeString(target, CheckpointSnapshotWriter.serialize(sampleSnapshot("iso", "wm-original")));

        // Block tmp-write by pre-creating a directory at the tmp path —
        // portable failure injection (Files.write on a dir throws IOException).
        Files.createDirectory(tmp);
        assertTrue(Files.isDirectory(tmp), "precondition: tmp path must be a blocking directory");

        CheckpointSnapshotWriter writer = new CheckpointSnapshotWriter();
        CheckpointSnapshot newSnap = sampleSnapshot("iso", "wm-should-never-land");

        assertThrows(NopAiAgentException.class, () -> writer.write(target, newSnap),
                "write must surface the tmp-write failure as NopAiAgentException");

        // CRASH-SAFETY GUARANTEE: target unchanged — complete original content.
        assertTrue(Files.exists(target),
                "target must still exist after a pre-move failure");
        CheckpointSnapshotReader reader = new CheckpointSnapshotReader();
        CheckpointSnapshot survivor = reader.readIfExists(target);
        assertNotNull(survivor,
                "target must still be a valid, complete snapshot after a failed write");
        assertEquals("wm-original", survivor.getLastWatermark(),
                "target content must be the original, not truncated or partially overwritten");

        assertFalse(Files.exists(tmp),
                "finally cleanup must remove the tmp artifact even on failure");
    }

    // ========================================================================
    // (5) overwrite-write
    // ========================================================================

    @Test
    void consecutiveOverwritesLeaveLatestContentAndNoTmp() {
        CheckpointSnapshotWriter writer = new CheckpointSnapshotWriter();
        Path target = targetFile("overwrite");

        writer.write(target, sampleSnapshot("overwrite", "wm-first"));
        assertFalse(Files.exists(tmpFile("overwrite")),
                "no tmp after first write");

        writer.write(target, sampleSnapshot("overwrite", "wm-second"));
        assertFalse(Files.exists(tmpFile("overwrite")),
                "no tmp after second write");

        CheckpointSnapshotReader reader = new CheckpointSnapshotReader();
        CheckpointSnapshot restored = reader.readIfExists(target);
        assertNotNull(restored);
        assertEquals("wm-second", restored.getLastWatermark(),
                "target must contain the latest content after consecutive overwrites");
    }

    // ========================================================================
    // (6) smoke test through FileBackedCheckpointManager (wiring)
    // ========================================================================

    @Test
    void managerSnapshotWriteUsesAtomicPath() {
        Path root = tempDir.resolve("mgr");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root);

        // Saving enough checkpoints to trigger a snapshot write, then verify
        // the snapshot.json on disk has no residual .tmp (proving the atomic
        // move path is exercised through the manager).
        mgr.saveCheckpoint(Checkpoint.of("sess", "wm-a", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", "in0", "out-0", 1, 10L));
        mgr.flushSnapshot("sess");

        Path snapshotFile = root.resolve("sess").resolve("snapshot.json");
        Path tmp = snapshotFile.resolveSibling(snapshotFile.getFileName() + ".tmp");
        assertTrue(Files.exists(snapshotFile),
                "manager must write snapshot.json to disk");
        assertFalse(Files.exists(tmp),
                "manager must leave no tmp residue (atomic move completed)");

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(snapshotFile);
        } catch (Exception e) {
            throw new NopAiAgentException("read failed", e);
        }
        assertTrue(bytes.length > 0, "snapshot.json must not be empty");
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        assertTrue(text.startsWith("{") && text.endsWith("}"),
                "snapshot.json must contain a JSON object");
    }
}
