/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.rocksdb;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.checkpoint.storage.ISegmentStore;
import io.nop.stream.core.common.state.backend.StateSnapshot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.44 R4-04 (plan 2537) red⑦+red⑩ acceptance pins — W-07 (finding
 * 1.2-C6-006) + W-10 (finding 1.2-C7-003) on
 * {@code RocksDBIncrementalRestore}/{@code restoreIncremental}, registered as
 * ONE same-diff group (the restructure of {@code restoreRangeInto} carries both
 * faces; adjudication C.2 + D.3).
 *
 * <p>red⑦ structure pin: inside {@code restoreRangeInto} every column-family
 * handle must close BEFORE the read-only source DB (rocksdbjni contract —
 * disposing a DB's handles after the DB is a use-after-free). The TWR shape
 * ({@code try (RocksDB src = ...)} + handles closed in the trailing finally) is
 * the DEFECT carrier, so the pin asserts it is gone and the two-phase manual
 * close (handles-first comment → src.close) is in order. Native UAF is
 * non-deterministic — a behavioral red here would be fake; the mutation probe
 * (reverse the order → pin red → restore → green) is the documented
 * 回退论证 channel (see the ledger trial chain).
 *
 * <p>red⑩ behavioral legs: (a) stale {@code rocksdb-restore-range-{旧id}} dirs
 * (id strictly below the restore id) must be swept at the restore entry; (b) a
 * mid-reconstruct failure must leave no {@code rocksdb-restore-range-{id}}
 * residue (the reconstruct block now sits inside the guarded boundary, so the
 * finally deleteRecursively covers it).
 *
 * <p>red line 8: the seed call
 * ({@code ensureIncrementalStrategy().seedIndexFromHandles(...)}) must keep its
 * position AFTER the restoreRangeInto call — pinned by the seed-order leg.
 */
class TestRocksDBRestoreCloseOrderAndResidueR404 {

    private static final String RESTORE_FILE = "src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalRestore.java";
    private static final String BACKEND_FILE = "src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java";

    @TempDir
    File tempDir;

    // ==================== red⑦ structure pin ====================

    @Test
    void restoreRangeIntoClosesEveryHandleBeforeTheSourceDb() throws Exception {
        Path restoreSource = Path.of(RESTORE_FILE);
        assertTrue(Files.exists(restoreSource),
                "the restore source must be readable from the module working directory: "
                        + restoreSource.toAbsolutePath());
        String src = Files.readString(restoreSource, StandardCharsets.UTF_8);

        assertFalse(src.contains("try (RocksDB src"),
                "the TWR-on-src shape is the defect carrier (src auto-closes BEFORE the "
                        + "trailing finally closes the handles = use-after-free order) — it "
                        + "must be replaced by the manual two-phase close (red⑦ structure pin)");

        int phase1 = src.indexOf("W-07 phase 1");
        int srcClose = src.indexOf("src.close()");
        assertTrue(phase1 >= 0, "the handles-first close phase must exist");
        assertTrue(srcClose > phase1,
                "src.close() must appear AFTER the handles-close phase (phase1@" + phase1
                        + " < srcClose@" + srcClose + ") — red⑦ structure pin");

        // the finally-boundary declaration: handles + src close inside one finally
        int finallyIdx = src.indexOf("} finally {", phase1 - 2000 < 0 ? 0 : phase1 - 2000);
        assertTrue(finallyIdx >= 0 && finallyIdx < phase1,
                "the two close phases must live in the finally block");
    }

    // ==================== red line 8 seed-order pin ====================

    @Test
    void restoreIncrementalKeepsSeedCallAfterRestoreRangeInto() throws Exception {
        Path backendSource = Path.of(BACKEND_FILE);
        assertTrue(Files.exists(backendSource),
                "the backend source must be readable: " + backendSource.toAbsolutePath());
        String src = Files.readString(backendSource, StandardCharsets.UTF_8);

        int sweepIdx = src.indexOf("sweepStaleRestoreRangeDirs(baseDir");
        int restoreIdx = src.indexOf("RocksDBIncrementalRestore.restoreRangeInto(");
        int seedIdx = src.indexOf("seedIndexFromHandles(result.getSstHandles())");
        assertTrue(sweepIdx >= 0, "the W-10 entry sweep must exist");
        assertTrue(restoreIdx > sweepIdx,
                "the sweep must run BEFORE restoreRangeInto (sweep@" + sweepIdx + " < restore@"
                        + restoreIdx + ")");
        assertTrue(seedIdx > restoreIdx,
                "red line 8: the 2536 seed call must keep its position AFTER restoreRangeInto"
                        + " (restore@" + restoreIdx + " < seed@" + seedIdx + ")");
        assertTrue(src.contains("ensureIncrementalStrategy().seedIndexFromHandles(result.getSstHandles());"),
                "the seed call text must stay unchanged (zero-touch anchor)");
    }

    // ==================== red⑩ behavioral legs ====================

    private static ISegmentStore missingSegmentStore() {
        return new ISegmentStore() {
            @Override
            public void storeSegment(Path sourceFile, String contentHash) {
            }

            @Override
            public void discardSegment(String contentHash) {
            }

            @Override
            public boolean segmentExists(String contentHash) {
                return false;
            }

            @Override
            public Path getSegmentPath(String contentHash) {
                return Path.of("/nonexistent-segments", contentHash + ".sst");
            }

            @Override
            public String getName() {
                return "missing-segment-stub";
            }
        };
    }

    private File nonSstDirWithMissingSegment() throws Exception {
        File nonSstDir = new File(tempDir, "non-sst-" + System.nanoTime());
        nonSstDir.mkdirs();
        Files.writeString(new File(nonSstDir, "sst-name-map.txt").toPath(),
                "deadbeefdeadbeef|000001.sst\n", StandardCharsets.UTF_8);
        return nonSstDir;
    }

    private void restore(RocksDBKeyedStateBackend<String> backend, File nonSstDir,
                         long checkpointId) throws Exception {
        IncrementalSnapshotResult result = new IncrementalSnapshotResult(
                checkpointId, Collections.emptyList(), nonSstDir.getAbsolutePath(),
                Collections.emptyList(), 0L);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(IncrementalSnapshotResult.MARKER_KEY, result);
        data.put(RocksDBKeyEncoder.KEY_LAYOUT_VERSION_FIELD, RocksDBKeyEncoder.KEY_LAYOUT_VERSION);
        backend.restoreState(new StateSnapshot(data));
    }

    @Test
    void entrySweepRemovesStaleLowerIdRestoreDirs() throws Exception {
        File baseDir = new File(tempDir, "restore-base");
        baseDir.mkdirs();
        // stale residue from an OLDER failed restore (id 1 << current 5)
        File stale = new File(baseDir, "rocksdb-restore-range-1");
        stale.mkdirs();
        Files.writeString(new File(stale, "junk.sst").toPath(), "stale", StandardCharsets.UTF_8);
        // a dir with id >= current must NEVER be touched (conservative window)
        File future = new File(baseDir, "rocksdb-restore-range-9");
        future.mkdirs();

        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                new File(tempDir, "db-" + System.nanoTime()).getAbsolutePath(),
                String.class, 16, null);
        backend.setSegmentStore(missingSegmentStore());
        backend.setCheckpointBaseDir(baseDir.getAbsolutePath());
        try {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> restore(backend, nonSstDirWithMissingSegment(), 5L),
                    "the missing shared segment must still fail the restore");
            assertFalse(stale.exists(),
                    "the stale lower-id restore dir must be swept at the restore entry "
                            + "(CRA 8.44 R4-04 W-10 red⑩ leg a) — pre-fix it survived forever");
            assertTrue(future.exists(),
                    "the conservative window must never delete dirs with id >= current");
        } finally {
            backend.close();
        }
    }

    @Test
    void midReconstructFailureLeavesNoResidue() throws Exception {
        File baseDir = new File(tempDir, "restore-base");
        baseDir.mkdirs();

        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                new File(tempDir, "db-" + System.nanoTime()).getAbsolutePath(),
                String.class, 16, null);
        backend.setSegmentStore(missingSegmentStore());
        backend.setCheckpointBaseDir(baseDir.getAbsolutePath());
        try {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> restore(backend, nonSstDirWithMissingSegment(), 5L));
            File residue = new File(baseDir, "rocksdb-restore-range-5");
            assertFalse(residue.exists(),
                    "a mid-reconstruct failure must leave no rocksdb-restore-range-{id} "
                            + "residue (the reconstruct block is inside the guarded boundary; "
                            + "pre-fix the dir was created BEFORE the try and survived = "
                            + "red⑩ leg b)");
        } finally {
            backend.close();
        }
    }
}
