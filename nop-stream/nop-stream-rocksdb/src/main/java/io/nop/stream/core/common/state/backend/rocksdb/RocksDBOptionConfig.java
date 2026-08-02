/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.Serializable;

/**
 * Minimal RocksDB tuning configuration for Stage 30.
 *
 * <p>Stage 30 exposes only the most impactful options. Advanced tuning
 * (block cache, bloom filter, compaction style) is deferred to a successor.
 */
public class RocksDBOptionConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_WRITE_BUFFER_SIZE = 64 * 1024 * 1024L;
    public static final int DEFAULT_MAX_BACKGROUND_THREADS = 4;

    private final long writeBufferSize;
    private final int maxBackgroundThreads;

    public RocksDBOptionConfig() {
        this(DEFAULT_WRITE_BUFFER_SIZE, DEFAULT_MAX_BACKGROUND_THREADS);
    }

    public RocksDBOptionConfig(long writeBufferSize, int maxBackgroundThreads) {
        this.writeBufferSize = writeBufferSize;
        this.maxBackgroundThreads = maxBackgroundThreads;
    }

    public long getWriteBufferSize() {
        return writeBufferSize;
    }

    public int getMaxBackgroundThreads() {
        return maxBackgroundThreads;
    }
}
