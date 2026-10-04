/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.api.core.convert.IByteArrayView;
import io.nop.api.core.util.IComponentModel;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.text.CharacterCase;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.exception.ISQLExceptionTranslator;
import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.dao.dialect.lock.LockOption;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlDataTypeModel;
import io.nop.dao.dialect.pagination.IPaginationHandler;
import io.nop.dataset.binder.IDataParameterBinder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Set;

public interface IDialect extends IComponentModel {
    String getName();

    DialectModel getDialectModel();

    default SqlDataTypeModel getNativeType(String sqlTypeName) {
        return getNativeType(sqlTypeName, false);
    }

    SqlDataTypeModel getNativeType(String sqlTypeName, boolean ignoreUnknown);

    SQLDataType stdToNativeSqlType(StdSqlType sqlType, int precision, int scale);

    IDataTypeHandler getGeometryTypeHandler();

    IDataTypeHandler getJsonTypeHandler();

    ISQLExceptionTranslator getSQLExceptionTranslator();

    IPaginationHandler getPaginationHandler();

    int getMaxStringSize();

    int getMaxBytesSize();

    Integer getStreamingFetchSize();

    boolean isSupportExecuteLargeUpdate();

    boolean isSupportQueryTimeout();

    boolean isSupportLargeMaxRows();

    /**
     * 是否支持jdbc的batchUpdate操作
     *
     * @return
     */
    boolean isSupportBatchUpdate();

    boolean isSupportBatchUpdateCount();

    boolean isSupportSavePoint();

    boolean isSupportTransaction();

    boolean isSupportTruncateTable();

    boolean isSupportUpdateTableAlias();

    boolean isSupportDeleteTableAlias();

    boolean isSupportRowValueConstructor();

    boolean isSupportWithAsClause();

    boolean isSupportWindowFrameRows();

    boolean isSupportWindowFrameRange();

    boolean isSupportWindowFrameGroups();

    /**
     * Plan 2308 D1：数据库是否接受原生 {@code ORDER BY ... NULLS FIRST/LAST} 语法。
     *
     * <p>这是空序 emission 的<b>唯一</b>判据：true → 直接输出 NULLS 子句；
     * false → 用 {@code CASE WHEN col IS NULL THEN ... END, col <dir>} 改写，
     * 该写法对任何方言都语义正确。
     *
     * <p>未声明（{@code null}）一律视为 false——这是安全的保守缺省：
     * false 只是让 SQL 多一个 CASE 键，不会产出错误结果。
     *
     * <p><b>不</b>提供"方言缺省空序"查询接口：单个布尔无法表达各方言的真实空序模型
     * （H2/PG/MySQL 随方向翻转，DuckDB 不翻转且有 4 种模式），
     * 用它推导 DESC 缺省会静默返回相反行序。详见 design §3.8。
     */
    boolean isSupportNullsFirst();

    /**
     * sqlserver 和mysql 支持 update t set x = xx from t , t2语法
     *
     * @return
     */
    boolean isSupportUpdateFromJoin();

    boolean isSupportSequence();

    boolean isSupportILike();

    boolean isSupportSomeSubQuery();

    boolean isSupportReturningForUpdate();

    /**
     * h2数据库的Date类型读取存在问题。1899以前的日期按照Date读取会少一天，例如1899-02-03会变成1899-02-02。所以需要改成按照字符串格式读取
     */
    boolean isUseGetStringForDate();

    boolean isUseAsInFrom();

    CharacterCase getTableNameCase();

    CharacterCase getColumnNameCase();

    default String normalizeTableName(String tableName) {
        CharacterCase charCase = getTableNameCase();
        if (charCase != null) {
            if (tableName.charAt(0) != '"') {
                String name = charCase.normalize(tableName);
                return escapeSQLName(name);
            }
        }
        return escapeSQLName(tableName);
    }

    default String normalizeColumnName(String colName) {
        CharacterCase charCase = getColumnNameCase();
        if (charCase != null) {
            if (colName.charAt(0) != '"') {
                String name = charCase.normalize(colName);
                return escapeSQLName(name);
            }
        }
        return colName;
    }

    default String getInsertKeyword() {
        return "insert";
    }

    default String getUpdateKeyword() {
        return "update";
    }

    default String getIntersectKeyword() {
        return "intersect";
    }

    default String getExceptKeyword() {
        return "except";
    }

    String getCreateSequenceSql(String sequenceName, long initialValue, int incrementSize);

    String getSequenceNextValSql(String sequenceName);

    String getDropSequenceSql(String sequenceName);

    String getSelectFromDualSql(String fields);

    String getCurrentTimestampSql();

    /**
     * from table_name {lockHint}。在from table_name后面追加的关于锁的sql语句。
     * 为了兼容各类数据库的锁语法。getLockHintSql和getForUpdateSql这两个函数需要配合使用
     *
     * @param lockOption 锁参数
     */
    String getLockHintSql(LockOption lockOption);

    /**
     * select xxx from x where y for update。 在整个select语句最后追加的for update语句
     *
     * @param lockOption 锁参数
     */
    String getForUpdateSql(LockOption lockOption);

    String getDropTableSql(String tableName, boolean ifExists);

    /**
     * 判断某个名称是否是数据库中的keyword, 从而必须要用``把它括起来
     *
     * @param name
     * @return
     */
    boolean isReservedKeyword(String name);

    String escapeSQLName(String name);

    String unescapeSQLName(String name);

    String getBooleanValueLiteral(boolean value);

    String getDateTimeLiteral(LocalDateTime value);

    String getDateLiteral(LocalDate value);

    String getTimeLiteral(LocalTime value);

    String getTimestampLiteral(Timestamp value);

    String getBitValueLiteral(String value);

    String getHexValueLiteral(ByteString value);

    String getStringLiteral(String value);

    String getValueLiteral(Object value);

    /**
     * 数据库中的数据类型与java程序中的数据类型可能不一致，由binder负责转换
     *
     * @param stdType java程序中的数据类型
     * @param sqlType 数据库中的数据类型
     * @return 内置转换功能的binder
     */
    IDataParameterBinder getDataParameterBinder(StdDataType stdType, StdSqlType sqlType);

    ISQLFunction getFunction(String fnName);

    Set<String> getFunctionNames();

    Object jdbcGet(ResultSet rs, int index);

    String jdbcGetString(ResultSet rs, int index);

    void jdbcSet(ResultSet rs, int index, Object value);

    void jdbcSetClob(ResultSet rs, int index, String value);

    void jdbcSetBlob(ResultSet rs, int index, IByteArrayView bytes);

    void jdbcSet(PreparedStatement ps, int index, Object value);

    void jdbcSetClob(PreparedStatement ps, int index, String clob);

    void jdbcSetBlob(PreparedStatement ps, int index, IByteArrayView bytes);
}