/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

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
     * 获取或创建 InternalAppendingState。
     * 
     * <p>用于 Window 状态存储，支持按 namespace（如 Window）分区。
     *
     * @param descriptor 状态描述符
     * @param <IN> 输入元素类型
     * @param <ACC> 累加器类型
     * @return InternalAppendingState 实例
     */
    <N, IN, ACC> InternalAppendingState<K, N, IN, ACC, ACC> getInternalAppendingState(
            ReducingStateDescriptor<IN> descriptor);

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
