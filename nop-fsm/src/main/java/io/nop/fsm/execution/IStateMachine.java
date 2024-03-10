/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.fsm.execution;

import io.nop.core.context.IEvalContext;
import io.nop.fsm.model.StateModel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 有限状态自动机模型
 */
public interface IStateMachine {
    /**
     * 当前状态是stateValue的时候触发事件 event，执行迁移逻辑，并迁移到目标状态
     *
     * @param stateValue    当前状态
     * @param event         触发事件
     * @param context       执行上下文
     * @param onStateChange 当状态发生变化的时候，通过这个回调函数来通知迁移到的目标状态。
     */
    void transit(Object stateValue, String event, IEvalContext context, BiConsumer<StateModel, Object> onStateChange);

    void initState(Object bean);

    default void triggerStateChange(Object bean, String event, IEvalContext context) {
        triggerStateChange(bean, event, context, null);
    }

    void triggerStateChange(Object bean, String event, IEvalContext context, Consumer<StateModel> action);
}
