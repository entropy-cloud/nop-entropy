package io.nop.orm.model.lazy;

import io.nop.api.core.beans.DictBean;
import io.nop.core.model.graph.TopoEntry;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IOrmModel;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class LazyLoadOrmModel implements IOrmModel {
    private final IOrmModel baseModel;

    public LazyLoadOrmModel(IOrmModel baseModel) {
        this.baseModel = baseModel;
    }

    @Override
    public boolean isAnyEntityUseTenant() {
        return false;
    }

    @Override
    public TopoEntry<IEntityModel> getTopoEntry(String entityName) {
        return null;
    }

    @Override
    public Collection<IEntityModel> getEntityModelsInTopoOrder() {
        return List.of();
    }

    @Override
    public List<? extends IEntityModel> getEntityModels() {
        return List.of();
    }

    @Override
    public Set<String> getEntityNames() {
        return Set.of();
    }

    @Override
    public IEntityModel getEntityModel(String entityName) {
        return null;
    }

    @Override
    public IEntityModel getEntityModelByTableName(String tableName) {
        return null;
    }

    @Override
    public IEntityModel getEntityModelByUnderscoreName(String name) {
        return null;
    }

    @Override
    public IEntityRelationModel getCollectionModel(String collectionName) {
        return null;
    }

    @Override
    public List<DictBean> getDicts() {
        return baseModel.getDicts();
    }

    @Override
    public DictBean getDict(String name) {
        return baseModel.getDict(name);
    }

    @Override
    public Object prop_get(String propName) {
        return baseModel.prop_get(propName);
    }

    @Override
    public boolean prop_has(String propName) {
        return baseModel.prop_has(propName);
    }
}
