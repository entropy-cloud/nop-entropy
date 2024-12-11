/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.consumer;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchRecordHistoryStore;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.rowmapper.ListStringRowMapper;
import io.nop.dataset.rowmapper.StringColumnRowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JdbcKeyDuplicateFilter<S> implements IBatchRecordHistoryStore<S> {
    static final Logger LOG = LoggerFactory.getLogger(JdbcKeyDuplicateFilter.class);

    private final IJdbcTemplate jdbcTemplate;
    private final String tableName;
    private final Map<String, IDataParameterBinder> keyBinders;

    public JdbcKeyDuplicateFilter(IJdbcTemplate jdbcTemplate, String tableName, Map<String, IDataParameterBinder> keyBinders) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.keyBinders = keyBinders;
    }

    @Override
    public Collection<S> filterProcessed(Collection<S> records, IBatchChunkContext context) {
        if (keyBinders.size() == 1) {
            String keyCol = CollectionHelper.first(keyBinders.keySet());
            IDataParameterBinder binder = keyBinders.get(keyCol);
            Map<String, S> keyMap = new LinkedHashMap<>();
            records.forEach(record -> {
                String key = ConvertHelper.toString(BeanTool.getProperty(record, keyCol));
                keyMap.put(key, record);
            });

            SQL sql = buildSelectByKeySql(records, keyCol, binder);
            List<String> list = jdbcTemplate.findAll(sql, StringColumnRowMapper.INSTANCE);
            if (!list.isEmpty())
                LOG.info("nop.batch.jdbc.filter-records:table={},ids={}", tableName, StringHelper.join(list, ","));

            keyMap.keySet().removeAll(list);
            return new ArrayList<>(keyMap.values());
        } else {
            Map<List<String>, S> keyMap = new LinkedHashMap<>();
            records.forEach(record -> {
                List<String> key = getCompositeKey(record);
                keyMap.put(key, record);
            });

            SQL sql = buildSelectByCompositeKeySql(records);
            List<List<String>> list = jdbcTemplate.findAll(sql, ListStringRowMapper.INSTANCE);
            if (!list.isEmpty())
                LOG.info("nop.batch.jdbc.filter-records-with-multi-key:table={},ids={}", tableName, StringHelper.join(list, ","));

            keyMap.keySet().removeAll(list);
            return new ArrayList<>(keyMap.values());
        }
    }

    private SQL buildSelectByKeySql(Collection<S> records, String keyCol, IDataParameterBinder binder) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.select().append(keyCol);
        sb.from().sql(tableName);
        sb.where().sql(keyCol).append(" in ");
        sb.append('(');
        sb.forEach(",", records, (s, record) -> {
            Object value = BeanTool.getProperty(record, keyCol);
            value = binder.getStdDataType().convert(value);
            sb.typeParam(binder, value, false);
        });

        sb.append(')');
        return sb.end();
    }

    private List<String> getCompositeKey(S record) {
        List<String> ret = new ArrayList<>(keyBinders.size());
        for (Map.Entry<String, IDataParameterBinder> entry : keyBinders.entrySet()) {
            String col = entry.getKey();
            Object value = BeanTool.getProperty(record, col);
            ret.add(StringHelper.toString(value, ""));
        }
        return ret;
    }

    private SQL buildSelectByCompositeKeySql(Collection<S> records) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.select().fields(null, keyBinders.keySet());
        sb.from().sql(tableName);
        sb.where().append('(').fields(null, keyBinders.keySet()).append(") in ");
        sb.append('(');
        sb.forEach(",", records, this::appendIdCond);
        sb.append(')');
        return sb.end();
    }

    private void appendIdCond(SQL.SqlBuilder sb, S record) {
        sb.append('(');
        sb.forEach(",", keyBinders.entrySet(), (s, entry) -> {
            String pkCol = entry.getKey();
            IDataParameterBinder binder = entry.getValue();
            Object value = BeanTool.getProperty(record, pkCol);
            value = binder.getStdDataType().convert(value);
            sb.typeParam(binder, value, false);
        });
        sb.append(')');
    }

    @Override
    public void saveProcessed(Collection<S> filtered, Throwable exception, IBatchChunkContext context) {

    }
}
