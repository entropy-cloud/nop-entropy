/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc.impl;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.text.CharacterCase;
import io.nop.commons.text.marker.Marker;
import io.nop.commons.text.marker.Markers;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.TypedValueMarker;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dao.DaoConfigs;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.impl.BaseDataFieldMeta;
import io.nop.dataset.impl.BaseDataSetMeta;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.pagination.IPaginationHandler;
import io.nop.dao.jdbc.dataset.JdbcStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import static io.nop.api.core.ApiErrors.ERR_CONTEXT_TIMEOUT;
import static io.nop.dao.DaoErrors.ARG_SQL;

public class JdbcHelper {
    static final Logger LOG = LoggerFactory.getLogger(JdbcHelper.class);

    public static void loadAllDrivers() {
        // 装载所有JDBC Driver
        final ServiceLoader<Driver> drivers = ServiceLoader.load(Driver.class);
        final Iterator<Driver> iterator = drivers.iterator();
        while (iterator.hasNext()) {
            try {
                // load the driver
                iterator.next();
            } catch (Throwable t) {
                // ignore
                LOG.debug("nop.jdbc.load-driver-fail", t);
            }
        }
    }

    public static boolean getAutoCommit(Connection conn, IDialect dialect) {
        try {
            return conn.getAutoCommit();
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("getAutoCommit", e);
        }
    }

    public static void setAutoCommit(Connection conn, boolean autoCommit, IDialect dialect) {
        try {
            conn.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("setAutoCommit", e);
        }
    }

    public static void rollback(Connection conn, IDialect dialect) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("rollback", e);
        }
    }

    public static void commit(Connection conn, IDialect dialect) {
        try {
            conn.commit();
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("commit", e);
        }
    }

    public static IDataSetMeta getDataSetMeta(ResultSetMetaData metadata, CharacterCase tableNameLowerCase,
                                              CharacterCase columnNameLowerCase) throws SQLException {
        return new BaseDataSetMeta(getColumnMetas(metadata, tableNameLowerCase, columnNameLowerCase));
    }

    public static List<BaseDataFieldMeta> getColumnMetas(ResultSetMetaData metadata, CharacterCase tableNameLowerCase,
                                                         CharacterCase columnNameLowerCase) throws SQLException {
        int colCount = metadata.getColumnCount();
        List<BaseDataFieldMeta> ret = new ArrayList<>(colCount);
        for (int i = 0; i < colCount; i++) {
            ret.add(getColumnMeta(metadata, i + 1, tableNameLowerCase, columnNameLowerCase));
        }
        return ret;
    }

    static String normalize(String str, CharacterCase charCase) {
        if (charCase != null) {
            if (charCase == CharacterCase.lower) {
                return str.toLowerCase();
            } else if (charCase == CharacterCase.upper) {
                return str.toUpperCase();
            }
        }
        return str;
    }

    static BaseDataFieldMeta getColumnMeta(ResultSetMetaData metadata, int column, CharacterCase tableNameLowerCase,
                                           CharacterCase columnNameCase) throws SQLException {
        String tableName = metadata.getTableName(column);
        // 无论是否使用别名，是否用反引号包裹起来，H2数据库获得的列名总是大写的
        String name = metadata.getColumnLabel(column);
        String sourceName = metadata.getColumnName(column);
        if (name == null)
            name = sourceName;

        if (name.equals(sourceName)) {
            sourceName = normalize(sourceName, columnNameCase);
            name = sourceName;
        } else {
            sourceName = normalize(sourceName, columnNameCase);
            name = normalize(name, columnNameCase);
        }

        tableName = normalize(tableName, tableNameLowerCase);
        StdSqlType sqlType = StdSqlType.fromJdbcType(metadata.getColumnType(column));
        StdDataType dataType = sqlType == null ? StdDataType.ANY : sqlType.getStdDataType();
        return new BaseDataFieldMeta(name, sourceName, tableName, dataType,false);
    }

    public static CallableStatement prepareCallableStatement(IDialect dialect, Connection conn, SQL sql)
            throws SQLException {

        CallableStatement st = conn.prepareCall(sql.getText());
        try {
            st.registerOutParameter(1, Types.VARCHAR);
            setParameters(dialect, st, sql);
        } catch (SQLException e) {
            IoHelper.safeCloseObject(st);
            throw e;
        }
        return st;
    }

    public static PreparedStatement prepareStatement(IDialect dialect, Connection conn, SQL sql, LongRangeBean range)
            throws SQLException {

        PreparedStatement ps = null;

        try {
            if (range != null && !range.isEmpty()) {
                IPaginationHandler pageHandler = dialect.getPaginationHandler();
                sql = pageHandler.getPagedSql(range, sql);
                sql.dump("pagedSql:");

                ps = conn.prepareStatement(sql.getText());
                pageHandler.prepareStatement(range, ps);
            } else {
                // sqlserver的 next value for xxx语法不允许设置maxRows
                // ps.setMaxRows(1);
                ps = conn.prepareStatement(sql.getText());
            }

            if (sql.getFetchSize() > 0)
                ps.setFetchSize(sql.getFetchSize());

            setParameters(dialect, ps, sql);
        } catch (SQLException e) {
            IoHelper.safeCloseObject(ps);
            throw e;
        }
        return ps;
    }

    public static PreparedStatement prepareStatement(IDialect dialect, Connection conn, SQL sql) throws SQLException {

        PreparedStatement ps = conn.prepareStatement(sql.getText());

        try {
            if (sql.getFetchSize() > 0)
                ps.setFetchSize(sql.getFetchSize());

            setParameters(dialect, ps, sql);
        } catch (SQLException e) {
            IoHelper.safeClose(ps);
            throw e;
        }
        return ps;
    }

    public static void setQueryTimeout(IDialect dialect, PreparedStatement ps, SQL sql, boolean update)
            throws SQLException {
        if (dialect.isSupportQueryTimeout()) {
            int seconds = JdbcHelper.getQueryTimeout(sql, update);
            if (seconds > 0)
                ps.setQueryTimeout(seconds);
        }
    }

    public static int getMaxTimeout(boolean update) {
        if (update)
            return DaoConfigs.CFG_DAO_MAX_UPDATE_TIMEOUT.get();
        return DaoConfigs.CFG_DAO_MAX_QUERY_TIMEOUT.get();
    }

    /**
     * 计算指定的SQL超时时间和上下文超时时间的最小值，转换为单位秒之后返回
     *
     * @param sql SQL对象
     * @return 单位为秒
     */
    public static int getQueryTimeout(SQL sql, boolean update) {
        long timeout = sql.getTimeout();
        if (timeout > 0) {
            int maxTimeout = getMaxTimeout(update);
            if (timeout > maxTimeout)
                timeout = maxTimeout;
        }

        long expireTime = ContextProvider.currentContext().getCallExpireTime();
        if (expireTime > 0) {
            long contextTimeout = expireTime - CoreMetrics.currentTimeMillis();
            if (contextTimeout <= 0)
                throw new NopTimeoutException(ERR_CONTEXT_TIMEOUT, true).param(ARG_SQL, sql);
            // 如果sql本身没有设置超时时间或者sql指定的超时时间小于上下文超时时间
            if (timeout < 0 || timeout > contextTimeout)
                timeout = contextTimeout;
        }
        if (timeout > 0) {
            return (int) ((timeout + 999) / 1000);
        }
        return -1;
    }

    public static void setParameters(IDialect dialect, PreparedStatement ps, SQL sql) throws SQLException {
        if (sql.getMarkers().isEmpty())
            return;

        JdbcStatement jst = new JdbcStatement(ps, dialect);
        int index = 0;
        for (Marker marker : sql.getMarkers()) {
            if (marker instanceof TypedValueMarker) {
                TypedValueMarker valueMarker = (TypedValueMarker) marker;
                IDataParameterBinder binder = valueMarker.getBinder();
                binder.setValue(jst, index, valueMarker.getValue());
                index++;
            } else if (marker instanceof Markers.ValueMarker) {
                Object value = ((Markers.ValueMarker) marker).getValue();
                dialect.jdbcSet(ps, index, value);
                index++;
            } else if (marker instanceof Markers.ProviderMarker) {
                Object value = ((Markers.ProviderMarker) marker).getValue();
                dialect.jdbcSet(ps, index, value);
                index++;
            }
        }
    }

    public static void setParameters(IDialect dialect, PreparedStatement ps, List<Object> params) throws SQLException {
        setParameters(dialect, ps, 0, params);
    }

    public static void setParameters(IDialect dialect, PreparedStatement ps, int index, List<Object> params)
            throws SQLException {

        for (int i = 0, n = params.size(); i < n; ++i) {
            dialect.jdbcSet(ps, index + i, params.get(i));
        }
    }
}