package io.nop.orm.dataset;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dataset.IDataFieldMeta;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmEntityState;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.sql_lib.OrmEntityRefreshBehavior;

import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PROP_IS_DIRTY_WHEN_REFRESH;

public class OrmEntityRowMapper<T extends IOrmEntity> implements IRowMapper<T> {
    private final IOrmEntityDao<T> entityDao;
    private final boolean camelCase;
    private final OrmEntityRefreshBehavior refreshBehavior;
    private List<String> colNames;
    private MutableIntArray propIds;
    private int[] pkColIndexes;

    public OrmEntityRowMapper(IOrmEntityDao<T> entityDao, boolean camelCase,
                              OrmEntityRefreshBehavior refreshBehavior) {
        this.entityDao = entityDao;
        this.camelCase = camelCase;
        this.refreshBehavior = refreshBehavior;
    }

    @Override
    public T mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper) {
        initColNames(row);

        int columnCount = colNames.size();
        if (pkColIndexes == null) {
            // 如果数据集中没有包含主键，则新建对象，且不与OrmSession关联
            T bean = entityDao.newEntity();
            for (int i = 0; i < columnCount; i++) {
                String colName = colNames.get(i);
                Object value = colMapper.getValue(row, i);
                if (colName.indexOf('.') < 0) {
                    bean.orm_propValueByName(colName, value);
                } else {
                    BeanTool.setComplexProperty(bean, colName, value);
                }
            }
            return bean;
        } else {
            T entity = loadEntity(row, colMapper);
            OrmEntityRefreshBehavior behavior = this.refreshBehavior;
            if (entity.orm_proxy()) {
                behavior = OrmEntityRefreshBehavior.useLast;
            }

            // 如果是useFirst，且实体已经装载，则直接返回实体
            if (behavior == OrmEntityRefreshBehavior.useFirst) {
                return entity;
            }

            if (behavior == null)
                behavior = OrmEntityRefreshBehavior.errorWhenDirty;

            for (int i = 0; i < columnCount; i++) {
                Object value = colMapper.getValue(row, i);
                setProp(entity, i, value, behavior);
            }

            if (entity.orm_proxy()) {
                entity.orm_state(OrmEntityState.MANAGED);
            }
            return entity;
        }
    }

    private void setProp(T entity, int index, Object value, OrmEntityRefreshBehavior behavior) {
        String colName = colNames.get(index);
        int propId = propIds.get(index);
        if (propId > 0) {
            // 是数据库列
            if (behavior == OrmEntityRefreshBehavior.errorWhenDirty) {
                if (entity.orm_propDirty(propId))
                    throw new NopException(ERR_ORM_ENTITY_PROP_IS_DIRTY_WHEN_REFRESH)
                            .param(ARG_ENTITY_NAME, entity.orm_entityName())
                            .param(ARG_ENTITY_ID, entity.orm_id())
                            .param(ARG_PROP_NAME, colName);
            }
            entity.orm_internalSet(propId, value);
        } else {
            if (colName.indexOf('.') < 0) {
                entity.orm_propValueByName(colName, value);
            } else {
                BeanTool.setComplexProperty(entity, colName, value);
            }
        }
    }

    private void initColNames(IDataRow row) {
        if (colNames != null)
            return;

        IEntityModel entityModel = entityDao.getEntityModel();
        List<String> colNames = new ArrayList<>(row.getFieldCount());
        MutableIntArray propIds = new MutableIntArray();
        for (IDataFieldMeta fieldMeta : row.getMeta().getFieldMetas()) {
            String colName = fieldMeta.getFieldName();
            if (camelCase) {
                colName = StringHelper.camelCase(colName, '_', false);
            }
            colNames.add(colName);

            IColumnModel col = entityModel.getColumn(colName, true);
            if (col != null) {
                propIds.add(col.getPropId());
            } else {
                propIds.add(0);
            }
        }

        this.propIds = propIds;

        List<String> pkColNames = entityModel.getPkColumnNames();
        if (entityModel.isCompositePk()) {
            if (colNames.containsAll(pkColNames)) {
                int[] indexes = new int[pkColNames.size()];
                for (int i = 0, n = indexes.length; i < n; i++) {
                    indexes[i] = colNames.indexOf(pkColNames.get(i));
                }
                this.pkColIndexes = indexes;
            }
        } else {
            int index = colNames.indexOf(pkColNames.get(0));
            if (index > 0) {
                pkColIndexes = new int[]{index};
            }
        }
        this.colNames = colNames;
    }

    private T loadEntity(IDataRow row, IFieldMapper colMapper) {
        if (pkColIndexes.length == 1) {
            Object id = colMapper.getValue(row, pkColIndexes[0]);
            return entityDao.loadEntityById(id);
        } else {
            Object[] pk = new Object[pkColIndexes.length];
            for (int i = 0; i < pkColIndexes.length; i++) {
                pk[i] = colMapper.getValue(row, pkColIndexes[i]);
            }
            return entityDao.loadEntityById(pk);
        }
    }
}
