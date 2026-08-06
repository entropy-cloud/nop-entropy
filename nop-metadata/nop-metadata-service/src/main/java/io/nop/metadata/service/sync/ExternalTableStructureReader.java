
package io.nop.metadata.service.sync;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 从外部 jdbc 数据源扫描物理表结构（表 + 列），返回结构化快照。
 *
 * <p>结构读取走标准 JDBC {@link DatabaseMetaData#getTables(String, String, String, String[])}
 * 与 {@link DatabaseMetaData#getColumns(String, String, String, String)}，跨方言可移植，
 * 等价于 {@code information_schema.COLUMNS} 信息（架构基线 §2.5.1 / 设计 05 §4.4）。
 *
 * <p>本类不自建连接，由调用方在 P2-1 {@code withConnection} callback 内传入已打开的
 * {@link Connection} + {@link DatabaseMetaData}。方言由 callback 内
 * {@link DatabaseMetaData#getDatabaseProductName()} 运行时获取，不依赖任何持久化字段。
 *
 * <p>首版支持的方言：MySQL / PostgreSQL / H2。其余方言（ClickHouse {@code system.columns}、
 * Oracle 等）在入口显式抛 {@link NopException}（携带
 * {@link NopMetadataErrors#ERR_DATASOURCE_TYPE_NOT_SUPPORTED}；快速失败，非静默跳过）。
 *
 * <p>每张扫描到的表读取其 {@code TABLE_SCHEM} 并填入 {@link ExternalTableInfo#getSchema()}，
 * 供 BizModel 持久化到 {@code NopMetaTable.metaSchema}（架构基线 §2.3.2 / §2.5.1）。
 */
public class ExternalTableStructureReader {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalTableStructureReader.class);

    private static final String[] TABLE_TYPES = new String[]{"TABLE", "VIEW"};

    /**
     * 扫描外部库的物理表结构。
     *
     * @param conn           已打开的连接（由 withConnection callback 提供，本方法不关闭连接）
     * @param metaData       连接的 DatabaseMetaData
     * @param schemaPattern  schema 过滤模式（null/空串表示不过滤，扫描全部 schema）
     * @return 扫描到的表结构列表（每张表含其列结构），不含表时返回空列表
     * @throws NopException 目标库方言首版不支持时显式抛出（携带 ERR_DATASOURCE_TYPE_NOT_SUPPORTED；非静默跳过）
     */
    public List<ExternalTableInfo> read(Connection conn, DatabaseMetaData metaData, String schemaPattern) {
        String productName = requireSupportedDialect(metaData);
        String schema = normalizeSchema(schemaPattern);

        LOG.info("syncExternalTables scanning: dialect={}, schemaPattern={}", productName, schema);

        List<ExternalTableInfo> tables = new ArrayList<>();
        ResultSet rs = null;
        try {
            // catalog=null 表示不过滤 catalog；tableNamePattern="%" 表示全部表
            rs = metaData.getTables(null, schema, "%", TABLE_TYPES);
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName == null || tableName.isEmpty()) {
                    continue;
                }
                // 读取 TABLE_SCHEM（plan 2026-07-17-0852-3）：null/空（部分方言/无 schema 行）留空，
                // 不伪造、不静默跳过该表——沿用 null=不过滤语义（架构基线 §2.3.2 D1）
                String tableSchema = rs.getString("TABLE_SCHEM");
                ExternalTableInfo info = new ExternalTableInfo();
                info.setTableName(tableName);
                info.setSchema(tableSchema);
                info.setTableType(rs.getString("TABLE_TYPE"));
                info.setRemark(rs.getString("REMARKS"));
                // 列结构读取：优先用该行实际的 TABLE_SCHEM（精确，比 schemaPattern 更具体），
                // TABLE_SCHEM 缺失时回退到 schemaPattern 入参（防御性，保持原行为）
                String columnSchema = (tableSchema != null && !tableSchema.isEmpty()) ? tableSchema : schema;
                readColumns(metaData, columnSchema, tableName, info);
                tables.add(info);
            }
        } catch (SQLException e) {
            // AR-23⑤（R8.2）：扫描级故障（连接中断/权限/元数据异常）与"方言不支持"区分——
            // 真实故障携带真实 productName + 原始异常消息，不再误报 ERR_DIALECT_NOT_SUPPORTED + "unknown"
            throw new NopMetadataException(NopMetadataErrors.ERR_EXTERNAL_TABLE_SCAN_FAILED, e)
                    .param(NopMetadataErrors.ARG_DATABASE_PRODUCT_NAME, productName)
                    .param(NopMetadataErrors.ARG_ERROR, e.getMessage());
        } finally {
            IoHelper.safeCloseObject(rs);
        }
        return tables;
    }

    private void readColumns(DatabaseMetaData metaData, String schemaPattern, String tableName,
                             ExternalTableInfo info) throws SQLException {
        ResultSet colRs = null;
        try {
            colRs = metaData.getColumns(null, schemaPattern, tableName, null);
            while (colRs.next()) {
                ExternalColumnInfo col = new ExternalColumnInfo();
                col.setColumnName(colRs.getString("COLUMN_NAME"));
                col.setDataType(colRs.getString("TYPE_NAME"));
                col.setPrecision(safeInt(colRs, "COLUMN_SIZE"));
                col.setScale(safeInt(colRs, "DECIMAL_DIGITS"));
                col.setNullable(colRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                col.setRemark(colRs.getString("REMARKS"));
                col.setOrdinal(safeOrdinal(colRs, "ORDINAL_POSITION"));
                col.setDefaultValue(colRs.getString("COLUMN_DEF"));
                info.getColumns().add(col);
            }
        } finally {
            IoHelper.safeCloseObject(colRs);
        }
    }

    private String requireSupportedDialect(DatabaseMetaData metaData) {
        String productName;
        try {
            productName = metaData.getDatabaseProductName();
        } catch (SQLException e) {
            // AR-23⑤（R8.2）：getDatabaseProductName 失败是元数据访问故障（非"方言不支持"）——
            // productName 此时确实不可得，如实记 "unknown"，但错误码归类为扫描故障
            throw new NopMetadataException(NopMetadataErrors.ERR_EXTERNAL_TABLE_SCAN_FAILED, e)
                    .param(NopMetadataErrors.ARG_DATABASE_PRODUCT_NAME, "unknown")
                    .param(NopMetadataErrors.ARG_ERROR, e.getMessage());
        }
        requireSupportedProductName(productName);
        return productName;
    }

    /**
     * 方言白名单门禁：仅 MySQL / PostgreSQL / H2（H2 同时服务 AutoTest 真实建连路径）。
     * ClickHouse（{@code system.columns}）/ Oracle 等首版显式不支持，抛
     * {@link NopException}（{@link NopMetadataErrors#ERR_DATASOURCE_TYPE_NOT_SUPPORTED}，
     * plan 2026-07-19-1250-3 Phase 2 维度09-07：替代 UnsupportedOperationException）。
     *
     * <p>包级可见以便单元测试直接验证门禁（无需真实非 H2 数据库即可覆盖"不支持方言显式失败"路径）。
     */
    static void requireSupportedProductName(String productName) {
        if (productName == null || !isSupportedDialect(productName)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED)
                    .param(NopMetadataErrors.ARG_DATABASE_PRODUCT_NAME, String.valueOf(productName));
        }
    }

    /** 方言白名单判断（包级可见以便单元测试）。 */
    static boolean isSupportedDialect(String productName) {
        String p = productName.toLowerCase();
        return p.contains("mysql") || p.contains("postgresql") || p.equals("h2");
    }

    private static String normalizeSchema(String schemaPattern) {
        // 空白串视为不过滤（与 null 一致），交由 JDBC 解释
        if (schemaPattern == null || schemaPattern.trim().isEmpty()) {
            return null;
        }
        return schemaPattern.trim();
    }

    /** AR-23⑤（R8.2）：NULL → null（保留 JDBC 原始语义，不伪造 0）；非 NULL → Integer。 */
    private static Integer safeInt(ResultSet rs, String columnLabel) throws SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? null : value;
    }

    /** ORDINAL_POSITION 无 NULL 语义：NULL/缺省保底 0（保持 int 契约）。 */
    private static int safeOrdinal(ResultSet rs, String columnLabel) throws SQLException {
        Integer value = safeInt(rs, columnLabel);
        return value != null ? value : 0;
    }

}
