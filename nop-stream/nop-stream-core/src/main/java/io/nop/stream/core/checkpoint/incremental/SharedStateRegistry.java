/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.incremental;

import java.util.List;

/**
 * Reference-counting registry for content-addressed shared state (SST files).
 *
 * <p>The registry is the single source of truth for how many live checkpoints
 * reference a given SST content hash. It performs <em>only in-memory</em> bookkeeping;
 * physical file deletion is delegated to {@code ISegmentStore.discardSegment} by the
 * caller, driven by the zero-reference handles returned from {@link #unregister}.
 *
 * <p>Lifecycle: one registry per job, held by {@code CheckpointCoordinator}.
 */
public interface SharedStateRegistry {

    /**
     * Register (or re-register) a shared state handle. If a handle with the same
     * {@code stateObjectId} (= content hash) is already registered, the previously
     * registered canonical handle is returned and its reference count is incremented;
     * otherwise the supplied handle becomes the canonical one with reference count 1.
     *
     * @param handle the handle produced by the task-side snapshot (content-addressed)
     * @return the de-duplicated canonical handle (identity-equal for repeated calls
     *         with the same content hash)
     */
    SharedStateHandle register(SharedStateHandle handle);

    /**
     * Decrement the reference count for the state object identified by
     * {@code stateObjectId} (= content hash). If the count drops to zero, the entry
     * is removed from the registry and its handle is returned so the caller can
     * physically discard the file. If the count is still positive (or the id was
     * never registered), an empty list is returned.
     *
     * @param stateObjectId the content hash / state-object id to release
     * @return a list containing the now-unreferenced handle (size 0 or 1)
     */
    List<SharedStateHandle> unregister(String stateObjectId);

    /**
     * @return the current reference count for the given state object id, or 0 if unknown.
     */
    int getReferenceCount(String stateObjectId);
}
