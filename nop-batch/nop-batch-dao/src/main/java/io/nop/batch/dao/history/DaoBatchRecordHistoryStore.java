package io.nop.batch.dao.history;

import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchRecordHistoryStore;
import io.nop.batch.core.history.IBatchHistoryStoreModel;
import io.nop.batch.dao.entity.NopBatchRecordResult;
import io.nop.dao.api.IEntityDao;

import java.util.Collection;
import java.util.List;

public class DaoBatchRecordHistoryStore<S> implements IBatchRecordHistoryStore<S> {
    private final IEntityDao<NopBatchRecordResult> dao;
    private final IBatchHistoryStoreModel model;

    public DaoBatchRecordHistoryStore(IEntityDao<NopBatchRecordResult> dao, IBatchHistoryStoreModel model) {
        this.dao = Guard.notNull(dao, "dao");
        this.model = Guard.notNull(model, "model");
    }

    @Override
    public Collection<S> filterProcessed(Collection<S> records, IBatchChunkContext context) {
        return List.of();
    }

    @Override
    public void saveProcessed(Collection<S> filtered, Throwable exception, IBatchChunkContext context) {

    }
}
