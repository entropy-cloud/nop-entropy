/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.orm.IOrmColumnBinderEnhancer;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SqlExprMetaCache {
    private final IOrmColumnBinderEnhancer binderEnhancer;
    private final IDialectProvider dialectProvider;
    private final IOrmModel ormModel;

    private final Map<String, EntityTableMeta> entityMetas = new ConcurrentHashMap<>();

    public SqlExprMetaCache(IOrmColumnBinderEnhancer binderEnhancer, IDialectProvider dialectProvider,
                            IOrmModel ormModel) {
        this.binderEnhancer = binderEnhancer;
        this.dialectProvider = dialectProvider;
        this.ormModel = ormModel;
    }

    public EntityTableMeta getEntityTableMeta(String entityName) {
        EntityTableMeta meta = entityMetas.get(entityName);
        if (meta != null)
            return meta;
        if (ormModel.getEntityModel(entityName) == null)
            return null;
        return entityMetas.computeIfAbsent(entityName, this::createEntityTableMeta);
    }

    EntityTableMeta createEntityTableMeta(String entityName) {
        IEntityModel entityModel = ormModel.requireEntityModel(entityName);
        IDialect dialect = dialectProvider.getDialectForQuerySpace(entityModel.getQuerySpace());
        return new EntityTableMeta(entityModel, binderEnhancer, dialect);
    }
}
