/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.time.IEstimatedClock;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.commons.cache.ICacheProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.metrics.IDaoMetrics;
import io.nop.dataset.IComplexDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.IRowMapper;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 测试用捕获型 jdbc 模板 fake（仅服务 plan 2554 载体：SQL 文本 / querySpace / 参数捕获与
 * 可编程应答）。未覆盖的 JDBC 面命中即显式失败（无静默），与 FakeEntityModelFacade 同族。
 */
public final class CapturingJdbcTemplate implements IJdbcTemplate {

    /** 已执行的 SQL 捕获（按执行序）。 */
    public final List<SQL> executedSql = new ArrayList<>();
    private final IJdbcTemplate delegate;

    public CapturingJdbcTemplate(IJdbcTemplate delegate) {
        this.delegate = delegate;
    }

    /** 可编程应答：按 SQL 文本前缀匹配返回行（null = 未命中）。 */
    private Responder responder;

    public interface Responder {
        List<Map<String, Object>> answer(SQL sql);
    }

    public void setResponder(Responder responder) {
        this.responder = responder;
    }

    public List<SQL> executed() {
        return executedSql;
    }

    @Override
    public <T> List<T> findAll(SQL sql) {
        executedSql.add(sql);
        if (responder == null)
            throw new UnsupportedOperationException("no responder configured for: " + sql.getText());
        @SuppressWarnings("unchecked")
        List<T> rows = (List<T>) responder.answer(sql);
        return rows;
    }

    @Override
    public <T> List<T> findAll(SQL sql, IRowMapper<T> rowMapper) {
        return findAll(sql);
    }

    @Override
    public Long findLong(SQL sql, Long defaultValue) {
        executedSql.add(sql);
        return defaultValue;
    }

    // ------------------------------------------------------------------
    // 元数据/方言面：委托真实模板（方言转义与分页装配仍走真实实现）
    // ------------------------------------------------------------------

    @Override
    public IDialect getDialectForQuerySpace(String querySpace) {
        return delegate.getDialectForQuerySpace(querySpace);
    }

    @Override
    public IDaoMetrics getDaoMetrics() {
        return delegate.getDaoMetrics();
    }

    @Override
    public boolean existsTable(String querySpace, String tableName) {
        return true;
    }

    @Override
    public boolean isQuerySpaceDefined(String querySpace) {
        return true;
    }

    @Override
    public IDataSetMeta getTableMeta(String querySpace, String tableName) {
        return delegate.getTableMeta(querySpace, tableName);
    }

    // ------------------------------------------------------------------
    // 未覆盖面：显式失败
    // ------------------------------------------------------------------

    @Override
    public void clearQueryCache() {
        // no-op
    }

    @Override
    public void clearQueryCacheFor(String cacheName) {
        // no-op
    }

    @Override
    public void evictQueryCache(String cacheName, java.io.Serializable cacheKey) {
        // no-op
    }

    @Override
    public ICacheProvider getCacheProvider() {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.getCacheProvider");
    }

    @Override
    public boolean exists(SQL sql) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.exists");
    }

    @Override
    public <T> IRowMapper<T> getDefaultRowMapper() {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.getDefaultRowMapper");
    }

    @Override
    public <T> T findFirst(SQL sql, IRowMapper<T> rowMapper) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.findFirst");
    }

    @Override
    public long executeUpdate(SQL sql) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.executeUpdate");
    }

    @Override
    public <T> T executeQuery(SQL sql, LongRangeBean range, Function<? super io.nop.dataset.IDataSet, T> callback) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.executeQuery");
    }

    @Override
    public <T> T executeStatement(SQL sql, LongRangeBean range,
                                  Function<IComplexDataSet, T> callback, ICancelToken cancelToken) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.executeStatement");
    }

    @Override
    public java.sql.Connection currentConnection(String querySpace) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.currentConnection");
    }

    @Override
    public java.sql.Connection openConnection(String querySpace) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.openConnection");
    }

    @Override
    public Timestamp getDbCurrentTimestamp(String querySpace) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.getDbCurrentTimestamp");
    }

    @Override
    public IEstimatedClock getDbEstimatedClock(String querySpace) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.getDbEstimatedClock");
    }

    @Override
    public Object callFunc(SQL sql) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.callFunc");
    }

    @Override
    public <T> T runWithConnection(SQL sql, java.util.function.Function<java.sql.Connection, T> callback) {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.runWithConnection");
    }

    @Override
    public io.nop.dao.txn.ITransactionTemplate txn() {
        throw new UnsupportedOperationException("fake CapturingJdbcTemplate.txn");
    }
}
