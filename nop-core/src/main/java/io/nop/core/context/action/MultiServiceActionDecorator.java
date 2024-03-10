/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.context.action;

import io.nop.api.core.util.OrderedComparator;

import java.util.Collections;
import java.util.List;

public class MultiServiceActionDecorator implements IServiceActionDecorator {
    private final List<IServiceActionDecorator> decorators;

    public MultiServiceActionDecorator(List<IServiceActionDecorator> decorators) {
        this.decorators = decorators;
        Collections.sort(decorators, OrderedComparator.instance());
    }

    @Override
    public IServiceAction decorate(IServiceAction action) {
        for (IServiceActionDecorator decorator : decorators) {
            action = decorator.decorate(action);
        }
        return action;
    }
}
