/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.meta;

import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.orm.eql.binder.IOrmColumnBinderEnhancer;
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

    public EntityTableMeta getEntityTableMeta(String entityName, boolean allowUnderscoreName) {
        EntityTableMeta meta = entityMetas.get(entityName);
        if (meta != null)
            return meta;
        IEntityModel entityModel = getEntityModel(entityName, allowUnderscoreName);
        if (entityModel == null)
            return null;
        return entityMetas.computeIfAbsent(entityName, k -> createEntityTableMeta(entityModel));
    }

    IEntityModel getEntityModel(String entityName, boolean allowUnderscoreName) {
        IEntityModel entityModel = ormModel.getEntityModel(entityName);
        if (entityModel == null) {
            if (allowUnderscoreName)
                entityModel = ormModel.getEntityModelByUnderscoreName(entityName);
        }
        return entityModel;
    }

    EntityTableMeta createEntityTableMeta(IEntityModel entityModel) {
        IDialect dialect = dialectProvider.getDialectForQuerySpace(entityModel.getQuerySpace());
        return new EntityTableMeta(entityModel, binderEnhancer, dialect);
    }
}
