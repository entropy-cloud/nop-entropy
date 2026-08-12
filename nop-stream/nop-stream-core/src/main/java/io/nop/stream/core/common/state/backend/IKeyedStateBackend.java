/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.shard.KeyGroup;

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

    /**
     * Snapshot all keyed state to a StateSnapshot for checkpoint persistence.
     *
     * @return snapshot object, or null if no state
     */
    StateSnapshot snapshotState() throws Exception;

    /**
     * Restore keyed state from a previously taken snapshot.
     *
     * @param snapshot the state snapshot
     */
    void restoreState(StateSnapshot snapshot) throws Exception;

    /**
     * Stage 35: job-global key-group upper bound used by this backend. Constant
     * for the job lifetime, which keeps the key&#8594;group mapping stable across
     * parallelism-only rescales. Defaults to
     * {@link KeyGroup#DEFAULT_MAX_PARALLELISM} for backends that do not model
     * key groups.
     */
    default int getMaxParallelism() {
        return KeyGroup.DEFAULT_MAX_PARALLELISM;
    }

    /**
     * P1-01: registers the LIVE {@code AggregateFunction} that the restore path
     * must prefer when re-materializing aggregating state under
     * {@code stateName}. The snapshot only records the function's class name;
     * reflection-based recreation fails for capturing anonymous classes and
     * lambdas (the window reduce/aggregate descriptor path). Operators that own
     * an {@code AggregatingStateDescriptor} register its function here BEFORE
     * restore runs (e.g. {@code WindowOperator} in {@code open()} prior to
     * {@code applyPendingRestoreState()}).
     *
     * <p>Registration is a hint, not a mutation of the snapshot: a provider
     * absent for a given state name simply falls back to the reflection path.
     *
     * @param stateName the state name (descriptor name) being restored
     * @param function  the live aggregate function instance to use on restore
     */
    default void registerRestoreAggregateFunction(String stateName,
                                                  io.nop.stream.core.common.functions.AggregateFunction<?, ?, ?> function) {
    }

    /**
     * P1-01: the live aggregate function registered for {@code stateName}, or
     * {@code null} when no provider was registered (fall back to the snapshot's
     * class-name reflection path).
     */
    default io.nop.stream.core.common.functions.AggregateFunction<?, ?, ?> getRestoreAggregateFunction(String stateName) {
        return null;
    }
}
