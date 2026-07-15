package io.nop.metadata.service.catalog;

import io.nop.commons.util.IoHelper;
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
 * 从外部 jdbc 数据源收集物理表的运行时统计（行数/索引等），返回结构化快照
 * （{@link CatalogTableStats}），由 {@code NopMetaDataSourceBizModel.collectCatalog} 据此写入 NopMetaCatalog。
 *
 * <p>设计 05 §四 / 架构基线 §2.3.2：
 * <ul>
 *   <li><b>行数</b>：便携 {@code SELECT COUNT(*) FROM <限定表名>}（全方言含 H2）</li>
 *   <li><b>索引</b>：标准 JDBC {@link DatabaseMetaData#getIndexInfo}（便携，统计 distinct 索引名，排除 STATISTIC 行）</li>
 *   <li><b>大小/分区/lastModified</b>：方言特定，首版不实现 → null + {@code details.unavailable} 显式标记
 *       （不静默跳过整行、不伪造 0）</li>
 * </ul>
 *
 * <p>本类不自建连接，由调用方在 P2-1 {@code withConnection} callback 内传入已打开的
 * {@link Connection} + {@link DatabaseMetaData}。
 *
 * <p>schema 限定（D1）：NopMetaTable 无 schema 列，{@code schemaPattern} 限定 COUNT/索引查询的物理 schema——
 * 传入时用 {@code <schemaPattern>.<tableName>}；不传时依赖连接默认 schema。多 schema 同名表为已知限制（follow-up）。
 */
public class MetaCatalogCollector {

    private static final Logger LOG = LoggerFactory.getLogger(MetaCatalogCollector.class);

    /**
     * 收集单张表的运行时统计。
     *
     * <p>单表失败（SQL 异常）直接抛出，由调用方在 per-table try/catch 中收集到 errors 不中断整批。
     *
     * @param conn           已打开的连接（由 withConnection callback 提供，本方法不关闭连接）
     * @param metaData       连接的 DatabaseMetaData
     * @param schemaPattern  schema 限定（null/空串表示依赖连接默认 schema）
     * @param tableName      物理表名（来自 external NopMetaTable.tableName）
     * @param productName    DB 产品名（运行时从 DatabaseMetaData 获取，放入 details.extras）
     * @return 该表的运行时统计快照
     * @throws SQLException COUNT/索引查询失败时抛出（交调用方 per-table 隔离）
     */
    public CatalogTableStats collectForTable(Connection conn, DatabaseMetaData metaData,
                                             String schemaPattern, String tableName,
                                             String productName) throws SQLException {
        String normalizedSchema = normalizeSchema(schemaPattern);
        CatalogTableStats stats = new CatalogTableStats();
        stats.setTableName(tableName);
        if (productName != null) {
            stats.getExtras().put("databaseProductName", productName);
        }

        stats.setRowCount(countRows(conn, normalizedSchema, tableName));
        stats.setIndexCount(countIndexes(metaData, normalizedSchema, tableName));

        // 方言特定统计首版不实现：null + 显式 unavailable 标记（不静默跳过、不伪造 0）
        stats.setSizeBytes(null);
        stats.markUnavailable("sizeBytes");
        stats.setPartitionCount(null);
        stats.markUnavailable("partitionCount");
        stats.setLastModified(null);
        stats.markUnavailable("lastModified");

        return stats;
    }

    /** 便携行数收集：{@code SELECT COUNT(*) FROM <限定表名>}（全方言）。 */
    private long countRows(Connection conn, String schema, String tableName) throws SQLException {
        String qualified = qualifyTable(schema, tableName);
        String sql = "SELECT COUNT(*) FROM " + qualified;
        LOG.info("collectCatalog COUNT: {}", sql);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                throw new SQLException("COUNT(*) returned no row for table: " + qualified);
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
}
