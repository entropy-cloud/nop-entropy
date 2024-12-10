package io.nop.batch.orm.support;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchRecordSnapshotBuilder;
import io.nop.dao.api.IDaoEntity;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrmBatchRecordSnapshotBuilder<T extends IOrmEntity>
        implements IBatchRecordSnapshotBuilder<T> {
    private final IOrmTemplate ormTemplate;
    private final boolean cascadeAttach;

    public OrmBatchRecordSnapshotBuilder(IOrmTemplate ormTemplate, boolean cascadeAttach) {
        this.ormTemplate = ormTemplate;
        this.cascadeAttach = cascadeAttach;
    }

    @Override
    public ISnapshot<T> buildSnapshot(List<T> items, IBatchChunkContext ctx) {
        Map<IDaoEntity, Map<String, Object>> map = new HashMap<>();
        for (IDaoEntity item : items) {
            map.put(item, item.orm_initedValues());
        }
        return new ISnapshot<T>() {
            @Override
            public List<T> restore(List<T> list, IBatchChunkContext chunkContext) {
                IOrmSession session = ormTemplate.currentSession();
                List<T> ret = new ArrayList<>(list.size());
                for (T item : list) {
                    Map<String, Object> values = map.get(item);
                    item.orm_restoreValues(values);
                    session.attach(item, cascadeAttach);
                }
                return ret;
            }

            @Override
            public void onError(Throwable e) {
                ormTemplate.clearSession();
            }
        };
    }
}