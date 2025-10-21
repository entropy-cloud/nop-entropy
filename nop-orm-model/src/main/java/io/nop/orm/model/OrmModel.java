/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.core.model.graph.TopoEntry;
import io.nop.orm.model._gen._OrmModel;
import io.nop.orm.model.init.OrmModelInitializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_ENTITY_NAME;

public class OrmModel extends _OrmModel implements IOrmModel, INeedInit {
    private Map<String, TopoEntry<IEntityModel>> topoEntryMap;
    private Map<String, IEntityModel> entityModelByTableMap;
    private Map<String, OrmToManyReferenceModel> collectionMap;
    private Map<String, OrmEntityModel> entityModelMap;

    private Map<String, OrmEntityModel> snakeCaseNameMap;
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
    public List<IEntityModel> getEntityModelInTopoOrder(Collection<String> entityNames) {
        if (entityNames.isEmpty())
            return Collections.emptyList();
        if (entityNames.size() == 1)
            return List.of(requireEntityModel(entityNames.iterator().next()));

        TreeMap<TopoEntry<IEntityModel>, IEntityModel> map = new TreeMap<>();
        entityNames.forEach(name -> {
            TopoEntry<IEntityModel> entry = topoEntryMap.get(name);
            if (entry == null)
                throw new NopException(ERR_ORM_UNKNOWN_ENTITY_NAME).param(ARG_ENTITY_NAME, name);
            map.put(entry, entry.getValue());
        });
        return new ArrayList<>(map.values());
    }

    @Override
    public List<IEntityModel> sortEntityModelInTopoOrder(Collection<IEntityModel> entityModels) {
        if (entityModels.isEmpty())
            return Collections.emptyList();
        if (entityModels.size() == 1)
            return List.of(entityModels.iterator().next());

        TreeMap<TopoEntry<IEntityModel>, IEntityModel> map = new TreeMap<>();
        entityModels.forEach(entityModel -> {
            String name = entityModel.getName();
            TopoEntry<IEntityModel> entry = topoEntryMap.get(name);
            if (entry == null)
                throw new NopException(ERR_ORM_UNKNOWN_ENTITY_NAME).param(ARG_ENTITY_NAME, name);
            map.put(entry, entry.getValue());
        });
        return new ArrayList<>(map.values());
    }

    @Override
    public List<? extends IEntityModel> getEntityModels() {
        if (topoEntryMap == null)
            return getEntities();
        return topoEntryMap.values().stream().map(TopoEntry::getValue).collect(Collectors.toList());
    }

    @Override
    public Collection<? extends IEntityModel> getEntityModelsInTopoOrder() {
        return getEntityModels();
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
    public IEntityModel getEntityModelBySnakeCaseName(String name) {
        return snakeCaseNameMap.get(name);
    }

    @Override
    public IEntityRelationModel getCollectionModel(String collectionName) {
        return collectionMap.get(collectionName);
    }

    @Override
    public void init() {
        OrmModelInitializer initializer = new OrmModelInitializer(this);
        this.topoEntryMap = initializer.getEntryMap();
        this.entityModelMap = initializer.getEntityMap();
        this.entityModelByTableMap = initializer.getEntityModelByTableMap();
        this.collectionMap = initializer.getCollectionMap();
        this.anyEntityUseTenant = this.entityModelMap.values().stream().anyMatch(IEntityModel::isUseTenant);
        this.snakeCaseNameMap = initializer.getUnderscoreNameMap();
    }
}
