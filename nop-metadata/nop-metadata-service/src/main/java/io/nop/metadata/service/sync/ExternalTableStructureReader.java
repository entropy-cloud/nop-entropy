package io.nop.metadata.service.sync;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
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
 * Oracle 等）在入口显式抛 {@link UnsupportedOperationException}（快速失败，非静默跳过）。
 *
 * <p>每张扫描到的表读取其 {@code TABLE_SCHEM} 并填入 {@link ExternalTableInfo#getSchema()}，
 * 供 BizModel 持久化到 {@code NopMetaTable.schema}（架构基线 §2.3.2 / §2.5.1）。
 */
public class ExternalTableStructureReader {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalTableStructureReader.class);

    static final String ERR_DIALECT_NOT_SUPPORTED = "metadata.dialect-not-supported";

    private static final String[] TABLE_TYPES = new String[]{"TABLE", "VIEW"};

    /**
     * 扫描外部库的物理表结构。
     *
     * @param conn           已打开的连接（由 withConnection callback 提供，本方法不关闭连接）
     * @param metaData       连接的 DatabaseMetaData
     * @param schemaPattern  schema 过滤模式（null/空串表示不过滤，扫描全部 schema）
     * @return 扫描到的表结构列表（每张表含其列结构），不含表时返回空列表
     * @throws UnsupportedOperationException 目标库方言首版不支持时显式抛出（非静默跳过）
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
            throw new NopException(newErrorCode(ERR_DIALECT_NOT_SUPPORTED,
                    "Failed to read table structure from external datasource: {error}", "error"))
                    .param("error", e.getMessage()).cause(e);
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
                col.setOrdinal(safeInt(colRs, "ORDINAL_POSITION"));
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
            throw new NopException(newErrorCode(ERR_DIALECT_NOT_SUPPORTED,
                    "Failed to read database product name: {error}", "error"))
                    .param("error", e.getMessage()).cause(e);
        }
        requireSupportedProductName(productName);
        return productName;
    }

    /**
     * 方言白名单门禁：仅 MySQL / PostgreSQL / H2（H2 同时服务 AutoTest 真实建连路径）。
     * ClickHouse（{@code system.columns}）/ Oracle 等首版显式不支持，抛
     * {@link UnsupportedOperationException}（快速失败，非静默跳过）。
     *
     * <p>包级可见以便单元测试直接验证门禁（无需真实非 H2 数据库即可覆盖"不支持方言显式失败"路径）。
     */
    static void requireSupportedProductName(String productName) {
        if (productName == null || !isSupportedDialect(productName)) {
            throw new UnsupportedOperationException(
                    "External table sync not yet implemented for dialect: " + productName
                            + ". Supported dialects: MySQL, PostgreSQL, H2.");
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

    private static int safeInt(ResultSet rs, String columnLabel) throws SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? 0 : value;
    }

    private static io.nop.api.core.exceptions.ErrorCode newErrorCode(String code, String desc, String... params) {
        return io.nop.api.core.exceptions.ErrorCode.define(code, desc, params);
    }
}
