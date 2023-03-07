/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.dao;

import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IEntityModel;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OrmDaoProvider implements IDaoProvider {
    private final IOrmTemplate ormTemplate;
    private final Map<String, IEntityDao<?>> daoMap = new ConcurrentHashMap<>();
    private final Map<String, IEntityDao<?>> daoByTableMap = new ConcurrentHashMap<>();

    @Inject
    public OrmDaoProvider(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Override
    public Set<String> getEntityNames() {
        return ormTemplate.getOrmModel().getEntityNames();
    }

    @Override
    public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
        IEntityDao<?> dao = daoMap.get(entityName);
        if (dao == null) {
            dao = daoMap.computeIfAbsent(entityName, name -> new OrmEntityDao<>(this, ormTemplate, entityName));
        }
        return (IEntityDao<T>) dao;
    }

    @Override
    public <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName) {
        IEntityDao<?> dao = daoByTableMap.get(tableName);
        if (dao == null) {
            IEntityModel entityModel = ormTemplate.getOrmModel().requireEntityModelByTableName(tableName);
            dao = dao(entityModel.getName());
            daoByTableMap.put(tableName, dao);
        }
        return (IEntityDao<T>) dao;
    }
}
