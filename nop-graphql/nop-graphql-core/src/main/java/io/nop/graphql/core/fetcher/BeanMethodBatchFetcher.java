/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.core.reflect.IFunctionModel;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.IGraphQLExecutionContext;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;

import java.util.List;
import java.util.function.Function;

/**
 * 第一个参数对应于source对象的列表
 */
public class BeanMethodBatchFetcher implements IDataFetcher {
    private final String loaderName;
    private final Object bean;
    private final IFunctionModel function;
    private final List<Function<IDataFetchingEnvironment, Object>> argBuilders;
    private final int sourceIndex;

    public BeanMethodBatchFetcher(String loaderName, Object bean, IFunctionModel function,
                                  List<Function<IDataFetchingEnvironment, Object>> argBuilders, int sourceIndex) {
        this.loaderName = loaderName;
        this.bean = bean;
        this.function = function;
        this.argBuilders = argBuilders;
        this.sourceIndex = Guard.checkPositionIndex(sourceIndex, argBuilders.size(), "sourceIndex");
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IGraphQLExecutionContext context = env.getExecutionContext();
        DataLoader<Object, Object> loader = context.getDataLoader(loaderName);
        if (loader == null) {
            Object[] args = new Object[argBuilders.size()];
            // 这里假定了除了source之外，其他的参数都相同
            for (int i = 0, n = args.length; i < n; i++) {
                if (i != sourceIndex)
                    args[i] = argBuilders.get(i).apply(env);
            }
            BatchLoader<Object, Object> batchLoader = keys -> {
                args[sourceIndex] = keys;
                return FutureHelper.futureCall(() -> function.invoke(bean, args, context.getEvalScope()));
            };
            loader = DataLoaderFactory.newDataLoader(batchLoader);
            context.registerDataLoader(loaderName, loader);
        }
        return loader.load(env.getSource());
    }
}