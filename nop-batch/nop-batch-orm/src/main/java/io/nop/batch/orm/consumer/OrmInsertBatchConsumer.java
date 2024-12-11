package io.nop.batch.orm.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;

import java.util.Collection;

public class OrmInsertBatchConsumer<R extends IOrmEntity> implements IBatchConsumerProvider.IBatchConsumer<R>, IBatchConsumerProvider<R> {
    private final IOrmTemplate ormTemplate;

    public OrmInsertBatchConsumer(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        return this;
    }

    @Override
    public void consume(Collection<R> items, IBatchChunkContext context) {
        ormTemplate.batchSaveOrUpdate(items);
    }
}