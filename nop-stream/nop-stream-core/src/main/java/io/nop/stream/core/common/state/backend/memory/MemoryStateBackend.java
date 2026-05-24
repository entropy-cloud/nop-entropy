/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;

import java.io.Serializable;

/**
 * 内存状态后端实现，用于测试和简单场景。
 * 
 * <p>所有状态存储在 JVM 内存中，重启后状态丢失。
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

    private final int shardCount;

    /**
     * 默认构造函数（无分片）
     */
    public MemoryStateBackend() {
        this(1);
    }

    /**
     * 构造函数
     *
     * @param shardCount 分片数量，必须 >= 1。当 > 1 时，key 会被路由到不同的分片。
     */
    public MemoryStateBackend(int shardCount) {
        if (shardCount < 1) {
            throw new IllegalArgumentException("shardCount must be at least 1");
        }
        this.shardCount = shardCount;
    }

    @Override
    public String getName() {
        return "MemoryStateBackend";
    }

    @Override
    public <K> IKeyedStateBackend<K> createKeyedStateBackend(Class<K> keyType) {
        return new MemoryKeyedStateBackend<>(keyType, shardCount);
    }
}
