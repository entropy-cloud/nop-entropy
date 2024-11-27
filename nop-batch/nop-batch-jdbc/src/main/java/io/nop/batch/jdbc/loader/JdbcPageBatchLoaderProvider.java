/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.loader;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.lang.sql.ISqlGenerator;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.INamedSqlBuilder;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.rowmapper.ColumnMapRowMapper;
import jakarta.inject.Inject;

import java.util.List;

public class JdbcPageBatchLoaderProvider<T> implements IBatchLoaderProvider<T> {
    private IJdbcTemplate jdbcTemplate;

    private ISqlGenerator sqlGenerator;

    private INamedSqlBuilder namedSqlBuilder;

    @SuppressWarnings("unchecked")
    private IRowMapper<T> rowMapper = (IRowMapper<T>) ColumnMapRowMapper.CASE_INSENSITIVE;


    public void setRowMapper(IRowMapper<T> rowMapper) {
        this.rowMapper = rowMapper;
    }

    @Inject
    public void setNamedSqlBuilder(INamedSqlBuilder sqlBuilder) {
        this.namedSqlBuilder = sqlBuilder;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setSqlGenerator(ISqlGenerator sqlGenerator) {
        Guard.notNull(sqlGenerator, "sqlGenerator");
        Guard.checkState(this.sqlGenerator == null, "sqlGenerator not allow change");
        this.sqlGenerator = sqlGenerator;
    }

    public void setSqlText(String sql) {
        setSql(SQL.begin().sql(sql).end());
    }

    public void setSql(SQL sql) {
        setSqlGenerator(ctx -> sql);
    }

    public void setSqlName(String sqlName) {
        setSqlGenerator(ctx -> namedSqlBuilder.buildSql(sqlName, ctx));
    }

    static class LoaderState {
        SQL sql;
        LongRangeBean range;
    }

    @Override
    public IBatchLoader<T> setup(IBatchTaskContext context) {
        LoaderState state = new LoaderState();
        state.sql = sqlGenerator.generateSql(context);

        return (batchSize, ctx) -> load(batchSize, ctx, state);
    }

    synchronized List<T> load(int batchSize, IBatchChunkContext context, LoaderState state) {
        LongRangeBean range = state.range;
        if (range == null) {
            range = LongRangeBean.longRange(0, batchSize);
        } else {
            range = LongRangeBean.longRange(range.getEnd(), batchSize);
        }
        state.range = range;

        return jdbcTemplate.findPage(state.sql, range.getOffset(), (int) range.getLimit(), rowMapper);
    }
}
