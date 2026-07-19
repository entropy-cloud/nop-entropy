package io.nop.metadata.service.profiling;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.tableref.TableReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据剖析器（无状态，参考 {@code MetaCatalogCollector} + {@code MetaQualityRuleExecutor}）。按设计 06 §三
 * （最终设计）/ 架构基线 §2.7.2 D2 统计范围 + 降级，对表的每列做统计分析，返回结构化快照
 * （{@link ProfilingSnapshot}），由 BizModel 据此写入 NopMetaProfilingResult 时序行。
 *
 * <p>本类不自建连接，由调用方在 P2-1 {@code withConnection} callback 内传入已打开的 {@link Connection} +
 * {@link DatabaseMetaData}。物理解析（metaTableId → NopMetaTable → querySpace → NopMetaDataSource）在 BizModel 层完成。
 *
 * <p>统计范围 + 可移植性 + 降级（D2，已 live 核查）：
 * <ul>
 *   <li><b>便携 SQL 聚合（全方言精确，含 H2/MySQL/PG）</b>：totalCount(COUNT(*)) / distinctCount(COUNT DISTINCT) /
 *       nullCount / emptyCount / min / max / mean(AVG) / stddev(STDDEV_SAMP，已 live 验证) /
 *       minLength/maxLength(MIN/MAX LENGTH) / avgLength(AVG LENGTH) / topValues(GROUP BY ... ORDER BY COUNT(*) DESC LIMIT N)</li>
 *   <li><b>in-app 精确（全方言，仅依赖可移植 ORDER BY）</b>：median / percentiles / distribution（拉取列值排序后 Java 计算，
 *       因 MySQL 无原生 percentile，in-app 保证全方言精确）</li>
 *   <li><b>不可用（方言特定，null + unavailable 标记，不伪造）</b>：tableStats.sizeBytes / tableStats.lastModified
 *       （首版不实现，对齐 Catalog §2.3.2 降级模式）</li>
 * </ul>
 *
 * <p>列类型适配（D2）：运行时通过 {@link DatabaseMetaData#getColumns} 获取每列 JDBC 类型 → 数值列收集 numericStats，
 * 字符串列收集 stringStats；类型不适用统计省略（不伪造）。
 *
 * <p>列名解析（D1）：运行时 DatabaseMetaData.getColumns 解析物理列名 + 类型（不依赖 buildSql JSON 同步），
 * 可选 columns 参数（逗号分隔）过滤。
 *
 * <p>标识符注入防护：列名/表名/schema 名拼接前通过 {@link #IDENTIFIER_PATTERN} 白名单校验（对齐质量执行器 D3）。
 *
 * <p>失败隔离：单列剖析失败（SQL 异常）per-column try/catch 收集进 errors，不中断整表。
 */
public class MetaTableProfiler {

    private static final Logger LOG = LoggerFactory.getLogger(MetaTableProfiler.class);

    /** SQL 标识符白名单：字母/下划线开头，后跟字母/数字/下划线。用于校验列名/表名/schema，防注入。 */
    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** inline ErrorCode：标识符（列名/表名/schema）不符合白名单，拒绝拼接防注入。 */
    public static final ErrorCode ERR_PROFILING_INVALID_IDENTIFIER =
            ErrorCode.define("metadata.profiling-invalid-identifier",
                    "Identifier (column/table/schema) does not match whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    "identifier");

    /** topValues 默认取前 N 个值。 */
    private static final int DEFAULT_TOP_VALUES_LIMIT = 10;

    /** 数值类 JDBC 类型名关键字（大写），用于列类型适配（contains 匹配，兼容 "DOUBLE PRECISION" 等）。 */
    private static final List<String> NUMERIC_KEYWORDS = Arrays.asList(
            "INT", "DOUBLE", "FLOAT", "REAL", "DECIMAL", "NUMERIC", "NUMBER", "BIT", "BOOLEAN");

    /** 字符串类 JDBC 类型名关键字（大写）。 */
    private static final List<String> STRING_KEYWORDS = Arrays.asList(
            "CHAR", "TEXT", "CLOB", "STRING");

    /**
     * 剖析单表，返回结构化快照（消费 {@link TableReference}，架构基线 §4.4.3 D3）。
     *
     * <p>单表整体失败（如 COUNT(*) 失败）直接抛出，交调用方 per-table 处理；单列失败收集进 snapshot.errors 不中断。
     *
     * @param conn           已打开的连接（external/sql 由 withConnection 提供，entity 由平台 IJdbcTransaction 提供）
     * @param metaData       连接的 DatabaseMetaData（sql 子查询不用于列发现，由 ref.fields 承担）
     * @param ref            table-reference（external/entity/sql 三态）
     * @param schemaPattern  schema 限定（null/空串表示依赖连接默认 schema；sql 子查询忽略此参数）
     * @param columnsFilter  可选，要剖析的列名（逗号分隔，null/空=所有列）
     * @param productName    DB 产品名（运行时取自 DatabaseMetaData，放入 tableStats.extras）
     * @return 剖析快照（rowCount 真实 + columnStats 真实统计 + 表级 sizeBytes/lastModified null+unavailable）
     */
    public ProfilingSnapshot profile(Connection conn, DatabaseMetaData metaData,
                                      TableReference ref, String schemaPattern,
                                      String columnsFilter, String productName) {
        // external/entity 物理表名校验（防注入）；sql 子查询不校验（用户显式提供）
        if (!ref.isSubquery()) {
            validateIdentifier(ref.getPhysicalTableName());
        }

        String normalizedSchema = normalizeSchema(schemaPattern);
        Set<String> filter = parseColumnsFilter(columnsFilter);

        String displayTableName = ref.isSubquery()
                ? "(" + ref.getSourceSql() + ") _t" : ref.getPhysicalTableName();

        ProfilingSnapshot snapshot = new ProfilingSnapshot();
        snapshot.setTableName(displayTableName);
        if (productName != null) {
            snapshot.getTableExtras().put("databaseProductName", productName);
        }
        snapshot.getTableExtras().put("tableType", ref.getKind().name().toLowerCase());

        try {
            String fromClause = buildFromClause(ref, normalizedSchema);

            // 表级行数：便携 COUNT(*)（精确）
            snapshot.setRowCount(countRows(conn, fromClause));

            // 表级方言特定统计首版不实现：null + 显式 unavailable 标记（不伪造 0，对齐 Catalog §2.3.2）
            snapshot.markTableUnavailable("sizeBytes");
            snapshot.markTableUnavailable("lastModified");

            // 列级：优先用 ref.fields（entity 从 ORM 模型取物理列，sql 从 AST 取）；
            // 无 fields 时（external）走 DatabaseMetaData.getColumns 运行时读
            List<ColumnMeta> columns;
            if (ref.getFields() != null) {
                columns = columnsFromFields(ref.getFields(), filter);
            } else {
                columns = readColumns(metaData, normalizedSchema, ref.getPhysicalTableName(), filter);
            }

            for (ColumnMeta col : columns) {
                try {
                    ProfilingColumnStats cs = profileColumn(conn, fromClause, col);
                    snapshot.getColumnStats().add(cs);
                } catch (Exception e) {
                    LOG.error("profileTable failed for column: {} of table: {}", col.name, displayTableName, e);
                    snapshot.recordColumnError(col.name, messageOf(e));
                }
            }
        } catch (SQLException e) {
            // 表级失败（COUNT(*) 失败、列结构读取失败）包装为运行时异常，由调用方 per-table 处理
            throw new NopException(ErrorCode.define("metadata.profiling-sql-failed",
                    "Profile table SQL execution failed: {tableName} -- {error}", "tableName", "error"), e)
                    .param("tableName", displayTableName)
                    .param("error", messageOf(e));
        }

        return snapshot;
    }

    // ===== 列级剖析 =====

    /** 解析列结构 + 类型，按类型适配收集统计。 */
    private ProfilingColumnStats profileColumn(Connection conn, String fromClause, ColumnMeta col) throws SQLException {
        // D5：sql 视图 <expr_N> 合成列通不过标识符白名单 → 显式 SKIP + unavailable 标记（不整表失败）
        if (isDerivedColumnName(col.name)) {
            ProfilingColumnStats cs = new ProfilingColumnStats();
            cs.setColumnName(col.name);
            cs.setDataType(col.dataType);
            cs.markUnavailable("derived-column-skipped");
            return cs;
        }
        validateIdentifier(col.name);
        ProfilingColumnStats cs = new ProfilingColumnStats();
        cs.setColumnName(col.name);
        cs.setDataType(col.dataType);

        // 所有类型通用：totalCount / distinctCount / nullCount / emptyCount / min / max
        long totalCount = queryLong(conn, "SELECT COUNT(*) FROM " + fromClause);
        cs.setTotalCount(totalCount);
        if (totalCount == 0) {
            // 空表：其余统计无意义（null），但不伪造，直接返回
            return cs;
        }
        long distinctCount = queryLong(conn, "SELECT COUNT(DISTINCT " + col.name + ") FROM " + fromClause);
        cs.setDistinctCount(distinctCount);
        long nullCount = totalCount - queryLong(conn, "SELECT COUNT(" + col.name + ") FROM " + fromClause);
        cs.setNullCount(nullCount);
        // emptyCount（空字符串数）是字符串概念：仅已知字符串列执行 WHERE col=''；数值/未知类型跳过（记 0）
        boolean stringLike = isStringType(col.dataType);
        if (stringLike) {
            try {
                cs.setEmptyCount(queryLong(conn, "SELECT COUNT(*) FROM " + fromClause + " WHERE " + col.name + " = ''"));
            } catch (SQLException e) {
                // 类型名含字符串关键字但实际不支持 = '' 比较（如 CLOB）→ emptyCount 无意义，记 0 不中断
                cs.setEmptyCount(0L);
            }
        } else {
            cs.setEmptyCount(0L);
        }
        cs.setMinValue(queryString(conn, "SELECT MIN(" + col.name + ") FROM " + fromClause));
        cs.setMaxValue(queryString(conn, "SELECT MAX(" + col.name + ") FROM " + fromClause));

        boolean numeric = isNumericType(col.dataType);
        boolean string = isStringType(col.dataType);

        // 类型适配：数值列收集 numericStats；字符串列收集 stringStats。
        // 未知类型（dataType=null，如 sql 视图列）→ 运行时探测：试 SUM(col) 成功则按数值，否则按字符串。
        if (numeric) {
            cs.setNumericStats(collectNumericStats(conn, fromClause, col.name));
        } else if (string) {
            cs.setStringStats(collectStringStats(conn, fromClause, col.name));
        } else if (probeNumeric(conn, fromClause, col.name)) {
            cs.setNumericStats(collectNumericStats(conn, fromClause, col.name));
        } else {
            cs.setStringStats(collectStringStats(conn, fromClause, col.name));
        }
        return cs;
    }

    /** 运行时探测列是否为数值类型（dataType=null 时）：试 SUM(col)，成功则视为数值列。 */
    private boolean probeNumeric(Connection conn, String fromClause, String col) {
        try {
            queryNullableDouble(conn, "SELECT SUM(" + col + ") FROM " + fromClause);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /** 数值列统计：min/max/mean/stddev（便携 SQL）+ median/percentiles/distribution（in-app 排序）。 */
    private Map<String, Object> collectNumericStats(Connection conn, String qualified, String col) throws SQLException {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("minValue", queryNullableDouble(conn, "SELECT MIN(" + col + ") FROM " + qualified));
        stats.put("maxValue", queryNullableDouble(conn, "SELECT MAX(" + col + ") FROM " + qualified));
        stats.put("meanValue", queryNullableDouble(conn, "SELECT AVG(" + col + ") FROM " + qualified));
        // STDDEV_SAMP 全方言精确（已 live 验证 H2/MySQL/PG）；样本标准差
        stats.put("stddevValue", queryNullableDouble(conn, "SELECT STDDEV_SAMP(" + col + ") FROM " + qualified));

        // in-app median / percentiles / distribution（全方言精确，仅依赖可移植 ORDER BY）
        List<Double> sorted = loadSortedDoubles(conn, qualified, col);
        if (sorted.isEmpty()) {
            // 全 null 列：median/percentiles 无意义（null），不伪造
            stats.put("medianValue", null);
            stats.put("percentiles", null);
            stats.put("distribution", null);
            return stats;
        }
        stats.put("medianValue", median(sorted));
        stats.put("percentiles", percentiles(sorted));
        stats.put("distribution", distribution(sorted));
        return stats;
    }

    /** 字符串列统计：minLength/maxLength/avgLength（便携 SQL）+ topValues（GROUP BY ... LIMIT N）。 */
    private Map<String, Object> collectStringStats(Connection conn, String qualified, String col) throws SQLException {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("minLength", queryNullableLong(conn, "SELECT MIN(LENGTH(" + col + ")) FROM " + qualified));
        stats.put("maxLength", queryNullableLong(conn, "SELECT MAX(LENGTH(" + col + ")) FROM " + qualified));
        stats.put("avgLength", queryNullableDouble(conn, "SELECT AVG(LENGTH(" + col + ")) FROM " + qualified));
        stats.put("topValues", topValues(conn, qualified, col, DEFAULT_TOP_VALUES_LIMIT));
        return stats;
    }

    // ===== in-app 排序统计（median / percentiles / distribution）=====

    /** 拉取非空数值并升序排序（仅依赖可移植 ORDER BY，全方言精确）。 */
    private List<Double> loadSortedDoubles(Connection conn, String qualified, String col) throws SQLException {
        // 使用 TreeMap 自动排序并去重计数；为 percentiles/distribution 同时服务
        String sql = "SELECT " + col + " FROM " + qualified + " WHERE " + col + " IS NOT NULL ORDER BY " + col;
        List<Double> all = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                double v = rs.getDouble(1);
                if (!rs.wasNull()) {
                    all.add(v);
                }
            }
        }
        // ORDER BY 已排序，但保险起见再排一次（防御方言 ORDER BY 对数值的不确定性）
        Collections.sort(all);
        return all;
    }

    /** 中位数（in-app）。 */
    static Double median(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int n = sorted.size();
        int mid = n / 2;
        if (n % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    /** 百分位数 {25,50,75,90,95}（in-app，线性插值，对齐 numpy linear）。 */
    static Map<String, Object> percentiles(List<Double> sorted) {
        Map<String, Object> p = new LinkedHashMap<>();
        for (int pct : new int[]{25, 50, 75, 90, 95}) {
            p.put(String.valueOf(pct), percentile(sorted, pct));
        }
        return p;
    }

    /** 单百分位（线性插值法）。 */
    private static Double percentile(List<Double> sorted, int pct) {
        if (sorted.isEmpty()) {
            return null;
        }
        int n = sorted.size();
        if (n == 1) {
            return sorted.get(0);
        }
        double rank = pct / 100.0 * (n - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double frac = rank - lo;
        return sorted.get(lo) + frac * (sorted.get(hi) - sorted.get(lo));
    }

    /** 值分布（in-app 分桶）：min/max 间等宽 4 桶，返回 buckets 边界 + counts。 */
    static Map<String, Object> distribution(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        double min = sorted.get(0);
        double max = sorted.get(sorted.size() - 1);
        if (min == max) {
            // 所有值相同：单桶
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("buckets", Collections.singletonList(min));
            d.put("counts", Collections.singletonList(sorted.size()));
            return d;
        }
        int bucketCount = 4;
        double width = (max - min) / bucketCount;
        // 桶边界：[min, min+w, min+2w, min+3w, max]
        List<Double> buckets = new ArrayList<>(bucketCount + 1);
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(min + i * width);
        }
        buckets.add(max);
        // 计数（最后一个桶含 max）
        List<Integer> counts = new ArrayList<>(Collections.nCopies(bucketCount, 0));
        for (Double v : sorted) {
            int idx;
            if (v >= max) {
                idx = bucketCount - 1;
            } else {
                idx = (int) ((v - min) / width);
                if (idx < 0) {
                    idx = 0;
                }
                if (idx >= bucketCount) {
                    idx = bucketCount - 1;
                }
            }
            counts.set(idx, counts.get(idx) + 1);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("buckets", buckets);
        d.put("counts", counts);
        return d;
    }

    // ===== 查询辅助 =====

    /** 便携行数：{@code SELECT COUNT(*) FROM <fromClause>}（全方言）。 */
    private long countRows(Connection conn, String fromClause) throws SQLException {
        return queryLong(conn, "SELECT COUNT(*) FROM " + fromClause);
    }

    /** topValues：{@code SELECT col, COUNT(*) ... GROUP BY col ORDER BY COUNT(*) DESC LIMIT N}（便携）。 */
    private List<Map<String, Object>> topValues(Connection conn, String qualified, String col, int limit) throws SQLException {
        String sql = "SELECT " + col + ", COUNT(*) FROM " + qualified
                + " WHERE " + col + " IS NOT NULL GROUP BY " + col + " ORDER BY COUNT(*) DESC LIMIT " + limit;
        List<Map<String, Object>> list = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> tv = new LinkedHashMap<>();
                tv.put("value", rs.getString(1));
                tv.put("count", rs.getLong(2));
                list.add(tv);
            }
        }
        return list;
    }

    /** 读取列结构（列名 + JDBC 类型名），运行时由 DatabaseMetaData.getColumns 解析。可选 filter 过滤。 */
    private List<ColumnMeta> readColumns(DatabaseMetaData metaData, String schema, String tableName,
                                         Set<String> filter) throws SQLException {
        List<ColumnMeta> columns = new ArrayList<>();
        ResultSet rs = null;
        try {
            rs = metaData.getColumns(null, schema, tableName, null);
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                if (name == null) {
                    continue;
                }
                if (filter != null && !filter.isEmpty() && !filter.contains(name.toUpperCase())) {
                    continue;
                }
                columns.add(new ColumnMeta(name, type));
            }
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException ignored) {
                    // ignored
                }
            }
        }
        return columns;
    }

    private static long queryLong(Connection conn, String sql) throws SQLException {
        LOG.info("profileTable SQL: {}", sql);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                throw new SQLException("aggregate returned no row: " + sql);
            }
            return toLong(rs, 1);
        }
    }

    private static Double queryNullableDouble(Connection conn, String sql) throws SQLException {
        LOG.info("profileTable SQL: {}", sql);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            String s = rs.getString(1);
            if (s == null || s.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                // 非数值字符串（如全 null 列的 MIN/MAX），按 null 处理（不伪造）
                return null;
            }
        }
    }

    private static Long queryNullableLong(Connection conn, String sql) throws SQLException {
        LOG.info("profileTable SQL: {}", sql);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            String s = rs.getString(1);
            if (s == null || s.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static String queryString(Connection conn, String sql) throws SQLException {
        LOG.info("profileTable SQL: {}", sql);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            return rs.getString(1);
        }
    }

    /**
     * 从 ResultSet 第 col 列读 long。优先按数值读，回退按字符串解析（H2 部分 NUMERIC 结果按 String 返回）。
     */
    private static long toLong(ResultSet rs, int col) throws SQLException {
        try {
            long v = rs.getLong(col);
            if (!rs.wasNull()) {
                return v;
            }
        } catch (SQLException ignore) {
            // 非数值列类型，回退按字符串解析
        }
        String s = rs.getString(col);
        return s == null ? 0L : Long.parseLong(s.trim());
    }

    static boolean isNumericType(String typeName) {
        if (typeName == null) {
            return false;
        }
        String upper = typeName.toUpperCase();
        for (String kw : NUMERIC_KEYWORDS) {
            if (upper.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    static boolean isStringType(String typeName) {
        if (typeName == null) {
            return false;
        }
        String upper = typeName.toUpperCase();
        for (String kw : STRING_KEYWORDS) {
            if (upper.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** schema 限定：<schema>.<tableName>；schema 为空时用 <tableName>（依赖连接默认 schema）。与 Catalog/质量执行器一致。 */
    static String qualifyTable(String schema, String tableName) {
        if (schema == null || schema.isEmpty()) {
            return tableName;
        }
        return schema + "." + tableName;
    }

    /**
     * 构建 FROM 子句目标（架构基线 §4.4.3 D3）：
     * external/entity → {@code <schema>.<tableName>}；sql → {@code (<sourceSql>) _t}。
     */
    static String buildFromClause(TableReference ref, String normalizedSchema) {
        if (ref.isSubquery()) {
            return "(" + ref.getSourceSql() + ") _t";
        }
        return qualifyTable(normalizedSchema, ref.getPhysicalTableName());
    }

    /** D5：检测 sql 视图合成列名（形如 {@code <expr_N>}，含 {@code <>} 非标识符字符）。 */
    static boolean isDerivedColumnName(String col) {
        return col != null && col.startsWith("<") && col.contains("expr");
    }

    /** sql 表列结构从 AST 解析的 fields 构造（DatabaseMetaData.getColumns 对子查询不适用）。 */
    private static List<ColumnMeta> columnsFromFields(List<ResolvedTableField> fields, Set<String> filter) {
        List<ColumnMeta> columns = new ArrayList<>(fields.size());
        for (ResolvedTableField f : fields) {
            if (f.getName() == null) {
                continue;
            }
            if (filter != null && !filter.isEmpty() && !filter.contains(f.getName().toUpperCase())) {
                continue;
            }
            columns.add(new ColumnMeta(f.getName(), f.getDataType()));
        }
        return columns;
    }

    private static String normalizeSchema(String schemaPattern) {
        if (schemaPattern == null || schemaPattern.trim().isEmpty()) {
            return null;
        }
        String trimmed = schemaPattern.trim();
        // AR-01: schemaPattern 与 tableName/列名一致走标识符白名单（防 SQL 注入家族）
        validateIdentifier(trimmed);
        return trimmed;
    }

    /** 解析 columns 过滤参数（逗号分隔列名），转大写匹配 JDBC COLUMN_NAME。 */
    private static Set<String> parseColumnsFilter(String columnsFilter) {
        if (columnsFilter == null || columnsFilter.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = new java.util.HashSet<>();
        for (String p : columnsFilter.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) {
                set.add(t.toUpperCase());
            }
        }
        return set;
    }

    static void validateIdentifier(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new NopException(ERR_PROFILING_INVALID_IDENTIFIER)
                    .param("identifier", String.valueOf(identifier));
        }
    }

    /** 提取异常消息（null 时回退类名）。 */
    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }

    /** 列结构元数据（列名 + JDBC 类型名）。 */
    private static class ColumnMeta {
        final String name;
        final String dataType;

        ColumnMeta(String name, String dataType) {
            this.name = name;
            this.dataType = dataType;
        }
    }
}
