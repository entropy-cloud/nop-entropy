package io.nop.code.core.cluster;

import java.util.List;

/**
 * N6.3: 一个索引分片 = 稳定哈希分配给它的相对路径集合。分片 id 为 0..shardCount-1。
 */
public class IndexShard {
    private final int shardId;
    private final List<String> filePaths;

    public IndexShard(int shardId, List<String> filePaths) {
        this.shardId = shardId;
        this.filePaths = List.copyOf(filePaths);
    }

    public int getShardId() {
        return shardId;
    }

    public List<String> getFilePaths() {
        return filePaths;
    }
}
