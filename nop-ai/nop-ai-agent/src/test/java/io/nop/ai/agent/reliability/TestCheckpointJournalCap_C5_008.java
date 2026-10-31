package io.nop.ai.agent.reliability;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 (C5-008 / 裁定⑥) focused regression — the journal accumulation
 * upper bound, INCREMENT faces only (design §3.6 防假红条款: the terminal
 * five-container cleanup is the pre-existing {@code remove} implementation —
 * red evidence for it hangs on WIRING absence under 裁定②, never on container
 * logic here):
 *
 * <ul>
 *   <li>{@link #entryCapRotationTruncatesOldestKeepsLatest} — the active
 *       session segment beyond the entry cap is ring-truncated OLDEST-first;
 *       the latest write segment is never truncated (defect state: rotation
 *       call removed → the journal grows unbounded — RED).</li>
 *   <li>{@link #byteCapRotationBoundsJournalFile} — the byte cap bounds the
 *       journal file on the write path (defect state: same RED face).</li>
 *   <li>{@link #compactionEvictionRemovesPreBaselineEntries} — the
 *       compaction-completed session eviction (the ONLY 裁定⑥ increment on
 *       the byWatermark face): pre-COMPACTION-baseline watermark entries are
 *       evicted session-level, post-baseline entries stay resolvable (defect
 *       state: eviction body removed — RED; asserting on the pre-existing
 *       remove() here would be a false red, per design §3.6).</li>
 *   <li>{@link #recoveryPeakHeldRefsBounded} — the recovery peak
 *       materialisation observer (held-reference counter, design §3.6 m4):
 *       batched parse keeps the peak held references at a small constant,
 *       NOT the total checkpoint count (defect state: full-scan parse —
 *       peak == total — RED, the ~4x amplification face).</li>
 * </ul>
 */
public class TestCheckpointJournalCap_C5_008 {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static Checkpoint cp(String sessionId, String watermark, int seq, String output) {
        return Checkpoint.of(sessionId, watermark, seq, 1000L + seq,
                CheckpointType.TOOL_EXECUTION, "tool-" + seq, "call-" + seq,
                null, output, seq + 1, 10L * (seq + 1));
    }

    /**
     * Entry-cap rotation: after saving 2×cap checkpoints, the manager holds
     * exactly the cap newest entries; the oldest watermarks are evicted, the
     * latest stays resolvable. Defect state (rotation call removed from the
     * save path): all 2×cap entries retained → RED.
     */
    @Test
    void entryCapRotationTruncatesOldestKeepsLatest() {
        Path root = tempDir.resolve("entry-cap");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root, 1000);
        mgr.setJournalMaxEntries(5);

        for (int i = 0; i < 10; i++) {
            mgr.saveCheckpoint(cp("sess", "wm-" + i, i, "out-" + i));
        }

        assertEquals(5, mgr.getCheckpoints("sess").size(),
                "the active segment must be ring-truncated to the entry cap "
                        + "(unbounded growth = the C5-008 defect state — RED)");
        assertNotNull(mgr.getCheckpoint("wm-9"),
                "the latest write segment is never truncated");
        assertNotNull(mgr.getCheckpoint("wm-8"),
                "the cap-newest entries stay resolvable");
        assertNull(mgr.getCheckpoint("wm-0"),
                "the OLDEST entries are the truncation victims (ring discipline)");
        assertNull(mgr.getCheckpoint("wm-4"),
                "everything older than the cap window is evicted");
        assertEquals("wm-9", mgr.getLatestCheckpoint("sess").getWatermark(),
                "the latest checkpoint survives the rotation");
    }

    /**
     * Byte-cap rotation: large outputs drive the journal past a tiny byte
     * cap; the write path bounds the journal file to the cap.
     */
    @Test
    void byteCapRotationBoundsJournalFile() throws Exception {
        Path root = tempDir.resolve("byte-cap");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root, 1000);
        mgr.setJournalMaxBytes(2048);

        String fatOutput = "x".repeat(400);
        for (int i = 0; i < 20; i++) {
            mgr.saveCheckpoint(cp("sess", "wm-" + i, i, fatOutput));
        }

        Path journalFile = root.resolve("sess").resolve(FileBackedCheckpointManager.JOURNAL_FILE_NAME);
        assertTrue(Files.exists(journalFile), "the journal file must exist");
        long size = Files.size(journalFile);
        assertTrue(size <= 2048,
                "the byte cap must bound the journal file (observed " + size
                        + " bytes > 2048 cap — the unbounded defect state — RED)");
        assertNotNull(mgr.getCheckpoint("wm-19"),
                "the latest checkpoint is never truncated by the byte cap");
    }

    /**
     * Compaction-completed session eviction (裁定⑥ 增量面 — NOT the pre-existing
     * remove()): entries BEFORE the latest COMPACTION baseline are evicted
     * session-level; the baseline and everything after stays resolvable.
     * Asserting a defect state against remove() here would be a FALSE red
     * (design §3.6 防假红条款) — this pin targets the increment body only.
     */
    @Test
    void compactionEvictionRemovesPreBaselineEntries() {
        Path root = tempDir.resolve("compaction-evict");
        FileBackedCheckpointManager mgr = new FileBackedCheckpointManager(root, 1000);

        mgr.saveCheckpoint(cp("sess", "wm-0", 0, "pre-0"));
        mgr.saveCheckpoint(cp("sess", "wm-1", 1, "pre-1"));
        mgr.saveCheckpoint(Checkpoint.of("sess", "wm-comp", 2, 1002L,
                CheckpointType.COMPACTION, null, null, null, "compaction-baseline", 3, 30L));
        mgr.saveCheckpoint(cp("sess", "wm-3", 3, "post-3"));
        mgr.saveCheckpoint(cp("sess", "wm-4", 4, "post-4"));

        assertNotNull(mgr.getCheckpoint("wm-0"), "pre-baseline entries exist before eviction");
        assertNotNull(mgr.getCheckpoint("wm-comp"), "the compaction baseline exists");

        mgr.evictCompactedSession("sess");

        assertNull(mgr.getCheckpoint("wm-0"),
                "pre-compaction-baseline watermark entries must be evicted "
                        + "(eviction body removed = defect state — RED)");
        assertNull(mgr.getCheckpoint("wm-1"),
                "all pre-baseline entries are evicted");
        assertNotNull(mgr.getCheckpoint("wm-comp"),
                "the COMPACTION baseline stays resolvable");
        assertNotNull(mgr.getCheckpoint("wm-3"),
                "post-baseline entries stay resolvable");
        assertNotNull(mgr.getCheckpoint("wm-4"),
                "the newest entry stays resolvable");
        assertEquals("wm-4", mgr.getLatestCheckpoint("sess").getWatermark(),
                "the restore view is untouched by the eviction");
    }

    /**
     * Recovery peak materialisation observer (held-reference counter): after
     * a fresh-manager restore of a 60-checkpoint journal, the peak held
     * references stays a small constant (batched streaming parse) — NOT the
     * total count. Defect state (full-scan parse / never-decremented held
     * refs): peak == total → RED (the ~4x amplification face).
     */
    @Test
    void recoveryPeakHeldRefsBounded() {
        Path root = tempDir.resolve("peak");
        FileBackedCheckpointManager writer = new FileBackedCheckpointManager(root, 1000);
        for (int i = 0; i < 60; i++) {
            writer.saveCheckpoint(cp("sess", "wm-" + i, i, "out-" + i));
        }

        // Fresh manager: recovery from disk (no in-memory state).
        FileBackedCheckpointManager reader = new FileBackedCheckpointManager(root, 1000);
        assertEquals(0, reader.getParseHeldRefsPeak(), "no recovery ran yet");
        assertNotNull(reader.getLatestCheckpoint("sess"), "the restore must run");
        assertEquals("wm-59", reader.getLatestCheckpoint("sess").getWatermark());

        long peak = reader.getParseHeldRefsPeak();
        assertTrue(peak > 0, "the parse path must pass through the held-ref counter");
        assertTrue(peak <= 5,
                "the recovery peak materialisation must be bounded (batched parse keeps "
                        + "single-section residency); observed peak " + peak
                        + " for 60 checkpoints — full-scan parse peaks at the total count "
                        + "(the ~4x amplification defect state — RED)");
        assertEquals(0, reader.getParseHeldRefs(),
                "held references return to zero after the parse completes (no leak)");
    }
}
