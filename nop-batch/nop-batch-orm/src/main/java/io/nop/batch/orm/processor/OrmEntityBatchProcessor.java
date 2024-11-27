package io.nop.batch.orm.processor;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchProcessorProvider;
import io.nop.batch.orm.support.OrmBatchHelper;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;

import java.util.function.Consumer;

public class OrmEntityBatchProcessor<S, R extends IOrmEntity> implements IBatchProcessorProvider.IBatchProcessor<S, R> {
    private final IEntityDao<R> dao;

    public OrmEntityBatchProcessor(IEntityDao<R> dao) {
        this.dao = dao;
    }

    @Override
    public void process(S item, Consumer<R> consumer, IBatchChunkContext context) {
        R entity = toEntity(item);
        consumer.accept(entity);
    }

    protected R toEntity(S item) {
        if (item instanceof IOrmEntity) {
            IOrmEntity entity = (IOrmEntity) item;
            if (entity.orm_entityName().equals(dao.getEntityName()))
                return (R) item;
        }

        IOrmEntity ret = dao.newEntity();
        OrmBatchHelper.assignEntity(ret, item);
        return (R) ret;
    }
}
