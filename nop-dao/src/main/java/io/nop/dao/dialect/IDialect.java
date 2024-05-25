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
import java.util.Set;

public interface IDialect extends IComponentModel {
    String getName();

    DialectModel getDialectModel();

    SqlDataTypeModel getNativeType(String sqlTypeName);

    SQLDataType stdToNativeSqlType(StdSqlType sqlType, int precision, int scale);

    IDataTypeHandler getGeometryTypeHandler();

    IDataTypeHandler getJsonTypeHandler();

    ISQLExceptionTranslator getSQLExceptionTranslator();

    IPaginationHandler getPaginationHandler();

    int getMaxStringSize();

    int getMaxBytesSize();

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

    CharacterCase getTableNameCase();

    CharacterCase getColumnNameCase();

    default String getInsertKeyword() {
        return "insert";
    }

    default String getUpdateKeyword() {
        return "update";
    }

    default  String getIntersectKeyword(){
        return "intersect";
    }

    default  String getExceptKeyword(){
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