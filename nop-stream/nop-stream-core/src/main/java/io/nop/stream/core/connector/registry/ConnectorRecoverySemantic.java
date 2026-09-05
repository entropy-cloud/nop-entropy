/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

/**
 * Structured summary of the recovery semantic (cursor/offset checkpoint path) of a
 * registered connector endpoint. This is the descriptor-level summary of the recovery
 * column of the connector capability matrix; the full prose (state keys, restore paths,
 * pinning tests) remains doc-owned per the Phase 1 alignment口径 (connector-design.md §8.4).
 */
public enum ConnectorRecoverySemantic {

    /** No checkpoint participation; recovery behavior is fully backend/driver-defined. */
    NONE,

    /** A consumed offset/counter is checkpointed into operator state and restored on recovery. */
    OFFSET_CHECKPOINT,

    /** Per-split cursors plus enumerator state are checkpointed (FLIP-27 split-based sources). */
    SPLIT_CURSOR_CHECKPOINT,

    /** Two-phase-commit pending transactions are checkpointed (durable epochs re-committed, others aborted). */
    TWO_PHASE_PENDING_COMMITS,

    /** No checkpoint participation; failure retry relies on buffered re-flush semantics. */
    BUFFERED_RETRY
}
