package io.nop.orm.model.lazy;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.graph.TopoEntry;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.init.OrmModelTopEntryBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_OTHER_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_OTHER_LOC;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_DUPLICATE_ENTITY_SHORT_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_ENTITY_NAME;

public class LazyLoadOrmModel implements IOrmModel {
    private final IOrmModel baseModel;
    private final IDynamicEntityModelProvider entityModelLoader;

    private final Map<String, TopoEntry<IEntityModel>> topoEntryMap = new ConcurrentHashMap<>();
    private final Map<String, IEntityModel> entityModelByTableMap = new ConcurrentHashMap<>();
    private final Map<String, IEntityModel> entityModelMap = new ConcurrentHashMap<>();

    private final Map<String, IEntityModel> snakeCaseNameMap = new ConcurrentHashMap<>();
    private boolean anyEntityUseTenant;

    private boolean topoEntryInited = true;

    public LazyLoadOrmModel(IOrmModel baseModel, IDynamicEntityModelProvider entityModelLoader) {
        this.baseModel = baseModel;
        this.anyEntityUseTenant = baseModel != null && baseModel.isAnyEntityUseTenant();
        this.entityModelLoader = entityModelLoader;
    }

    @Override
    public boolean isAnyEntityUseTenant() {
        return anyEntityUseTenant;
    }

    protected void checkTopoEntryReady() {
        if (this.topoEntryInited)
            return;

        synchronized (this.entityModelMap) {
            if (topoEntryInited)
                return;


            List<IEntityModel> entityModels = new ArrayList<>();
            if (baseModel != null)
                entityModels.addAll(baseModel.getEntityModels());
            entityModels.addAll(entityModelMap.values());
            new OrmModelTopEntryBuilder().build(entityModels, topoEntryMap);
            topoEntryInited = true;
        }
    }

    protected IEntityModel loadEntityModel(String entityName) {
        return entityModelLoader.getEntityModel(entityName);
    }

    @Override
    public List<IEntityModel> getEntityModelInTopoOrder(Collection<String> entityNames) {
        checkTopoEntryReady();
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
        checkTopoEntryReady();
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
    public Collection<? extends IEntityModel> getEntityModelsInTopoOrder() {
        return getEntityModels();
    }

    @Override
    public List<? extends IEntityModel> getEntityModels() {
        checkTopoEntryReady();
        TreeMap<TopoEntry<IEntityModel>, IEntityModel> map = new TreeMap<>();
        for (String name : topoEntryMap.keySet()) {
            TopoEntry<IEntityModel> entry = topoEntryMap.get(name);
            map.put(entry, entry.getValue());
        }
        return new ArrayList<>(map.values());
    }

    @Override
    public Set<String> getEntityNames() {
        checkTopoEntryReady();
        return topoEntryMap.keySet();
    }

    @Override
    public IEntityModel getEntityModel(String entityName) {
        if (baseModel != null) {
            IEntityModel entityModel = baseModel.getEntityModel(entityName);
            if (entityModel != null)
                return entityModel;
        }
        IEntityModel entityModel = entityModelMap.get(entityName);
        if (entityModel != null)
            return entityModel;

        synchronized (entityModelMap) {
            entityModel = entityModelMap.get(entityName);
            if (entityModel != null)
                return entityModel;

            entityModel = this.loadEntityModel(entityName);
            if (entityModel == null)
                return null;

            putEntityModel(entityModel);
        }
        return entityModel;
    }

    void putEntityModel(IEntityModel entityModel) {
        entityModelByTableMap.put(entityModel.getTableName(), entityModel);

        entityModelMap.put(entityModel.getName(), entityModel);
        if (entityModel.isRegisterShortName()) {
            IEntityModel oldModel = entityModelMap.put(entityModel.getShortName(), entityModel);
            if (oldModel != null && oldModel != entityModel)
                throw new NopException(ERR_ORM_MODEL_DUPLICATE_ENTITY_SHORT_NAME).source(entityModel)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_OTHER_LOC, oldModel.getLocation())
                        .param(ARG_OTHER_ENTITY_NAME, oldModel.getName());
        }

        if (entityModel.isRegisterShortName()) {
            // 只有entityModel的短名字不重复的情况下才支持underscore名称，否则可能会出现重名的问题
            String underscoreName = StringHelper.camelCaseToUnderscore(entityModel.getShortName(), true);
            if (underscoreName.equals(entityModel.getTableName()))
                underscoreName = entityModel.getTableName();
            // 只支持全大写和全小写
            snakeCaseNameMap.put(underscoreName, entityModel);
            snakeCaseNameMap.put(underscoreName.toUpperCase(Locale.ROOT), entityModel);
        }
        this.topoEntryInited = false;
    }

    @Override
    public IEntityModel getEntityModelByTableName(String tableName) {
        if (baseModel != null) {
            IEntityModel entityModel = baseModel.getEntityModelByTableName(tableName);
            if (entityModel != null)
                return entityModel;
        }
        return entityModelByTableMap.get(tableName);
    }

    @Override
    public IEntityModel getEntityModelBySnakeCaseName(String name) {
        if (baseModel != null) {
            IEntityModel entityModel = baseModel.getEntityModelBySnakeCaseName(name);
            if (entityModel != null)
                return entityModel;
        }
        return snakeCaseNameMap.get(name);
    }

    @Override
    public IEntityRelationModel getCollectionModel(String collectionName) {
        if (baseModel != null) {
            IEntityRelationModel relModel = baseModel.getCollectionModel(collectionName);
            if (relModel != null)
                return relModel;
        }

        int pos = collectionName.indexOf('@');
        String entityName = collectionName.substring(0, pos);
        IEntityModel entityModel = requireEntityModel(entityName);

        String propName = collectionName.substring(pos + 1);
        return entityModel.getRelation(propName, true);
    }

    @Override
    public List<DictBean> getDicts() {
        if (baseModel != null)
            return baseModel.getDicts();
        return null;
    }

    @Override
    public DictBean getDict(String name) {
        if (baseModel != null)
            return baseModel.getDict(name);
        return null;
    }

    @Override
    public Object prop_get(String propName) {
        if (baseModel != null)
            return baseModel.prop_get(propName);
        return null;
    }

    @Override
    public boolean prop_has(String propName) {
        if (baseModel != null)
            return baseModel.prop_has(propName);
        return false;
    }
}
