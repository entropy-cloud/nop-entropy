/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.support;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.orm.IOrmCompositePk;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.component.OrmFileComponent;
import io.nop.orm.component.OrmFileListComponent;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityComponentModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmModelConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static io.nop.orm.OrmErrors.ARG_COLLECTION_NAME;
import static io.nop.orm.OrmErrors.ARG_COUNT;
import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_OWNER;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_NO_ID_PROP;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_COMPOSITE_PK_PART_COUNT;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_ENTITY_ID;
import static io.nop.orm.OrmErrors.ERR_ORM_NOT_SINGLETON_SET;

public class OrmEntityHelper {
    public static boolean isFileComponent(IEntityComponentModel comp) {
        return comp.getClassName().equals(OrmFileComponent.class.getName());
    }

    public static boolean isFileListComponent(IEntityComponentModel comp) {
        return comp.getClassName().equals(OrmFileListComponent.class.getName());
    }

    @SuppressWarnings("unchecked")
    public static List<String> parseFileList(String filePath) {
        if (StringHelper.isEmpty(filePath))
            return null;
        if (filePath.startsWith("[") && filePath.endsWith("]"))
            return (List<String>) JsonTool.parseNonStrict(filePath);
        return StringHelper.stripedSplit(filePath, ',');
    }

    public static String joinFileList(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty())
            return null;
        return StringHelper.join(filePaths, ",");
    }

    public static void copyRefProps(IOrmEntity entity, IEntityRelationModel rel, IOrmEntity relatedEntity) {
        for (IEntityJoinConditionModel cond : rel.getJoin()) {
            IEntityPropModel leftCol = cond.getLeftPropModel();
            if (leftCol == null)
                continue;

            Object rightValue = getRightValue(cond, relatedEntity);
            setPropValue(leftCol, entity, rightValue);
        }
    }

    /**
     * 装载关联对象所需的属性是否都已经被设置。
     */
    public static boolean isRefPropLoaded(IEntityRelationModel refModel, IOrmEntity entity) {
        for (IEntityJoinConditionModel cond : refModel.getJoin()) {
            IEntityPropModel leftCol = cond.getLeftPropModel();
            if (leftCol != null) {
                if (!isPropLoaded(entity, leftCol))
                    return false;
            }
        }
        return true;
    }

    public static boolean isPropLoaded(IOrmEntity entity, IEntityPropModel propModel) {
        if (propModel.isSingleColumn()) {
            return entity.orm_propInited(propModel.getColumnPropId());
        }
        for (int propId : propModel.getColumnPropIds()) {
            if (!entity.orm_propInited(propId))
                return false;
        }
        return true;
    }

    public static Object getPropValue(IEntityPropModel propModel, IOrmEntity entity) {
        if (propModel.isColumnModel()) {
            return entity.orm_propValue(propModel.getColumnPropId());
        } else if (propModel.isCompositePk()) {
            return entity.orm_idString();
        } else {
            return entity.orm_propValueByName(propModel.getName());
        }
    }

    public static void setPropValue(IEntityPropModel propModel, IOrmEntity entity, Object value) {
        if (propModel.isColumnModel()) {
            entity.orm_propValue(propModel.getColumnPropId(), value);
        } else if (propModel.isCompositePk()) {
            IEntityModel entityModel = propModel.getOwnerEntityModel();
            Object id = OrmEntityHelper.castId(entityModel, value);
            OrmEntityHelper.setId(entityModel, entity, id);
        } else {
            entity.orm_propValueByName(propModel.getName(), value);
        }
    }

    public static boolean isAnyPropNull(IOrmEntity entity, int[] propIds) {
        for (int i = 0, n = propIds.length; i < n; i++) {
            Object value = entity.orm_propValue(propIds[i]);
            if (value == null)
                return true;
        }
        return false;
    }

    public static boolean isPropValueEquals(IEntityPropModel propA, Object valueA, IEntityPropModel propB, Object valueB) {
        StdDataType typeA = propA.getStdDataType();
        StdDataType typeB = propB.getStdDataType();
        if (typeA == typeB)
            return Objects.equals(valueA, valueB);

        if (typeA == StdDataType.STRING)
            return Objects.equals(valueA, StringHelper.toString(valueB, null));

        if (typeB == StdDataType.STRING)
            return Objects.equals(StringHelper.toString(valueA, null), valueB);

        return MathHelper.eq(valueA, valueB);
    }

    public static Object castId(IEntityModel entityModel, Object id) {
        if (id == null)
            return null;

        IEntityPropModel idProp = entityModel.getIdProp();
        if (idProp == null)
            throw new NopException(ERR_ORM_ENTITY_NO_ID_PROP)
                    .param(ARG_ENTITY_NAME, entityModel.getName());

        if (idProp.isSingleColumn()) {
            return idProp.getColumns().get(0).getStdDataType().convert(id,
                    err -> new OrmException(err).param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_ENTITY_ID, id)
                            .param(ARG_PROP_NAME, idProp.getName()));
        }

        if (id instanceof IOrmCompositePk) {
            IOrmCompositePk pk = (IOrmCompositePk) id;
            if (pk.size() != entityModel.getPkColumns().size())
                throw new OrmException(ERR_ORM_INVALID_COMPOSITE_PK_PART_COUNT)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_ENTITY_ID, id)
                        .param(ARG_COUNT, entityModel.getPkColumns().size());
            return pk;
        }

        if (id instanceof String)
            return OrmCompositePk.parse(entityModel, (String) id);

        if (id instanceof Object[]) {
            return OrmCompositePk.build(entityModel, (Object[]) id);
        }

        throw new OrmException(ERR_ORM_INVALID_ENTITY_ID).param(ARG_ENTITY_NAME, entityModel.getName())
                .param(ARG_ENTITY_ID, id);
    }

    public static void setId(IEntityModel entityModel, IOrmEntity entity, Object id) {
        IEntityPropModel idProp = entityModel.getIdProp();
        if (idProp.isSingleColumn()) {
            entity.orm_internalSet(idProp.getColumnPropId(), id);
        } else {
            int[] propIds = idProp.getColumnPropIds();
            if (StringHelper.isEmptyObject(id)) {
                for (int i = 0, n = propIds.length; i < n; i++) {
                    entity.orm_internalSet(propIds[i], null);
                }
            } else {
                IOrmCompositePk pk;
                if (id instanceof String) {
                    pk = OrmCompositePk.parse(entityModel, (String) id);
                } else {
                    pk = (IOrmCompositePk) id;
                }
                for (int i = 0, n = propIds.length; i < n; i++) {
                    Object idValue = pk.get(i);
                    entity.orm_internalSet(propIds[i], idValue);
                }
            }
        }
    }

    public static Object getOwnerKey(IEntityRelationModel collectionModel, IOrmEntity refEntity) {
        IEntityJoinConditionModel ownerIdJoin = collectionModel.getSingleColumnJoin();
        if (ownerIdJoin != null) {
            Object value = getRightValue(ownerIdJoin, refEntity);
            return value;
        } else {
            List<Object> list = new ArrayList<>(collectionModel.getJoin().size());
            for (IEntityJoinConditionModel join : collectionModel.getJoin()) {
                if (join.getRightProp() == null)
                    continue;
                Object value = getRightValue(join, refEntity);
                list.add(value);
            }
            return list;
        }
    }

    public static Object getOwnerKey(IEntityRelationModel relModel, IOrmEntitySet coll) {
        IEntityJoinConditionModel singleJoin = relModel.getSingleColumnJoin();
        if (singleJoin != null) {
            Object value = getLeftValue(singleJoin, coll.orm_owner());
            value = singleJoin.getRightType().convert(value, NopEvalException::new);
            return value;
        } else {
            List<Object> list = new ArrayList<>(relModel.getJoin().size());
            for (IEntityJoinConditionModel join : relModel.getJoin()) {
                if (join.getRightProp() == null)
                    continue;

                Object value = getLeftValue(join, coll.orm_owner());
                value = join.getRightType().convert(value, NopEvalException::new);
                list.add(value);
            }
            return list;
        }
    }

    public static Object getLeftValue(IEntityJoinConditionModel join, IOrmEntity owner) {
        IEntityPropModel propModel = join.getLeftPropModel();
        if (propModel != null)
            return getPropValue(propModel, owner);
        return join.getLeftValue();
    }

    public static Object getRightValue(IEntityJoinConditionModel join, IOrmEntity entity) {
        IEntityPropModel propModel = join.getRightPropModel();
        if (propModel != null)
            return getPropValue(propModel, entity);
        return join.getRightValue();
    }

    /**
     * 如果实体被修改，返回修改前和修改后的属性值。
     *
     * @param entity 实体对象
     * @return 格式为 [[propId,oldValue,newValue]]
     */
    public static List<List<Object>> getEntityChange(IOrmEntity entity) {
        if (entity.orm_dirty())
            return Collections.emptyList();

        List<List<Object>> ret = new ArrayList<>();
        entity.orm_forEachDirtyProp((oldValue, propId) -> {
            List<Object> change = new ArrayList<>(3);
            change.add(propId);
            change.add(oldValue);
            change.add(entity.orm_propValue(propId));
        });
        return ret;
    }

    /**
     * 假设多对多关联表中目前实际只维持一对多或者一对一关系
     *
     * @param coll     多对多关联实体集合，实际其中最多只有一条记录
     * @param propName 关联实体上的属性
     * @return 属性值
     */
    public static Object getPropFromSingleton(IOrmEntitySet<? extends IOrmEntity> coll, String propName) {
        if (coll.isEmpty())
            return null;
        if (coll.size() > 1)
            throw new OrmException(ERR_ORM_NOT_SINGLETON_SET)
                    .param(ARG_COLLECTION_NAME, coll.orm_collectionName())
                    .param(ARG_OWNER, coll.orm_owner());

        IOrmEntity data = coll.iterator().next();
        return data.orm_propValueByName(propName);
    }

    public static String getRefIdFromSingleton(IOrmEntitySet<? extends IOrmEntity> coll, String propName) {
        if (coll.isEmpty())
            return null;
        if (coll.size() > 1)
            throw new OrmException(ERR_ORM_NOT_SINGLETON_SET)
                    .param(ARG_COLLECTION_NAME, coll.orm_collectionName())
                    .param(ARG_OWNER, coll.orm_owner());

        IOrmEntity data = coll.iterator().next();
        Object value = data.orm_propValueByName(propName);
        return getRefPropId(value);
    }

    public static <T extends IOrmEntity> void setPropToSingleton(IOrmEntitySet<T> coll, String propName, Object value) {
        IOrmEntity entity;
        if (coll.isEmpty()) {
            entity = coll.orm_newItem();
            entity.orm_propValueByName(propName, value);
            coll.add((T) entity);
        } else {
            if (coll.size() > 1)
                throw new OrmException(ERR_ORM_NOT_SINGLETON_SET)
                        .param(ARG_COLLECTION_NAME, coll.orm_collectionName())
                        .param(ARG_OWNER, coll.orm_owner());

            entity = coll.iterator().next();
            entity.orm_propValueByName(propName, value);
        }
    }

    public static <T extends IOrmEntity> void setRefIdToSingleton(IOrmEntitySet<T> coll, String propName, String value) {
        IEntityModel refEntityModel = coll.orm_enhancer().getEntityModel(coll.orm_refEntityName());
        IEntityPropModel propModel = refEntityModel.requireProp(propName);
        if (propModel.isRelationModel()) {
            String refEntityName = ((IEntityRelationModel) propModel).getRefEntityName();
            setPropToSingleton(coll, propName, coll.orm_enhancer().internalLoad(refEntityName, value));
        } else {
            setPropToSingleton(coll, propName, value);
        }
    }


    /**
     * 从多对多关联表中获取指定关联属性值
     *
     * @param coll     关联实体集合
     * @param propName 关联属性值
     * @return 属性值集合
     */
    public static List<?> getRefProps(IOrmEntitySet<? extends IOrmEntity> coll, String propName) {
        List<Object> ret = new ArrayList<>(coll.size());
        for (IOrmEntity entity : coll) {
            Object value = entity.orm_propValueByName(propName);
            ret.add(value);
        }
        return ret;
    }

    /**
     * 根据name和values列表构造关联实体列表，然后设置到coll集合中
     *
     * @param coll     多对多关联实体集合
     * @param propName 关联实体上对应于value的属性名
     * @param values   value列表
     * @param <T>      实体类型
     */
    public static <T extends IOrmEntity> void setRefProps(IOrmEntitySet<T> coll, String propName, List<?> values) {
        if (values == null)
            values = Collections.emptyList();

        if (coll.isEmpty()) {
            for (Object value : values) {
                IOrmEntity entity = coll.orm_newItem();
                entity.orm_propValueByName(propName, value);
                coll.add((T) entity);
            }
        } else {
            List<Object> newValues = new ArrayList<>(values);
            Iterator<T> it = coll.iterator();
            while (it.hasNext()) {
                T entity = it.next();
                Object value = entity.orm_propValueByName(propName);
                // 如果旧集合中有但是新的数据中没有，则删除
                if (!newValues.remove(value)) {
                    it.remove();
                }
            }

            for (Object value : newValues) {
                IOrmEntity entity = coll.orm_newItem();
                entity.orm_propValueByName(propName, value);
                coll.add((T) entity);
            }
        }
    }

    static String getRefPropId(Object value) {
        if (value == null)
            return null;
        if (value instanceof IOrmEntity)
            return ((IOrmEntity) value).orm_idString();
        return ConvertHelper.toString(value);
    }

    public static List<String> getRefIds(IOrmEntitySet<? extends IOrmEntity> coll, String propName) {
        List<String> ret = new ArrayList<>(coll.size());
        for (IOrmEntity entity : coll) {
            Object value = entity.orm_propValueByName(propName);
            ret.add(getRefPropId(value));
        }
        return ret;
    }

    public static <T extends IOrmEntity> void setRefIds(IOrmEntitySet<T> coll, String propName, List<String> values) {
        if (values == null)
            values = Collections.emptyList();

        IEntityModel refEntityModel = coll.orm_enhancer().getEntityModel(coll.orm_refEntityName());
        IEntityPropModel propModel = refEntityModel.requireProp(propName);
        if (propModel.isRelationModel()) {
            String refEntityName = ((IEntityRelationModel) propModel).getRefEntityName();
            List<IOrmEntity> refEntities = new ArrayList<>(values.size());
            for (String value : values) {
                refEntities.add(coll.orm_enhancer().internalLoad(refEntityName, value));
            }
            setRefProps(coll, propName, refEntities);
        } else {
            setRefProps(coll, propName, values);
        }
    }

    public static String getLabelForRefProps(IOrmEntitySet<? extends IOrmEntity> coll, String propName) {
        IEntityModel refEntityModel = coll.orm_enhancer().getEntityModel(coll.orm_refEntityName());
        String labelProp = refEntityModel.getLabelProp();
        if (labelProp == null)
            labelProp = OrmModelConstants.PROP_ID;

        StringBuilder sb = new StringBuilder();
        for (IOrmEntity entity : coll) {
            IOrmEntity refEntity = entity.orm_refEntity(propName);
            if (refEntity != null) {
                if (sb.length() == 0)
                    sb.append(',');
                sb.append(refEntity.orm_propValueByName(labelProp));
            }
        }
        return sb.toString();
    }

    public static void normalizePropTypes(List<Object> list, IEntityModel entityModel, List<String> propNames) {
        for (String propName : propNames) {
            normalizePropType(list, entityModel, propName);
        }
    }

    /**
     * 将列表对象中指定属性的类型转换为实体属性类型
     *
     * @param list        对象列表。可能是JSON对象。
     * @param entityModel 实体模型
     * @param propName    属性名
     */
    public static void normalizePropType(List<Object> list, IEntityModel entityModel, String propName) {
        IEntityPropModel propModel = entityModel.getProp(propName, true);
        if (propModel == null)
            return;

        for (Object obj : list) {
            Object value = BeanTool.getProperty(obj, propName);
            if (value != null) {
                Object converted = propModel.getStdDataType().convert(value);
                if (converted != value)
                    BeanTool.instance().setProperty(obj, propName, converted);
            }
        }
    }

    public static boolean isSameTenant(IOrmEntity entityA, IOrmEntity entityB) {
        IEntityModel entityModelA = entityA.orm_entityModel();
        IEntityModel entityModelB = entityB.orm_entityModel();
        boolean useTenant = entityModelA.isUseTenant();
        // 一方使用租户，另一方不使用租户时必然不匹配
        if (useTenant != entityModelB.isUseTenant())
            return false;

        // 如果都不使用租户，则匹配
        if (!useTenant)
            return true;

        Object tenantIdA = entityA.orm_propValue(entityModelA.getTenantPropId());
        Object tenantIdB = entityB.orm_propValue(entityModelB.getTenantPropId());
        if (tenantIdA == null || !tenantIdA.equals(tenantIdB))
            return false;

        return true;
    }
}