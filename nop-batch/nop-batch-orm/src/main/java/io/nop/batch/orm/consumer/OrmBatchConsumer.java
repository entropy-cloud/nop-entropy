package io.nop.batch.orm.consumer;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.orm.support.OrmBatchHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrmBatchConsumer<S extends IOrmEntity, R> implements IBatchConsumerProvider.IBatchConsumer<R> {
    private final IOrmEntityDao<S> dao;
    private final boolean useIdAsKey;
    private final boolean allowInsert;
    private final boolean allowUpdate;
    private final List<IColumnModel> keyCols;
    private final IColumnModel keyCol;

    public OrmBatchConsumer(IOrmEntityDao<S> dao, Collection<String> keyFields,
                            boolean allowInsert, boolean allowUpdate) {
        this.dao = dao;
        this.useIdAsKey = isId(dao, keyFields);
        Collection<String> normalizedKeyFields = useIdAsKey ? dao.getPkColumnNames() : keyFields;
        this.allowInsert = allowInsert;
        this.allowUpdate = allowUpdate;
        this.keyCols = getColumns(dao, normalizedKeyFields);
        this.keyCol = keyCols.size() == 1 ? keyCols.get(0) : null;
    }

    private boolean isId(IEntityDao<S> dao, Collection<String> fields) {
        List<String> pkFields = dao.getPkColumnNames();
        if (pkFields.size() != fields.size())
            return false;
        for (String field : fields) {
            if (!pkFields.contains(field))
                return false;
        }
        return true;
    }

    private List<IColumnModel> getColumns(IOrmEntityDao<S> dao, Collection<String> fields) {
        IEntityModel entityModel = dao.getEntityModel();
        return fields.stream().map(entityModel::requireColumn).collect(Collectors.toList());
    }

    @Override
    public void consume(List<R> items, IBatchChunkContext context) {
        Map<Object, R> keyMap = new HashMap<>(items.size());
        for (R item : items) {
            keyMap.put(getKey(item), item);
        }

        Map<Object, S> map;
        if (useIdAsKey) {
            map = dao.batchGetEntityMapByIds(keyMap.keySet());
        } else {
            map = new HashMap<>();
            QueryBean query = new QueryBean();
            List<S> list = dao.findAllByQuery(query);
            for (S entity : list) {
                Object key = getKeyFromEntity(entity);
                map.put(key, entity);
            }
        }

        keyMap.forEach((key, item) -> {
            S entity = map.get(key);
            if (entity != null) {
                if (!allowUpdate)
                    return;

                OrmBatchHelper.assignEntity(entity, item);
                dao.updateEntity(entity);
            } else {
                if (!allowInsert)
                    return;

                S newEntity = dao.newEntity();
                OrmBatchHelper.assignEntity(newEntity, item);
                dao.saveEntity(newEntity);
            }
        });
    }


    private Object getKey(R item) {
        if (keyCol != null) {
            return keyCol.getStdDataType().convert(BeanTool.getProperty(item, keyCol.getName()));
        } else {
            List<Object> keys = keyCols.stream().map(col -> col.getStdDataType().convert(BeanTool.getProperty(item, col.getName())))
                    .collect(Collectors.toList());
            if (useIdAsKey)
                return dao.castId(keys);
            return keys;
        }
    }

    private Object getKeyFromEntity(S entity) {
        if (keyCol != null) {
            return entity.orm_propInited(keyCol.getPropId());
        } else if (useIdAsKey) {
            return entity.orm_id();
        } else {
            List<Object> keys = keyCols.stream().map(col -> entity.orm_propValue(col.getPropId()))
                    .collect(Collectors.toList());
            return keys;
        }
    }
}