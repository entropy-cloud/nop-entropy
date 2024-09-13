/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.commons.collections.IntArray;
import io.nop.commons.util.CollectionHelper;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.binder.IDataParameters;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.support.OrmCompositePk;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.nop.orm.support.OrmEntityHelper.getOwnerKey;

public class OrmAssembly {
    // public static void setProperties(IOrmEntity entity, MapOfInt<Object> values) {
    // values.forEachEntry((value, propId) -> {
    // entity.orm_propValue(propId, value);
    // });
    // }
    //
    // public static void getProperties(IOrmEntity entity, MapOfInt<Object> values) {
    // entity.orm_forEachInitedProp((value, propId) -> {
    // values.set(propId, value);
    // });
    // }
    //

    /**
     * 将主键列追加到propIds的最前面
     */
    // public static IntArray prependIdCols(IntArray propIds, IEntityModel entityModel) {
    // List<? extends IColumnModel> cols = entityModel.getPkColumns();
    // int n = cols.size();
    // boolean idColFirst = true;
    // if (n > propIds.size()) {
    // idColFirst = false;
    // } else {
    // for (int i = 0; i < n; i++) {
    // int propId = cols.get(i).getPropId();
    // if (propIds.get(i) != propId) {
    // idColFirst = false;
    // break;
    // }
    // }
    // }
    //
    // if (!idColFirst) {
    // MutableIntArray mutable = propIds.toMutable();
    // for (int i = 0; i < n; i++) {
    // int propId = cols.get(i).getPropId();
    // if (propIds.size() <= i) {
    // mutable.insert(i, propId);
    // } else {
    // if (propIds.get(i) != propId) {
    // mutable.removeValue(propId);
    // mutable.insert(i, propId);
    // }
    // }
    // }
    // return mutable;
    // }
    // return propIds;
    // }
    public static Object[] getPropValues(IOrmEntity entity, IntArray propIds) {
        Object[] ret = new Object[propIds.size()];
        for (int i = 0, n = propIds.size(); i < n; i++) {
            int propId = propIds.get(i);
            Object value = entity.orm_propValue(propId);
            ret[i] = value;
        }
        return ret;
    }

    public static void getPropValues(IDataParameters params, IDataParameterBinder[] binders, IntArray propIds,
                                     Object[] result) {
        for (int i = 0, n = propIds.size(); i < n; i++) {
            int propId = propIds.get(i);
            IDataParameterBinder binder = binders[propId];
            Object value = binder.getValue(params, i);
            result[i] = value;
        }
    }

    public static Object[] getPropValues(IDataParameters params, IDataParameterBinder[] binders, IntArray propIds) {
        Object[] result = new Object[propIds.size()];
        getPropValues(params, binders, propIds, result);
        return result;
    }

    public static Object[] getValuesByIndexes(IDataParameters params, IDataParameterBinder[] binders,
                                              int[] colIndexes) {
        Object[] result = new Object[colIndexes.length];
        for (int i = 0, n = colIndexes.length; i < n; i++) {
            IDataParameterBinder binder = binders[i];
            Object value = binder.getValue(params, colIndexes[i]);
            result[i] = value;
        }
        return result;
    }

    public static Map<Object, IOrmEntity> toIdMap(Collection<IOrmEntity> entities) {
        Map<Object, IOrmEntity> ret = CollectionHelper.newHashMap(entities.size());
        for (IOrmEntity entity : entities) {
            ret.put(entity.get_id(), entity);
        }
        return ret;
    }

    public static Map<Object, IOrmEntitySet> toOwnerKeyMap(IEntityRelationModel relModel,
                                                           Collection<IOrmEntitySet> colls) {
        Map<Object, IOrmEntitySet> ret = CollectionHelper.newHashMap(colls.size());
        for (IOrmEntitySet coll : colls) {
            ret.put(getOwnerKey(relModel, coll), coll);
        }
        return ret;
    }

    public static Object readId(Object[] values, IEntityModel entityModel) {
        return readId(values, 0, entityModel);
    }

    /**
     * 假设了主键排在最前面，而且顺序和主键定义顺序一致
     */
    public static Object readId(Object[] values, int fromIndex, IEntityModel entityModel) {
        IEntityPropModel prop = entityModel.getIdProp();
        if (prop == null)
            return null;

        if (prop.isSingleColumn()) {
            return values[0];
        }

        int n = entityModel.getPkColumns().size();
        Object[] ids = new Object[n];
        System.arraycopy(values, fromIndex, ids, 0, n);
        return new OrmCompositePk(entityModel.getPkColumnNames(), ids);
    }

    public static Object readId(IEntityModel entityModel, IDataParameters params, IDataParameterBinder[] binders,
                                int[] indexes) {
        Object[] values = new Object[indexes.length];
        for (int i = 0, n = indexes.length; i < n; i++) {
            Object value = binders[i].getValue(params, indexes[i]);
            if (value == null)
                return null;
            values[i] = value;
        }
        return OrmCompositePk.build(entityModel, values);
    }

    public static IOrmEntity readEntity(IDataParameters rs, IEntityModel entityModel, IDataParameterBinder[] binders,
                                        IntArray propIds, IOrmSessionImplementor session) {

        Object[] values = OrmAssembly.getPropValues(rs, binders, propIds);
        Object id = OrmAssembly.readId(values, entityModel);
        IOrmEntity entity = session.internalLoad(entityModel.getName(), id);
        session.internalAssemble(entity, values, propIds);

        return entity;
    }

    public static IOrmEntity assemble(Object[] values, IEntityModel entityModel, IntArray propIds,
                                      IOrmSessionImplementor session) {
        Object id = OrmAssembly.readId(values, entityModel);
        IOrmEntity entity = session.internalLoad(entityModel.getName(), id);
        session.internalAssemble(entity, values, propIds);
        return entity;
    }

    public static IOrmEntity assemble(Map<String, Object> map, IEntityModel entityModel,
                                      IntArray propIds, IOrmSessionImplementor session) {
        Object[] values = new Object[propIds.size()];
        int i = 0;
        for (int propId : propIds) {
            String propName = entityModel.getColumnByPropId(propId, false).getName();
            values[i] = map.get(propName);
            i++;
        }
        IOrmEntity entity = OrmAssembly.assemble(values, entityModel, propIds, session);
        return entity;
    }

    public static Object[] readRow(IDataParameters rs, List<IDataParameterBinder> binders) {
        Object[] ret = new Object[binders.size()];
        for (int i = 0, n = binders.size(); i < n; i++) {
            IDataParameterBinder binder = binders.get(i);
            ret[i] = binder.getValue(rs, i);
        }
        return ret;
    }
}