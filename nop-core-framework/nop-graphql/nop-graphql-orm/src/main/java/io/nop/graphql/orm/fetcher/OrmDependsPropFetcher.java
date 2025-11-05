/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm.fetcher;

import io.nop.core.reflect.bean.BeanTool;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.orm.IOrmBatchLoadQueue;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import org.dataloader.DataLoader;

import java.util.Collection;
import java.util.function.Supplier;

public class OrmDependsPropFetcher implements IDataFetcher {
    private final IOrmTemplate ormTemplate;
    private final Collection<String> dependsOn;
    private final String propName;
    private final IDataFetcher fetcher;

    public OrmDependsPropFetcher(IOrmTemplate ormTemplate, Collection<String> dependsOn,
                                 String propName,
                                 IDataFetcher fetcher) {
        this.ormTemplate = ormTemplate;
        this.dependsOn = dependsOn;
        this.propName = propName;
        this.fetcher = fetcher;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IOrmEntity entity = (IOrmEntity) env.getSource();
        IOrmSession session = ormTemplate.requireSession();

        IOrmBatchLoadQueue loadQueue = session.getBatchLoadQueue();
        if (dependsOn != null && !dependsOn.isEmpty()) {
            for (String depend : dependsOn) {
                loadQueue.enqueueProp(entity, depend);
            }
        }

        if (propName != null)
            session.getBatchLoadQueue().enqueueProp(entity, propName);

        DataLoader<Supplier<Object>, Object> loader = OrmBatchLoader.makeDataLoader(ormTemplate,
                env.getGraphQLExecutionContext());

        return loader.load(() -> {
            if (fetcher != null)
                return fetcher.get(env);
            return BeanTool.getComplexProperty(entity, propName);
        });
    }
}
