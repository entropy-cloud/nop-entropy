/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.io.Serializable;

import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IOperatorStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryOperatorStateBackend;
import io.nop.stream.core.common.state.shard.KeyGroup;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * RocksDB state backend. All keyed state is stored in off-heap RocksDB
 * column families, allowing the working set to exceed the JVM heap.
 *
 * <p>Snapshot/restore produces a {@link io.nop.stream.core.common.state.backend.StateSnapshot}
 * that is byte-compatible with {@code MemoryKeyedStateBackend}, so checkpoints
 * are interchangeable across backends.
 *
 * <p>Operator state reuses {@code MemoryOperatorStateBackend} (operator state
 * is small and not the target of off-heap storage).
 *
 * <p>Stage 34：{@code shardCount} 参数语义已迁移为 job-global {@code maxParallelism}
 * （key-group 上界，默认 {@link KeyGroup#DEFAULT_MAX_PARALLELISM}）。
 */
public class RocksDBStateBackend implements IStateBackend, Serializable {

    private static final long serialVersionUID = 1L;

    private final String dbPath;
    private final int maxParallelism;
    private final RocksDBOptionConfig optionConfig;

    /**
     * Create a RocksDB state backend.
     *
     * @param dbPath         directory for the RocksDB database
     * @param maxParallelism job-global key-group upper bound (&ge; 1); when &gt; 1,
     *                       keys are routed to key-groups via a stable hash.
     *                       Replaces the legacy {@code shardCount} parameter.
     * @param optionConfig   tuning options (may be null for defaults)
     */
    public RocksDBStateBackend(String dbPath, int maxParallelism, RocksDBOptionConfig optionConfig) {
        if (maxParallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "maxParallelism")
                    .param(ARG_DETAIL, "must be at least 1");
        }
        this.dbPath = dbPath;
        this.maxParallelism = maxParallelism;
        this.optionConfig = optionConfig != null ? optionConfig : new RocksDBOptionConfig();
    }

    public RocksDBStateBackend(String dbPath, int maxParallelism) {
        this(dbPath, maxParallelism, null);
    }

    /**
     * Convenience constructor. R5 KR-3c: defaults to the platform key-group
     * bound ({@link KeyGroup#DEFAULT_MAX_PARALLELISM}) — the previous M=1
     * default made any parallelism&gt;1 keyBy illegal with a first-record
     * failure far from the misconfiguration. Keyed jobs may still pass an
     * explicit M.
     */
    public RocksDBStateBackend(String dbPath) {
        // R5 KR-3c: default to the platform-wide key-group bound so keyBy
        // jobs constructed with this convenience constructor get rescale
        // capacity instead of a first-record failure (M=1 makes any
        // parallelism > 1 keyBy illegal). Existing non-keyed users are
        // unaffected; keyed jobs should still pass M explicitly.
        this(dbPath, io.nop.stream.core.common.state.shard.KeyGroup.DEFAULT_MAX_PARALLELISM, null);
    }

    @Override
    public String getName() {
        return "RocksDBStateBackend";
    }

    public String getDbPath() {
        return dbPath;
    }

    /**
     * Stage 34: job-global key-group upper bound for this backend.
     */
    public int getMaxParallelism() {
        return maxParallelism;
    }

    /**
     * Deprecated alias for {@link #getMaxParallelism()}; retained for source
     * compatibility. Returns the same value as {@code maxParallelism}.
     *
     * @deprecated use {@link #getMaxParallelism()}
     */
    @Deprecated
    public int getShardCount() {
        return maxParallelism;
    }

    public RocksDBOptionConfig getOptionConfig() {
        return optionConfig;
    }

    @Override
    public <K> IKeyedStateBackend<K> createKeyedStateBackend(Class<K> keyType) {
        return new RocksDBKeyedStateBackend<>(dbPath, keyType, maxParallelism, optionConfig);
    }

    @Override
    public IOperatorStateBackend createOperatorStateBackend() {
        return new MemoryOperatorStateBackend();
    }
}
