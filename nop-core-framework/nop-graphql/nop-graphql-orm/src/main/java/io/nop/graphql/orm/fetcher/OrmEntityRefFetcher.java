/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm.fetcher;

import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import org.dataloader.DataLoader;

import java.util.function.Supplier;

public class OrmEntityRefFetcher implements IDataFetcher {
    private final IOrmTemplate ormTemplate;
    private final String propName;

    public OrmEntityRefFetcher(IOrmTemplate ormTemplate, String propName) {
        this.ormTemplate = ormTemplate;
        this.propName = propName;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IOrmEntity entity = (IOrmEntity) env.getSource();
        IOrmEntity refEntity = entity.orm_refEntity(propName);
        if (refEntity == null || refEntity.orm_state().isMissing())
            return null;
        if (!refEntity.orm_proxy()) {
            return refEntity;
        }

        IOrmSession session = ormTemplate.requireSession();
        session.getBatchLoadQueue().enqueueEntity(refEntity, env.getSelectionBean());

        DataLoader<Supplier<Object>, Object> loader = OrmBatchLoader.makeDataLoader(ormTemplate,
                env.getGraphQLExecutionContext());
        return loader.load(() -> (refEntity == null || refEntity.orm_state().isMissing()) ? null : refEntity);
    }
}
