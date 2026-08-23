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

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 第一个参数对应于source对象的列表
 */
public class BeanMethodBatchFetcher implements IDataFetcher {
    private final String loaderName;
    private final BiFunction<Object[], IGraphQLExecutionContext, Object> realFetcher;
    private final List<Function<IDataFetchingEnvironment, Object>> argBuilders;
    private final int sourceIndex;

    public BeanMethodBatchFetcher(String loaderName, Object bean, IFunctionModel function,
                                  List<Function<IDataFetchingEnvironment, Object>> argBuilders, int sourceIndex) {
        this(loaderName, (args, context) -> function.invoke(bean, args, context.getEvalScope()), argBuilders, sourceIndex);
    }

    public BeanMethodBatchFetcher(String loaderName, BiFunction<Object[], IGraphQLExecutionContext, Object> realFetcher,
                                  List<Function<IDataFetchingEnvironment, Object>> argBuilders, int sourceIndex) {
        this.loaderName = loaderName;
        this.realFetcher = realFetcher;
        this.argBuilders = argBuilders;
        this.sourceIndex = Guard.checkPositionIndex(sourceIndex, argBuilders.size(), "sourceIndex");
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IGraphQLExecutionContext context = env.getGraphQLExecutionContext();

        // 非source参数参与loader注册名：同一请求内同名loader字段携带不同参数时必须各自成批装载，
        // 否则第二个分支的参数被静默丢弃，整批按首次调用捕获的参数计算（错误数据）
        Object[] args = new Object[argBuilders.size()];
        for (int i = 0, n = args.length; i < n; i++) {
            if (i != sourceIndex)
                args[i] = argBuilders.get(i).apply(env);
        }

        String key = buildLoaderKey(args);
        DataLoader<Object, Object> loader;
        // check-then-act与registerDataLoader之间无原子性：并发分支同时首次命中同一loader时，
        // 后注册方会触发ERR_GRAPHQL_DUPLICATED_LOADER使整个请求失败。此处与dispatchAll同锁串行化，
        // 先注册者胜出，后到方直接复用已注册实例
        synchronized (context) {
            loader = context.getDataLoader(key);
            if (loader == null) {
                BatchLoader<Object, Object> batchLoader = keys -> {
                    args[sourceIndex] = keys;
                    return FutureHelper.futureCall(() -> realFetcher.apply(args, context));
                };
                loader = DataLoaderFactory.newDataLoader(batchLoader);
                context.registerDataLoader(key, loader);
            }
        }
        return loader.load(env.getSource());
    }

    /**
     * 无额外参数时保持原loaderName（兼容既有注册名语义）；有额外参数时以参数内容哈希区分，
     * 参数不同则各自成批。hashCode退化为身份哈希的对象只会导致批次变细，不影响正确性。
     */
    private String buildLoaderKey(Object[] args) {
        if (args.length == 1)
            return loaderName;

        Object[] keyArgs = new Object[args.length - 1];
        for (int i = 0, j = 0; i < args.length; i++) {
            if (i != sourceIndex)
                keyArgs[j++] = args[i];
        }
        return loaderName + "@" + Arrays.deepHashCode(keyArgs);
    }
}