/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.storage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Side-channel content-addressed store for shared state segments (SST files).
 *
 * <p>Unlike {@link ICheckpointStorage} (whose contract is checkpoint-level blob
 * storage), {@code ISegmentStore} operates at the granularity of a single
 * content-addressed file. Two segments with the same content hash share one
 * physical file. Reference counting is <em>not</em> performed here — that is the
 * exclusive responsibility of {@code SharedStateRegistry}. The store only owns
 * the file's physical lifecycle: store (materialize), check existence, locate,
 * and discard when the registry reports zero references.
 *
 * <p>This interface is deliberately minimal so that a JDBC / object-store backed
 * implementation can be added later without touching the registry contract.
 */
public interface ISegmentStore {

    /**
     * Materialize a segment file under its content hash. If a segment with the same
     * hash already exists, this is a no-op (content-addressed reuse) — the caller is
     * responsible for not double-counting references via {@code SharedStateRegistry}.
     *
     * @param sourceFile  the source file to copy into the store
     * @param contentHash the SHA-256 content hash of the file (its storage identity)
     * @throws IOException if the copy fails
     */
    void storeSegment(Path sourceFile, String contentHash) throws IOException;

    /**
     * Physically delete the segment identified by {@code contentHash}. If it does not
     * exist, this is a no-op.
     *
     * @param contentHash the SHA-256 content hash of the segment to discard
     * @throws IOException if the delete fails
     */
    void discardSegment(String contentHash) throws IOException;

    /**
     * @return {@code true} iff a segment with the given content hash is present.
     */
    boolean segmentExists(String contentHash);

    /**
     * @return the storage path for the given content hash (the file may or may not exist yet).
     */
    Path getSegmentPath(String contentHash);

    /** @return a human-readable name for this store (for logging / diagnostics). */
    String getName();
}
