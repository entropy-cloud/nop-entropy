/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.loader;

import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.sql.ISqlGenerator;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.INamedSqlBuilder;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.dataset.JdbcDataSet;
import io.nop.dao.jdbc.impl.JdbcHelper;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.impl.DefaultFieldMapper;
import io.nop.dataset.record.impl.RecordInputImpls;
import io.nop.dataset.rowmapper.ColumnMapRowMapper;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.function.BiFunction;

public class JdbcBatchLoaderProvider<T> implements IBatchLoaderProvider<T> {
    private IJdbcTemplate jdbcTemplate;
    private String querySpace;

    private ISqlGenerator sqlGenerator;

    private INamedSqlBuilder namedSqlBuilder;

    private String sqlName;

    @SuppressWarnings("unchecked")
    private IRowMapper<T> rowMapper = (IRowMapper<T>) ColumnMapRowMapper.CASE_INSENSITIVE;

    private BiFunction<T, IBatchChunkContext, T> transformer;

    private long maxRows;

    private int maxFieldSize;

    private Integer fetchSize;

    private boolean streaming;

    private int queryTimeout;

    public String getQuerySpace() {
        return querySpace;
    }

    public void setQuerySpace(String querySpace) {
        this.querySpace = querySpace;
    }

    public void setTransformer(BiFunction<T,IBatchChunkContext,T> transformer){
        this.transformer = transformer;
    }

    public int getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(int queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public void setFetchSize(Integer fetchSize) {
        this.fetchSize = fetchSize;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }


    public void setRowMapper(IRowMapper<T> rowMapper) {
        this.rowMapper = rowMapper;
    }

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
        this.sqlName = sqlName;
    }

    public long getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(long maxRows) {
        this.maxRows = maxRows;
    }

    public void setMaxFieldSize(int maxFieldSize) {
        this.maxFieldSize = maxFieldSize;
    }

    static class LoaderState {
        SQL sql;
        IDialect dialect;
        Connection connection;
        boolean closeConnection;

        IDataSet dataSet;

        PreparedStatement ps;

        public void close() {
            IoHelper.safeCloseObject(ps);
            IoHelper.safeCloseObject(dataSet);
            if (closeConnection)
                IoHelper.safeCloseObject(connection);
        }

    }

    @Override
    public IBatchLoader<T> setup(IBatchTaskContext context) {
        LoaderState state = newState(context);
        context.onAfterComplete(err -> state.close());
        return (batchSize, ctx) -> load(batchSize, ctx, state);
    }

    LoaderState newState(IBatchTaskContext context) {
        ISqlGenerator sqlGenerator = this.sqlGenerator;
        if (sqlGenerator == null) {
            if (sqlName != null)
                sqlGenerator = ctx -> namedSqlBuilder.buildSql(sqlName, ctx);
        }
        Guard.notNull(sqlGenerator, "sqlGenerator");


        SQL sql = sqlGenerator.generateSql(context);
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(querySpace);
        boolean closeConnection = false;
        Connection connection = jdbcTemplate.currentConnection(querySpace);
        if (connection == null) {
            connection = jdbcTemplate.openConnection(querySpace);
            closeConnection = true;
        }


        try {
            PreparedStatement ps = JdbcHelper.prepareStatement(dialect, connection, sql);
            if (queryTimeout > 0) {
                if (dialect.isSupportQueryTimeout()) {
                    ps.setQueryTimeout((queryTimeout + 999) / 1000);
                }
            } else {
                JdbcHelper.setQueryTimeout(dialect, ps, sql, false);
            }

            if (streaming && dialect.getStreamingFetchSize() != null) {
                ps.setFetchSize(dialect.getStreamingFetchSize());
            } else {
                if (sql.getFetchSize() != -1 && fetchSize != null)
                    ps.setFetchSize(fetchSize);
            }

            if (maxRows > 0) {
                if (maxRows < Integer.MAX_VALUE) {
                    ps.setMaxRows((int) maxRows);
                } else {
                    ps.setLargeMaxRows(maxRows);
                }
            }

            if (maxFieldSize > 0)
                ps.setMaxFieldSize(maxFieldSize);

            ResultSet rs = ps.executeQuery();
            IDataSet dataSet = new JdbcDataSet(dialect, rs);

            LoaderState state = new LoaderState();
            state.dialect = dialect;
            state.sql = sql;
            state.ps = ps;
            state.dataSet = dataSet;
            state.connection = connection;
            state.closeConnection = closeConnection;
            return state;
        } catch (SQLException e) {
            if (closeConnection)
                IoHelper.safeCloseObject(connection);
            throw dialect.getSQLExceptionTranslator().translate(sql, e);
        }
    }

    synchronized List<T> load(int batchSize, IBatchChunkContext context, LoaderState state) {
        List<T> list = RecordInputImpls.defaultReadBatch(state.dataSet, batchSize,
                row -> {
                    T data = rowMapper.mapRow(row, -1, DefaultFieldMapper.INSTANCE);
                    if (transformer != null)
                        return transformer.apply(data, context);
                    return data;
                });
        if (list.isEmpty()) {
            state.close();
        }
        return list;
    }
}
