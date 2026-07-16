package io.nop.metadata.service.catalog;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.metadata.service.tableref.TableReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * 从 JDBC 数据源收集物理表的运行时统计（行数/索引等），返回结构化快照
 * （{@link CatalogTableStats}），由 {@code NopMetaDataSourceBizModel.collectCatalog} 据此写入 NopMetaCatalog。
 *
 * <p>设计 05 §四 / 架构基线 §2.3.2 + §4.4.3 D1-D5：
 * <ul>
 *   <li><b>行数</b>：便携 {@code SELECT COUNT(*) FROM <fromClause>}（全方言含 H2）。
 *       external/entity：{@code <schemaPattern>.<tableName>}；sql：{@code (<sourceSql>) _t} 子查询。</li>
 *   <li><b>索引</b>：标准 JDBC {@link DatabaseMetaData#getIndexInfo}（便携，统计 distinct 索引名，排除 STATISTIC 行）。
 *       仅 external/entity 适用（物理表）；**sql 子查询无物理索引 → indexCount=null + unavailable 标记**（D4 能力边界：
 *       sql 视图是虚拟表，无物理索引；不伪造 0、不静默跳过）。</li>
 *   <li><b>大小/分区/lastModified</b>：方言特定，首版不实现 → null + {@code details.unavailable} 显式标记
 *       （不静默跳过整行、不伪造 0）</li>
 * </ul>
 *
 * <p>本类不自建连接，由调用方在 callback 内传入已打开的 {@link Connection} + {@link DatabaseMetaData}。
 * 消费 {@link TableReference}（external/entity/sql 三态，架构基线 §4.4.3 D3）。
 */
public class MetaCatalogCollector {

    private static final Logger LOG = LoggerFactory.getLogger(MetaCatalogCollector.class);

    /** inline ErrorCode：标识符（列名/表名/schema）不符合白名单，拒绝拼接防注入。 */
    static final ErrorCode ERR_CATALOG_INVALID_IDENTIFIER =
            ErrorCode.define("metadata.catalog-invalid-identifier",
                    "Identifier (table/schema) does not match whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    "identifier");

    /** SQL 标识符白名单：字母/下划线开头，后跟字母/数字/下划线。用于校验表名/schema，防注入。 */
    private static final java.util.regex.Pattern IDENTIFIER_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * 收集单张表的运行时统计（消费 {@link TableReference}，架构基线 §4.4.3 D3）。
     *
     * <p>单表失败（SQL 异常）直接抛出，由调用方在 per-table try/catch 中收集到 errors 不中断整批。
     *
     * @param conn           已打开的连接（external/sql 由 withConnection 提供，entity 由平台 IJdbcTransaction 提供）
     * @param metaData       连接的 DatabaseMetaData
     * @param ref            table-reference（external/entity/sql 三态）
     * @param schemaPattern  schema 限定（null/空串表示依赖连接默认 schema；sql 子查询忽略此参数）
     * @param productName    DB 产品名（运行时从 DatabaseMetaData 获取，放入 details.extras）
     * @return 该表的运行时统计快照
     * @throws SQLException COUNT/索引查询失败时抛出（交调用方 per-table 隔离）
     */
    public CatalogTableStats collectForTable(Connection conn, DatabaseMetaData metaData,
                                              TableReference ref, String schemaPattern,
                                              String productName) throws SQLException {
        String normalizedSchema = normalizeSchema(schemaPattern);
        String displayTableName = ref.isSubquery() ? "(" + ref.getSourceSql() + ") _t" : ref.getPhysicalTableName();

        CatalogTableStats stats = new CatalogTableStats();
        stats.setTableName(displayTableName);
        if (productName != null) {
            stats.getExtras().put("databaseProductName", productName);
        }
        stats.getExtras().put("tableType", ref.getKind().name().toLowerCase());

        String fromClause = buildFromClause(ref, normalizedSchema);
        stats.setRowCount(countRows(conn, fromClause));

        if (ref.isSubquery()) {
            // sql 子查询无物理索引（D4 能力边界）→ null + 显式 unavailable 标记（不伪造 0、不静默跳过）
            stats.setIndexCount(null);
            stats.markUnavailable("indexCount");
        } else {
            stats.setIndexCount(countIndexes(metaData, normalizedSchema, ref.getPhysicalTableName()));
        }

        // 方言特定统计首版不实现：null + 显式 unavailable 标记（不静默跳过、不伪造 0）
        stats.setSizeBytes(null);
        stats.markUnavailable("sizeBytes");
        stats.setPartitionCount(null);
        stats.markUnavailable("partitionCount");
        stats.setLastModified(null);
        stats.markUnavailable("lastModified");

        return stats;
    }

    /** 便携行数收集：{@code SELECT COUNT(*) FROM <fromClause>}（全方言）。 */
    private long countRows(Connection conn, String fromClause) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + fromClause;
        LOG.info("collectCatalog COUNT: {}", sql);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                throw new SQLException("COUNT(*) returned no row for: " + fromClause);
            }
            return rs.getLong(1);
        }
    }

    /** 便携索引数收集：标准 JDBC getIndexInfo，统计 distinct 索引名（排除 tableIndexStatistic）。 */
    private int countIndexes(DatabaseMetaData metaData, String schema, String tableName) throws SQLException {
        Set<String> indexNames = new HashSet<>();
        ResultSet rs = null;
        try {
            rs = metaData.getIndexInfo(null, schema, tableName, false, false);
            while (rs.next()) {
                short type = rs.getShort("TYPE");
                // tableIndexStatistic (0) 不是真实索引，H2/MySQL 会用它返回表级统计行
                if (type == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String indexName = rs.getString("INDEX_NAME");
                if (indexName != null) {
                    indexNames.add(indexName);
                }
            }
        } finally {
            IoHelper.safeCloseObject(rs);
        }
        return indexNames.size();
    }

    /**
     * 构建 FROM 子句目标（架构基线 §4.4.3 D3）：
     * external/entity → {@code <schema>.<tableName>}（标识符经白名单校验）；
     * sql → {@code (<sourceSql>) _t}（用户显式提供，不校验标识符，同 custom_sql 已知显式风险）。
     */
    private String buildFromClause(TableReference ref, String normalizedSchema) {
        if (ref.isSubquery()) {
            return "(" + ref.getSourceSql() + ") _t";
        }
        String tableName = ref.getPhysicalTableName();
        validateIdentifier(tableName);
        return qualifyTable(normalizedSchema, tableName);
    }

    /** schema 限定：<schemaPattern>.<tableName>；schema 为空时用 <tableName>（依赖连接默认 schema）。 */
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
            throw new NopException(ERR_CATALOG_INVALID_IDENTIFIER)
                    .param("identifier", String.valueOf(identifier));
        }
    }
}
