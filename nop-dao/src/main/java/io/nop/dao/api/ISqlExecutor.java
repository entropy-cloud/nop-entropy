/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.api;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.cache.ICacheProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.IComplexDataSet;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.rowmapper.IgnoreAllExtractor;
import io.nop.dataset.rowmapper.RowMapperAllExtractor;
import io.nop.dataset.rowmapper.SingleBinderRowMapper;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.dao.utils.SqlExecHelper;

import jakarta.annotation.Nonnull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 统一{@link io.nop.dao.jdbc.IJdbcTemplate}和IOrmTemplate的公共基类
 */
public interface ISqlExecutor extends IDialectProvider {

    IDialect getDialectForQuerySpace(String querySpace);

    /**
     * 清空所有查询数据缓存
     */
    void clearQueryCache();

    /**
     * 清空指定查询缓存
     */
    void clearQueryCacheFor(String cacheName);

    void evictQueryCache(String cacheName, Serializable cacheKey);

    ICacheProvider getCacheProvider();

    /**
     * 判断数据是否存在
     */
    boolean exists(SQL sql);

    /**
     * 查找到第一条记录
     */
    default <T> T findFirst(SQL sql) {
        return findFirst(sql, getDefaultRowMapper());
    }

    /**
     * 返回满足条件的所有数据
     */
    default <T> List<T> findAll(final SQL sql) {
        return findAll(sql, getDefaultRowMapper());
    }

    /**
     * 将findFirst的返回结果转型为long
     *
     * @param sql          待执行的SQL对象
     * @param defaultValue 如果没有找到数据，则返回缺省值
     */
    default Long findLong(final SQL sql, final Long defaultValue) {
        Long value = (Long) findFirst(sql, new SingleBinderRowMapper(DataParameterBinders.LONG));
        if (value == null)
            return defaultValue;
        return value;
    }

    /**
     * 将findFirst的返回结果转型为String类型
     *
     * @param sql          待执行的SQL对象
     * @param defaultValue 如果没有找到数据，则返回缺省值
     */
    default String findString(final SQL sql, final String defaultValue) {
        String value = (String) findFirst(sql, new SingleBinderRowMapper(DataParameterBinders.STRING));
        if (value == null)
            return defaultValue;
        return value;
    }

    /**
     * 将findFirst的返回结果转型为int类型
     *
     * @param sql          待执行的SQL对象
     * @param defaultValue 如果没有找到数据，则返回缺省值
     */
    default Integer findInt(final SQL sql, final Integer defaultValue) {
        Integer value = (Integer) findFirst(sql, new SingleBinderRowMapper(DataParameterBinders.INT));
        if (value == null)
            return defaultValue;
        return value;
    }

    /**
     * 将findFirst的返回结果转型为String类型
     *
     * @param sql          待执行的SQL对象
     * @param defaultValue 如果没有找到数据，则返回缺省值
     */
    default Double findDouble(final SQL sql, final Double defaultValue) {
        Double value = (Double) findFirst(sql, new SingleBinderRowMapper(DataParameterBinders.DOUBLE));
        if (value == null)
            return defaultValue;
        return value;
    }

    /**
     * 将findFirst的返回结果转型为LocalDate类型
     *
     * @param sql          待执行的SQL对象
     * @param defaultValue 如果没有找到数据，则返回缺省值
     */
    default LocalDate findLocalDate(final SQL sql, final LocalDate defaultValue) {
        LocalDate value = (LocalDate) findFirst(sql, new SingleBinderRowMapper(DataParameterBinders.DATE));
        if (value == null)
            return defaultValue;
        return value;
    }

    default LocalDateTime findLocalDateTime(final SQL sql, final LocalDateTime defaultValue) {
        LocalDateTime value = (LocalDateTime) findFirst(sql, new SingleBinderRowMapper(DataParameterBinders.DATETIME));
        if (value == null)
            return defaultValue;
        return value;
    }

    default Timestamp findTimestamp(final SQL sql, final Timestamp defaultValue) {
        Timestamp value = (Timestamp) findFirst(sql, new SingleBinderRowMapper(DataParameterBinders.TIMESTAMP));
        if (value == null)
            return defaultValue;
        return value;
    }

    default BigDecimal findDecimal(final SQL sql, final BigDecimal defaultValue) {
        BigDecimal value = (BigDecimal) findFirst(sql, new SingleBinderRowMapper(DataParameterBinders.DECIMAL));
        if (value == null)
            return defaultValue;
        return value;
    }

    <T> IRowMapper<T> getDefaultRowMapper();

    /**
     * 分页获取数据
     *
     * @param sql    待执行的SQL语句
     * @param offset 从0开始
     * @param limit  最多取多少条
     */
    default <T> List<T> findPage(final SQL sql, final long offset, final int limit) {
        return findPage(sql, offset, limit, getDefaultRowMapper());
    }

    default <T> List<T> findAll(final SQL sql, IRowMapper<T> rowMapper) {
        return executeQuery(sql, null, new RowMapperAllExtractor<>(rowMapper));
    }

    default <T> List<T> findPage(final SQL sql, final long offset, final int limit, IRowMapper<T> rowMapper) {
        return executeQuery(sql, new LongRangeBean(offset, limit), new RowMapperAllExtractor<>(rowMapper));
    }

    <T> T findFirst(SQL sql, IRowMapper<T> rowMapper);

    default <T> T executeQuery(@Nonnull final SQL sql, @Nonnull final Function<? super IDataSet, T> callback) {
        return executeQuery(sql, null, callback);
    }

    /**
     * 执行SQL语句并忽略它的返回结果。相当于是执行预加载操作，将数据预取到缓存中
     */
    default void prefetch(@Nonnull final SQL sql) {
        executeQuery(sql, IgnoreAllExtractor.INSTANCE);
    }

    /**
     * 允许一次性执行多条更新语句，更新条目数将累加在一起返回
     *
     * @param sql 可以是通过;分隔的多条SQL语句
     * @return 更新总条目数
     */
    default long executeMultiSql(SQL sql) {
        return SqlExecHelper.executeMultiSql(this, sql);
    }

    /**
     * 执行sql语句，返回受影响的记录条目数
     */
    long executeUpdate(final SQL sql);

    <T> T executeQuery(@Nonnull final SQL sql, final LongRangeBean range,
                       @Nonnull final Function<? super IDataSet, T> callback);

    <T> T executeStatement(@Nonnull SQL sql, LongRangeBean range, @Nonnull Function<IComplexDataSet, T> callback,
                           ICancelToken cancelToken);
}