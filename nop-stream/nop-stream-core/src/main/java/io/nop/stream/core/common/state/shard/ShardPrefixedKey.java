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
 * A key wrapper that prefixes the original key with a key-group id.
 * Used internally by {@link io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend}
 * for group-aware key routing.
 *
 * <p>Stage 34: the {@code shardId} field now holds the key-group id (computed
 * via {@link KeyGroupAssignment#assignToKeyGroup(Object, int)}). The field name
 * is retained for binary/source compatibility; {@link #getKeyGroupId()} is the
 * preferred accessor.
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

    /**
     * Stage 34: the key-group id this key is routed to. Identical to
     * {@link #getShardId()} (the underlying field).
     */
    public int getKeyGroupId() {
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
        return "ShardPrefixedKey{keyGroupId=" + shardId + ", key=" + key + '}';
    }
}
