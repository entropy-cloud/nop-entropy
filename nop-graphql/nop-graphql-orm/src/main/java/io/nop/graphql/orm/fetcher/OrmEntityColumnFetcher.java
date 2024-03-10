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

public class OrmEntityColumnFetcher implements IDataFetcher {
    private final IOrmTemplate ormTemplate;
    private final int propId;

    public OrmEntityColumnFetcher(IOrmTemplate ormTemplate, int propId) {
        this.ormTemplate = ormTemplate;
        this.propId = propId;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IOrmEntity entity = (IOrmEntity) env.getSource();
        if (entity.orm_propInited(propId))
            return entity.orm_propValue(propId);

        IOrmSession session = ormTemplate.requireSession();
        session.getBatchLoadQueue().enqueueProp(entity, entity.orm_propName(propId));

        DataLoader<Supplier<Object>, Object> loader = OrmBatchLoader.makeDataLoader(ormTemplate,
                env.getExecutionContext());
        return loader.load(() -> entity.orm_propValue(propId));
    }
}
