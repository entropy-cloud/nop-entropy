/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.io.Serializable;

/**
 * Minimal RocksDB tuning configuration for Stage 30.
 *
 * <p>Stage 30 exposed only the most impactful options (write buffer size,
 * background threads). Plan 369 Phase 3 adds the memory-control preset:
 * shared block cache, a {@code WriteBufferManager} total-cap bound to that
 * cache, and a bloom filter — all OFF by default (value {@code 0}), which
 * keeps the pre-preset native option set byte-identical. A control is enabled
 * only by configuring a positive value:
 * <ul>
 *   <li>{@code blockCacheSize} &gt; 0 — a shared LRU block cache of that many
 *       bytes, wired into every column family's table format.</li>
 *   <li>{@code writeBufferManagerCapacity} &gt; 0 — caps the TOTAL memtable
 *       memory across all column families via a {@code WriteBufferManager}
 *       charged to the shared block cache (rocksdbjni constructs a
 *       {@code WriteBufferManager} only over a {@code Cache}, so this requires
 *       {@code blockCacheSize} &gt; 0; the backend fails fast otherwise).</li>
 *   <li>{@code bloomFilterBitsPerKey} &gt; 0 — a bloom filter with that many
 *       bits per key on every mem/SST table.</li>
 * </ul>
 */
public class RocksDBOptionConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_WRITE_BUFFER_SIZE = 64 * 1024 * 1024L;
    public static final int DEFAULT_MAX_BACKGROUND_THREADS = 4;

    private final long writeBufferSize;
    private final int maxBackgroundThreads;
    private final long blockCacheSize;
    private final long writeBufferManagerCapacity;
    private final double bloomFilterBitsPerKey;

    public RocksDBOptionConfig() {
        this(DEFAULT_WRITE_BUFFER_SIZE, DEFAULT_MAX_BACKGROUND_THREADS);
    }

    public RocksDBOptionConfig(long writeBufferSize, int maxBackgroundThreads) {
        this(writeBufferSize, maxBackgroundThreads, 0L, 0L, 0d);
    }

    /**
     * Full constructor including the plan 369 memory-control preset. A value
     * of {@code 0} leaves the corresponding control disabled (default
     * behavior, no custom table format).
     */
    public RocksDBOptionConfig(long writeBufferSize, int maxBackgroundThreads,
                               long blockCacheSize, long writeBufferManagerCapacity,
                               double bloomFilterBitsPerKey) {
        this.writeBufferSize = writeBufferSize;
        this.maxBackgroundThreads = maxBackgroundThreads;
        this.blockCacheSize = blockCacheSize;
        this.writeBufferManagerCapacity = writeBufferManagerCapacity;
        this.bloomFilterBitsPerKey = bloomFilterBitsPerKey;
    }

    public long getWriteBufferSize() {
        return writeBufferSize;
    }

    public int getMaxBackgroundThreads() {
        return maxBackgroundThreads;
    }

    /**
     * Shared LRU block cache capacity in bytes; {@code 0} = no shared cache
     * (RocksDB's per-column-family default, pre-preset behavior).
     */
    public long getBlockCacheSize() {
        return blockCacheSize;
    }

    /**
     * Total memtable memory cap (all column families) in bytes, enforced by a
     * {@code WriteBufferManager} charged to the shared block cache; {@code 0} =
     * no cap. Requires {@link #getBlockCacheSize()} &gt; 0.
     */
    public long getWriteBufferManagerCapacity() {
        return writeBufferManagerCapacity;
    }

    /**
     * Bloom filter bits per key; {@code 0} = no bloom filter (default).
     */
    public double getBloomFilterBitsPerKey() {
        return bloomFilterBitsPerKey;
    }

    /**
     * Whether any memory-control knob is enabled (a custom table format /
     * cache / write-buffer manager must be built at open time).
     */
    public boolean isMemoryControlEnabled() {
        return blockCacheSize > 0 || writeBufferManagerCapacity > 0 || bloomFilterBitsPerKey > 0;
    }

    @Override
    public String toString() {
        return "RocksDBOptionConfig{writeBufferSize=" + writeBufferSize
                + ", maxBackgroundThreads=" + maxBackgroundThreads
                + ", blockCacheSize=" + blockCacheSize
                + ", writeBufferManagerCapacity=" + writeBufferManagerCapacity
                + ", bloomFilterBitsPerKey=" + bloomFilterBitsPerKey + "}";
    }
}
