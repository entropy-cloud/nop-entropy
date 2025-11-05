/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.graphql.core.reflection.IServiceActionArgBuilder;

import java.util.List;

import static io.nop.graphql.core.fetcher.FetcherHelper.processResponse;

public class BeanMethodAction implements IServiceAction {
    private final Object bean;
    private final IFunctionModel function;
    private final List<IServiceActionArgBuilder> argBuilders;
    private IClassModel sourceClassModel;

    public BeanMethodAction(Object bean, IFunctionModel function, List<IServiceActionArgBuilder> argBuilders) {
        this.bean = bean;
        this.function = function;
        this.argBuilders = argBuilders;
    }

    public IClassModel getSourceClassModel() {
        return sourceClassModel;
    }

    public void setSourceClassModel(IClassModel sourceClassModel) {
        this.sourceClassModel = sourceClassModel;
    }

    public IFunctionModel getFunctionModel() {
        return function;
    }

    public String toString() {
        return function.toString();
    }

    @Override
    public Object invoke(Object request, FieldSelectionBean selection, IServiceContext context) {
        Object[] args = new Object[argBuilders.size()];
        for (int i = 0, n = args.length; i < n; i++) {
            args[i] = argBuilders.get(i).build(request, selection, context);
        }
        return processResponse(function.invoke(bean, args, context.getEvalScope()), function, context);
    }
}
