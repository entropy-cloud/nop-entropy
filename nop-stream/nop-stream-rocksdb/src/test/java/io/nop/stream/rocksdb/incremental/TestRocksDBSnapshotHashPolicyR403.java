/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb.incremental;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.checkpoint.incremental.SharedStateHandle;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ⑨ hash-policy leg — W-09 (MA-18=C8-013,
 * adjudication D.2): the {@code nop.stream.rocksdb.snapshot-hash-policy} knob
 * governs SST checksum reuse on the incremental path.
 *
 * <p>Drive form (deterministic, no timing dependence): the test seeds the
 * strategy's SST index with a sentinel hash ("FAKE") via the
 * {@code seedIndexFromHandles} seam and inspects which hash lands in the
 * produced {@link SharedStateHandle}s:
 * <ul>
 *   <li>{@code new-only} + size match &rarr; the seeded sentinel is REUSED
 *       (hash-reuse path taken, full-file sha256 skipped);</li>
 *   <li>{@code new-only} + size drift &rarr; re-hash (sentinel not in
 *       result, true content hash present);</li>
 *   <li>{@code full} + size match &rarr; re-hash regardless (opt-out tier);</li>
 *   <li>any other policy value &rarr; typed {@code ERR_STREAM_INVALID_ARG}
 *       reject.</li>
 * </ul>
 * The red-⑨ validation-method compile red and the declared-budget direct-call
 * legs live in core
 * ({@code TestCheckpointSnapshotBudgetValidationR403}); the runtime two-line
 * wiring at the GraphModelCheckpointExecutor validate funnel is registered as
 * a structural grep in {@code _tmp/red-green-ledger-2536.md}.
 */
class TestRocksDBSnapshotHashPolicyR403 {

    private static final String FAKE_HASH = "FAKE-r403-sentinel";

    @TempDir
    Path tempDir;

    private static RocksDB openDb(Path dir) {
        try (Options options = new Options().setCreateIfMissing(true)) {
            return RocksDB.open(options, dir.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void invalidPolicyValueFailsTyped() {
        StreamException e = assertThrows(StreamException.class,
                () -> new RocksDBIncrementalSnapshotStrategy("sampled"),
                "a policy outside {new-only, full} must be rejected typed");
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    @Test
    void newOnlyReusesSeededHashWhenNameAndSizeMatch() throws Exception {
        Path base = tempDir.resolve("reuse");
        RocksDB db = openDb(tempDir.resolve("db-reuse"));
        try {
            db.put("k1".getBytes(StandardCharsets.UTF_8), "v1".getBytes(StandardCharsets.UTF_8));
            // First snapshot: fresh instance (empty index) — full hash, index populated.
            RocksDBIncrementalSnapshotStrategy bootstrap = new RocksDBIncrementalSnapshotStrategy();
            IncrementalSnapshotResult first = bootstrap.doSnapshot(db, base, 1L);
            assertTrue(first.getSstHandles().size() >= 1, "at least one SST file after a write");
            assertEquals(first.getSstHandles().size(), bootstrap.previousSstIndexSize(),
                    "index rolls forward with every hashed SST");

            // Seed a second instance from the first snapshot's handles but with a
            // sentinel hash and the TRUE sizes — new-only must reuse the sentinel.
            RocksDBIncrementalSnapshotStrategy seeded = new RocksDBIncrementalSnapshotStrategy();
            seeded.seedIndexFromHandles(withSentinelHashes(first.getSstHandles()));
            IncrementalSnapshotResult second = seeded.doSnapshot(db, base, 2L);
            for (SharedStateHandle handle : second.getSstHandles()) {
                assertEquals(FAKE_HASH, handle.getContentHash(),
                        "new-only must reuse the previous hash for an unchanged (name, size) pair: "
                                + handle.getFilePath());
            }
        } finally {
            db.close();
        }
    }

    @Test
    void newOnlyRehashesWhenSizeDrifts() throws Exception {
        Path base = tempDir.resolve("drift");
        RocksDB db = openDb(tempDir.resolve("db-drift"));
        try {
            db.put("k1".getBytes(StandardCharsets.UTF_8), "v1".getBytes(StandardCharsets.UTF_8));
            RocksDBIncrementalSnapshotStrategy bootstrap = new RocksDBIncrementalSnapshotStrategy();
            IncrementalSnapshotResult first = bootstrap.doSnapshot(db, base, 1L);

            // Seed the sentinel with WRONG sizes — a drifted size must re-hash.
            RocksDBIncrementalSnapshotStrategy seeded = new RocksDBIncrementalSnapshotStrategy();
            for (SharedStateHandle handle : first.getSstHandles()) {
                seeded.seedIndexFromHandles(List.of(new SharedStateHandle(
                        FAKE_HASH, handle.getFilePath(), handle.getSize() + 1)));
            }
            IncrementalSnapshotResult second = seeded.doSnapshot(db, base, 2L);
            for (SharedStateHandle handle : second.getSstHandles()) {
                assertNotEquals(FAKE_HASH, handle.getContentHash(),
                        "a size drift must invalidate the index entry and re-hash: " + handle.getFilePath());
            }
        } finally {
            db.close();
        }
    }

    @Test
    void fullPolicyAlwaysRehashes() throws Exception {
        Path base = tempDir.resolve("full");
        RocksDB db = openDb(tempDir.resolve("db-full"));
        try {
            db.put("k1".getBytes(StandardCharsets.UTF_8), "v1".getBytes(StandardCharsets.UTF_8));
            RocksDBIncrementalSnapshotStrategy bootstrap = new RocksDBIncrementalSnapshotStrategy();
            IncrementalSnapshotResult first = bootstrap.doSnapshot(db, base, 1L);

            // Even a perfect (name, size) match is re-hashed under the full tier.
            RocksDBIncrementalSnapshotStrategy full = new RocksDBIncrementalSnapshotStrategy(
                    RocksDBIncrementalSnapshotStrategy.HASH_POLICY_FULL);
            full.seedIndexFromHandles(withSentinelHashes(first.getSstHandles()));
            IncrementalSnapshotResult second = full.doSnapshot(db, base, 2L);
            for (SharedStateHandle handle : second.getSstHandles()) {
                assertNotEquals(FAKE_HASH, handle.getContentHash(),
                        "the full policy must re-hash every SST file: " + handle.getFilePath());
            }
        } finally {
            db.close();
        }
    }

    private static List<SharedStateHandle> withSentinelHashes(List<SharedStateHandle> handles) {
        return handles.stream()
                .map(h -> new SharedStateHandle(FAKE_HASH, h.getFilePath(), h.getSize()))
                .toList();
    }
}
