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
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.dataset.JdbcDataSet;
import io.nop.dao.jdbc.impl.JdbcHelper;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.impl.DefaultFieldMapper;
import io.nop.dataset.record.impl.RecordInputImpls;
import io.nop.dataset.rowmapper.ColumnMapRowMapper;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class JdbcBatchLoader<T> implements IBatchLoaderProvider<T> {
    private DataSource dataSource;

    private ISqlGenerator sqlGenerator;

    private INamedSqlBuilder namedSqlBuilder;

    private String sqlName;

    @SuppressWarnings("unchecked")
    private IRowMapper<T> rowMapper = (IRowMapper<T>) ColumnMapRowMapper.CASE_INSENSITIVE;

    private long maxRows;

    private int maxFieldsSize;


    public void setRowMapper(IRowMapper<T> rowMapper) {
        this.rowMapper = rowMapper;
    }

    @Inject
    public void setNamedSqlBuilder(INamedSqlBuilder sqlBuilder) {
        this.namedSqlBuilder = sqlBuilder;
    }

    @Inject
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
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

    public void setMaxFieldsSize(int maxFieldsSize) {
        this.maxFieldsSize = maxFieldsSize;
    }

    static class LoaderState {
        SQL sql;
        IDialect dialect;
        Connection connection;

        IDataSet dataSet;

        PreparedStatement ps;

        public void close() {
            IoHelper.safeCloseObject(ps);
            IoHelper.safeCloseObject(dataSet);
            IoHelper.safeCloseObject(connection);
        }

    }

    @Override
    public IBatchLoader<T> setup(IBatchTaskContext context) {
        LoaderState state = newState(context);
        context.addAfterComplete(err -> state.close());
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
        IDialect dialect = DialectManager.instance().getDialectForDataSource(dataSource);
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("nop.err.jdbc.open-connection", e);
        }


        try {
            PreparedStatement ps = JdbcHelper.prepareStatement(dialect, connection, sql);
            JdbcHelper.setQueryTimeout(dialect, ps, sql, false);

            if (maxRows > 0) {
                if (maxRows < Integer.MAX_VALUE) {
                    ps.setMaxRows((int) maxRows);
                } else {
                    ps.setLargeMaxRows(maxRows);
                }
            }

            if (maxFieldsSize > 0)
                ps.setMaxFieldSize(maxFieldsSize);

            ResultSet rs = ps.executeQuery();
            IDataSet dataSet = new JdbcDataSet(dialect, rs);


            LoaderState state = new LoaderState();
            state.dialect = dialect;
            state.sql = sql;
            state.ps = ps;
            state.dataSet = dataSet;
            return state;
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate(sql, e);
        }
    }

    synchronized List<T> load(int batchSize, IBatchChunkContext context, LoaderState state) {
        return RecordInputImpls.defaultReadBatch(state.dataSet, batchSize,
                row -> rowMapper.mapRow(row, -1, DefaultFieldMapper.INSTANCE));
    }
}
