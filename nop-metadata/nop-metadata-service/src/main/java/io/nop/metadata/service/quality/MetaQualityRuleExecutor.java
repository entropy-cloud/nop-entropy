package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 质量规则执行器（无状态，参考 {@code MetaCatalogCollector} 收集器模式）。按架构基线 §2.7.1 D3 判定语义，
 * 根据 ruleType 生成并执行检测 SQL，返回结构化判定结果（{@link QualityRuleJudgment}），
 * 由 {@code NopMetaQualityRuleBizModel} 据此写入 NopMetaQualityResult 时序行。
 *
 * <p>本类不自建连接，由调用方在 P2-1 {@code withConnection} callback 内传入已打开的 {@link Connection}。
 * 物理解析（entityId → NopMetaTable → querySpace → NopMetaDataSource）在 BizModel 层完成，本类仅消费解析结果。
 *
 * <p>标识符注入防护（§2.7.1 D3）：列名 / 表名 / schema 名为 SQL 标识符，拼接前必须通过
 * {@link #IDENTIFIER_PATTERN} 白名单校验；比较值（range min/max、regex pattern）使用 PreparedStatement 参数绑定。
 * custom_sql 的 sqlExpression/params.sql 为用户显式提供（已知显式风险），直接执行不解析不改写。
 *
 * <p>不支持的方言（regex REGEXP 不可用）→ SKIP + details 标记（不静默跳过、不伪造值）。
 */
public class MetaQualityRuleExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MetaQualityRuleExecutor.class);

    /** SQL 标识符白名单：字母/下划线开头，后跟字母/数字/下划线。用于校验列名/表名/schema，防注入。 */
    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** inline ErrorCode：标识符（列名/表名/schema）不符合白名单，拒绝拼接防注入。 */
    static final ErrorCode ERR_QUALITY_INVALID_IDENTIFIER =
            ErrorCode.define("metadata.quality-invalid-identifier",
                    "Identifier (column/table/schema) does not match whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    "identifier");

    /**
     * 执行单条质量规则并返回判定结果。
     *
     * @param conn           已打开的连接（由 withConnection callback 提供，本方法不关闭连接）
     * @param schemaPattern  schema 限定（null/空串表示依赖连接默认 schema）
     * @param ruleType       规则类型（not_null/unique/range/regex/custom_sql/freshness/volume）
     * @param entityType     对象类型（field/table/database）
     * @param paramsJson     规则 params JSON 文本（可能为 null/空）
     * @param sqlExpression  规则 sqlExpression 列（custom_sql 优先使用，可能为 null）
     * @param threshold      规则阈值（可能为 null）
     * @param tableName      目标物理表名（来自 external NopMetaTable.tableName）
     * @param productName    DB 产品名（运行时取自 DatabaseMetaData，放入 details）
     * @return 判定结果（status/actualValue/expectedValue/message/details 全显式填充）
     */
    public QualityRuleJudgment judge(Connection conn, String schemaPattern,
                                     String ruleType, String entityType,
                                     String paramsJson, String sqlExpression,
                                     Double threshold, String tableName,
                                     String productName) {
        // 基础校验：表名必须是合法标识符（防注入，与 Catalog qualifyTable 一致策略）
        validateIdentifier(tableName);

        Map<String, Object> params = parseParams(paramsJson);
        QualityRuleJudgment j = new QualityRuleJudgment();
        j.getDetails().put("ruleType", ruleType);
        j.getDetails().put("entityType", entityType);
        j.getDetails().put("tableName", tableName);
        if (threshold != null) {
            j.getDetails().put("threshold", threshold);
        }
        if (productName != null) {
            j.getDetails().put("databaseProductName", productName);
        }
        if (schemaPattern != null && !schemaPattern.trim().isEmpty()) {
            j.getDetails().put("schema", schemaPattern);
        }

        String normalizedSchema = normalizeSchema(schemaPattern);

        // entityType=database 首版不支持（§2.7.1 D1）→ SKIP + details 标记
        if ("database".equals(entityType)) {
            j.setStatus("SKIP");
            j.setMessage("entityType=database not supported in first version (external-table-only execution)");
            j.getDetails().put("reason", "database-not-supported-first-version");
            return j;
        }

        switch (ruleType) {
            case "volume":
                return judgeVolume(conn, normalizedSchema, tableName, threshold, params, j);
            case "freshness":
                return judgeFreshness(conn, normalizedSchema, tableName, threshold, params, j);
            case "custom_sql":
                return judgeCustomSql(conn, sqlExpression, params, j);
            case "not_null":
                return judgeNotNull(conn, normalizedSchema, tableName, threshold, params, j);
            case "unique":
                return judgeUnique(conn, normalizedSchema, tableName, params, j);
            case "range":
                return judgeRange(conn, normalizedSchema, tableName, params, j);
            case "regex":
                return judgeRegex(conn, normalizedSchema, tableName, params, j);
            default:
                j.setStatus("ERROR");
                j.setMessage("Unsupported ruleType: " + ruleType);
                return j;
        }
    }

    // ===== table 级规则 =====

    /** volume：SELECT COUNT(*) → actualValue=行数；与 minRows/maxRows/threshold 比较。 */
    private QualityRuleJudgment judgeVolume(Connection conn, String schema, String tableName,
                                            Double threshold, Map<String, Object> params, QualityRuleJudgment j) {
        String qualified = qualifyTable(schema, tableName);
        long rowCount = queryLong(conn, "SELECT COUNT(*) FROM " + qualified);
        j.setActualValue((double) rowCount);

        Double minRows = getDouble(params, "minRows");
        Double maxRows = getDouble(params, "maxRows");
        // 若 params 未给 min/max，回退用 threshold 作为 minRows（最小行数语义）
        if (minRows == null && maxRows == null && threshold != null) {
            minRows = threshold;
        }
        if (minRows != null) {
            j.setExpectedValue(minRows);
        }
        j.getDetails().put("minRows", minRows);
        j.getDetails().put("maxRows", maxRows);

        boolean pass = true;
        if (minRows != null && rowCount < minRows) {
            pass = false;
        }
        if (maxRows != null && rowCount > maxRows) {
            pass = false;
        }
        j.setStatus(pass ? "PASS" : "FAIL");
        j.setMessage(pass
                ? "volume ok: rowCount=" + rowCount
                : "volume fail: rowCount=" + rowCount + " outside [" + minRows + "," + maxRows + "]");
        return j;
    }

    /** freshness：SELECT MAX(tsCol) → age(now-maxTs) 分钟；与 maxAgeMinutes/threshold 比较。 */
    private QualityRuleJudgment judgeFreshness(Connection conn, String schema, String tableName,
                                               Double threshold, Map<String, Object> params, QualityRuleJudgment j) {
        String tsCol = getString(params, "timestampColumn");
        if (tsCol == null || tsCol.isEmpty()) {
            j.setStatus("ERROR");
            j.setMessage("freshness rule missing required param 'timestampColumn'");
            return j;
        }
        validateIdentifier(tsCol);
        j.getDetails().put("timestampColumn", tsCol);

        String qualified = qualifyTable(schema, tableName);
        Timestamp maxTs = queryTimestamp(conn, "SELECT MAX(" + tsCol + ") FROM " + qualified);
        if (maxTs == null) {
            j.setStatus("ERROR");
            j.setMessage("freshness cannot be computed: no rows with timestamp value in column " + tsCol);
            return j;
        }
        j.getDetails().put("maxTimestamp", maxTs.toString());

        Double maxAgeMinutes = getDouble(params, "maxAgeMinutes");
        if (maxAgeMinutes == null && threshold != null) {
            maxAgeMinutes = threshold;
        }
        long ageMinutes = ageMinutesFromNow(maxTs);
        j.setActualValue((double) ageMinutes);
        if (maxAgeMinutes != null) {
            j.setExpectedValue(maxAgeMinutes);
        }
        j.getDetails().put("maxAgeMinutes", maxAgeMinutes);

        boolean pass = true;
        if (maxAgeMinutes != null && ageMinutes > maxAgeMinutes) {
            pass = false;
        }
        j.setStatus(pass ? "PASS" : "FAIL");
        j.setMessage(pass
                ? "freshness ok: ageMinutes=" + ageMinutes + " (maxTs=" + maxTs + ")"
                : "freshness fail: ageMinutes=" + ageMinutes + " exceeds maxAgeMinutes=" + maxAgeMinutes);
        return j;
    }

    /** custom_sql：执行 sqlExpression（优先）或 params.sql；期望返回单数值/单布尔；按 params.expectPassWhen 判定。 */
    private QualityRuleJudgment judgeCustomSql(Connection conn, String sqlExpression,
                                               Map<String, Object> params, QualityRuleJudgment j) {
        String sql = sqlExpression;
        if (sql == null || sql.isEmpty()) {
            sql = getString(params, "sql");
        }
        if (sql == null || sql.isEmpty()) {
            j.setStatus("ERROR");
            j.setMessage("custom_sql rule missing both sqlExpression column and params.sql");
            return j;
        }
        // custom_sql 为用户显式提供（已知显式风险，§2.7.1 D3），直接执行不解析不改写
        j.getDetails().put("sql", sql);

        Double value;
        try {
            value = querySingleValue(conn, sql);
        } catch (SQLException e) {
            LOG.error("custom_sql execution failed", e);
            j.setStatus("ERROR");
            j.setMessage("custom_sql execution failed: " + messageOf(e));
            return j;
        }
        if (value == null) {
            j.setStatus("ERROR");
            j.setMessage("custom_sql did not return a single numeric/boolean value");
            return j;
        }
        j.setActualValue(value);

        String expectPassWhen = getString(params, "expectPassWhen");
        boolean pass;
        if (expectPassWhen == null || expectPassWhen.isEmpty()) {
            // 默认：返回 0 = pass
            pass = value == 0.0;
            j.getDetails().put("expectPassWhen", "default: eq 0");
        } else {
            pass = evalExpectPassWhen(expectPassWhen, value);
            j.getDetails().put("expectPassWhen", expectPassWhen);
        }
        j.setStatus(pass ? "PASS" : "FAIL");
        j.setMessage(pass
                ? "custom_sql ok: returned " + value + " satisfies " + expectPassWhen
                : "custom_sql fail: returned " + value + " does not satisfy " + (expectPassWhen == null ? "eq 0" : expectPassWhen));
        return j;
    }

    // ===== field 级规则（external params.column 约定）=====

    /** not_null：SELECT COUNT(*) WHERE col IS NULL → actualValue=nullCount；expectedValue=threshold(默认0)。 */
    private QualityRuleJudgment judgeNotNull(Connection conn, String schema, String tableName,
                                             Double threshold, Map<String, Object> params, QualityRuleJudgment j) {
        String col = requireColumn(params, j);
        if (col == null) {
            return j;
        }
        String qualified = qualifyTable(schema, tableName);
        long nullCount = queryLong(conn,
                "SELECT COUNT(*) FROM " + qualified + " WHERE " + col + " IS NULL");
        j.setActualValue((double) nullCount);
        double allowed = threshold != null ? threshold : 0.0;
        j.setExpectedValue(allowed);
        boolean pass = nullCount <= allowed;
        j.setStatus(pass ? "PASS" : "FAIL");
        j.setMessage(pass
                ? "not_null ok: nullCount=" + nullCount + " <= " + allowed
                : "not_null fail: nullCount=" + nullCount + " exceeds threshold " + allowed);
        return j;
    }

    /** unique：SELECT COUNT(*) FROM (SELECT col WHERE col IS NOT NULL GROUP BY col HAVING COUNT(*)>1) → 重复值组数。 */
    private QualityRuleJudgment judgeUnique(Connection conn, String schema, String tableName,
                                            Map<String, Object> params, QualityRuleJudgment j) {
        String col = requireColumn(params, j);
        if (col == null) {
            return j;
        }
        String qualified = qualifyTable(schema, tableName);
        long dupGroups = queryLong(conn,
                "SELECT COUNT(*) FROM (SELECT " + col + " FROM " + qualified
                        + " WHERE " + col + " IS NOT NULL GROUP BY " + col + " HAVING COUNT(*)>1) d");
        j.setActualValue((double) dupGroups);
        j.setExpectedValue(0.0);
        boolean pass = dupGroups == 0;
        j.setStatus(pass ? "PASS" : "FAIL");
        j.setMessage(pass
                ? "unique ok: no duplicate value groups"
                : "unique fail: " + dupGroups + " duplicate value groups found");
        return j;
    }

    /** range：SELECT COUNT(*) WHERE col IS NOT NULL AND (col < :min OR col > :max) → 越界行数；expectedValue=0。 */
    private QualityRuleJudgment judgeRange(Connection conn, String schema, String tableName,
                                           Map<String, Object> params, QualityRuleJudgment j) {
        String col = requireColumn(params, j);
        if (col == null) {
            return j;
        }
        Double min = getDouble(params, "min");
        Double max = getDouble(params, "max");
        if (min == null && max == null) {
            j.setStatus("ERROR");
            j.setMessage("range rule requires at least one of params.min / params.max");
            return j;
        }
        j.getDetails().put("min", min);
        j.getDetails().put("max", max);

        String qualified = qualifyTable(schema, tableName);
        // 边界缺失时用一个不触发的哨兵值占位（保证 SQL 语法合法且语义正确）
        boolean hasMin = min != null;
        boolean hasMax = max != null;
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(qualified)
                .append(" WHERE ").append(col).append(" IS NOT NULL AND (");
        if (hasMin) {
            sql.append(col).append(" < ?");
        }
        if (hasMin && hasMax) {
            sql.append(" OR ");
        }
        if (hasMax) {
            sql.append(col).append(" > ?");
        }
        sql.append(")");

        long outOfRange;
        try (PreparedStatement st = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (hasMin) {
                st.setDouble(idx++, min);
            }
            if (hasMax) {
                st.setDouble(idx, max);
            }
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("range COUNT(*) returned no row");
                }
                outOfRange = rs.getLong(1);
            }
        } catch (SQLException e) {
            LOG.error("range SQL execution failed", e);
            j.setStatus("ERROR");
            j.setMessage("range SQL execution failed: " + messageOf(e));
            return j;
        }
        j.setActualValue((double) outOfRange);
        j.setExpectedValue(0.0);
        boolean pass = outOfRange == 0;
        j.setStatus(pass ? "PASS" : "FAIL");
        j.setMessage(pass
                ? "range ok: no out-of-range rows"
                : "range fail: " + outOfRange + " rows outside [" + min + "," + max + "]");
        return j;
    }

    /** regex：SELECT COUNT(*) WHERE col IS NOT NULL AND col NOT REGEXP :pattern → 不匹配数；方言不支持 → SKIP。 */
    private QualityRuleJudgment judgeRegex(Connection conn, String schema, String tableName,
                                           Map<String, Object> params, QualityRuleJudgment j) {
        String col = requireColumn(params, j);
        if (col == null) {
            return j;
        }
        String pattern = getString(params, "pattern");
        if (pattern == null || pattern.isEmpty()) {
            j.setStatus("ERROR");
            j.setMessage("regex rule missing required param 'pattern'");
            return j;
        }
        j.getDetails().put("pattern", pattern);

        String qualified = qualifyTable(schema, tableName);
        String sql = "SELECT COUNT(*) FROM " + qualified
                + " WHERE " + col + " IS NOT NULL AND " + col + " NOT REGEXP ?";
        long notMatching;
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, pattern);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("regex COUNT(*) returned no row");
                }
                notMatching = rs.getLong(1);
            }
        } catch (SQLException e) {
            // 方言不支持 REGEXP → SKIP + details 标记（不静默跳过、不伪造值）
            if (isRegexpUnsupported(e)) {
                LOG.warn("regex skipped: dialect does not support REGEXP operator", e);
                j.setStatus("SKIP");
                j.setMessage("regex skipped: dialect does not support REGEXP operator (" + messageOf(e) + ")");
                j.getDetails().put("reason", "regexp-unsupported-dialect");
                return j;
            }
            LOG.error("regex SQL execution failed", e);
            j.setStatus("ERROR");
            j.setMessage("regex SQL execution failed: " + messageOf(e));
            return j;
        }
        j.setActualValue((double) notMatching);
        j.setExpectedValue(0.0);
        boolean pass = notMatching == 0;
        j.setStatus(pass ? "PASS" : "FAIL");
        j.setMessage(pass
                ? "regex ok: all non-null values match pattern"
                : "regex fail: " + notMatching + " non-null values do not match pattern " + pattern);
        return j;
    }

    // ===== helpers =====

    private String requireColumn(Map<String, Object> params, QualityRuleJudgment j) {
        String col = getString(params, "column");
        if (col == null || col.isEmpty()) {
            j.setStatus("ERROR");
            j.setMessage("field-level rule missing required param 'column'");
            return null;
        }
        validateIdentifier(col);
        j.getDetails().put("column", col);
        return col;
    }

    private static long queryLong(Connection conn, String sql) {
        LOG.info("qualityRule SQL: {}", sql);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                throw new NopException(ErrorCode.define("metadata.quality-sql-no-row",
                        "Quality rule SQL returned no row: {sql}", "sql"))
                        .param("sql", sql);
            }
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new NopException(ErrorCode.define("metadata.quality-sql-failed",
                    "Quality rule SQL execution failed: {sql} -- {error}", "sql", "error"), e)
                    .param("sql", sql);
        }
    }

    private static Timestamp queryTimestamp(Connection conn, String sql) {
        LOG.info("qualityRule SQL: {}", sql);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            return rs.getTimestamp(1);
        } catch (SQLException e) {
            throw new NopException(ErrorCode.define("metadata.quality-sql-failed",
                    "Quality rule SQL execution failed: {sql} -- {error}", "sql", "error"), e)
                    .param("sql", sql);
        }
    }

    /** 执行 custom_sql，期望返回单数值或单布尔；否则返回 null（由调用方记 ERROR）。 */
    private static Double querySingleValue(Connection conn, String sql) throws SQLException {
        LOG.info("qualityRule custom_sql: {}", sql);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            // 优先按数值读
            try {
                double d = rs.getDouble(1);
                if (!rs.wasNull()) {
                    return d;
                }
            } catch (SQLException ignore) {
                // 非数值列，尝试按布尔读
            }
            // 回退按布尔读（true=1, false=0）
            try {
                boolean b = rs.getBoolean(1);
                return b ? 1.0 : 0.0;
            } catch (SQLException ignore) {
                return null;
            }
        }
    }

    private static boolean evalExpectPassWhen(String expectPassWhen, double value) {
        String s = expectPassWhen.trim().toLowerCase();
        if ("true".equals(s) || "eq 1".equals(s) || "eq1".equals(s)) {
            return value == 1.0;
        }
        if (s.startsWith("eq ")) {
            return value == Double.parseDouble(s.substring(3).trim());
        }
        if (s.startsWith("gt ")) {
            return value > Double.parseDouble(s.substring(3).trim());
        }
        if (s.startsWith("lt ")) {
            return value < Double.parseDouble(s.substring(3).trim());
        }
        if (s.startsWith("ge ")) {
            return value >= Double.parseDouble(s.substring(3).trim());
        }
        if (s.startsWith("le ")) {
            return value <= Double.parseDouble(s.substring(3).trim());
        }
        // 默认 eq 0
        return value == 0.0;
    }

    private static boolean isRegexpUnsupported(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("regexp") || lower.contains("syntax")
                || lower.contains("function") && lower.contains("not found");
    }

    static long ageMinutesFromNow(Timestamp ts) {
        long nowMs = System.currentTimeMillis();
        long tsMs = ts.getTime();
        long diffMs = nowMs - tsMs;
        return diffMs / 60000L;
    }

    /** schema 限定：<schema>.<tableName>；schema 为空时用 <tableName>（依赖连接默认 schema）。与 Catalog 一致。 */
    static String qualifyTable(String schema, String tableName) {
        if (schema == null || schema.isEmpty()) {
            return tableName;
        }
        return schema + "." + tableName;
    }

    private static String normalizeSchema(String schemaPattern) {
        if (schemaPattern == null || schemaPattern.trim().isEmpty()) {
            return null;
        }
        return schemaPattern.trim();
    }

    static void validateIdentifier(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new NopException(ERR_QUALITY_INVALID_IDENTIFIER)
                    .param("identifier", String.valueOf(identifier));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Object parsed = JsonTool.parse(paramsJson);
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        return java.util.Collections.emptyMap();
    }

    private static String getString(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Double getDouble(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 提取异常消息（null 时回退类名）；集中调用避免 catch 块内直接 e.getMessage() 丢失堆栈。 */
    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }
}
