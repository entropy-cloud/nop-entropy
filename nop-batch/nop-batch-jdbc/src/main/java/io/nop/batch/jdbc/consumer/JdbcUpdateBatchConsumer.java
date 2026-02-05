/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.JdbcBatcher;
import io.nop.dataset.binder.IDataParameterBinder;

import java.util.Collection;
import java.util.Map;

public class JdbcUpdateBatchConsumer<S> implements IBatchConsumerProvider<S>, IBatchConsumer<S> {
    private final IJdbcTemplate jdbcTemplate;
    private final IDialect dialect;
    private final String tableName;
    private final Collection<String> keyFields;

    private final Map<String, IDataParameterBinder> colBinders;
    private final Map<String, String> fromNameMap;

    public JdbcUpdateBatchConsumer(IJdbcTemplate jdbcTemplate, IDialect dialect, String tableName,
                                   Collection<String> keyFields,
                                   Map<String, IDataParameterBinder> colBinders,
                                   Map<String, String> fromNameMap) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
        this.tableName = tableName;
        this.keyFields = keyFields;
        this.colBinders = colBinders;
        this.fromNameMap = fromNameMap;
    }

    @Override
    public IBatchConsumer<S> setup(IBatchTaskContext context) {
        return this;
    }

    @Override
    public void consume(Collection<S> items, IBatchChunkContext context) {
        SQL sql = SQL.begin().name("batch-update").insertInto(tableName).end();

        jdbcTemplate.runWithConnection(sql, conn -> {
            JdbcBatcher batcher = new JdbcBatcher(conn, dialect, jdbcTemplate.getDaoMetrics());
            batcher.setForceTxn(true);
            for (S item : items) {
                SQL itemSql = buildSql(item);
                batcher.addCommand(itemSql, false, null);
            }
            batcher.flush();
            return null;
        });

        context.getTaskContext().incUpdateCount(items.size());
    }

    SQL buildSql(S record) {
        SQL.SqlBuilder sb = SQL.begin().name("update:" + tableName);
        sb.update(tableName);
        sb.set();
        boolean first = true;
        for (String colName : colBinders.keySet()) {
            String fromName = getFromName(colName);
            if (!BeanTool.hasProperty(record, fromName))
                continue;

            if (keyFields.contains(colName))
                continue;

            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            Object value = BeanTool.getProperty(record, fromName);
            sb.eq(colName, value);
        }
        sb.where();
        first = true;
        for (String keyField : keyFields) {
            if (first) {
                first = false;
            } else {
                sb.and();
            }
            sb.append(keyField).append('=');
            IDataParameterBinder binder = this.colBinders.get(keyField);
            appendParam(sb, binder, BeanTool.getProperty(record, getFromName(keyField)));
        }
        return sb.end();
    }

    String getFromName(String key) {
        if (fromNameMap == null)
            return key;
        String from = fromNameMap.get(key);
        if (from == null)
            return key;
        return from;
    }

    void appendParam(SQL.SqlBuilder sb, IDataParameterBinder binder, Object value) {
        value = binder.getStdDataType().convert(value);
        sb.typeParam(binder, value, false);
    }
}
