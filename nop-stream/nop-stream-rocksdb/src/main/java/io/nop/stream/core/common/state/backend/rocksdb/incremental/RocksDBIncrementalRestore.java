/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb.incremental;

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
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

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
                cfNames = new ArrayList<>();
                cfNames.add(RocksDB.DEFAULT_COLUMN_FAMILY);
            }
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            for (byte[] cfName : cfNames) {
                descriptors.add(new ColumnFamilyDescriptor(cfName, cfOpts));
            }
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            try (RocksDB src = RocksDB.openReadOnly(dbOptions, reconstructed.toAbsolutePath().toString(),
                    descriptors, handles)) {
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
            return "__default__";
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
                        } catch (IOException ignored) {
                            // best-effort cleanup of the temp reconstructed dir
                        }
                    });
        }
    }
}
