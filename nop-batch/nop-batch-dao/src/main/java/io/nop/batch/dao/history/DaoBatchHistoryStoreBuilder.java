package io.nop.batch.dao.history;

import io.nop.batch.core.IBatchRecordHistoryStore;
import io.nop.batch.core.history.IBatchHistoryStoreBuilder;
import io.nop.batch.core.history.IBatchHistoryStoreModel;
import io.nop.batch.dao.entity.NopBatchRecordResult;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.dao.AbstractDaoHandler;

public class DaoBatchHistoryStoreBuilder extends AbstractDaoHandler implements IBatchHistoryStoreBuilder {

    @Override
    public <S> IBatchRecordHistoryStore<S> newHistoryStore(IBatchHistoryStoreModel model) {
        IEntityDao<NopBatchRecordResult> dao = daoFor(NopBatchRecordResult.class);
        return new DaoBatchRecordHistoryStore<>(dao, model);
    }
}
