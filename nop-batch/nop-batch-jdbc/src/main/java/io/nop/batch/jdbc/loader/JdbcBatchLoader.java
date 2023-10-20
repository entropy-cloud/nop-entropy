/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.jdbc.loader;

import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;
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

public class JdbcBatchLoader<T> implements IBatchLoader<T, IBatchChunkContext>, IBatchTaskListener {
    private DataSource dataSource;

    private ISqlGenerator sqlGenerator;

    private INamedSqlBuilder namedSqlBuilder;

    private IRowMapper<T> rowMapper = (IRowMapper<T>) ColumnMapRowMapper.CASE_INSENSITIVE;

    private SQL sql;

    private Connection connection;

    private IDialect dialect;

    private IDataSet dataSet;

    private PreparedStatement ps;

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
        setSqlGenerator(ctx -> namedSqlBuilder.buildSql(sqlName, ctx));
    }

    @Override
    public void onTaskBegin(IBatchTaskContext context) {
        this.sql = sqlGenerator.generateSql(context);
        this.dialect = DialectManager.instance().getDialectForDataSource(dataSource);
        try {
            this.connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("nop.err.jdbc.open-connection", e);
        }


        try {
            ps = JdbcHelper.prepareStatement(dialect, connection, sql);
            JdbcHelper.setQueryTimeout(dialect, ps, sql, false);

            ResultSet rs = ps.executeQuery();
            this.dataSet = new JdbcDataSet(dialect, rs);
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate(sql, e);
        }
    }

    @Override
    public void onTaskEnd(Throwable exception, IBatchTaskContext context) {
        this.sql = null;
        IoHelper.safeCloseObject(ps);
        this.ps = null;
        IoHelper.safeCloseObject(dataSet);
        this.dataSet = null;
        IoHelper.safeCloseObject(connection);
        this.connection = null;
    }

    @Override
    public synchronized List<T> load(int batchSize, IBatchChunkContext context) {
        return RecordInputImpls.defaultReadBatch(dataSet, batchSize,
                row -> rowMapper.mapRow(row, -1, DefaultFieldMapper.INSTANCE));
    }
}
