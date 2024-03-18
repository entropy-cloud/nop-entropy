/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl.resolvers;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.XNode;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.impl.IBeanPropValueResolver;

public class ExpressionValueResolver implements IBeanPropValueResolver {
    private final IEvalAction action;
    private final String expr;

    public ExpressionValueResolver(IEvalAction action, String expr) {
        this.action = action;
        this.expr = expr;
    }

    @Override
    public String toConfigString() {
        return "@expr:" + expr;
    }

    @Override
    public XNode toConfigNode() {
        return null;
    }

    @Override
    public Object resolveValue(IBeanContainerImplementor container, IBeanScope scope) {
        return action.invoke(scope.getEvalScope());
    }
}