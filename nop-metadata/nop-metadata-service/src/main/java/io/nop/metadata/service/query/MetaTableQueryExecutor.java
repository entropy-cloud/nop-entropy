/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.query.FilterToSqlTranslator;
import io.nop.metadata.service.query.SqlPagination;
import io.nop.metadata.service.NopMetadataErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单表数据查询执行器（plan 2026-07-19-1250-3 Phase 3 维度07-04：从 NopMetaTableBizModel 抽出）。
 *
 * <p>承载 external / sql 路径的 SQL 构建 + JDBC 执行 + 结果序列化逻辑。BizModel 仅做表加载、entityName 校验、
 * 错误上下文附加，本类负责"拼出 SELECT SQL 并跑出来"的纯执行工作。
 *
 * <p>本 phase 首版仅迁移 SQL 构建静态助手（{@link #buildExternalSelectSql} / {@link #buildSqlSelectSql}）+
 * JDBC 执行助手（{@link #executeQuery}）+ 结果包装助手（{@link #buildQueryResult}）；BizModel 通过本类
 * 静态方法调用，签名兼容，无行为变化。后续 slice 把 queryEntityTable / queryExternalTable / querySqlTable
 * 整体迁移到本类作为实例方法（依赖 daoProvider/orm 注入），届时 BizModel 行数可降到 ≤ 500。
 */
public final class MetaTableQueryExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MetaTableQueryExecutor.class);

    /** BizModel 内部使用的查询失败 ErrorCode（沿用 BizModel 的 inline ErrorCode，渐进迁移到 NopMetadataErrors）。 */

    private MetaTableQueryExecutor() {
    }

    /**
     * 构建 external 路径 SELECT SQL：{@code SELECT col1,col2 FROM <table> [WHERE <filter>] [LIMIT ? OFFSET ?]}。
     *
     * <p>列名/表名经标识符白名单校验（{@link FilterToSqlTranslator#validateIdentifier}）防注入。
     */
    public static String buildExternalSelectSql(String tableName, List<String> columns,
                                                 String filterSql, Long limit, Long offset, String dialect) {
        StringBuilder sb = new StringBuilder("SELECT ");
        if (columns == null || columns.isEmpty()) {
            sb.append("*");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                FilterToSqlTranslator.validateIdentifier(columns.get(i));
                sb.append(columns.get(i));
            }
        }
        sb.append(" FROM ");
        FilterToSqlTranslator.validateIdentifier(tableName);
        sb.append(tableName);
        if (filterSql != null && !filterSql.isEmpty()) {
            sb.append(" WHERE ").append(filterSql);
        }
        SqlPagination.appendLimitOffset(sb, limit, offset, dialect);
        return sb.toString();
    }

    /**
     * 构建 sql 路径 SELECT SQL：{@code SELECT * FROM (<sourceSql>) _t [WHERE <filter>] [LIMIT ? OFFSET ?]}。
     */
    public static String buildSqlSelectSql(String sourceSql, String filterSql, Long limit, Long offset, String dialect) {
        StringBuilder sb = new StringBuilder("SELECT * FROM (");
        sb.append(sourceSql);
        sb.append(") _t");
        if (filterSql != null && !filterSql.isEmpty()) {
            sb.append(" WHERE ").append(filterSql);
        }
        SqlPagination.appendLimitOffset(sb, limit, offset, dialect);
        return sb.toString();
    }

    /**
     * 执行查询 SQL（filter 参数 + limit/offset 参数按序绑定），返回行列表（每行为列名→值 Map）。
     *
     * <p>失败路径显式（不静默吞 SQLException）：抛 {@link NopException} 携带 {@link #NopMetadataErrors.ERR_QUERY_SQL_EXEC_FAILED}
     * + sql + error 上下文。
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>>[] newArrayHolder() {
        return (List<Map<String, Object>>[]) new List<?>[1];
    }

    /**
     * 执行查询 SQL 并把每行序列化为 {@code Map<String,Object>}（key=列名/标签，value=列值）。
     */
    public static List<Map<String, Object>> executeQuery(Connection conn, String sql, List<Object> filterParams,
                                                          Long limit, Long offset) {
        LOG.info("MetaTableQueryExecutor SQL: {}", sql);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            int idx = 1;
            if (filterParams != null) {
                for (Object p : filterParams) {
                    st.setObject(idx++, p);
                }
            }
            if (limit != null) {
                st.setObject(idx++, limit);
            }
            if (offset != null && offset > 0) {
                st.setObject(idx++, offset);
            }
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= columnCount; c++) {
                        String label = meta.getColumnLabel(c);
                        if (label == null || label.isEmpty()) {
                            label = meta.getColumnName(c);
                        }
                        row.put(label, rs.getObject(c));
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new NopException(NopMetadataErrors.ERR_QUERY_SQL_EXEC_FAILED, e)
                    .param("sql", sql)
                    .param("error", messageOf(e));
        }
    }

    /** 构建查询结果 Map（统一 {@code {tableType, items:[...]}} 结构）。 */
    public static Map<String, Object> buildQueryResult(String tableType, List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableType", tableType);
        result.put("items", items != null ? items : new ArrayList<>());
        return result;
    }

    /** 提取异常消息（null 时回退类名）。 */
    public static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }
}
