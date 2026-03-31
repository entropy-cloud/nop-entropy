/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
import io.nop.commons.text.CharacterCase;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.AbstractSqlExecutor;
import io.nop.dao.api.QuerySpaceEnv;
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
import io.nop.dataset.IDataSetMeta;
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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.nop.dao.DaoConfigs.CFG_DAO_DB_TIME_CACHE_TIMEOUT;
import static io.nop.dao.DaoConfigs.CFG_ORM_ENABLE_DYNAMIC_QUERY_SPACE;
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
    public Connection openConnection(String querySpace) {
        return transactionTemplate.openConnection(querySpace);
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
        String dynamicQuerySpace = useDynamicQuerySpace(querySpace);
        T ret = transactionTemplate.runInTransaction(dynamicQuerySpace, TransactionPropagation.SUPPORTS, txn -> {
            if (!(txn instanceof IJdbcTransaction))
                throw new NopException(ERR_DAO_QUERY_SPACE_NOT_JDBC_CONNECTION).param(ARG_QUERY_SPACE, dynamicQuerySpace)
                        .param(ARG_TXN, txn);

            // 如果没有调用过transaction.open，则这里返回的连接实际上是非事务状态的。
            return callback.apply(((IJdbcTransaction) txn).getConnection());
        });
        return ret;
    }

    private String useDynamicQuerySpace(String querySpace) {
        if (!CFG_ORM_ENABLE_DYNAMIC_QUERY_SPACE.get())
            return querySpace;

        // 如果是缺省的querySpace，则尝试映射到动态querySpace
        if (DaoHelper.isDefaultQuerySpace(querySpace))
            return QuerySpaceEnv.getQuerySpaceOrDefault();
        return querySpace;
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
                    daoMetrics.endExecuteUpdate(sql, meter, count, error);
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
                    daoMetrics.endQuery(sql, meter, readCount, error);
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
                    daoMetrics.endQuery(sql, meter, readCount, error);
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
        return existsTable(querySpace, null, tableName);
    }

    @Override
    public boolean existsTable(String querySpace, String schemaName, String tableName) {
        String template = getExistsTemplate(querySpace, ExistsKind.TABLE);
        if (!StringHelper.isEmpty(template)) {
            Boolean ret = tryExistsByTemplate(querySpace, template, schemaName, tableName, null, null);
            if (ret != null) {
                return ret;
            }
        }

        if (!StringHelper.isEmpty(schemaName)) {
            return runWithConnection("jdbc.existsTable", null, null,
                    conn -> existsObjectByMeta(conn, querySpace, schemaName, tableName, "TABLE"));
        }

        IDialect dialect = getDialectForQuerySpace(querySpace);
        SQL sql = SQL.begin().querySpace(querySpace).sql("select 1 from ")
                .sql(dialect.escapeSQLName(tableName)).where().sql("1=0").end();
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
    public boolean existsColumn(String querySpace, String schemaName, String tableName, String columnName) {
        String template = getExistsTemplate(querySpace, ExistsKind.COLUMN);
        if (!StringHelper.isEmpty(template)) {
            Boolean ret = tryExistsByTemplate(querySpace, template, schemaName, tableName, columnName, null);
            if (ret != null) {
                return ret;
            }
        }

        return runWithConnection("jdbc.existsColumn", null, null,
                conn -> existsColumnByMeta(conn, querySpace, schemaName, tableName, columnName));
    }

    @Override
    public boolean existsIndex(String querySpace, String schemaName, String tableName, String indexName) {
        String template = getExistsTemplate(querySpace, ExistsKind.INDEX);
        if (!StringHelper.isEmpty(template)) {
            Boolean ret = tryExistsByTemplate(querySpace, template, schemaName, tableName, null, indexName);
            if (ret != null) {
                return ret;
            }
        }

        return runWithConnection("jdbc.existsIndex", null, null,
                conn -> existsIndexByMeta(conn, querySpace, schemaName, tableName, indexName));
    }

    @Override
    public boolean existsForeignKey(String querySpace, String schemaName, String tableName, String foreignKeyName) {
        String template = getExistsTemplate(querySpace, ExistsKind.FOREIGN_KEY);
        if (!StringHelper.isEmpty(template)) {
            Boolean ret = tryExistsByTemplate(querySpace, template, schemaName, tableName, null, foreignKeyName);
            if (ret != null) {
                return ret;
            }
        }

        return runWithConnection("jdbc.existsForeignKey", null, null,
                conn -> existsForeignKeyByMeta(conn, querySpace, schemaName, tableName, foreignKeyName));
    }

    @Override
    public boolean existsSequence(String querySpace, String schemaName, String sequenceName) {
        String template = getExistsTemplate(querySpace, ExistsKind.SEQUENCE);
        if (!StringHelper.isEmpty(template)) {
            Boolean ret = tryExistsByTemplate(querySpace, template, schemaName, null, null, sequenceName);
            if (ret != null) {
                return ret;
            }
        }

        return runWithConnection("jdbc.existsSequence", null, null,
                conn -> existsObjectByMeta(conn, querySpace, schemaName, sequenceName, "SEQUENCE"));
    }

    @Override
    public boolean existsView(String querySpace, String schemaName, String viewName) {
        String template = getExistsTemplate(querySpace, ExistsKind.VIEW);
        if (!StringHelper.isEmpty(template)) {
            Boolean ret = tryExistsByTemplate(querySpace, template, schemaName, null, null, viewName);
            if (ret != null) {
                return ret;
            }
        }

        return runWithConnection("jdbc.existsView", null, null,
                conn -> existsObjectByMeta(conn, querySpace, schemaName, viewName, "VIEW"));
    }

    private enum ExistsKind {
        TABLE,
        COLUMN,
        INDEX,
        FOREIGN_KEY,
        SEQUENCE,
        VIEW
    }

    private String getExistsTemplate(String querySpace, ExistsKind kind) {
        IDialect dialect = getDialectForQuerySpace(querySpace);
        if (dialect.getDialectModel() == null || dialect.getDialectModel().getSqls() == null)
            return null;

        switch (kind) {
            case TABLE:
                return dialect.getDialectModel().getSqls().getTableExists();
            case COLUMN:
                return dialect.getDialectModel().getSqls().getColumnExists();
            case INDEX:
                return dialect.getDialectModel().getSqls().getIndexExists();
            case FOREIGN_KEY:
                return dialect.getDialectModel().getSqls().getForeignKeyExists();
            case SEQUENCE:
                return dialect.getDialectModel().getSqls().getSequenceExists();
            case VIEW:
                return dialect.getDialectModel().getSqls().getViewExists();
            default:
                return null;
        }
    }

    private Boolean tryExistsByTemplate(String querySpace, String template,
                                        String schemaName, String tableName,
                                        String columnName, String objectName) {
        IDialect dialect = getDialectForQuerySpace(querySpace);
        try {
            String sqlText = StringHelper.renderTemplate(template, name -> {
                if (name.equals("schemaName"))
                    return sqlStringLiteral(dialect, normalizeTableName(dialect, schemaName));
                if (name.equals("tableName"))
                    return sqlStringLiteral(dialect, normalizeTableName(dialect, tableName));
                if (name.equals("columnName"))
                    return sqlStringLiteral(dialect, normalizeColumnName(dialect, columnName));
                if (name.equals("indexName"))
                    return sqlStringLiteral(dialect, normalizeTableName(dialect, objectName));
                if (name.equals("constraintName"))
                    return sqlStringLiteral(dialect, normalizeTableName(dialect, objectName));
                if (name.equals("foreignKeyName"))
                    return sqlStringLiteral(dialect, normalizeTableName(dialect, objectName));
                if (name.equals("sequenceName"))
                    return sqlStringLiteral(dialect, normalizeTableName(dialect, objectName));
                if (name.equals("viewName"))
                    return sqlStringLiteral(dialect, normalizeTableName(dialect, objectName));
                throw new IllegalArgumentException("unsupported exists template param:" + name);
            });

            SQL sql = SQL.begin().querySpace(querySpace).sql(sqlText).end();
            return exists(sql);
        } catch (RuntimeException e) {
            LOG.info("nop.jdbc.exists-template-fallback:querySpace={},error={}", querySpace, e.getMessage());
            LOG.debug("nop.jdbc.exists-template-fallback-detail", e);
            return null;
        }
    }

    private String sqlStringLiteral(IDialect dialect, String name) {
        if (name == null)
            return "null";
        return dialect.getStringLiteral(name);
    }

    private String normalizeTableName(IDialect dialect, String name) {
        return normalizeName(dialect.getTableNameCase(), name);
    }

    private String normalizeColumnName(IDialect dialect, String name) {
        return normalizeName(dialect.getColumnNameCase(), name);
    }

    private String normalizeName(CharacterCase charCase, String name) {
        if (StringHelper.isEmpty(name))
            return null;
        if (name.charAt(0) == '"')
            return StringHelper.unquoteDupEscapeString(name);
        if (charCase == null)
            return name;
        return charCase.normalize(name);
    }

    private boolean existsColumnByMeta(Connection conn, String querySpace, String schemaName,
                                       String tableName, String columnName) {
        if (StringHelper.isEmpty(tableName) || StringHelper.isEmpty(columnName))
            return false;

        IDialect dialect = getDialectForQuerySpace(querySpace);
        String normalizedSchema = normalizeTableName(dialect, schemaName);
        String normalizedTable = normalizeTableName(dialect, tableName);
        String normalizedColumn = normalizeColumnName(dialect, columnName);

        try {
            DatabaseMetaData meta = conn.getMetaData();
            for (String candidateTable : toCandidateNames(normalizedTable)) {
                for (String candidateColumn : toCandidateNames(normalizedColumn)) {
                    if (matchColumns(meta, conn.getCatalog(), normalizedSchema, candidateTable, candidateColumn))
                        return true;
                }
            }
            return false;
        } catch (SQLException e) {
            throw getDialectForQuerySpace(querySpace).getSQLExceptionTranslator()
                    .translate(SQL.begin().querySpace(querySpace).sql("/*meta:getColumns*/select 1").end(), e);
        }
    }

    private boolean existsIndexByMeta(Connection conn, String querySpace, String schemaName,
                                      String tableName, String indexName) {
        if (StringHelper.isEmpty(tableName) || StringHelper.isEmpty(indexName))
            return false;

        IDialect dialect = getDialectForQuerySpace(querySpace);
        String normalizedSchema = normalizeTableName(dialect, schemaName);
        String normalizedTable = normalizeTableName(dialect, tableName);
        String normalizedIndex = normalizeTableName(dialect, indexName);

        try {
            DatabaseMetaData meta = conn.getMetaData();
            for (String candidateTable : toCandidateNames(normalizedTable)) {
                try (ResultSet rs = meta.getIndexInfo(conn.getCatalog(), normalizedSchema, candidateTable, false, true)) {
                    while (rs.next()) {
                        String found = rs.getString("INDEX_NAME");
                        if (sameIdentifier(found, normalizedIndex))
                            return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw getDialectForQuerySpace(querySpace).getSQLExceptionTranslator()
                    .translate(SQL.begin().querySpace(querySpace).sql("/*meta:getIndexInfo*/select 1").end(), e);
        }
    }

    private boolean existsForeignKeyByMeta(Connection conn, String querySpace, String schemaName,
                                           String tableName, String foreignKeyName) {
        if (StringHelper.isEmpty(foreignKeyName))
            return false;

        IDialect dialect = getDialectForQuerySpace(querySpace);
        String normalizedSchema = normalizeTableName(dialect, schemaName);
        String normalizedTable = normalizeTableName(dialect, tableName);
        String normalizedForeignKey = normalizeTableName(dialect, foreignKeyName);

        try {
            DatabaseMetaData meta = conn.getMetaData();
            if (!StringHelper.isEmpty(normalizedTable)) {
                for (String candidateTable : toCandidateNames(normalizedTable)) {
                    if (matchImportedKeys(meta, conn.getCatalog(), normalizedSchema, candidateTable, normalizedForeignKey))
                        return true;
                }
                return false;
            }

            String[] tableTypes = new String[]{"TABLE"};
            try (ResultSet tables = meta.getTables(conn.getCatalog(), normalizedSchema, null, tableTypes)) {
                while (tables.next()) {
                    String foundTable = tables.getString("TABLE_NAME");
                    if (matchImportedKeys(meta, conn.getCatalog(), normalizedSchema, foundTable, normalizedForeignKey))
                        return true;
                }
            }
            return false;
        } catch (SQLException e) {
            throw getDialectForQuerySpace(querySpace).getSQLExceptionTranslator()
                    .translate(SQL.begin().querySpace(querySpace).sql("/*meta:getImportedKeys*/select 1").end(), e);
        }
    }

    private boolean existsObjectByMeta(Connection conn, String querySpace, String schemaName,
                                       String objectName, String type) {
        if (StringHelper.isEmpty(objectName))
            return false;

        IDialect dialect = getDialectForQuerySpace(querySpace);
        String normalizedSchema = normalizeTableName(dialect, schemaName);
        String normalizedName = normalizeTableName(dialect, objectName);

        try {
            DatabaseMetaData meta = conn.getMetaData();
            for (String candidateName : toCandidateNames(normalizedName)) {
                if (matchTables(meta, conn.getCatalog(), normalizedSchema, candidateName, type))
                    return true;
            }
            return false;
        } catch (SQLException e) {
            throw getDialectForQuerySpace(querySpace).getSQLExceptionTranslator()
                    .translate(SQL.begin().querySpace(querySpace).sql("/*meta:getTables*/select 1").end(), e);
        }
    }

    private boolean matchColumns(DatabaseMetaData meta, String catalog, String schema,
                                 String tableName, String columnName) throws SQLException {
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, columnName)) {
            return rs.next();
        }
    }

    private boolean matchImportedKeys(DatabaseMetaData meta, String catalog, String schema,
                                      String tableName, String foreignKeyName) throws SQLException {
        try (ResultSet rs = meta.getImportedKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                String found = rs.getString("FK_NAME");
                if (sameIdentifier(found, foreignKeyName))
                    return true;
            }
        }
        return false;
    }

    private boolean matchTables(DatabaseMetaData meta, String catalog, String schema,
                                String objectName, String type) throws SQLException {
        try (ResultSet rs = meta.getTables(catalog, schema, objectName, new String[]{type})) {
            return rs.next();
        }
    }

    private boolean sameIdentifier(String left, String right) {
        if (left == null || right == null)
            return false;
        return left.equals(right) || left.equalsIgnoreCase(right);
    }

    private List<String> toCandidateNames(String name) {
        Set<String> ret = new LinkedHashSet<>();
        ret.add(name);
        ret.add(name.toUpperCase());
        ret.add(name.toLowerCase());
        return new ArrayList<>(ret);
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

    @Override
    public IDataSetMeta getTableMeta(String querySpace, String tableName) {
        SQL sql = SQL.begin().querySpace(querySpace).select().star().from().sql(tableName).where().alwaysFalse().end();
        return executeQuery(sql, IDataSet::getMeta);
    }
}