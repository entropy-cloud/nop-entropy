/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb.incremental;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.checkpoint.storage.ISegmentStore;
import io.nop.stream.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_SEGMENT_CORRUPT;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_SEGMENT_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_FILE_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_CHECKSUM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_CHECKSUM;

/**
 * Stage 31 restore helper: reconstructs a complete, openable RocksDB directory from the
 * content-addressed shared SST files (in {@link ISegmentStore}, renamed to
 * {@code {hash}.sst}) plus the per-checkpoint non-SST companion files (WAL / MANIFEST /
 * OPTIONS / CURRENT / IDENTITY) and the {@code sst-name-map.txt} sidecar that maps each
 * content hash back to its original SST filename (which RocksDB's MANIFEST references).
 *
 * <p>The result directory is a consistent RocksDB checkpoint that can be opened directly.
 *
 * <p>Stage 35 extends this with {@link #restoreRangeInto}, a real key-group range scan
 * that consumes the Stage 34 sortable binary prefix: the reconstructed DB is opened
 * read-only and iterated over the byte range {@code [startGroup, endGroup)} (the
 * key-group id is the big-endian first 4 bytes of every key), and only entries whose
 * group falls in the target range are copied into the live backend. This closes the
 * Stage 31 deferred item "Key-group range SST reading" with a real consumer.
 */
public final class RocksDBIncrementalRestore {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(RocksDBIncrementalRestore.class);

    private RocksDBIncrementalRestore() {
    }

    /**
     * Read the {@code sst-name-map.txt} sidecar produced by
     * {@link RocksDBIncrementalSnapshotStrategy}.
     */
    public static Map<String, String> readSstNameMap(Path nonSstDir) throws IOException {
        Path sidecar = nonSstDir.resolve(RocksDBIncrementalSnapshotStrategy.SST_NAME_MAP_FILE);
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.exists(sidecar)) {
            return map;
        }
        List<String> lines = Files.readAllLines(sidecar, StandardCharsets.UTF_8);
        for (String line : lines) {
            int sep = line.indexOf('|');
            if (sep > 0) {
                map.put(line.substring(0, sep), line.substring(sep + 1));
            }
        }
        return map;
    }

    /**
     * Reconstruct a restorable RocksDB directory at {@code targetDir} from the shared
     * segment store and the per-checkpoint non-SST dir.
     *
     * <p>F-03 (Plan 2026-09-04-1326-1 Phase 3): every shared SST segment is
     * integrity-re-verified before use — the recomputed SHA-256 must equal the content
     * hash the segment is addressed by. The writer already computed the hash
     * ({@code SstFileChecksum.sha256Hex} at snapshot time), so verification is cheap;
     * a mismatch (truncated write, bit rot, tampering) fails fast with the typed
     * {@code ERR_STREAM_CHECKPOINT_SEGMENT_CORRUPT} instead of feeding corrupt bytes
     * into a reopened RocksDB.
     *
     * @param segmentStore the content-addressed shared SST store
     * @param nonSstDir    the per-checkpoint non-SST companion dir (MANIFEST/OPTIONS/.../sidecar)
     * @param targetDir    the directory to assemble (must not be an existing live DB)
     */
    public static void reconstructRocksdbDir(ISegmentStore segmentStore, Path nonSstDir, Path targetDir)
            throws IOException {
        Files.createDirectories(targetDir);

        Map<String, String> nameMap = readSstNameMap(nonSstDir);
        for (Map.Entry<String, String> e : nameMap.entrySet()) {
            String hash = e.getKey();
            String originalName = e.getValue();
            Path source = segmentStore.getSegmentPath(hash);
            if (!Files.exists(source)) {
                throw new IOException("Shared SST segment missing in store for hash " + hash
                        + " (original name " + originalName + ") — cannot reconstruct RocksDB");
            }
            // F-03a: restore-time content re-verification (writer computed the same
            // hash when addressing the segment; mismatch = physical corruption).
            String recomputed;
            try {
                recomputed = io.nop.stream.core.checkpoint.incremental.SstFileChecksum.sha256Hex(source);
            } catch (IOException ioex) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, ioex)
                        .param(ARG_DETAIL, "Failed to re-hash shared SST segment " + source
                                + " during restore-time integrity verification");
            }
            if (!hash.equalsIgnoreCase(recomputed)) {
                throw new StreamException(ERR_STREAM_CHECKPOINT_SEGMENT_CORRUPT)
                        .param(ARG_SEGMENT_ID, hash)
                        .param(ARG_FILE_NAME, originalName)
                        .param(ARG_EXPECTED_CHECKSUM, hash)
                        .param(ARG_ACTUAL_CHECKSUM, recomputed);
            }
            Files.copy(source, targetDir.resolve(originalName), StandardCopyOption.REPLACE_EXISTING);
        }

        // Copy the non-SST companion files (MANIFEST / CURRENT / OPTIONS / IDENTITY / WAL ...).
        try (var stream = Files.list(nonSstDir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String name = p.getFileName().toString();
                if (name.equals(RocksDBIncrementalSnapshotStrategy.SST_NAME_MAP_FILE)) {
                    continue;
                }
                Files.copy(p, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Stage 35: real key-group range restore from an incremental checkpoint. Reconstructs
     * the content-addressed SST set into a temporary RocksDB directory, opens it
     * read-only, and copies only the entries whose key-group prefix falls inside
     * {@code targetRange} into the live {@code backend}. This is the true SST range scan
     * (consuming the Stage 34 sortable prefix), not an in-memory filter.
     *
     * <p>When {@code targetRange == null} the whole reconstructed DB is copied (full
     * restore), preserving the pre-Stage-35 behaviour.
     *
     * @param backend       the live backend whose RocksDB instance receives the range entries
     * @param result        the incremental snapshot result (SST handles + non-SST dir)
     * @param segmentStore  the content-addressed store that resolves SST content hashes
     * @param targetRange   the key-group range to restore, or {@code null} for full restore
     * @param tempDir       a temporary directory under which the reconstructed DB is built
     * @return the number of entries copied into the live backend
     */
    public static int restoreRangeInto(RocksDBKeyedStateBackend<?> backend,
                                       IncrementalSnapshotResult result,
                                       ISegmentStore segmentStore,
                                       KeyGroupRange targetRange,
                                       Path tempDir) throws IOException, RocksDBException {
        Path reconstructed = tempDir.resolve("rocksdb-restore-range-" + result.getCheckpointId());
        deleteRecursively(reconstructed);
        Path nonSstDir = Path.of(result.getNonSstDir());
        reconstructRocksdbDir(segmentStore, nonSstDir, reconstructed);

        // The reconstructed SST files were produced by the Stage 34 v2 encoder
        // (key-group id as big-endian sortable prefix), so the range scan is safe.
        int copied;
        try (ColumnFamilyOptions cfOpts = new ColumnFamilyOptions();
             DBOptions dbOptions = new DBOptions().setCreateIfMissing(false)) {
            List<byte[]> cfNames;
            try (Options listOpts = new Options(dbOptions, cfOpts)) {
                cfNames = RocksDB.listColumnFamilies(listOpts, reconstructed.toAbsolutePath().toString());
            } catch (RocksDBException e) {
                // A directory whose MANIFEST/CURRENT is truncated or inconsistent lists
                // no column families — that is NOT "empty state", it is a physically
                // broken checkpoint (F-03). Treat a native list failure as fatal only
                // when the dir actually carries files; an empty/genuinely-default-only
                // dir still falls back to the default CF below.
                try (var listing = Files.list(reconstructed)) {
                    long fileCount = listing.filter(Files::isRegularFile).count();
                    if (fileCount > 1) {
                        throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                                .param(ARG_DETAIL, "Failed to list column families of the reconstructed"
                                        + " RocksDB dir " + reconstructed + " (" + fileCount + " files)"
                                        + " — the checkpoint is physically inconsistent (corrupt"
                                        + " MANIFEST/CURRENT?)");
                    }
                }
                cfNames = new ArrayList<>();
                cfNames.add(RocksDB.DEFAULT_COLUMN_FAMILY);
            }
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            for (byte[] cfName : cfNames) {
                descriptors.add(new ColumnFamilyDescriptor(cfName, cfOpts));
            }
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            try (RocksDB src = openReadOnlyTyped(dbOptions, reconstructed, descriptors, handles)) {
                copied = 0;
                for (ColumnFamilyHandle srcCf : handles) {
                    String cfName = cfNameOf(srcCf);
                    // Keyed state lives only in state-name column families; the RocksDB
                    // default CF holds no user data and re-creating it on the live backend
                    // conflicts with the built-in default CF, so skip it.
                    if (isDefaultCf(cfName)) {
                        continue;
                    }
                    try {
                        copied += backend.copyColumnFamilyRange(src, srcCf, cfName, targetRange);
                    } catch (RocksDBException e) {
                        throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                                .param(ARG_DETAIL, "Failed to copy range entry during incremental restore");
                    }
                }
            } finally {
                for (ColumnFamilyHandle h : handles) {
                    if (h != null) h.close();
                }
            }
        } finally {
            deleteRecursively(reconstructed);
        }
        return copied;
    }

    private static String cfNameOf(ColumnFamilyHandle cf) {
        try {
            byte[] name = cf.getName();
            return new String(name, StandardCharsets.UTF_8);
        } catch (RocksDBException e) {
            // Failing to read the CF name must not silently skip that column
            // family's data during restore (item 11 RK-6): fail fast instead of
            // misclassifying it as the default CF.
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to read column family name during incremental restore; "
                            + "refusing to skip its entries silently");
        }
    }

    /**
     * F-03 (Plan 2026-09-04-1326-1 Phase 3): {@code RocksDB.openReadOnly} wrapped so a
     * native open failure on the reconstructed checkpoint dir surfaces as a typed
     * {@code ERR_STREAM_STATE_ERROR} chaining the native root cause — a truncated
     * MANIFEST/CURRENT/WAL must fail fast with the real reason visible, not escape as a
     * bare {@code RocksDBException} (and never as silently-missing state).
     */
    private static RocksDB openReadOnlyTyped(DBOptions dbOptions, Path dir,
                                             List<ColumnFamilyDescriptor> descriptors,
                                             List<ColumnFamilyHandle> handles) {
        try {
            return RocksDB.openReadOnly(dbOptions, dir.toAbsolutePath().toString(), descriptors, handles);
        } catch (RocksDBException e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to open the reconstructed RocksDB checkpoint directory "
                            + dir.toAbsolutePath() + " read-only — the checkpoint is physically"
                            + " inconsistent (corrupt/truncated MANIFEST, CURRENT, WAL or SST?)");
        }
    }

    private static boolean isDefaultCf(String cfName) {
        return "default".equals(cfName) || "__default__".equals(cfName);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // best-effort cleanup of the temp reconstructed dir; the
                            // residue is observable via the warning (item 11 RK-7).
                            LOG.warn("Failed to delete temporary restore file {}", p, e);
                        }
                    });
        }
    }
}
