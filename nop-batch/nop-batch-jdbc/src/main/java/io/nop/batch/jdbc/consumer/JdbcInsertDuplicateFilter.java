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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JdbcInsertDuplicateFilter<S> implements IBatchRecordHistoryStore<S> {
    private final IJdbcTemplate jdbcTemplate;
    private final String tableName;
    private final Map<String, IDataParameterBinder> pkBinders;

    public JdbcInsertDuplicateFilter(IJdbcTemplate jdbcTemplate, String tableName, Map<String, IDataParameterBinder> pkBinders) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.pkBinders = pkBinders;
    }

    @Override
    public List<S> filterProcessed(List<S> records, IBatchChunkContext context) {
        if (pkBinders.size() == 1) {
            String pkCol = CollectionHelper.first(pkBinders.keySet());
            IDataParameterBinder binder = pkBinders.get(pkCol);
            Map<String, S> keyMap = new LinkedHashMap<>();
            records.forEach(record -> {
                String key = ConvertHelper.toString(BeanTool.getProperty(record, pkCol));
                keyMap.put(key, record);
            });

            SQL sql = buildSelectByIdSql(records, pkCol, binder);
            List<String> list = jdbcTemplate.findAll(sql, StringColumnRowMapper.INSTANCE);
            keyMap.keySet().removeAll(list);
            return new ArrayList<>(keyMap.values());
        } else {
            Map<List<String>, S> keyMap = new LinkedHashMap<>();
            records.forEach(record -> {
                List<String> key = getCompositePk(record);
                keyMap.put(key, record);
            });

            SQL sql = buildSelectByCompositePkSql(records);
            List<List<String>> list = jdbcTemplate.findAll(sql, ListStringRowMapper.INSTANCE);
            keyMap.keySet().removeAll(list);
            return new ArrayList<>(keyMap.values());
        }
    }

    private SQL buildSelectByIdSql(List<S> records, String pkCol, IDataParameterBinder binder) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.select().append(pkCol);
        sb.from().sql(tableName);
        sb.where().sql(pkCol).append(" in ");
        sb.append('(');
        sb.forEach(",", records, (s, record) -> {
            Object value = BeanTool.getProperty(record, pkCol);
            value = binder.getStdDataType().convert(value);
            sb.typeParam(binder, value, false);
        });

        sb.append(')');
        return sb.end();
    }

    private List<String> getCompositePk(S record) {
        List<String> ret = new ArrayList<>(pkBinders.size());
        for (Map.Entry<String, IDataParameterBinder> entry : pkBinders.entrySet()) {
            String col = entry.getKey();
            Object value = BeanTool.getProperty(record, col);
            ret.add(StringHelper.toString(value, ""));
        }
        return ret;
    }

    private SQL buildSelectByCompositePkSql(List<S> records) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.select().fields(null, pkBinders.keySet());
        sb.from().sql(tableName);
        sb.where().append('(').fields(null, pkBinders.keySet()).append(") in ");
        sb.append('(');
        sb.forEach(",", records, this::appendIdCond);
        sb.append(')');
        return sb.end();
    }

    private void appendIdCond(SQL.SqlBuilder sb, S record) {
        sb.append('(');
        sb.forEach(",", pkBinders.entrySet(), (s, entry) -> {
            String pkCol = entry.getKey();
            IDataParameterBinder binder = entry.getValue();
            Object value = BeanTool.getProperty(record, pkCol);
            value = binder.getStdDataType().convert(value);
            sb.typeParam(binder, value, false);
        });
        sb.append(')');
    }

    @Override
    public void saveProcessed(List<S> filtered, Throwable exception, IBatchChunkContext context) {

    }
}
