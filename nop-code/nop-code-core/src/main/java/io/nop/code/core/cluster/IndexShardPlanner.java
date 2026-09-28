package io.nop.code.core.cluster;

import java.util.ArrayList;
import java.util.List;

/**
 * N6.3: 确定性分片计划——路径稳定哈希 mod shardCount，同一输入永远得到同一分片；
 * 分片互斥且并集完备（全部输入路径恰好出现一次）。分片内保持输入序（调用方传有序列表）。
 */
public final class IndexShardPlanner {

    private IndexShardPlanner() {
    }

    public static List<IndexShard> plan(List<String> relativePaths, int shardCount) {
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be >= 1, got: " + shardCount);
        }
        List<List<String>> buckets = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            buckets.add(new ArrayList<>());
        }
        for (String path : relativePaths) {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("relative path must be non-empty");
            }
            buckets.get(Math.floorMod(path.hashCode(), shardCount)).add(path);
        }
        List<IndexShard> shards = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            shards.add(new IndexShard(i, buckets.get(i)));
        }
        return shards;
    }

}
