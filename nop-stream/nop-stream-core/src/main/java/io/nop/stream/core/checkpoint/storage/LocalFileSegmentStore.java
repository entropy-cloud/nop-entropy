/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@link ISegmentStore} backed by the local filesystem. Segments are stored
 * content-addressed under {@code {baseDir}/shared-state/{hash-prefix}/{hash}.sst},
 * sharded by the first two hex characters of the hash to avoid single-directory
 * fan-out. {@code discardSegment} physically deletes the file.
 *
 * <p>This store does NOT perform reference counting; {@code SharedStateRegistry}
 * drives {@link #discardSegment} only when a handle's ref-count reaches zero.
 */
public class LocalFileSegmentStore implements ISegmentStore {

    private static final String SHARED_STATE_DIR = "shared-state";
    private static final String SST_SUFFIX = ".sst";

    private final Path baseDir;

    public LocalFileSegmentStore(Path baseDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir must not be null");
        }
        this.baseDir = baseDir;
    }

    @Override
    public void storeSegment(Path sourceFile, String contentHash) throws IOException {
        if (sourceFile == null || contentHash == null || contentHash.length() < 2) {
            throw new IllegalArgumentException("sourceFile and contentHash are required");
        }
        Path target = pathFor(contentHash);
        if (Files.exists(target)) {
            // Content-addressed reuse: identical hash -> identical bytes; nothing to do.
            // F-03b: this short-circuit is sound because the target only ever appears
            // via the atomic move below — a file at the final name is always a
            // COMPLETE segment (a crashed writer leaves only the temp name behind).
            return;
        }
        Files.createDirectories(target.getParent());
        // F-03b (Plan 2026-09-04-1326-1 Phase 3): write to a temp sibling then ATOMIC
        // MOVE into the final {hash}.sst name. The previous direct copy could crash
        // mid-write and leave a permanently truncated file at the content-addressed
        // name; combined with the coordinator's segmentExists(hash) short-circuit,
        // that truncation was then treated as "segment present" forever.
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp-" + java.util.UUID.randomUUID());
        try {
            Files.copy(sourceFile, tmp);
            try {
                Files.move(tmp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException amnse) {
                // Cross-filesystem or FS without atomic rename: fall back to a plain
                // REPLACE_EXISTING move. The temp name still shields the final name
                // from partially-copied bytes in the common case.
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public void discardSegment(String contentHash) throws IOException {
        if (contentHash == null || contentHash.length() < 2) {
            return;
        }
        Files.deleteIfExists(pathFor(contentHash));
    }

    @Override
    public boolean segmentExists(String contentHash) {
        if (contentHash == null || contentHash.length() < 2) {
            return false;
        }
        return Files.exists(pathFor(contentHash));
    }

    @Override
    public Path getSegmentPath(String contentHash) {
        if (contentHash == null || contentHash.length() < 2) {
            throw new IllegalArgumentException("contentHash must be at least 2 chars");
        }
        return pathFor(contentHash);
    }

    @Override
    public String getName() {
        return "local-file-segment-store(" + baseDir + ")";
    }

    public Path getBaseDir() {
        return baseDir;
    }

    private Path pathFor(String contentHash) {
        String prefix = contentHash.substring(0, 2);
        return baseDir.resolve(SHARED_STATE_DIR).resolve(prefix).resolve(contentHash + SST_SUFFIX);
    }
}
