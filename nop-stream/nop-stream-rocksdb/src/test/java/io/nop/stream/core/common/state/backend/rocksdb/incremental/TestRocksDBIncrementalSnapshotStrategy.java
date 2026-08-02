/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb.incremental;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.checkpoint.incremental.SharedStateHandle;
import io.nop.stream.core.checkpoint.incremental.SharedStateRegistry;
import io.nop.stream.core.checkpoint.incremental.SharedStateRegistryImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 31 Phase 2: deterministic content-addressing + cross-checkpoint de-duplication
 * for the RocksDB incremental snapshot strategy.
 */
class TestRocksDBIncrementalSnapshotStrategy {

    static {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tmp;

    private RocksDB openRocks(String sub) throws Exception {
        Path dir = tmp.resolve(sub);
        Files.createDirectories(dir);
        Options opts = new Options()
                .setCreateIfMissing(true)
                .setDisableAutoCompactions(true);
        return RocksDB.open(opts, dir.toAbsolutePath().toString());
    }

    private void flushDb(RocksDB db) throws Exception {
        try (FlushOptions fo = new FlushOptions()) {
            fo.setWaitForFlush(true);
            db.flush(fo);
        }
    }

    private Set<String> hashesOf(IncrementalSnapshotResult r) {
        Set<String> s = new HashSet<>();
        for (SharedStateHandle h : r.getSstHandles()) {
            s.add(h.getContentHash());
        }
        return s;
    }

    // ---- createCheckpoint succeeds + SST content addressing ----

    @Test
    void nativeCheckpointCreatedAndSstFilesContentAddressed() throws Exception {
        Path baseDir = tmp.resolve("checkpoints");
        try (RocksDB db = openRocks("db1")) {
            for (int i = 0; i < 50; i++) {
                db.put(("k" + i).getBytes(StandardCharsets.UTF_8),
                        ("v" + i).getBytes(StandardCharsets.UTF_8));
            }
            flushDb(db);

            RocksDBIncrementalSnapshotStrategy strategy = new RocksDBIncrementalSnapshotStrategy();
            IncrementalSnapshotResult result = strategy.doSnapshot(db, baseDir, 1L);

            assertNotNull(result);
            assertTrue(result.getSstFileCount() > 0, "expected at least one SST file after flush");
            // Every handle carries a 64-char lowercase hex SHA-256
            for (SharedStateHandle h : result.getSstHandles()) {
                assertEquals(64, h.getContentHash().length());
                assertTrue(Files.exists(Path.of(h.getFilePath())));
                assertTrue(h.getSize() > 0);
            }
        }
    }

    // ---- deterministic de-duplication: two checkpoints with no changes share SST handles ----

    @Test
    void deterministicDedupAcrossCheckpointsWithNoChanges() throws Exception {
        Path baseDir = tmp.resolve("checkpoints");
        try (RocksDB db = openRocks("db2")) {
            for (int i = 0; i < 100; i++) {
                db.put(("k" + i).getBytes(StandardCharsets.UTF_8),
                        ("v" + i).getBytes(StandardCharsets.UTF_8));
            }
            flushDb(db);

            RocksDBIncrementalSnapshotStrategy strategy = new RocksDBIncrementalSnapshotStrategy();
            IncrementalSnapshotResult cp1 = strategy.doSnapshot(db, baseDir, 1L);
            // No state changes between the two checkpoints.
            IncrementalSnapshotResult cp2 = strategy.doSnapshot(db, baseDir, 2L);

            Set<String> hashes1 = hashesOf(cp1);
            Set<String> hashes2 = hashesOf(cp2);
            assertEquals(hashes1, hashes2,
                    "two checkpoints with no state changes must yield identical SST content hashes");

            // Registry de-duplication: registering cp2's handles returns the SAME canonical
            // handles as cp1's, and ref-counts reach 2 instead of creating new entries.
            SharedStateRegistry registry = new SharedStateRegistryImpl();
            for (SharedStateHandle h : cp1.getSstHandles()) {
                registry.register(h);
            }
            for (SharedStateHandle h : cp2.getSstHandles()) {
                SharedStateHandle canonical = registry.register(h);
                assertTrue(hashes1.contains(canonical.getStateObjectId()),
                        "canonical handle for cp2 must be one of cp1's handles");
            }
            for (String hash : hashes1) {
                assertEquals(2, registry.getReferenceCount(hash),
                        "each shared SST must have ref-count 2 after two registrations");
            }
        }
    }

    // ---- state change introduces a NEW handle not seen before ----

    @Test
    void stateChangeProducesNewSstHandle() throws Exception {
        Path baseDir = tmp.resolve("checkpoints");
        try (RocksDB db = openRocks("db3")) {
            for (int i = 0; i < 100; i++) {
                db.put(("k" + i).getBytes(StandardCharsets.UTF_8),
                        ("v" + i).getBytes(StandardCharsets.UTF_8));
            }
            flushDb(db);

            RocksDBIncrementalSnapshotStrategy strategy = new RocksDBIncrementalSnapshotStrategy();
            IncrementalSnapshotResult cp1 = strategy.doSnapshot(db, baseDir, 1L);
            Set<String> before = hashesOf(cp1);

            // Write NEW data and flush -> a new SST file with a new content hash.
            for (int i = 1000; i < 1100; i++) {
                db.put(("k" + i).getBytes(StandardCharsets.UTF_8),
                        ("v" + i).getBytes(StandardCharsets.UTF_8));
            }
            flushDb(db);

            IncrementalSnapshotResult cp2 = strategy.doSnapshot(db, baseDir, 2L);
            Set<String> after = hashesOf(cp2);

            // cp2 must contain at least one handle that cp1 did NOT have.
            Set<String> newOnes = new HashSet<>(after);
            newOnes.removeAll(before);
            assertFalse(newOnes.isEmpty(),
                    "a state change must introduce at least one new SST content hash");
            // And the previous handles are a subset (shared) of the new set.
            assertTrue(after.containsAll(before),
                    "previous SSTs must still be present (content-addressed sharing)");
        }
    }

    // ---- non-SST files are copied into the per-checkpoint non-sst dir ----

    @Test
    void nonSstFilesCopiedToPerCheckpointDir() throws Exception {
        Path baseDir = tmp.resolve("checkpoints");
        try (RocksDB db = openRocks("db4")) {
            db.put("k1".getBytes(StandardCharsets.UTF_8), "v1".getBytes(StandardCharsets.UTF_8));
            flushDb(db);

            RocksDBIncrementalSnapshotStrategy strategy = new RocksDBIncrementalSnapshotStrategy();
            IncrementalSnapshotResult result = strategy.doSnapshot(db, baseDir, 7L);

            // MANIFEST / CURRENT / OPTIONS / IDENTITY must be present in the non-sst dir.
            Path nonSstDir = Path.of(result.getNonSstDir());
            assertTrue(Files.isDirectory(nonSstDir));
            List<String> names = result.getNonSstFileNames();
            assertFalse(names.isEmpty(), "non-SST files must be enumerated");

            Set<String> nameSet = new HashSet<>();
            for (String n : names) {
                nameSet.add(n.toUpperCase());
                assertTrue(Files.exists(nonSstDir.resolve(n)),
                        "non-SST file " + n + " must be copied");
            }
            // CURRENT is always produced by a RocksDB checkpoint.
            assertTrue(nameSet.stream().anyMatch(n -> n.equals("CURRENT")),
                    "CURRENT must be among non-SST files: " + nameSet);
        }
    }

    // ---- incrementalSnapshotId advances and produces distinct checkpoint dirs ----

    @Test
    void repeatedSnapshotsProduceDistinctCheckpointDirs() throws Exception {
        Path baseDir = tmp.resolve("checkpoints");
        try (RocksDB db = openRocks("db5")) {
            db.put("k".getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8));
            flushDb(db);

            RocksDBIncrementalSnapshotStrategy strategy = new RocksDBIncrementalSnapshotStrategy();
            IncrementalSnapshotResult r1 = strategy.doSnapshot(db, baseDir, 10L);
            IncrementalSnapshotResult r2 = strategy.doSnapshot(db, baseDir, 20L);

            assertEquals(10L, r1.getCheckpointId());
            assertEquals(20L, r2.getCheckpointId());
            assertTrue(Files.exists(baseDir.resolve("cp-10").resolve("native")));
            assertTrue(Files.exists(baseDir.resolve("cp-20").resolve("native")));
        }
    }
}
