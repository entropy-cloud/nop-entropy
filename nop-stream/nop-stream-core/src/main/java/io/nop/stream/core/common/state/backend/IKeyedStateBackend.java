/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import io.nop.stream.core.common.state.KeyedStateStore;

/**
 * Keyed 状态后端接口，管理按 key 分区的状态。
 * 
 * <p>简化版本：相比 Flink 的 KeyedStateBackend，去除了 key-group 分区和分布式相关的功能。
 * 
 * <p>使用方式：
 * <pre>{@code
 * // 1. 设置当前 key
 * keyedBackend.setCurrentKey("user123");
 * 
 * // 2. 设置当前 namespace（可选，用于 Window 等场景）
 * keyedBackend.setCurrentNamespace("window-1h");
 * 
 * // 3. 获取状态
 * ValueState<Long> state = keyedBackend.getState(
 *     new ValueStateDescriptor<>("count", Long.class));
 * 
 * // 4. 操作状态（自动针对当前 key）
 * Long count = state.value();
 * state.update(count + 1);
 * }</pre>
 *
 * @param <K> key 的类型
 */
public interface IKeyedStateBackend<K> extends KeyedStateStore, AutoCloseable {

    /**
     * 设置当前处理的 key。
     * 所有后续的状态操作都针对这个 key。
     *
     * @param key 当前 key
     */
    void setCurrentKey(K key);

    /**
     * 获取当前 key
     *
     * @return 当前 key
     */
    K getCurrentKey();

    /**
     * 设置当前 namespace。
     * 用于区分同一 key 下的不同状态（如不同的 Window）。
     *
     * <p>默认 namespace 为 {@link #DEFAULT_NAMESPACE}
     *
     * @param namespace namespace 名称
     */
    void setCurrentNamespace(String namespace);

    /**
     * 获取当前 namespace
     *
     * @return 当前 namespace
     */
    String getCurrentNamespace();

    /**
     * 关闭状态后端，释放资源
     */
    @Override
    void close();

    /**
     * 默认 namespace
     */
    String DEFAULT_NAMESPACE = "_default_";
}
