package io.nop.batch.orm.consumer;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.consumer.BatchProcessorConsumer;
import io.nop.batch.orm.processor.OrmEntityBatchProcessor;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import jakarta.inject.Inject;

import java.util.Collection;

public class OrmBatchConsumerProvider<R> implements IBatchConsumerProvider<R> {
    private IOrmTemplate ormTemplate;
    private IDaoProvider daoProvider;
    private String entityName;
    private Collection<String> keyFields;
    private boolean allowUpdate;
    private boolean allowInsert;

    public IOrmTemplate getOrmTemplate() {
        return ormTemplate;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Collection<String> getKeyFields() {
        return keyFields;
    }

    public void setKeyFields(Collection<String> keyFields) {
        this.keyFields = keyFields;
    }

    public boolean isAllowUpdate() {
        return allowUpdate;
    }

    public void setAllowUpdate(boolean allowUpdate) {
        this.allowUpdate = allowUpdate;
    }

    public boolean isAllowInsert() {
        return allowInsert;
    }

    public void setAllowInsert(boolean allowInsert) {
        this.allowInsert = allowInsert;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        IOrmEntityDao<IOrmEntity> dao = (IOrmEntityDao<IOrmEntity>) daoProvider.<IOrmEntity>dao(entityName);

        if (keyFields == null || keyFields.isEmpty()) {
            return new BatchProcessorConsumer<>(new OrmEntityBatchProcessor<>(dao), new OrmInsertBatchConsumer<>(ormTemplate));
        } else {
            return new OrmBatchConsumer<>(dao, keyFields, allowInsert, allowUpdate);
        }
    }
}
