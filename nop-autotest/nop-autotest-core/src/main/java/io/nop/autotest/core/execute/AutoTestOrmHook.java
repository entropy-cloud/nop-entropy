/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.core.execute;

import io.nop.orm.IOrmDaoListener;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.model.IEntityModel;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AutoTestOrmHook implements IOrmDaoListener, IOrmInterceptor {
    private final Map<IEntityModel, Map<String, EntityRow>> dataMap = new ConcurrentHashMap<>();
    private final Set<String> loadedTables = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Map<IEntityModel, Map<String, EntityRow>> getDataMap() {
        return dataMap;
    }

    @Override
    public void onRead(IEntityModel entityModel) {
        loadedTables.add(entityModel.getName());
        makeEntityData(entityModel);
    }

    public Set<String> getLoadedTables() {
        return loadedTables;
    }

    @Override
    public void onUpdate(IEntityModel entityModel) {
        makeEntityData(entityModel);
    }

    @Override
    public void onDelete(IEntityModel entityModel) {
        makeEntityData(entityModel);
    }

    @Override
    public void onSave(IEntityModel entityModel) {
        makeEntityData(entityModel);
    }

    private Map<String, EntityRow> makeEntityData(IEntityModel entityModel) {
        return dataMap.computeIfAbsent(entityModel, k -> new ConcurrentHashMap<>());
    }

    private EntityRow makeEntityRow(IOrmEntity entity) {
        String id = entity.orm_idString();
        return makeEntityData(entity.orm_entityModel()).computeIfAbsent(id, EntityRow::new);
    }

    @Override
    public void postSave(IOrmEntity entity) {
        EntityRow row = makeEntityRow(entity);
        row.onSave(entity, entity.orm_entityModel());
    }

    @Override
    public void postUpdate(IOrmEntity entity) {
        EntityRow row = makeEntityRow(entity);
        row.onUpdate(entity, entity.orm_entityModel());
    }

    @Override
    public void postDelete(IOrmEntity entity) {
        EntityRow row = makeEntityRow(entity);
        row.onDelete(entity, entity.orm_entityModel());
    }

    @Override
    public void postLoad(IOrmEntity entity) {
        EntityRow row = makeEntityRow(entity);
        row.onLoad(entity, entity.orm_entityModel());
    }
}