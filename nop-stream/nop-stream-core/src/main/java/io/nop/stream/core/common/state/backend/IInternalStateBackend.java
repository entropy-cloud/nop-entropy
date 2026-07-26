/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.ReducingStateDescriptor;

/**
 * 内部状态后端接口，提供对带命名空间的状态访问。
 * 
 * <p>相比 IKeyedStateBackend，此接口支持泛型的命名空间类型，
 * 用于 WindowOperator 等需要按 Window 分区状态的场景。
 *
 * @param <K> key 的类型
 */
public interface IInternalStateBackend<K> extends IKeyedStateBackend<K> {

    /**
     * 获取或创建 InternalAppendingState（reducing 重载）。
     *
     * <p>用于 Window 状态存储，支持按 namespace（如 Window）分区。
     * 对于 reducing state，输入类型 IN 同时也是累加器类型和输出类型
     * （reduce 函数语义为 (IN, IN) → IN），因此返回
     * {@code InternalAppendingState<K, N, IN, IN, IN>}。
     *
     * @param descriptor 状态描述符
     * @param <N> namespace 类型（如 Window）
     * @param <IN> 输入元素类型（同时为累加器和输出类型）
     * @return InternalAppendingState 实例
     */
    <N, IN> InternalAppendingState<K, N, IN, IN, IN> getInternalAppendingState(
            ReducingStateDescriptor<IN> descriptor);

    /**
     * 获取或创建 InternalAppendingState（aggregating 重载）。
     *
     * <p>用于 Window 状态存储，支持按 namespace（如 Window）分区。
     * IN/ACC/OUT 三个类型参数由 {@link AggregatingStateDescriptor} 携带的
     * {@link io.nop.stream.core.common.functions.AggregateFunction} 确定，
     * 方法签名仅声明 namespace 自由类型参数 N。
     *
     * @param descriptor 状态描述符
     * @param <N> namespace 类型（如 Window）
     * @param <IN> 输入元素类型
     * @param <ACC> 累加器类型
     * @param <OUT> 输出类型
     * @return InternalAppendingState 实例
     */
    <N, IN, ACC, OUT> InternalAppendingState<K, N, IN, ACC, OUT> getInternalAppendingState(
            AggregatingStateDescriptor<IN, ACC, OUT> descriptor);

    /**
     * 获取或创建 InternalListState。
     * 
     * <p>用于合并窗口的元数据存储。
     *
     * @param descriptor 状态描述符
     * @param <T> 列表元素类型
     * @return InternalListState 实例
     */
    <N, T> InternalListState<K, N, T> getInternalListState(ListStateDescriptor<T> descriptor);
}
