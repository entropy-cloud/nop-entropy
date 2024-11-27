package io.nop.batch.jdbc.consumer;

import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchRecordHistoryStore;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.consumer.EmptyBatchConsumer;
import io.nop.batch.core.consumer.WithHistoryBatchConsumer;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcDataSetHelper;
import io.nop.dataset.IDataFieldMeta;
import io.nop.dataset.binder.IDataParameterBinder;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class JdbcBatchConsumerProvider<R> implements IBatchConsumerProvider<R> {
    private IJdbcTemplate jdbcTemplate;
    private String querySpace;
    private String tableName;

    private List<? extends IDataFieldMeta> fields;
    private Collection<String> keyFields;
    private boolean allowUpdate;
    private boolean allowInsert;

    public String getQuerySpace() {
        return querySpace;
    }

    public void setQuerySpace(String querySpace) {
        this.querySpace = querySpace;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public List<? extends IDataFieldMeta> getFields() {
        return fields;
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

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setFields(List<? extends IDataFieldMeta> fields) {
        this.fields = fields;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        if (fields == null || fields.isEmpty()) {
            fields = jdbcTemplate.getTableMeta(querySpace, tableName).getFieldMetas();
        }

        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(querySpace);

        Map<String, IDataParameterBinder> colBinders = JdbcDataSetHelper.getColBinders(fields, dialect);
        if (keyFields == null || keyFields.isEmpty()) {
            return new JdbcInsertBatchConsumer<>(jdbcTemplate, dialect, tableName, colBinders);
        } else {
            Map<String, IDataParameterBinder> keyBinders = new CaseInsensitiveMap<>();
            for (String keyField : keyFields) {
                keyBinders.put(keyField, colBinders.get(keyField));
            }

            IBatchRecordHistoryStore<R> historyStore = new JdbcKeyDuplicateFilter<>(jdbcTemplate, tableName, keyBinders);
            IBatchConsumer<R> insertConsumer = allowInsert ? new JdbcInsertBatchConsumer<>(jdbcTemplate, dialect, tableName, colBinders) : EmptyBatchConsumer.instance();
            IBatchConsumer<R> updateConsumer = allowUpdate ? new JdbcUpdateBatchConsumer<>(jdbcTemplate, dialect, tableName, keyFields, colBinders) : null;

            WithHistoryBatchConsumer<R> consumer = new WithHistoryBatchConsumer<>(historyStore, insertConsumer, updateConsumer);
            return consumer;
        }
    }


}
