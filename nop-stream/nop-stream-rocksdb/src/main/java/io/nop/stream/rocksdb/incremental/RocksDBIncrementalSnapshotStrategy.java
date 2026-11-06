/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb.incremental;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.rocksdb.Checkpoint;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.checkpoint.incremental.SharedStateHandle;
import io.nop.stream.core.checkpoint.incremental.SstFileChecksum;
import io.nop.stream.core.exceptions.StreamException;

/**
 * Creates a physically-consistent RocksDB snapshot via the native
 * {@link Checkpoint} JNI API and turns it into a content-addressed
 * {@link IncrementalSnapshotResult}.
 *
 * <p>The native checkpoint (hard-links or copies the live SST files) is created
 * under {@code {checkpointBaseDir}/cp-{checkpointId}/native/}. SST files
 * ({@code .sst} / legacy {@code .ldb}) are read, SHA-256 hashed, and wrapped
 * into {@link SharedStateHandle}s — these are the candidates for cross-checkpoint
 * sharing via {@code SharedStateRegistry}. Non-SST files (WAL, MANIFEST,
 * OPTIONS, CURRENT, IDENTITY, …) are <em>copied</em> into
 * {@code {checkpointBaseDir}/cp-{checkpointId}/non-sst/} because they are
 * per-checkpoint and must be fully restored to reopen the DB.
 *
 * <p>This strategy does NOT register handles with the registry — that is the
 * coordinator's responsibility (Stage 31 Phase 4), per the design decision
 * "task 侧策略返回 raw handles，coordinator 侧做 registry 注册". The strategy
 * also performs no file deletion; lifecycle of the checkpoint directories is
 * owned by the caller / coordinator.
 *
 * <p>API note: rocksdbjni 9.11.2 {@link Checkpoint#createCheckpoint(String)}
 * is the single-arg form (no {@code logSizeForFlush} overload). The checkpoint
 * is consistent on return.
 */
public final class RocksDBIncrementalSnapshotStrategy {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(RocksDBIncrementalSnapshotStrategy.class);

    /** CRA 8.43 R4-03 W-09: hash only new or size-changed SST files (default). */
    public static final String HASH_POLICY_NEW_ONLY = "new-only";
    /** CRA 8.43 R4-03 W-09: re-hash every SST file each checkpoint (opt-out tier). */
    public static final String HASH_POLICY_FULL = "full";

    /** CRA 8.43 R4-03 W-09: hash policy for this strategy instance. */
    private final String hashPolicy;

    /**
     * CRA 8.43 R4-03 W-09: previous-checkpoint SST index keyed by file name
     * (an SST file never mutates under a fixed name, so (name, size) equality
     * is an honest hash-reuse basis — mtime is unreliable across checkpoint
     * directories and is not used). Seeded from the restored snapshot's
     * SharedStateHandle list; rolled forward after every checkpoint. A fresh
     * open starts empty, so the FIRST checkpoint degrades to full hashing —
     * the correct semantics with no index to reuse.
     */
    private final java.util.Map<String, SstIndexEntry> previousSstIndex = new java.util.HashMap<>();

    private static final class SstIndexEntry {
        final String hash;
        final long size;

        SstIndexEntry(String hash, long size) {
            this.hash = hash;
            this.size = size;
        }
    }

    public RocksDBIncrementalSnapshotStrategy() {
        this(HASH_POLICY_NEW_ONLY);
    }

    public RocksDBIncrementalSnapshotStrategy(String hashPolicy) {
        if (!HASH_POLICY_NEW_ONLY.equals(hashPolicy) && !HASH_POLICY_FULL.equals(hashPolicy)) {
            throw new StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG)
                    .param("hashPolicy", String.valueOf(hashPolicy))
                    .param("detail", "nop.stream.rocksdb.snapshot-hash-policy must be '"
                            + HASH_POLICY_NEW_ONLY + "' or '" + HASH_POLICY_FULL + "', got: " + hashPolicy);
        }
        this.hashPolicy = hashPolicy;
    }

    /**
     * CRA 8.43 R4-03 W-09: seed the SST index from a restored incremental
     * snapshot's handle list (hash + size per original SST file name), so the
     * first post-restore checkpoint can reuse the previous hashes for
     * unchanged files. Called by the owning backend's restore path.
     */
    public void seedIndexFromHandles(List<SharedStateHandle> handles) {
        if (handles == null) {
            return;
        }
        for (SharedStateHandle handle : handles) {
            String fileName = extractFileName(handle.getFilePath());
            if (fileName != null) {
                previousSstIndex.put(fileName, new SstIndexEntry(handle.getContentHash(), handle.getSize()));
            }
        }
    }

    private static String extractFileName(String path) {
        if (path == null) {
            return null;
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /** CRA 8.43 R4-03 W-09: how many SST entries the reuse index currently holds. */
    int previousSstIndexSize() {
        return previousSstIndex.size();
    }

    /**
     * Build an incremental snapshot result from the live RocksDB instance.
     *
     * @param db              the live RocksDB (must be open)
     * @param checkpointBaseDir parent directory under which {@code cp-{checkpointId}} is created
     * @param checkpointId    logical checkpoint id used to name the per-checkpoint subdirectory
     * @return a content-addressed incremental snapshot result
     */
    public IncrementalSnapshotResult doSnapshot(RocksDB db, Path checkpointBaseDir, long checkpointId)
            throws RocksDBException, IOException {
        Path checkpointDir = checkpointBaseDir.resolve("cp-" + checkpointId);
        Path nativeDir = checkpointDir.resolve("native");
        Path nonSstDir = checkpointDir.resolve("non-sst");

        Files.createDirectories(checkpointBaseDir);
        // createCheckpoint requires its target directory to NOT already exist; RocksDB
        // creates it. Ensure both the parent exists and any stale native dir is removed.
        Files.createDirectories(checkpointDir.toAbsolutePath());
        deleteIfExists(nativeDir);

        try (Checkpoint cp = Checkpoint.create(db)) {
            cp.createCheckpoint(nativeDir.toAbsolutePath().toString());
        }

        Files.createDirectories(nonSstDir);

        List<SharedStateHandle> sstHandles = new ArrayList<>();
        List<String> nonSstFileNames = new ArrayList<>();
        // hash -> original SST filename, so a restore can rename content-addressed shared
        // files back to the names RocksDB's MANIFEST references (see restore helper).
        StringBuilder nameMap = new StringBuilder();
        long totalSize = 0L;

        try (Stream<Path> entries = Files.list(nativeDir)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                String lower = name.toLowerCase();
                if (lower.endsWith(".sst") || lower.endsWith(".ldb")) {
                    long size = Files.size(entry);
                    // CRA 8.43 R4-03 W-09 (C8-013): hash-reuse under the SST
                    // immutability contract — an SST file never mutates under a
                    // fixed name, so (name, size) equality honestly carries the
                    // previous hash forward. full policy (or an index miss /
                    // size drift) re-hashes the whole file.
                    String hash;
                    SstIndexEntry previous = HASH_POLICY_NEW_ONLY.equals(hashPolicy)
                            ? previousSstIndex.get(name) : null;
                    if (previous != null && previous.size == size) {
                        hash = previous.hash;
                    } else {
                        hash = SstFileChecksum.sha256Hex(entry);
                        previousSstIndex.put(name, new SstIndexEntry(hash, size));
                    }
                    sstHandles.add(new SharedStateHandle(hash, entry.toAbsolutePath().toString(), size));
                    totalSize += size;
                    nameMap.append(hash).append('|').append(name).append('\n');
                } else {
                    Path dest = nonSstDir.resolve(name);
                    Files.copy(entry, dest, StandardCopyOption.REPLACE_EXISTING);
                    nonSstFileNames.add(name);
                }
            }
        }

        // Sidecar mapping (plain text, no JSON dep) consumed by the restore helper.
        Files.writeString(nonSstDir.resolve(SST_NAME_MAP_FILE), nameMap.toString(),
                java.nio.charset.StandardCharsets.UTF_8);

        // F-02 (Plan 2026-09-04-1326-1 Phase 3, adjudication D3=(b)): rolling retention
        // of the task-local cp-N dirs. Every snapshot previously left a full
        // {native + non-sst} copy behind forever (non-SST files are copied per
        // checkpoint), so a long-running incremental job grew local disk linearly in the
        // checkpoint count. Retention keeps the newest LOCAL_RETENTION dirs — the
        // newest is what a same-JVM restore resolves (result.getNonSstDir()), and
        // K=2 covers "last durable + one in-flight" under maxConcurrentCheckpoints=1.
        pruneOldCheckpoints(checkpointBaseDir, checkpointId, localRetention());

        return new IncrementalSnapshotResult(checkpointId, sstHandles,
                nonSstDir.toAbsolutePath().toString(), nonSstFileNames, totalSize);
    }

    /** Sidecar filename (in the non-sst dir) holding {@code hash|originalName} lines. */
    public static final String SST_NAME_MAP_FILE = "sst-name-map.txt";

    /**
     * F-02: how many task-local {@code cp-{id}} dirs the rolling retention keeps
     * (newest N). Default 2; overridable via the
     * {@code nop.stream.rocksdb.incremental.local-retention} system property (values
     * below 1 are clamped to 1 — the newest dir must always survive for restore).
     */
    static int localRetention() {
        String raw = System.getProperty("nop.stream.rocksdb.incremental.local-retention");
        if (raw == null || raw.isBlank()) {
            return 2;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    /**
     * F-02: delete every {@code cp-{id}} dir whose numeric id falls outside the newest
     * {@code retention} ids present. Best-effort per dir (a failed delete is logged via
     * the thrown StreamException only for the CURRENT checkpoint's own stale native dir;
     * retention deletions that fail leave an observable residue but never abort the
     * snapshot that already succeeded).
     */
    static void pruneOldCheckpoints(Path checkpointBaseDir, long currentId, int retention) {
        List<Long> ids;
        try (Stream<Path> entries = Files.list(checkpointBaseDir)) {
            ids = new ArrayList<>();
            for (Path entry : (Iterable<Path>) entries::iterator) {
                String name = entry.getFileName().toString();
                if (!Files.isDirectory(entry) || !name.startsWith("cp-")) {
                    continue;
                }
                try {
                    ids.add(Long.parseLong(name.substring("cp-".length())));
                } catch (NumberFormatException ignored) {
                    // not a checkpoint dir — leave it alone
                }
            }
        } catch (IOException e) {
            throw new StreamException("Failed to list checkpoint base dir for retention pruning: "
                    + checkpointBaseDir, e);
        }
        ids.sort(Long::compareTo);
        for (int i = 0; i < ids.size() - retention; i++) {
            Path victim = checkpointBaseDir.resolve("cp-" + ids.get(i));
            try {
                deleteIfExists(victim);
            } catch (IOException e) {
                // Retention pruning must not fail the snapshot; the residue stays
                // observable on disk and the next pruning pass retries.
                LOG.warn("F-02 retention pruning failed to delete task-local checkpoint dir {}: ",
                        victim, e);
            }
        }
    }

    private static void deleteIfExists(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                throw new StreamException("Failed to delete " + p, e);
                            }
                        });
            }
        }
    }
}
