package io.nop.batch.core.history;

import io.nop.batch.core.IBatchRecordHistoryStore;

public interface IBatchHistoryStoreBuilder {
    <S> IBatchRecordHistoryStore<S> newHistoryStore(IBatchHistoryStoreModel model);
}
