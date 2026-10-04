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
     * WI1 BUG-2: 是否支持 `from (select ...) x lateral (select ...) y` 形式的 LATERAL
     * 子查询表源。未配置时返回 false（保守缺省）：EQL 编译期对含 lateral 的查询报
     * `nop.err.eql.dialect-not-support-feature`，而不是静默丢弃 lateral 关键字
     * （那会退化为不相关的子查询，语义改变）。
     */
    boolean isSupportLateral();

    /**
     * WI7b（plan 37，roadmap EC ①）：是否支持 GROUP BY 位置的函数式 {@code ROLLUP(...)}。
     * 未配置时返回 false（保守缺省）：EQL 编译期报 nop.err.eql.dialect-not-support-feature
     * （feature=rollup）。分立于 {@link #isSupportCube()}——mysql 函数式 ROLLUP 8.4.3+ 才有
     * （超 G-1 基线 8.0.31）而 CUBE 永无，successor 触发条件不同。非 GROUP BY 位置的
     * rollup(x) 不经本门（函数表永不注册 → unknown-function）。
     */
    boolean isSupportRollup();

    /**
     * WI7b（plan 37，roadmap EC ①）：是否支持 GROUP BY 位置的 {@code CUBE(...)}。
     * 未配置时返回 false（保守缺省）：EQL 编译期报 nop.err.eql.dialect-not-support-feature
     * （feature=cube）。MySQL/MariaDB 全版本均无 CUBE（roadmap ledger 永久口径）。
     */
    boolean isSupportCube();

    /**
     * WI2 BUG-6: 是否支持 `full join`/`full outer join` 表源。已确认无 FULL JOIN 的
     * 只有 MySQL/MariaDB（TDengine 自 3.3.0.0 起支持、MSSQL 支持 FULL OUTER JOIN，
     * 继承 default 的 true）。未配置或配置为 false 时 EQL 编译期对含 full [outer] join
     * 的查询报 `nop.err.eql.dialect-not-support-feature`
     * （feature=supportFullJoin），而不是静默产出目标库不支持的 SQL。
     */
    boolean isSupportFullJoin();

    /**
     * WI10b（Phase 0 §2.5 终裁）：是否支持 `right join`/`right outer join` 表源。
     * 全部既有方言 XML 继承 default 的 true（RIGHT JOIN 为 MySQL 8.0.31/PG/H2/MSSQL/
     * Oracle/DuckDB 等全支持的标准能力）。配置为 false 时 EQL 编译期对含 right [outer]
     * join 的查询报 `nop.err.eql.dialect-not-support-feature`
     * （feature=supportRightJoin），而不是静默产出目标库不支持的 SQL。
     */
    boolean isSupportRightJoin();

    /**
     * WI10b（Phase 0 §2.4 终裁）：是否支持多表 DELETE（`DELETE t1 FROM t1 JOIN t2 …` /
     * `DELETE FROM t1 USING t2 …`）。EQL 文法当前为单表 DELETE（多表形式解析失败=响亮），
     * 本标志为方言能力声明：未来 DELETE 多表文法 WI 立项（plan-first）时必须接线本标志。
     * 配置断言见 `TestDialectDeadFlagDisposition`。
     */
    boolean isSupportDeleteFromJoin();

    /**
     * WI10b（Phase 0 §2.7 终裁=候选 B）：是否支持 `UPDATE … OUTPUT inserted./deleted. …`
     * 子句（MSSQL 形态，唯一 XML 载体 mssql 配 true）。v1 无运行时消费点——EQL 无任何
     * 语句产出 OUTPUT 子句，本标志为能力声明；升级触发条件：EQL 立项 MSSQL OUTPUT
     * 语义（文法 WI，plan-first）时必须消费本标志。注意与
     * {@link #isSupportReturningForUpdate()} 正交：mssql 上 `update..returning` 仍被后者
     * 拒绝（mssql 未配 returning）。
     */
    boolean isSupportOutputForUpdate();

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
     * 默认 NULL 排序事实：缺省升序排序时 NULL 是否排在非 NULL 之前。
     *
     * <p>与 {@link #isSupportNullsFirst()}（是否原生接受 <code>NULLS FIRST/LAST</code> 子句，
     * accessor 归 WI7）正交，各司其职：本标志描述方言的缺省行为事实，后者描述子句接受度。
     * 未配置时返回 false（保守缺省）。
     *
     * <p>⚠️ 消费点唯一 owner = roadmap WI7（NULLS FIRST/LAST 文法接受 + 门控 + 默认序行为）；
     * WI3 交付止于 accessor + 配置正确性断言，生成器消费不得在 WI3 声称完成
     * （plan 20「defaultNullsFirst 消费点调研结论」的移交链）。**WI7 已兑现**：消费点 =
     * {@code AstToSqlGenerator.visitSqlOrderByItem} 缺省序推导（一手文档准入集，裁定文档 §三）。
     */
    boolean isDefaultNullsFirst();

    /**
     * WI7（roadmap EC ③b，WI3 移交链另一半收口）：是否原生接受 <code>NULLS FIRST/LAST</code>
     * 子句。与 {@link #isDefaultNullsFirst()}（缺省 NULL 序事实）正交，各司其职：
     * 本标志描述子句接受度，后者描述缺省行为。未配置时返回 false（保守缺省）。
     *
     * <p>消费点 = {@code AstToSqlGenerator.visitSqlOrderByItem} 的子句输出唯一门：
     * true 时按请求打印子句；false 时仅当显式请求与该方向缺省序一致（且方言缺省序已有一手
     * 文档准入，见 WI7 裁定文档 §三）才省略子句，否则编译期报
     * {@code nop.err.eql.dialect-not-support-feature}（feature=nullsFirst(first|last)），
     * 不静默丢请求。
     */
    boolean isSupportNullsFirst();

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

    /**
     * INTERSECT 集合算子的输出关键字。值面契约（WI3 版本门，实现类读
     * {@code <sqls>} 的 intersectKeyword 配置）：null = 未配置（回落缺省 "intersect"）；
     * "none" = 方言无 INTERSECT 算子（EQL 编译含 INTERSECT [ ALL] 的查询时显式报错，
     * 不会静默输出目标库不支持的算子）；其他非空值 = 直接用作关键字。
     */
    default String getIntersectKeyword() {
        return "intersect";
    }

    /**
     * EXCEPT 集合算子的输出关键字。值面契约与 {@link #getIntersectKeyword()} 一致
     * （null 回落 "except"；"none" = 方言无该算子，编译期报错；如 Oracle 配置 minus）。
     */
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
     * WI1 BUG-5 骨架：INTERVAL 字面量的数据库层输出模板（`<sqls>` 的
     * `sqlIntervalLiteral` 元素）。返回已配置的模板文本，未配置返回 null。
     * ⚠️ 占位符形态与逐方言模板内容由 WI7b 收口（唯一 owner）；WI1 只承接配置
     * 通路，不消费模板改写 interval 输出。
     */
    String getSqlIntervalLiteral();

    /**
     * WI12 交付①（plan 38）：`FOR UPDATE NOWAIT` 锁后缀模板（`<sqls>` 的
     * `forUpdateNowait` 元素）。presence = 能力 + 文本二合一：返回已配置的锁文本，
     * 未配置返回 null（消费方 AstToSqlGenerator.printForUpdate 对 null 编译期响亮
     * {@code nop.err.eql.dialect-not-support-feature}，feature=forUpdateNowait——
     * 不允许静默降级为裸 for update）。继承链显式关断形态 =
     * {@code <forUpdateNowait x:override="replace"/>}（加载后为 null，同未配置口径）。
     */
    String getForUpdateNowait();

    /**
     * WI12 交付②（plan 38）：`FOR UPDATE SKIP LOCKED` 锁后缀模板（`<sqls>` 的
     * `forUpdateSkipLocked` 元素）。值面契约与 {@link #getForUpdateNowait()} 一致：
     * 未配置返回 null（消费方编译期响亮，feature=forUpdateSkipLocked）。
     */
    String getForUpdateSkipLocked();

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