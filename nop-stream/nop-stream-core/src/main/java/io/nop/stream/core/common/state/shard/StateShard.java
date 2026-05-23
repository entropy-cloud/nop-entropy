/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.Objects;

@DataBean
public class StateShard implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int stateShardCount;
    private final int stateShardId;
    private final int ownerSubtask;
    private final String hashPolicy;

    public StateShard(int stateShardCount, int stateShardId, int ownerSubtask, String hashPolicy) {
        if (stateShardCount < 1) {
            throw new IllegalArgumentException("stateShardCount must be at least 1");
        }
        if (stateShardId < 0 || stateShardId >= stateShardCount) {
            throw new IllegalArgumentException("stateShardId must be in [0, " + stateShardCount + ")");
        }
        this.stateShardCount = stateShardCount;
        this.stateShardId = stateShardId;
        this.ownerSubtask = ownerSubtask;
        this.hashPolicy = hashPolicy != null ? hashPolicy : "DEFAULT";
    }

    public StateShard() {
        this(1, 0, 0, "DEFAULT");
    }

    public static StateShard singleShard(int ownerSubtask) {
        return new StateShard(1, 0, ownerSubtask, "DEFAULT");
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

    public int computeShardId(Object key) {
        if (stateShardCount == 1) {
            return 0;
        }
        return Math.abs(stableHash(key)) % stateShardCount;
    }

    static int stableHash(Object key) {
        return key != null ? key.hashCode() : 0;
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
                && Objects.equals(hashPolicy, that.hashPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateShardCount, stateShardId, ownerSubtask, hashPolicy);
    }

    @Override
    public String toString() {
        return "StateShard{shardCount=" + stateShardCount
                + ", shardId=" + stateShardId
                + ", owner=" + ownerSubtask
                + ", policy='" + hashPolicy + '\''
                + '}';
    }
}
