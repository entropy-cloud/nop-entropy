/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.api;

import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.lang.eval.IEvalScope;

public interface IBeanScopeContext {
    IBeanScope newBeanScope(IBeanContainer container, String scopeName, IEvalScope evalScope);

    /**
     * 将beanScope绑定到当前上下文
     *
     * @param scope
     */
    void bind(IBeanScope scope);

    void unbind(IBeanScope scope);

    /**
     * 关闭scope，停止scope中的所有bean
     *
     * @param scopeName scope名称
     */
    default void closeScope(IBeanScope scope) {
        unbind(scope);
        scope.close();
    }

    IBeanScope getScope(IBeanContainerImplementor container, String scopeName);

    void onContainerStop(IBeanContainerImplementor container);
}