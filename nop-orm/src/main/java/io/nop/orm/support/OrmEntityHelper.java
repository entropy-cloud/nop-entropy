/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.support;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.commons.util.StringHelper;
import io.nop.orm.IOrmCompositePk;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.orm.OrmErrors.ARG_COUNT;
import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_COMPOSITE_PK_PART_COUNT;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_ENTITY_ID;

public class OrmEntityHelper {
    public static String buildCollectionName(String entityName, String propName) {
        return entityName + '@' + propName;
    }

    public static void copyRefProps(IOrmEntity entity, IEntityRelationModel rel, IOrmEntity relatedEntity) {
        for (IEntityJoinConditionModel cond : rel.getJoin()) {
            IEntityPropModel leftCol = cond.getLeftPropModel();
            if (leftCol == null)
                continue;

            Object rightValue = cond.getRightValue(relatedEntity);
            leftCol.setPropValue(entity, rightValue);
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

    public static boolean isAnyPropNull(IOrmEntity entity, int[] propIds) {
        for (int i = 0, n = propIds.length; i < n; i++) {
            Object value = entity.orm_propValue(propIds[i]);
            if (value == null)
                return true;
        }
        return false;
    }

    public static Object castId(IEntityModel entityModel, Object id) {
        if (id == null)
            return null;

        IEntityPropModel idProp = entityModel.getIdProp();
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
                    Object[] idValues = OrmCompositePk.parse(entityModel, (String) id);
                    pk = new OrmCompositePk(entityModel.getPkColumnNames(), idValues);
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
            Object value = ownerIdJoin.getRightValue(refEntity);
            return value;
        } else {
            List<Object> list = new ArrayList<>(collectionModel.getJoin().size());
            for (IEntityJoinConditionModel join : collectionModel.getJoin()) {
                if (join.getRightProp() == null)
                    continue;
                Object value = join.getRightValue(refEntity);
                list.add(value);
            }
            return list;
        }
    }

    public static Object getOwnerKey(IEntityRelationModel relModel, IOrmEntitySet coll) {
        IEntityJoinConditionModel singleJoin = relModel.getSingleColumnJoin();
        if (singleJoin != null) {
            Object value = singleJoin.getLeftValue(coll.orm_owner());
            value = singleJoin.getRightType().convert(value, NopEvalException::new);
            return value;
        } else {
            List<Object> list = new ArrayList<>(relModel.getJoin().size());
            for (IEntityJoinConditionModel join : relModel.getJoin()) {
                if (join.getRightProp() == null)
                    continue;

                Object value = join.getLeftValue(coll.orm_owner());
                value = join.getRightType().convert(value, NopEvalException::new);
                list.add(value);
            }
            return list;
        }
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

    public static int[] getPropIds(List<? extends IColumnModel> cols) {
        int[] ret = new int[cols.size()];
        for (int i = 0, n = cols.size(); i < n; i++) {
            ret[i] = cols.get(i).getPropId();
        }
        return ret;
    }
}
