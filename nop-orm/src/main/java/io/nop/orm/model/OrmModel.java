/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.api.core.util.INeedInit;
import io.nop.core.model.graph.TopoEntry;
import io.nop.orm.model._gen._OrmModel;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OrmModel extends _OrmModel implements IOrmModel, INeedInit {
    private Map<String, TopoEntry<IEntityModel>> topoEntryMap;
    private Map<String, IEntityModel> entityModelByTableMap;
    private Map<TopoEntry<IEntityModel>, IEntityModel> topoOrderMap;
    private Map<String, OrmToManyReferenceModel> collectionMap;
    private Map<String, OrmEntityModel> entityModelMap;
    private boolean anyEntityUseTenant;

    /**
     * 由多个app.orm.xml合并产生的模型，此时不会检查domain的有效性。
     */
    private boolean merged;

    public OrmModel() {

    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    @Override
    public boolean isAnyEntityUseTenant() {
        return anyEntityUseTenant;
    }

    @Override
    public TopoEntry<IEntityModel> getTopoEntry(String entityName) {
        return topoEntryMap.get(entityName);
    }

    @Override
    public List<IEntityModel> getEntityModels() {
        return topoEntryMap.values().stream().map(TopoEntry::getValue).collect(Collectors.toList());
    }

    @Override
    public Collection<IEntityModel> getEntityModelsInTopoOrder() {
        return topoOrderMap.values();
    }

    @Override
    public Set<String> getEntityNames() {
        return topoEntryMap.keySet();
    }

    @Override
    public IEntityModel getEntityModelByTableName(String tableName) {
        return entityModelByTableMap.get(tableName);
    }

    @Override
    public IEntityModel getEntityModel(String entityName) {
        return entityModelMap.get(entityName);
    }

    @Override
    public IEntityRelationModel getCollectionModel(String collectionName) {
        return collectionMap.get(collectionName);
    }

    @Override
    public void init() {
        OrmModelInitializer initializer = new OrmModelInitializer(this);
        this.topoOrderMap = initializer.topoMap;
        this.topoEntryMap = initializer.entryMap;
        this.entityModelMap = initializer.entityMap;
        this.entityModelByTableMap = initializer.entityModelByTableMap;
        this.collectionMap = initializer.collectionMap;
        this.anyEntityUseTenant = this.entityModelMap.values().stream().anyMatch(IEntityModel::isUseTenant);
    }
}
