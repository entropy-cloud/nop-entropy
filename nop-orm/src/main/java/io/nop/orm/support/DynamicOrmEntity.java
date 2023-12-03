/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.support;

import io.nop.commons.collections.IntArrayMap;
import io.nop.commons.collections.IntHashMap;
import io.nop.commons.collections.MapOfInt;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.reflect.hook.IPropSetMissingHook;
import io.nop.orm.IOrmComponent;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmObject;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityComponentModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PROP_NOT_ALLOW_SET;

public class DynamicOrmEntity extends OrmEntity implements IPropSetMissingHook, IPropGetMissingHook {
    private MapOfInt<Object> values;
    /**
     * 管理Relation以及Component属性
     */
    private final Map<String, Object> refProps = new HashMap<>();

    public DynamicOrmEntity(IEntityModel entityModel) {
        this.orm_entityModel(entityModel);
        this.values = new IntArrayMap<>(entityModel.getPropIdBound());
    }

    private DynamicOrmEntity(IEntityModel entityModel, MapOfInt<Object> values) {
        this.orm_entityModel(entityModel);
        this.values = values;
    }

    public DynamicOrmEntity() {
    }

    protected MapOfInt<Object> requireValues() {
        if (values == null)
            values = new IntHashMap<>();
        return values;
    }

    @Override
    public IOrmEntity cloneInstance() {
        DynamicOrmEntity entity = new DynamicOrmEntity(orm_entityModel(), values.cloneInstance());
        orm_forEachInitedProp((value, propId) -> {
            entity.onInitProp(propId);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
        return requireEntityModel().getName();
    }

    @Override
    public Object orm_id() {
        IEntityPropModel idProp = requireEntityModel().getIdProp();
        if (idProp.isSingleColumn()) {
            return buildSimpleId(idProp.getColumnPropId());
        } else {
            int[] propIds = idProp.getColumnPropIds();
            return buildCompositeId(orm_entityModel().getPkColumnNames(), propIds);
        }
    }

    @Override
    public int orm_propIdBound() {
        return requireEntityModel().getPropIdBound();
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        IColumnModel col = requireEntityModel().getColumnByPropId(propId, true);
        if (col == null)
            return false;
        return col.isPrimary();
    }

    @Override
    public String orm_propName(int propId) {
        IColumnModel col = requireEntityModel().getColumnByPropId(propId, true);
        return col == null ? null : col.getName();
    }

    @Override
    public int orm_propId(String propName) {
        IColumnModel col = requireEntityModel().getColumn(propName, true);
        return col == null ? -1 : col.getPropId();
    }

    @Override
    public Object orm_propValue(int propId) {
        checkPropIdRange(propId);
        onPropGet(propId);
        return internalGetValue(propId);
    }

    protected int nopRevChildChangePropId() {
        IEntityModel entityModel = orm_entityModel();
        if (entityModel == null)
            return -1;
        return entityModel.getNopRevExtChangePropId();
    }

    @Override
    public void orm_propValue(int propId, Object value) {
        checkPropIdRange(propId);
        IColumnModel col = requireEntityModel().getColumnByPropId(propId, false);

        value = col.getStdDataType().convert(value, err -> newTypeConversionError(col.getName()));

        if (onPropSet(propId, value)) {
            internalSetValue(propId, value);
            internalClearRefs(propId);

            if (col.isPrimary())
                orm_id();
        }
    }

    /**
     * 修改属性后，清除所有此前使用了此属性的关联对象。此时关联必然已经失效。
     */
    protected void internalClearRefs(int propId) {
        IEntityModel entityModel = orm_entityModel();
        if (entityModel != null) {
            List<? extends IEntityRelationModel> rels = entityModel.getColumnsRefs(propId);
            if (rels != null) {
                // 属性值发生变化，将导致清空引用对象
                for (IEntityRelationModel rel : rels) {
                    refProps.remove(rel.getName());
                }
            }
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        onInitProp(propId);
        internalSetValue(propId, value);
    }

    protected Object internalGetValue(int propId) {
        return requireValues().get(propId);
    }

    protected void internalSetValue(int propId, Object value) {
        requireValues().put(propId, value);
    }

    @Override
    public boolean prop_has(String propName) {
        IEntityModel entityModel = orm_entityModel();
        if (entityModel == null)
            return false;
        return entityModel.getProp(propName, true) != null;
    }

    @Override
    public Object prop_get(String propName) {
        IEntityPropModel propModel = requireEntityModel().getProp(propName, false);
        if (propModel.isColumnModel()) {
            return orm_propValue(propModel.getColumnPropId());
        } else if (propModel.isToOneRelation()) {
            return internalGetRefEntity((IEntityRelationModel) propModel);
        } else if (propModel.isToManyRelation()) {
            IOrmEntitySet entitySet = (IOrmEntitySet) this.refProps.get(propName);
            if (entitySet == null) {
                IEntityRelationModel relModel = (IEntityRelationModel) propModel;
                entitySet = new OrmEntitySet(this, propName, ((IEntityRelationModel) propModel).getRefPropName(),
                        relModel.getKeyProp(), orm_enhancer().getEntityClass(((IEntityRelationModel) propModel).getRefEntityModel()),
                        relModel.getRefEntityModel().getName());
                refProps.put(propName, entitySet);
            }
            return entitySet;
        } else if (propModel.isAliasModel()) {
            String aliasPath = propModel.getAliasPropPath();
            return internalGetAliasValue(aliasPath);
        } else if (propModel.isComponentModel()) {
            IEntityComponentModel compModel = (IEntityComponentModel) propModel;
            IOrmComponent component = (IOrmComponent) refProps.get(propName);
            if (component == null) {
                component = this.requireEnhancer().newComponent(compModel.getClassName());
                refProps.put(propName, component);
                component.bindToEntity(this, compModel.getColumnPropIdMap());
            }
            return component;
        } else {
            return defaultGetProp(propName);
        }
    }

    @Override
    public void prop_set(String propName, Object value) {
        IEntityPropModel propModel = requireEntityModel().getProp(propName, false);
        if (propModel.isColumnModel()) {
            orm_propValue(propModel.getColumnPropId(), value);
        } else if (propModel.isToOneRelation()) {
            internalSetRefEntity((IEntityRelationModel) propModel, (IOrmEntity) value);
        } else if (propModel.isToManyRelation()) {
            throw newError(ERR_ORM_ENTITY_PROP_NOT_ALLOW_SET).param(ARG_PROP_NAME, propName);
        } else if (propModel.isAliasModel()) {
            internalSetAliasValue(propModel.getAliasPropPath(), value);
        } else {
            defaultSetProp(propName, value);
        }
    }

    protected Object internalGetAliasValue(String propPath) {
        return BeanTool.getComplexProperty(this, propPath);
    }

    protected void internalSetAliasValue(String propPath, Object value) {
        BeanTool.setComplexProperty(this, propPath, value);
    }

    @Override
    public IOrmEntity orm_refEntity(String propName) {
        return internalGetRefEntity(propName);
    }

    @Override
    public boolean orm_refLoaded(String propName) {
        Object prop = refProps.get(propName);
        if (prop == null)
            return false;
        if (prop instanceof IOrmObject)
            return !((IOrmObject) prop).orm_proxy();
        return false;
    }

    @Override
    public void orm_unsetRef(String propName) {
        refProps.remove(propName);
    }

    protected IOrmEntity internalGetRefEntity(String propName) {
        IOrmEntity ref = (IOrmEntity) refProps.get(propName);
        if (ref != null)
            return ref;

        ref = requireEnhancer().internalLoadRefEntity(this, propName);
        if (ref == null)
            return null;

        refProps.put(propName, ref);
        return ref;
    }

    protected void internalSetRefEntity(String propName, IOrmEntity refEntity, Runnable pkWatcher) {
        if (refEntity == null) {
            refProps.remove(propName);
        } else {
            // watcher函数从refEntity读取关联属性值设置到当前实体上
            pkWatcher.run();

            refProps.put(propName, refEntity);
            // 如果实体主键尚未初始化，则注册回调函数。
            if (!refEntity.orm_hasId())
                refEntity.orm_addPkWatcher(pkWatcher);
        }
    }

    private Object internalGetRefEntity(IEntityRelationModel refModel) {
        return internalGetRefEntity(refModel.getName());
    }

    private void internalSetRefEntity(IEntityRelationModel refModel, IOrmEntity refEntity) {
        if (refEntity == null) {
            if (refModel.isSingleColumn()) {
                orm_propValue(refModel.getColumnPropId(), null);
            } else {
                int[] propIds = refModel.getColumnPropIds();
                for (int i = 0, n = propIds.length; i < n; i++) {
                    orm_propValue(propIds[i], null);
                }
            }
            refProps.remove(refModel.getName());
        } else {
            if (refModel.isSingleColumn()) {
                Object refValue = refEntity.orm_id();
                orm_propValue(refModel.getColumnPropId(), refValue);
            } else {
                for (IEntityJoinConditionModel join : refModel.getJoin()) {
                    if (join.getLeftProp() != null) {
                        Object value = OrmEntityHelper.getRightValue(join, refEntity);
                        orm_propValue(join.getLeftPropModel().getColumnPropId(), value);
                    }
                }
            }
            refProps.put(refModel.getName(), refEntity);
        }
    }
}