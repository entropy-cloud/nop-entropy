/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb.incremental;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.checkpoint.storage.ISegmentStore;

/**
 * Stage 31 restore helper: reconstructs a complete, openable RocksDB directory from the
 * content-addressed shared SST files (in {@link ISegmentStore}, renamed to
 * {@code {hash}.sst}) plus the per-checkpoint non-SST companion files (WAL / MANIFEST /
 * OPTIONS / CURRENT / IDENTITY) and the {@code sst-name-map.txt} sidecar that maps each
 * content hash back to its original SST filename (which RocksDB's MANIFEST references).
 *
 * <p>The result directory is a consistent RocksDB checkpoint that can be opened directly.
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
}
