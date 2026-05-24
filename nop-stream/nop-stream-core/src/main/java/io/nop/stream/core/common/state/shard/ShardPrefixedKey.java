/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.util.Objects;

/**
 * A key wrapper that prefixes the original key with a shard ID.
 * Used internally by {@link io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend}
 * for shard-aware key routing.
 */
public class ShardPrefixedKey {

    final int shardId;
    final Object key;

    public ShardPrefixedKey(int shardId, Object key) {
        this.shardId = shardId;
        this.key = key;
    }

    public int getShardId() {
        return shardId;
    }

    public Object getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShardPrefixedKey that = (ShardPrefixedKey) o;
        return shardId == that.shardId && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shardId, key);
    }

    @Override
    public String toString() {
        return "ShardPrefixedKey{shardId=" + shardId + ", key=" + key + '}';
    }
}
