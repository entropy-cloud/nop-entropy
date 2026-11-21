package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2584 Phase 2 (audit Minor): {@code purgeTerminal} must be a no-op for
 * a null sessionId (the pre-fix remove-then-null-check order still reached the
 * path validation on the null path) and must delete the persisted files for a
 * real session id.
 */
public class TestFileBackedCheckpointManagerPurgeNullSafety_2584 {

    @Test
    void purgeTerminalNullIsNoOp() {
        Path root = assertDoesNotThrow(() -> Files.createTempDirectory("purge-null-pin"));
        try {
            FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root);
            // pre-fix: threw (path validation on null) instead of the documented no-op
            assertDoesNotThrow(() -> mgr.purgeTerminal(null));
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void purgeTerminalDeletesPersistedFiles() throws IOException {
        Path root = Files.createTempDirectory("purge-pin");
        try {
            FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root);
            mgr.saveCheckpoint(Checkpoint.of("purge-sess", "wm-1", 1, 1000L,
                    CheckpointType.TOOL_EXECUTION, "tool", "call-1", "in", "out", 1, 10));
            Path journal = root.resolve("purge-sess").resolve(FileBackedCheckpointManager.JOURNAL_FILE_NAME);
            assertTrue(Files.exists(journal));

            mgr.purgeTerminal("purge-sess");

            assertFalse(Files.exists(journal));
            assertFalse(Files.exists(root.resolve("purge-sess")
                    .resolve(FileBackedCheckpointManager.SNAPSHOT_FILE_NAME)));
        } finally {
            deleteRecursively(root);
        }
    }

    private static void deleteRecursively(Path root) {
        java.util.List<Path> paths;
        try (var walk = Files.walk(root)) {
            paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
        } catch (IOException e) {
            return; // best-effort test cleanup
        }
        for (Path p : paths) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                // best-effort test cleanup
            }
        }
    }
}
