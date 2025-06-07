package io.nop.orm.dataset;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.MutableIntArray;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmEntityState;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.sql_lib.OrmEntityRefreshBehavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PROP_IS_DIRTY_WHEN_REFRESH;

public class OrmEntityBuilder<T extends IOrmEntity> {
    private final IOrmEntityDao<T> entityDao;
    private final OrmEntityRefreshBehavior refreshBehavior;
    private List<String> colNames;
    private List<String> extPropNames;
    private MutableIntArray propIds;
    private List<String> pkColNames;

    public OrmEntityBuilder(IOrmEntityDao<T> entityDao,
                            OrmEntityRefreshBehavior refreshBehavior) {
        this.entityDao = entityDao;
        this.refreshBehavior = refreshBehavior;
    }

    public T buildEntity(Map<String, Object> record) {
        // 这里假定了如果多次应用，record中的key是一样的，所以可以缓存colNames
        initColNames(record);

        int columnCount = colNames.size();
        if (pkColNames == null) {
            // 如果数据集中没有包含主键，则新建对象，且不与OrmSession关联
            T entity = entityDao.newEntity();
            for (int i = 0; i < columnCount; i++) {
                String colName = colNames.get(i);
                Object value = record.get(colName);
                entity.orm_propValueByName(colName, value);
            }
            setExtProps(entity, record);
            return entity;
        } else {
            T entity = loadEntity(record);
            OrmEntityRefreshBehavior behavior = this.refreshBehavior;
            if (entity.orm_proxy()) {
                // 如果尚未加载，则behavior的设置不起作用，这里进行归一化处理，减少判断
                behavior = OrmEntityRefreshBehavior.useLast;
            } else if (behavior == OrmEntityRefreshBehavior.useFirst) {
                // 如果是useFirst，且实体已经装载，则直接返回实体
                return entity;
            }

            if (behavior == null)
                behavior = OrmEntityRefreshBehavior.errorWhenDirty;

            for (int i = 0; i < columnCount; i++) {
                Object value = record.get(colNames.get(i));
                setProp(entity, i, value, behavior);
            }

            if (entity.orm_proxy()) {
                entity.orm_state(OrmEntityState.MANAGED);
            }

            setExtProps(entity, record);
            return entity;
        }
    }

    private void setProp(T entity, int index, Object value, OrmEntityRefreshBehavior behavior) {
        int propId = propIds.get(index);

        // 是数据库列
        if (behavior == OrmEntityRefreshBehavior.errorWhenDirty) {
            String colName = colNames.get(index);
            if (entity.orm_propDirty(propId))
                throw new NopException(ERR_ORM_ENTITY_PROP_IS_DIRTY_WHEN_REFRESH)
                        .param(ARG_ENTITY_NAME, entity.orm_entityName())
                        .param(ARG_ENTITY_ID, entity.orm_id())
                        .param(ARG_PROP_NAME, colName);
        }
        entity.orm_internalSet(propId, value);
    }

    private void setExtProps(T entity, Map<String, Object> record) {
        if (extPropNames != null) {
            int count = extPropNames.size();
            for (int i = 0; i < count; i++) {
                String colName = extPropNames.get(i);
                Object value = record.get(colName);
                if (colName.indexOf('.') < 0) {
                    entity.orm_propValueByName(colName, value);
                } else {
                    BeanTool.setComplexProperty(entity, colName, value);
                }
            }
        }
    }

    private void initColNames(Map<String, Object> record) {
        if (colNames != null)
            return;

        IEntityModel entityModel = entityDao.getEntityModel();
        List<String> colNames = new ArrayList<>(record.size());
        List<String> extPropNames = null;
        MutableIntArray propIds = new MutableIntArray();
        for (String colName : record.keySet()) {
            IColumnModel col = entityModel.getColumn(colName, true);
            if (col != null) {
                propIds.add(col.getPropId());
                colNames.add(colName);
            } else {
                if (extPropNames == null)
                    extPropNames = new ArrayList<>();
                extPropNames.add(colName);
            }
        }

        this.propIds = propIds;
        this.colNames = colNames;
        this.extPropNames = extPropNames;

        List<String> pkColNames = entityModel.getPkColumnNames();
        if (record.keySet().containsAll(pkColNames))
            this.pkColNames = pkColNames;
    }

    private T loadEntity(Map<String, Object> record) {
        if (pkColNames.size() == 1) {
            Object id = record.get(pkColNames.get(0));
            return entityDao.loadEntityById(id);
        } else {
            Object[] pk = new Object[pkColNames.size()];
            for (int i = 0; i < pkColNames.size(); i++) {
                pk[i] = record.get(pkColNames.get(i));
            }
            return entityDao.loadEntityById(pk);
        }
    }
}
