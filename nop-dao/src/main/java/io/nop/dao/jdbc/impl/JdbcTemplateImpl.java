/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc.impl;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.cache.CacheRef;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.AbstractSqlExecutor;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.dao.dialect.pagination.IPaginationHandler;
import io.nop.dao.exceptions.JdbcException;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.dataset.JdbcComplexDataSet;
import io.nop.dao.jdbc.dataset.JdbcDataSet;
import io.nop.dao.jdbc.txn.IJdbcTransaction;
import io.nop.dao.metrics.IDaoMetrics;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dao.utils.DaoHelper;
import io.nop.dao.utils.DbEstimatedClock;
import io.nop.dataset.IComplexDataSet;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.impl.DataSetCacheData;
import io.nop.dataset.impl.DataSetCacheHelper;
import io.nop.dataset.rowmapper.SmartRowMapper;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.nop.dao.DaoConfigs.CFG_DAO_DB_TIME_CACHE_TIMEOUT;
import static io.nop.dao.DaoErrors.ARG_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ARG_TXN;
import static io.nop.dao.DaoErrors.ERR_DAO_QUERY_SPACE_NOT_JDBC_CONNECTION;
import static io.nop.dao.DaoErrors.ERR_DAO_UNKNOWN_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ERR_SQL_BAD_SQL_GRAMMAR;

public class JdbcTemplateImpl extends AbstractSqlExecutor implements IJdbcTemplate {
    static final Logger LOG = LoggerFactory.getLogger(JdbcTemplateImpl.class);

    private ITransactionTemplate transactionTemplate;
    private ICacheProvider cacheProvider;
    private IDaoMetrics daoMetrics;

    private IDialectProvider dialectProvider;

    private final Map<String, IEstimatedClock> clockMap = new ConcurrentHashMap<>();

    private final Map<String, ICache<Object, DataSetCacheData>> cacheMap = new ConcurrentHashMap<>();

    public void setCacheProvider(ICacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    public void setDialectProvider(IDialectProvider dialectProvider) {
        this.dialectProvider = dialectProvider;
    }

    @Override
    public ICacheProvider getCacheProvider() {
        return cacheProvider;
    }

    public void setDaoMetrics(IDaoMetrics daoMetrics) {
        this.daoMetrics = daoMetrics;
    }

    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ITransactionTemplate txn() {
        return transactionTemplate;
    }

    @Override
    public Connection currentConnection(String querySpace) {
        ITransaction txn = transactionTemplate.getRegisteredTransaction(querySpace);
        if (txn instanceof IJdbcTransaction)
            return ((IJdbcTransaction) txn).getConnection();
        return null;
    }

    @Override
    public IDaoMetrics getDaoMetrics() {
        return daoMetrics;
    }

    @Override
    public IDialect getDialectForQuerySpace(String querySpace) {
        if (dialectProvider != null)
            return dialectProvider.getDialectForQuerySpace(querySpace);
        return transactionTemplate.getDialectForQuerySpace(querySpace);
    }

    @Override
    public <T> IRowMapper<T> getDefaultRowMapper() {
        return (IRowMapper<T>) SmartRowMapper.INSTANCE;
    }

    @Override
    public <T> T runWithConnection(SQL sql, Function<Connection, T> callback) {
        return runWithConnection("jdbc.runWithConnection", sql, null, callback);
    }

    protected <T> T runWithConnection(String title, SQL sql, LongRangeBean range, Function<Connection, T> callback) {
        if (sql != null) {
            sql.dump(title);
        }

        String querySpace = getQuerySpace(sql);

        long beginTime = CoreMetrics.currentTimeMillis();
        // spring中事务和连接分别管理，导致情况比较复杂。这里的实现方式是扩展事务的概念，ITransaction仅仅代表可打开的事务，
        // 实际调用ITransaction.open()操作才会打开事务，从而将非事务状态下的Connection统一到Transaction概念下。
        //
        // supports级别的事务是仅仅创建transaction对象，但并没有要求一定调用open来打开事务
        T ret = withTxn(querySpace, callback);

        long diff = CoreMetrics.currentTimeMillis() - beginTime;
        LOG.info("nop.jdbc.run:usedTime={},querySpace={},range={},name={},sql={}", diff, querySpace, range,
                sql == null ? null : sql.getName(), sql == null ? null : sql.getText());
        return ret;
    }

    private <T> T withTxn(String querySpace, Function<Connection, T> callback) {
        T ret = transactionTemplate.runInTransaction(querySpace, TransactionPropagation.SUPPORTS, txn -> {
            if (!(txn instanceof IJdbcTransaction))
                throw new NopException(ERR_DAO_QUERY_SPACE_NOT_JDBC_CONNECTION).param(ARG_QUERY_SPACE, querySpace)
                        .param(ARG_TXN, txn);

            // 如果没有调用过transaction.open，则这里返回的连接实际上是非事务状态的。
            return callback.apply(((IJdbcTransaction) txn).getConnection());
        });
        return ret;
    }

    private String getQuerySpace(SQL sql) {
        if (sql == null)
            return DaoConstants.DEFAULT_QUERY_SPACE;
        String querySpace = sql.getQuerySpace();
        if (querySpace == null)
            return DaoConstants.DEFAULT_QUERY_SPACE;
        return querySpace;
    }

    @Override
    public void clearQueryCache() {
        Iterator<ICache<Object, DataSetCacheData>> it = cacheMap.values().iterator();
        while (it.hasNext()) {
            it.next().clear();
            it.remove();
        }
    }

    @Override
    public void clearQueryCacheFor(String cacheName) {
        ICache<Object, DataSetCacheData> cache = cacheMap.get(cacheName);
        if (cache != null)
            cache.clear();
    }

    @Override
    public void evictQueryCache(String cacheName, Serializable cacheKey) {
        ICache<Object, DataSetCacheData> cache = cacheMap.get(cacheName);
        if (cache != null)
            cache.remove(cacheKey);
    }

    @Override
    public long executeUpdate(SQL sql) {
        return runWithConnection("jdbc.executeUpdate", sql, null, conn -> {
            PreparedStatement st = null;
            RuntimeException error = null;
            IDialect dialect = getDialectForQuerySpace(sql.getQuerySpace());

            long count = -1;

            Object meter = daoMetrics == null ? null : daoMetrics.beginExecuteUpdate(sql);
            try {
                st = JdbcHelper.prepareStatement(dialect, conn, sql);
                JdbcHelper.setQueryTimeout(dialect, st, sql, true);

                if (dialect.isSupportExecuteLargeUpdate()) {
                    count = st.executeLargeUpdate();
                } else {
                    count = st.executeUpdate();
                }
                LOG.info("nop.jdbc.executeUpdate:count={},name={}", count, sql.getName());
                return count;
            } catch (SQLException e) {
                error = dialect.getSQLExceptionTranslator().translate(sql, e);
                throw error;
            } catch (RuntimeException e) {
                error = e;
                throw error;
            } finally {
                IoHelper.safeCloseObject(st);
                if (daoMetrics != null) {
                    daoMetrics.endExecuteUpdate(meter, count);
                }
            }
        });
    }

    @Override
    public <T> T executeStatement(@Nonnull SQL sql, LongRangeBean range, @Nonnull Function<IComplexDataSet, T> callback,
                                  ICancelToken cancelToken) {
        return runWithConnection("jdbc.executeStatement", sql, range, conn -> {
            PreparedStatement st = null;
            RuntimeException error = null;
            IDialect dialect = getDialectForQuerySpace(sql.getQuerySpace());

            ResultSet rs = null;
            long readCount = -1;
            boolean success = false;

            Object meter = daoMetrics == null ? null : daoMetrics.beginQuery(sql, range);
            try {
                st = JdbcHelper.prepareStatement(dialect, conn, sql);
                JdbcHelper.setQueryTimeout(dialect, st, sql, false);

                JdbcComplexDataSet ds = new JdbcComplexDataSet(st, dialect);
                if (cancelToken != null) {
                    cancelToken.appendOnCancelTask(ds::cancel);
                }

                ds.execute();
                T ret = callback.apply(ds);

                if (ds.isResultSet()) {
                    readCount = ds.getResultSet().getReadCount();
                } else {
                    readCount = ds.getUpdateCount();
                }
                LOG.info("nop.jdbc.executeStatement:count={},name={}", readCount, sql.getName());
                success = true;
                return ret;
            } catch (SQLException e) {
                error = dialect.getSQLExceptionTranslator().translate(sql, e);
                throw error;
            } catch (RuntimeException e) {
                error = e;
                throw error;
            } finally {
                IoHelper.safeCloseObject(rs);
                IoHelper.safeCloseObject(st);
                if (daoMetrics != null) {
                    daoMetrics.endQuery(meter, readCount, success);
                }
            }
        });
    }

    @Override
    public <T> T executeQuery(@Nonnull SQL sql, LongRangeBean range, @Nonnull Function<? super IDataSet, T> callback) {
        DataSetCacheData cacheData = getCacheData(sql, range);
        if (cacheData != null) {
            return callback.apply(DataSetCacheHelper.toDataSet(cacheData, true));
        }

        IDialect dialect = getDialectForQuerySpace(sql.getQuerySpace());
        SQL pagedSql = buildPagedSql(sql, range, dialect);

        return runWithConnection("jdbc.executeQuery", pagedSql, range, conn -> {
            PreparedStatement st = null;
            RuntimeException error = null;

            ResultSet rs = null;
            long readCount = -1;
            boolean success = false;

            Object meter = daoMetrics == null ? null : daoMetrics.beginQuery(pagedSql, range);
            try {
                st = JdbcHelper.prepareStatement(dialect, conn, pagedSql);
                JdbcHelper.setQueryTimeout(dialect, st, sql, false);

                rs = st.executeQuery();
                IDataSet ds = new JdbcDataSet(dialect, rs);
                // 检查是否需要缓存结果集
                ds = saveCacheData(ds, sql, range);

                T ret = callback.apply(ds);

                readCount = ds.getReadCount();
                LOG.info("nop.jdbc.executeQuery:count={},name={}", readCount, sql.getName());
                success = true;
                return ret;
            } catch (SQLException e) {
                error = dialect.getSQLExceptionTranslator().translate(pagedSql, e);
                throw error;
            } catch (RuntimeException e) {
                error = e;
                throw error;
            } finally {
                IoHelper.safeCloseObject(rs);
                IoHelper.safeCloseObject(st);
                if (daoMetrics != null) {
                    daoMetrics.endQuery(meter, readCount, success);
                }
            }
        });
    }

    private SQL buildPagedSql(SQL sql, LongRangeBean range, IDialect dialect) {
        if (range == null || range == FIND_FIRST_RANGE)
            return sql;
        IPaginationHandler pageHandler = dialect.getPaginationHandler();
        return pageHandler.getPagedSql(range, sql);
    }

    private DataSetCacheData getCacheData(SQL sql, LongRangeBean range) {
        if (cacheProvider == null)
            return null;

        CacheRef cacheRef = sql.getCacheRef();
        if (cacheRef == null)
            return null;

        ICache<Object, DataSetCacheData> cache = cacheMap.computeIfAbsent(cacheRef.getCacheName(),
                k -> cacheProvider.getCache(cacheRef.getCacheName()));

        DataSetCacheData cacheData = cache.get(cacheRef.getCacheKey());
        if (cacheData == null)
            return null;

        // 如果缓存数据对应的sql文本和分页参数与请求参数不一致，则返回null，不使用缓存数据
        if (!Objects.equals(range, cacheData.getRange())) {
            LOG.info("nop.dao.cache-get-data-range-mismatch:cacheName={},cacheKey={},sql={},range={},cacheRange={}",
                    cacheRef.getCacheName(), cacheRef.getCacheKey(), sql, range, cacheData.getRange());
            return null;
        }

        if (!sql.getText().equals(cacheData.getSql())) {
            LOG.error("nop.dao.cache-get-data-sql-mismatch:cacheName={},cacheKey={},sql={},cachedSql={}",
                    cacheRef.getCacheName(), cacheRef.getCacheKey(), sql, cacheData.getSql());
            return null;
        }
        return cacheData;
    }

    /**
     * 将数据集中的数据读取到缓存中，并根据缓存数据构造出只读的数据集返回
     *
     * @param ds    数据集
     * @param sql   数据集对应的SQL语句
     * @param range 数据集的分页参数
     * @return 缓存数据构成的只读数据集
     */
    private IDataSet saveCacheData(IDataSet ds, SQL sql, LongRangeBean range) {
        if (cacheProvider == null)
            return ds;

        CacheRef cacheRef = sql.getCacheRef();
        if (cacheRef == null)
            return null;

        ICache<Object, DataSetCacheData> cache = cacheMap.computeIfAbsent(cacheRef.getCacheName(),
                k -> cacheProvider.getCache(cacheRef.getCacheName()));

        DataSetCacheData cacheData = DataSetCacheHelper.toCacheData(ds);
        cacheData.setSql(sql.getText());
        cacheData.setRange(range);

        cache.put(cacheRef.getCacheKey(), cacheData);

        return DataSetCacheHelper.toDataSet(cacheData, true);
    }

    @Override
    public boolean existsTable(String querySpace, String tableName) {
        IDialect dialect = getDialectForQuerySpace(querySpace);
        SQL sql = SQL.begin().querySpace(querySpace).sql("select 1 from " + dialect.escapeSQLName(tableName)).end();
        try {
            findFirst(sql);
            return true;
        } catch (JdbcException e) {
            if (e.getErrorCode().equals(ERR_SQL_BAD_SQL_GRAMMAR.getErrorCode()))
                return false;
            throw NopException.adapt(e);
        }
    }

    @Override
    public Object callFunc(@Nonnull SQL sql) {
        Object ret = runWithConnection("jdbc.callFunc", sql, null, conn -> {
            IDialect dialect = getDialectForQuerySpace(sql.getQuerySpace());

            PreparedStatement st = null;
            RuntimeException error = null;
            try {
                st = JdbcHelper.prepareCallableStatement(dialect, conn, sql);
                JdbcHelper.setQueryTimeout(dialect, st, sql, true);

                long count;
                if (dialect.isSupportExecuteLargeUpdate()) {
                    count = st.executeLargeUpdate();
                } else {
                    count = st.executeUpdate();
                }
                LOG.info("nop.jdbc.callFunc:count={},name={}", count, sql.getName());
                return count;
            } catch (SQLException e) {
                error = dialect.getSQLExceptionTranslator().translate(sql, e);
                throw error;
            } catch (RuntimeException e) {
                error = e;
                throw error;
            } finally {
                IoHelper.safeClose(st);
            }
        });
        return ret;
    }

    @Override
    public Timestamp getDbCurrentTimestamp(String querySpace) {
        SQL sql = SQL.begin().querySpace(querySpace).sql("select current_timestamp() ").end();
        return runWithConnection("jdbc.getDbCurrentTimestamp", sql, null, conn -> {
            IDialect dialect = getDialectForQuerySpace(querySpace);
            String select = dialect.getCurrentTimestampSql();
            Statement st = null;
            ResultSet rs = null;
            try {
                st = conn.createStatement();
                rs = st.executeQuery(select);
                rs.next();
                return (Timestamp) DataParameterBinders.TIMESTAMP.getValue(new JdbcDataSet(dialect, rs), 0);
            } catch (SQLException e) {
                throw dialect.getSQLExceptionTranslator().translate(sql, e);
            } finally {
                IoHelper.safeCloseObject(rs);
                IoHelper.safeCloseObject(st);
            }
        });
    }

    @Override
    public boolean isQuerySpaceDefined(String querySpace) {
        return transactionTemplate.isQuerySpaceDefined(querySpace);
    }

    @Override
    public IEstimatedClock getDbEstimatedClock(String querySpace) {
        String normalized = DaoHelper.normalizeQuerySpace(querySpace);

        if (!isQuerySpaceDefined(normalized))
            throw new NopException(ERR_DAO_UNKNOWN_QUERY_SPACE).param(ARG_QUERY_SPACE, querySpace);

        IEstimatedClock clock = clockMap.computeIfAbsent(normalized, key -> {
            return new DbEstimatedClock(normalized, this, CFG_DAO_DB_TIME_CACHE_TIMEOUT);
        });
        return clock;
    }
}