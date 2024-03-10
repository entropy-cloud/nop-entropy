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
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import org.dataloader.DataLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

public class OrmDependsPropFetcher implements IDataFetcher {
    private final IOrmTemplate ormTemplate;
    private final Collection<String> dependsOn;
    private final String propName;

    public OrmDependsPropFetcher(IOrmTemplate ormTemplate, Collection<String> dependsOn, String propName) {
        this.ormTemplate = ormTemplate;
        this.dependsOn = dependsOn;
        this.propName = propName;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IOrmEntity entity = (IOrmEntity) env.getSource();
        IOrmSession session = ormTemplate.requireSession();
        if (dependsOn != null && !dependsOn.isEmpty())
            session.getBatchLoadQueue().enqueueManyProps(Collections.singletonList(entity), dependsOn);
        session.getBatchLoadQueue().enqueueProp(entity, propName);

        DataLoader<Supplier<Object>, Object> loader = OrmBatchLoader.makeDataLoader(ormTemplate,
                env.getExecutionContext());
        return loader.load(() -> BeanTool.getComplexProperty(entity, propName));
    }
}
