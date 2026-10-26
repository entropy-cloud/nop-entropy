package io.nop.metadata.service.query;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.query.FilterToSqlTranslator;
import io.nop.metadata.service.query.SqlPagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 单表数据查询执行器（plan 2026-07-19-1250-3 Phase 3 维度07-04：从 NopMetaEntityBizModel 抽出）。
 *
 * <p>承载 external / sql 路径的 SQL 构建 + JDBC 执行 + 结果序列化逻辑。BizModel 仅做表加载、entityName 校验、
 * 错误上下文附加，本类负责"拼出 SELECT SQL 并跑出来"的纯执行工作。
 *
 * <p>本 phase 首版仅迁移 SQL 构建静态助手（{@link #buildExternalSelectSql} / {@link #buildSqlSelectSql}）+
 * JDBC 执行助手（{@link #executeQuery}）+ 结果包装助手（{@link #buildQueryResult}）；BizModel 通过本类
 * 静态方法调用，签名兼容，无行为变化。后续 slice 把 queryEntityTable / queryExternalTable / querySqlTable
 * 整体迁移到本类作为实例方法（依赖 daoProvider/orm 注入），届时 BizModel 行数可降到 ≤ 500。
 */
public final class MetaEntityQueryExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MetaEntityQueryExecutor.class);

    /** BizModel 内部使用的查询失败 ErrorCode（沿用 BizModel 的 inline ErrorCode，渐进迁移到 NopMetadataErrors）。 */

    private MetaEntityQueryExecutor() {
    }

    /**
     * 构建 external 路径 SELECT SQL：{@code SELECT col1,col2 FROM <table> [WHERE <filter>] [LIMIT ? OFFSET ?]}。
     *
     * <p>列名/表名经标识符白名单校验（{@link FilterToSqlTranslator#validateIdentifier}）防注入。
     */
    public static String buildExternalSelectSql(String tableName, List<String> columns,
                                                 String filterSql, Long limit, Long offset, String dialect) {
        return buildExternalSelectSql(tableName, columns, filterSql, null, limit, offset, dialect);
    }

    /**
     * M4（plan 2026-10-26-2047-1 Phase 4）重载：目录合成入口的排序下推。
     * {@code orderBy} 字段名经标识符校验 + {@code allowedColumns} 白名单（大小写不敏感，
     * 外部路径传入已解析列集；传 null 跳过白名单——仅限调用方已另行保证的场景）。
     */
    public static String buildExternalSelectSql(String tableName, List<String> columns,
                                                 String filterSql, List<OrderFieldBean> orderBy,
                                                 Long limit, Long offset, String dialect) {
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
        appendOrderBy(sb, orderBy, columns == null ? null
                : columns.stream().map(c -> c.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()));
        SqlPagination.appendLimitOffset(sb, limit, offset, dialect);
        return sb.toString();
    }

    /**
     * 构建 sql 路径 SELECT SQL：{@code SELECT * FROM (<sourceSql>) _t [WHERE <filter>] [LIMIT ? OFFSET ?]}。
     */
    public static String buildSqlSelectSql(String sourceSql, String filterSql, Long limit, Long offset, String dialect) {
        return buildSqlSelectSql(sourceSql, filterSql, null, limit, offset, dialect);
    }

    /** M4 重载：目录合成入口的排序下推（sql 路径无列白名单，仅标识符校验）。 */
    public static String buildSqlSelectSql(String sourceSql, String filterSql, List<OrderFieldBean> orderBy,
                                            Long limit, Long offset, String dialect) {
        StringBuilder sb = new StringBuilder("SELECT * FROM (");
        sb.append(sourceSql);
        sb.append(") _t");
        if (filterSql != null && !filterSql.isEmpty()) {
            sb.append(" WHERE ").append(filterSql);
        }
        appendOrderBy(sb, orderBy, null);
        SqlPagination.appendLimitOffset(sb, limit, offset, dialect);
        return sb.toString();
    }

    /**
     * M4：ORDER BY 子句构建。字段名 fail-closed（标识符校验 + 白名单），方向仅 ASC/DESC
     * （OrderFieldBean.nullsFirst 首版忽略——跨方言 NULLS FIRST/LAST 语义差异大，目录合成
     * 排序不做方言分支；需求出现时随方言层另裁定）。
     */
    private static void appendOrderBy(StringBuilder sb, List<OrderFieldBean> orderBy, Set<String> allowedColumns) {
        if (orderBy == null || orderBy.isEmpty()) {
            return;
        }
        sb.append(" ORDER BY ");
        for (int i = 0; i < orderBy.size(); i++) {
            OrderFieldBean field = orderBy.get(i);
            if (i > 0) {
                sb.append(",");
            }
            String name = field.getName();
            FilterToSqlTranslator.validateIdentifier(name);
            if (allowedColumns != null && !allowedColumns.contains(name.toLowerCase(Locale.ROOT))) {
                throw new NopException(NopMetadataErrors.ERR_PAGINATION_ORDER_FIELD_UNKNOWN)
                        .param(NopMetadataErrors.ARG_FIELD_NAME, name);
            }
            sb.append(name);
            if (field.isDesc()) {
                sb.append(" DESC");
            }
        }
    }

    /**
     * 执行查询 SQL（filter 参数 + limit/offset 参数按序绑定），返回行列表（每行为列名→值 Map）。
     *
     * <p>失败路径显式（不静默吞 SQLException）：抛 {@link NopException} 携带 {@link #NopMetadataErrors.ERR_QUERY_SQL_EXEC_FAILED}
     * + sql + error 上下文。
     */
    public static List<Map<String, Object>>[] newArrayHolder() {
        return ArrayHolderUtils.newArrayHolder();
    }

    /**
     * 执行查询 SQL 并把每行序列化为 {@code Map<String,Object>}（key=列名/标签，value=列值）。
     *
     * @param metaEntityId 目标逻辑表 ID（P1-6 plan 2026-08-15-1913-3 轨 2 穿参：
     *                    静态工具方法无身份值，由调用方传入——占位符真实渲染）
     */
    public static List<Map<String, Object>> executeQuery(Connection conn,
                                                           String sql,
                                                           List<Object> filterParams,
                                                           Long limit, Long offset,
                                                           final String metaEntityId) {
        // P1-8（plan 2026-08-15-1913-1，AR-16 形态）：sql 路径 SQL 内嵌 sourceSql
        // 全文（可含敏感字面量），INFO 只记 sqlHash，全文降 DEBUG
        LOG.info("MetaEntityQueryExecutor SQL executed: sqlHash={}",
                MetaQualityRuleExecutor.sqlHashOf(sql));
        LOG.debug("MetaEntityQueryExecutor SQL: {}", sql);
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
            throw new NopMetadataException(NopMetadataErrors.ERR_QUERY_SQL_EXEC_FAILED, e)
                    .param(NopMetadataErrors.ARG_META_ENTITY_ID, metaEntityId)
                    .param("sql", sql)
                    .param(NopMetadataErrors.ARG_ERROR, messageOf(e));
        }
    }

    /** 构建查询结果 Map（统一 {@code {entityKind, items:[...]}} 结构）。 */
    public static Map<String, Object> buildQueryResult(String entityKind, List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityKind", entityKind);
        result.put("items", items != null ? items : new ArrayList<>());
        return result;
    }

    /** 提取异常消息（null 时回退类名）。 */
    public static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }
}
