/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.io.Serializable;
import java.util.Objects;

import io.nop.api.core.annotations.core.Internal;

/**
 * Logical partitioning unit for keyed state. A key group is the atomic unit of
 * key&#8594;subtask redistribution: every key is assigned to exactly one
 * {@code KeyGroup} via a stable hash, and a rescale only changes which subtask
 * owns which contiguous {@link KeyGroupRange} &#8212; the key&#8594;group mapping
 * itself never changes for a fixed {@code maxParallelism}.
 *
 * <p>{@code maxParallelism} is the job-global upper bound on the number of key
 * groups (default {@value #DEFAULT_MAX_PARALLELISM}). It is set once when the
 * state backend is constructed and stays constant for the lifetime of the job,
 * which is what keeps the key&#8594;group mapping stable across parallelism
 * changes.
 */
@Internal
public final class KeyGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Default upper bound for the number of key groups in a keyed job. Matches
     * the Flink default and is large enough to support fine-grained rescale
     * (up to 128 subtasks with one group each).
     */
    public static final int DEFAULT_MAX_PARALLELISM = 128;

    /**
     * Hard upper bound accepted for {@code maxParallelism}. Keeps the key-group
     * id within a non-negative {@code int} range for the big-endian sortable
     * binary prefix used by the RocksDB encoder.
     */
    public static final int UPPER_BOUND_MAX_PARALLELISM = 1 << 15;

    private final int keyGroupId;

    public KeyGroup(int keyGroupId) {
        if (keyGroupId < 0) {
            throw new IllegalArgumentException("keyGroupId must be non-negative: " + keyGroupId);
        }
        this.keyGroupId = keyGroupId;
    }

    public int getKeyGroupId() {
        return keyGroupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyGroup keyGroup = (KeyGroup) o;
        return keyGroupId == keyGroup.keyGroupId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyGroupId);
    }

    @Override
    public String toString() {
        return "KeyGroup{id=" + keyGroupId + '}';
    }
}
