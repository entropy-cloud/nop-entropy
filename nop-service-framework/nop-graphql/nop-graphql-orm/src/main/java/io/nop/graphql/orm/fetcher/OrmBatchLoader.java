/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm.fetcher;

import io.nop.api.core.util.FutureHelper;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.orm.IOrmTemplate;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class OrmBatchLoader implements BatchLoader<Supplier<Object>, Object> {
    private final IOrmTemplate ormTemplate;

    public OrmBatchLoader(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Override
    public CompletionStage<List<Object>> load(List<Supplier<Object>> keys) {
        ormTemplate.requireSession().getBatchLoadQueue().flush();
        List<Object> ret = new ArrayList<>(keys.size());
        for (Supplier<Object> supplier : keys) {
            ret.add(supplier.get());
        }
        return FutureHelper.success(ret);
    }

    public static DataLoader<Supplier<Object>, Object> makeDataLoader(IOrmTemplate ormTemplate,
                                                                      IGraphQLExecutionContext context) {
        DataLoader<Supplier<Object>, Object> loader = context.getDataLoader(OrmBatchLoader.class.getName());
        if (loader == null) {
            DataLoaderOptions options = new DataLoaderOptions();
            options.setCachingEnabled(false);
            loader = DataLoaderFactory.newDataLoader(new OrmBatchLoader(ormTemplate), options);
            context.registerDataLoader(OrmBatchLoader.class.getName(), loader);
        }
        return loader;
    }
}