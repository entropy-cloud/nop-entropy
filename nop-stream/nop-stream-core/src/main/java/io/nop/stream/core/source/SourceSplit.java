/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import java.io.Serializable;

/**
 * Identifies a single, recoverable partition of a source's input that can be assigned to
 * exactly one {@link SourceReader} at a time. A split is the unit of parallel work for
 * FLIP-27 style split-based sources.
 *
 * <p>Implementations must be {@link Serializable} so they can travel from the
 * {@link SplitEnumerator} (coordinator side) to {@link SourceReader} (task side) and back
 * via checkpoint state.
 *
 * <p>The contract intentionally carries only a stable identifier. Cursor / offset / position
 * metadata is implementation-specific and lives on the concrete split type (e.g. file path +
 * byte offset for {@code FileSplit}, topic + partition + offset for a Kafka split).
 *
 * <p>Stage 49 D1: FLIP-27 whole-split assignment — a split is not further subdivided; if
 * the enumerator wants to redistribute work, it reassigns whole splits rather than
 * fraction-splitting a split.
 *
 * @see SimpleSourceSplit
 * @see SourceReader#addSplits(java.util.List)
 */
public interface SourceSplit extends Serializable {

    /**
     * Returns a stable, unique identifier for this split. Used as the key in enumerator
     * state (discovered / unassigned / assigned / finished sets) and in checkpoint state.
     *
     * @return a non-null, stable split id
     */
    String splitId();
}
