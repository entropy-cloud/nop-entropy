/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.Serializable;

import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IOperatorStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.shard.KeyGroup;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * 内存状态后端实现，用于测试和简单场景。
 *
 * <p>所有状态存储在 JVM 内存中，重启后状态丢失。
 *
 * <p>Stage 34：{@code shardCount} 参数语义已迁移为 job-global {@code maxParallelism}
 * （key-group 上界，默认 {@link KeyGroup#DEFAULT_MAX_PARALLELISM}）。{@code maxParallelism}
 * 作为后端实例属性在整个作业生命周期内不变，使 key&#8594;group 映射在并行度变化时保持稳定。
 *
 * <p>使用示例：
 * <pre>{@code
 * IStateBackend stateBackend = new MemoryStateBackend();
 * IKeyedStateBackend<String> keyedBackend =
 *     stateBackend.createKeyedStateBackend(String.class);
 *
 * keyedBackend.setCurrentKey("user123");
 * ValueState<Long> countState = keyedBackend.getState(
 *     new ValueStateDescriptor<>("count", Long.class));
 *
 * countState.update(countState.value() + 1);
 * }</pre>
 */
public class MemoryStateBackend implements IStateBackend, Serializable {

    private static final long serialVersionUID = 1L;

    private final int maxParallelism;

    /**
     * 默认构造函数。使用 {@link KeyGroup#DEFAULT_MAX_PARALLELISM}（128）作为
     * job-global {@code maxParallelism}，使 keyed 作业具备 rescale 容量。
     */
    public MemoryStateBackend() {
        this(KeyGroup.DEFAULT_MAX_PARALLELISM);
    }

    /**
     * 构造函数。
     *
     * @param maxParallelism job-global key-group 上界，必须 &ge; 1。当 &gt; 1 时，key 按
     *                       {@code (stableHash(key) & 0x7FFFFFFF) % maxParallelism} 路由到
     *                       不同的 key-group。等价于历史的 {@code shardCount} 参数。
     */
    public MemoryStateBackend(int maxParallelism) {
        if (maxParallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "maxParallelism").param(ARG_DETAIL, "must be at least 1");
        }
        this.maxParallelism = maxParallelism;
    }

    @Override
    public String getName() {
        return "MemoryStateBackend";
    }

    /**
     * Stage 34: job-global key-group upper bound for this backend.
     */
    public int getMaxParallelism() {
        return maxParallelism;
    }

    /**
     * Deprecated alias for {@link #getMaxParallelism()}; retained so that
     * snapshot info-maps and existing callers keep compiling. Returns the
     * same value as {@code maxParallelism}.
     *
     * @deprecated use {@link #getMaxParallelism()}
     */
    @Deprecated
    public int getShardCount() {
        return maxParallelism;
    }

    @Override
    public <K> IKeyedStateBackend<K> createKeyedStateBackend(Class<K> keyType) {
        return new MemoryKeyedStateBackend<>(keyType, maxParallelism);
    }

    @Override
    public IOperatorStateBackend createOperatorStateBackend() {
        return new MemoryOperatorStateBackend();
    }
}
