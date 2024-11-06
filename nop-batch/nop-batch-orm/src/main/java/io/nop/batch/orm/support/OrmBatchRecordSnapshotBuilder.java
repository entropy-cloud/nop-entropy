package io.nop.batch.orm.support;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchRecordSnapshotBuilder;
import io.nop.dao.api.IDaoEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrmBatchRecordSnapshotBuilder implements IBatchRecordSnapshotBuilder<IDaoEntity> {
    @Override
    public ISnapshot<IDaoEntity> buildSnapshot(List<IDaoEntity> items, IBatchChunkContext ctx) {
        Map<IDaoEntity, Map<String, Object>> map = new HashMap<>();
        for (IDaoEntity item : items) {
            map.put(item, item.orm_initedValues());
        }
        return (list, chunkContext) -> {
            List<IDaoEntity> ret = new ArrayList<>(list.size());
            for (IDaoEntity item : list) {
                Map<String, Object> values = map.get(item);
                item.orm_restoreValues(values);
            }
            return ret;
        };
    }
}
