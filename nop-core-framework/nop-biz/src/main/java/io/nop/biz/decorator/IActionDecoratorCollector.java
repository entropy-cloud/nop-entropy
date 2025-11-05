/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.decorator;

import io.nop.biz.model.BizActionModel;
import io.nop.biz.model.BizLoaderModel;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.core.reflect.IFunctionModel;

import java.util.List;

public interface IActionDecoratorCollector {
    void collectDecorator(IFunctionModel funcModel, List<IServiceActionDecorator> decorators);

    void collectDecorator(BizActionModel actionModel, List<IServiceActionDecorator> decorators);

    default void collectDecorator(BizLoaderModel loaderModel, List<IServiceActionDecorator> decorators) {

    }
}
