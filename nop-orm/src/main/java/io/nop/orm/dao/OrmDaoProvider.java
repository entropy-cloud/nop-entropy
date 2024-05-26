/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.util.Guard;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IEntityModel;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OrmDaoProvider implements IDaoProvider {
    private final IOrmTemplate ormTemplate;
    private final Map<String, IEntityDao<?>> daoMap = new ConcurrentHashMap<>();
    private final Map<String, IEntityDao<?>> daoByTableMap = new ConcurrentHashMap<>();

    @Inject
    public OrmDaoProvider(IOrmTemplate ormTemplate) {
        this.ormTemplate = Guard.notNull(ormTemplate,"ormTemplate");
    }

    public void clearCache() {
        daoMap.clear();
        daoByTableMap.clear();
    }

    @Override
    public Set<String> getEntityNames() {
        return ormTemplate.getOrmModel().getEntityNames();
    }

    public void register() {
        DaoProvider.registerInstance(this);
    }

    @Override
    public <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
        String fullName = normalizeEntityName(entityName);
        IEntityDao<?> dao = daoMap.get(fullName);
        if (dao == null) {
            dao = daoMap.computeIfAbsent(fullName, name -> new OrmEntityDao<>(this, ormTemplate, fullName));
        }
        return (IEntityDao<T>) dao;
    }

    @Override
    public boolean hasDao(String entityName) {
        String fullName = normalizeEntityName(entityName);
        return ormTemplate.getOrmModel().getEntityModel(entityName) != null;
    }

    @Override
    public String normalizeEntityName(String entityName) {
        return ormTemplate.getFullEntityName(entityName);
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
