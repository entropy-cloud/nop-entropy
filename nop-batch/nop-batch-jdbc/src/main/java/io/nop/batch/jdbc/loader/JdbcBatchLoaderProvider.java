/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.loader;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.sql.ISqlGenerator;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.INamedSqlBuilder;
import io.nop.dao.DaoErrors;
import io.nop.dao.api.IQueryBuilder;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.dataset.JdbcDataSet;
import io.nop.dao.jdbc.impl.JdbcHelper;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.impl.DefaultFieldMapper;
import io.nop.dataset.record.impl.RecordInputImpls;
import io.nop.dataset.rowmapper.ColumnMapRowMapper;
import io.nop.orm.dao.DaoQueryHelper;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public class JdbcBatchLoaderProvider<T> implements IBatchLoaderProvider<T> {
    static final Logger LOG = LoggerFactory.getLogger(JdbcBatchLoaderProvider.class);

    private IJdbcTemplate jdbcTemplate;
    private String querySpace;

    private ISqlGenerator sqlGenerator;

    private IQueryBuilder queryBuilder;
    private String partitionIndexField;

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

    public IQueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    public void setQueryBuilder(IQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    public String getPartitionIndexField() {
        return partitionIndexField;
    }

    public void setPartitionIndexField(String partitionIndexField) {
        this.partitionIndexField = partitionIndexField;
    }

    public String getQuerySpace() {
        return querySpace;
    }

    public void setQuerySpace(String querySpace) {
        this.querySpace = querySpace;
    }

    public void setTransformer(BiFunction<T, IBatchChunkContext, T> transformer) {
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

        private boolean closed;

        /**
         * 1.7-C7-009（plan 2546 W-05）：连接级失效标记。一旦置位，后续 load 必须重抛同一异常，
         * <b>不能</b>返回空列表——空列表会被上层当作 EOF 静默截断任务（数据丢失面）。
         */
        private boolean broken;

        private RuntimeException brokenError;

        public synchronized void markBroken(RuntimeException err) {
            if (!broken) {
                this.broken = true;
                this.brokenError = err;
            }
        }

        public synchronized RuntimeException getBrokenError() {
            return broken ? brokenError : null;
        }

        public synchronized void close() {
            closed = true;
            IoHelper.safeCloseObject(ps);
            IoHelper.safeCloseObject(dataSet);
            if (closeConnection)
                IoHelper.safeCloseObject(connection);
        }

        public synchronized boolean isClosed() {
            return closed;
        }
    }

    @Override
    public IBatchLoader<T> setup(IBatchTaskContext context) {
        LoaderState state = newState(context);
        context.onAfterComplete(err -> state.close());
        return (batchSize, ctx) -> {
            synchronized (state) {
                return load(batchSize, ctx, state);
            }
        };
    }

    LoaderState newState(IBatchTaskContext context) {
        ISqlGenerator sqlGenerator = this.sqlGenerator;
        if (sqlGenerator == null) {
            if (sqlName != null && namedSqlBuilder != null)
                sqlGenerator = ctx -> namedSqlBuilder.buildSql(sqlName, ctx);
        }

        if (sqlGenerator == null && queryBuilder != null) {
            QueryBean query = queryBuilder.buildQuery(context);
            if (query == null)
                query = new QueryBean();

            IntRangeSet partitions = context.getPartitionRange();

            // 自动增加分区条件
            if (partitionIndexField != null && partitions != null) {
                query.addPartitionFilter(partitions, partitionIndexField);
            }

            QueryBean fixedQuery = query;
            sqlGenerator = ctx -> {
                SQL.SqlBuilder sb = DaoQueryHelper.queryToSelectFieldsSql(
                        jdbcTemplate.getDialectForQuerySpace(querySpace), fixedQuery, null);
                return sb.end();
            };
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
            } else if (fetchSize != null) {
                // C5-002（plan 2516 裁定⑤）：坏门修复——原 `sql.getFetchSize() != -1 && fetchSize != null`
                // 中 query/sql 文本路径 SQL 对象 fetchSize 恒 -1 使 && 门恒 false，DSL 配置的 fetchSize
                // 被静默丢弃（驱动 executeQuery 全量缓冲结果集）。sql 层 fetchSize 已由 JdbcHelper 处理，
                // provider 层只做独立兜底。
                ps.setFetchSize(fetchSize);
            } else {
                // C5-002（plan 2516 裁定⑤）：streaming=false 且未配置 fetchSize 的路径显式可观测——
                // 驱动全量缓冲结果集是 GB 级 OOM 风险面，不允许静默。
                LOG.warn("nop.err.batch.jdbc-fetch-size-unset:sql={}", sql.getText());
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

    List<T> load(int batchSize, IBatchChunkContext context, LoaderState state) {
        // 1.7-C7-009（plan 2546 W-05）：broken 检查必须**先于** isClosed() 早退——连接失效后
        // close() 会置 closed，若先判 isClosed 就会走"EOF 空列表"分支把故障静默成正常结束。
        RuntimeException broken = state.getBrokenError();
        if (broken != null)
            throw broken;

        // concurrency>1且无dispatch包装时，多个消费线程共享同一LoaderState。线程A读到空数据后会close状态，
        // 兄弟线程B随后进入load时不能再触碰已关闭的ResultSet/PreparedStatement，直接返回空列表表示EOF
        if (state.isClosed())
            return Collections.emptyList();

        List<T> list;
        try {
            list = RecordInputImpls.defaultReadBatch(state.dataSet, batchSize,
                    row -> {
                        T data = rowMapper.mapRow(row, -1, DefaultFieldMapper.INSTANCE);
                        if (transformer != null)
                            return transformer.apply(data, context);
                        return data;
                    });
        } catch (RuntimeException e) {
            // 1.7-C7-009（plan 2546 W-05）：连接级失效判定（双轨）——
            // ① cause 链中的 SQLException：SQLState 前缀 08，或 SQLRecoverable/SQLTransientConnection/
            //    SQLNonTransientConnection 异常类型；
            // ② 方言映射码：transient-connection-fail / recoverable-data-access，或 data-access
            //    **且** cause 为 SQLNonTransientConnectionException。
            // **不收** ERR_SQL_UNCATEGORIZED_FAILURE —— 它是所有未映射 SQL 错误的兜底，收它会把
            // 「磁盘满/业务 SQL 错误」永久 poison 掉重试机会；PG 连接失效的 08006 由 ① 承接。
            if (isConnectionFailure(e)) {
                state.markBroken(e);
                state.close();
            }
            throw e;
        }
        if (list.isEmpty()) {
            state.close();
        }
        return list;
    }

    /**
     * 1.7-C7-009（plan 2546 W-05）：连接级失效判定。见 {@code load} 内注释的双轨规则。
     */
    static boolean isConnectionFailure(Throwable err) {
        SQLException sqlEx = null;
        for (Throwable cur = err; cur != null; cur = cur.getCause()) {
            if (cur instanceof SQLException) {
                sqlEx = (SQLException) cur;
                break;
            }
        }

        if (sqlEx != null) {
            String sqlState = sqlEx.getSQLState();
            if (sqlState != null && sqlState.startsWith("08"))
                return true;
            if (sqlEx instanceof SQLRecoverableException
                    || sqlEx instanceof SQLTransientConnectionException
                    || sqlEx instanceof SQLNonTransientConnectionException)
                return true;
        }

        if (err instanceof NopException) {
            String code = ((NopException) err).getErrorCode();
            if (DaoErrors.ERR_SQL_TRANSIENT_CONNECTION_FAIL.getErrorCode().equals(code)
                    || DaoErrors.ERR_SQL_RECOVERABLE_DATA_ACCESS.getErrorCode().equals(code))
                return true;
            if (DaoErrors.ERR_SQL_DATA_ACCESS.getErrorCode().equals(code)
                    && sqlEx instanceof SQLNonTransientConnectionException)
                return true;
        }
        return false;
    }
}
