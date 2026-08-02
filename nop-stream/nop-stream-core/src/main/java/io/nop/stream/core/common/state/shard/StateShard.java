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

import io.nop.api.core.annotations.data.DataBean;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

@DataBean
public class StateShard implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int stateShardCount;
    private final int stateShardId;
    private final int ownerSubtask;
    private final String hashPolicy;

    /**
     * Job-global upper bound on the number of key groups for the job that owns
     * this shard. Stage 34 evolves the routing model from a fixed
     * {@code stateShardCount} to a key-group model where
     * {@code keyGroupId = stableHash(key) % maxParallelism}. The field defaults
     * to {@link KeyGroup#DEFAULT_MAX_PARALLELISM} so that old checkpoints
     * (which carry an empty {@code shards} list, or a serialized StateShard
     * without this field) still JSON-round-trip with the default applied.
     */
    private final int maxParallelism;

    public StateShard(int stateShardCount, int stateShardId, int ownerSubtask, String hashPolicy) {
        this(stateShardCount, stateShardId, ownerSubtask, hashPolicy, KeyGroup.DEFAULT_MAX_PARALLELISM);
    }

    public StateShard(int stateShardCount, int stateShardId, int ownerSubtask, String hashPolicy, int maxParallelism) {
        if (stateShardCount < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "stateShardCount").param(ARG_DETAIL, "must be at least 1");
        }
        if (stateShardId < 0 || stateShardId >= stateShardCount) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "stateShardId")
                    .param(ARG_DETAIL, "must be in [0, " + stateShardCount + ")");
        }
        if (maxParallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "maxParallelism").param(ARG_DETAIL, "must be at least 1");
        }
        this.stateShardCount = stateShardCount;
        this.stateShardId = stateShardId;
        this.ownerSubtask = ownerSubtask;
        this.hashPolicy = hashPolicy != null ? hashPolicy : "DEFAULT";
        this.maxParallelism = maxParallelism;
    }

    public StateShard() {
        this(1, 0, 0, "DEFAULT", KeyGroup.DEFAULT_MAX_PARALLELISM);
    }

    public static StateShard singleShard(int ownerSubtask) {
        return new StateShard(1, 0, ownerSubtask, "DEFAULT", KeyGroup.DEFAULT_MAX_PARALLELISM);
    }

    public int getStateShardCount() {
        return stateShardCount;
    }

    public int getStateShardId() {
        return stateShardId;
    }

    public int getOwnerSubtask() {
        return ownerSubtask;
    }

    public String getHashPolicy() {
        return hashPolicy;
    }

    /**
     * Stage 34: job-global key-group upper bound associated with this shard.
     */
    public int getMaxParallelism() {
        return maxParallelism;
    }

    /**
     * Compute the logical shard/group id for {@code key} under this shard's
     * {@code stateShardCount}. Delegates to the stable key-group hash, so the
     * result is deterministic across JVM instances for value-stable key types.
     *
     * <p>Retained for backward compatibility with existing tests; the runtime
     * backends route via {@link KeyGroupAssignment#assignToKeyGroup(Object, int)}
     * keyed on {@code maxParallelism}.
     */
    public int computeShardId(Object key) {
        if (stateShardCount == 1) {
            return 0;
        }
        return (stableHash(key) & 0x7FFFFFFF) % stateShardCount;
    }

    /**
     * Stable, cross-JVM-deterministic hash for {@code key}. Delegates to
     * {@link KeyGroupAssignment#stableHash(Object)} since Stage 34. For
     * built-in value types (String/Long/Integer/...) this is identical to the
     * legacy {@code key.hashCode()}, preserving routing parity; for POJO keys
     * it is now Murmur3 over canonical JSON bytes (G38).
     */
    public static int stableHash(Object key) {
        return KeyGroupAssignment.stableHash(key);
    }

    public String keyPrefix() {
        if (stateShardCount == 1) {
            return "";
        }
        return stateShardId + "/";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateShard that = (StateShard) o;
        return stateShardCount == that.stateShardCount
                && stateShardId == that.stateShardId
                && ownerSubtask == that.ownerSubtask
                && maxParallelism == that.maxParallelism
                && Objects.equals(hashPolicy, that.hashPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateShardCount, stateShardId, ownerSubtask, hashPolicy, maxParallelism);
    }

    @Override
    public String toString() {
        return "StateShard{shardCount=" + stateShardCount
                + ", shardId=" + stateShardId
                + ", owner=" + ownerSubtask
                + ", policy='" + hashPolicy + '\''
                + ", maxParallelism=" + maxParallelism
                + '}';
    }
}
