package io.nop.metadata.service.sync;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link ExternalTableStructureReader} 的方言门禁：首版仅 MySQL/PostgreSQL/H2 支持，
 * 其余方言（ClickHouse/Oracle 等）在入口显式抛 {@link NopException}（携带
 * {@code ERR_DATASOURCE_TYPE_NOT_SUPPORTED}；非静默跳过）。
 *
 * <p>plan 2026-07-19-1250-3 Phase 2 维度09-07：从 UnsupportedOperationException 改为 NopException +
 * 模块 ErrorCode 常量。同包测试，直访包级门禁方法，无需真实非 H2 数据库即可覆盖"不支持方言显式失败"路径。
 *
 * <p>AR-23⑤（R8.2）：扫描级故障（连接中断/权限）与"方言不支持"区分——真实扫描故障抛
 * {@code ERR_EXTERNAL_TABLE_SCAN_FAILED}（携带真实 productName + 异常消息），不再误报
 * {@code ERR_DIALECT_NOT_SUPPORTED}；NULL 精度（COLUMN_SIZE / DECIMAL_DIGITS）保留为 null 不再归 0。
 */
public class TestExternalTableStructureReader {

    private final ExternalTableStructureReader reader = new ExternalTableStructureReader();

    @Test
    public void testSupportedDialectsRecognized() {
        assertTrue(ExternalTableStructureReader.isSupportedDialect("MySQL"));
        assertTrue(ExternalTableStructureReader.isSupportedDialect("PostgreSQL"));
        assertTrue(ExternalTableStructureReader.isSupportedDialect("H2"));
    }

    @Test
    public void testUnsupportedDialectsRejected() {
        assertFalse(ExternalTableStructureReader.isSupportedDialect("Oracle"));
        assertFalse(ExternalTableStructureReader.isSupportedDialect("ClickHouse"));
        assertFalse(ExternalTableStructureReader.isSupportedDialect("Microsoft SQL Server"));
    }

    @Test
    public void testUnsupportedDialectThrowsExplicitly() {
        // plan 2026-07-19-1250-3 Phase 2：不支持方言必须显式抛 NopException（含 ErrorCode），不静默跳过
        assertThrows(NopException.class,
                () -> ExternalTableStructureReader.requireSupportedProductName("Oracle"),
                "unsupported dialect must throw NopException");
        assertThrows(NopException.class,
                () -> ExternalTableStructureReader.requireSupportedProductName(null),
                "null dialect must throw NopException");
    }

    @Test
    public void testSupportedDialectDoesNotThrow() {
        // 已支持方言不应抛异常
        ExternalTableStructureReader.requireSupportedProductName("MySQL");
        ExternalTableStructureReader.requireSupportedProductName("PostgreSQL");
        ExternalTableStructureReader.requireSupportedProductName("H2");
    }

    // ===== AR-23⑤（R8.2）：扫描故障错误归类 + NULL 精度 =====

    /** getTables 扫描故障（真实 SQLException）→ ERR_EXTERNAL_TABLE_SCAN_FAILED（非方言错误）+ 真实 productName/消息。 */
    @Test
    public void testScanFailureThrowsScanErrorNotDialectError() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");
        when(metaData.getTables(any(), any(), any(), any()))
                .thenThrow(new SQLException("connection reset by peer"));

        NopException ex = assertThrows(NopException.class, () -> reader.read(null, metaData, null));
        assertEquals(NopMetadataErrors.ERR_EXTERNAL_TABLE_SCAN_FAILED.getErrorCode(), ex.getErrorCode(),
                "scan failure must be classified as scan error, not dialect-not-supported (AR-23⑤)");
        assertEquals("MySQL", String.valueOf(ex.getParam(NopMetadataErrors.ARG_DATABASE_PRODUCT_NAME)),
                "real productName must be carried (was hardcoded 'unknown' before AR-23⑤)");
        assertTrue(String.valueOf(ex.getParam(NopMetadataErrors.ARG_ERROR)).contains("connection reset"),
                "original error message must be preserved: " + ex.getParam(NopMetadataErrors.ARG_ERROR));
    }

    /** getDatabaseProductName 本身失败 → ERR_EXTERNAL_TABLE_SCAN_FAILED（元数据访问故障，非方言不支持）。 */
    @Test
    public void testGetDatabaseProductNameFailureThrowsScanError() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenThrow(new SQLException("metadata access denied"));

        NopException ex = assertThrows(NopException.class, () -> reader.read(null, metaData, null));
        assertEquals(NopMetadataErrors.ERR_EXTERNAL_TABLE_SCAN_FAILED.getErrorCode(), ex.getErrorCode(),
                "getDatabaseProductName failure must be scan error, not dialect-not-supported (AR-23⑤)");
    }

    /** 方言门禁不误伤：不支持方言仍抛 ERR_DATASOURCE_TYPE_NOT_SUPPORTED。 */
    @Test
    public void testUnsupportedDialectStillDialectGateError() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("Oracle");

        NopException ex = assertThrows(NopException.class, () -> reader.read(null, metaData, null));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode(),
                "dialect gate must keep its own error code (not misclassified as scan failure)");
    }

    /** COLUMN_SIZE / DECIMAL_DIGITS 为 NULL → precision/scale 保留 null（不再归 0）；ORDINAL_POSITION 保持 int。 */
    @Test
    public void testNullPrecisionAndScalePreservedAsNull() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        ResultSet tablesRs = mock(ResultSet.class);
        when(tablesRs.next()).thenReturn(true, false);
        when(tablesRs.getString("TABLE_NAME")).thenReturn("T1");
        when(tablesRs.getString("TABLE_SCHEM")).thenReturn("PUBLIC");
        when(tablesRs.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(tablesRs.getString("REMARKS")).thenReturn("r");
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(tablesRs);

        ResultSet colsRs = mock(ResultSet.class);
        when(colsRs.next()).thenReturn(true, false);
        when(colsRs.getString("COLUMN_NAME")).thenReturn("C1");
        when(colsRs.getString("TYPE_NAME")).thenReturn("VARCHAR");
        when(colsRs.getString("REMARKS")).thenReturn("col remark");
        when(colsRs.getString("COLUMN_DEF")).thenReturn(null);
        when(colsRs.getInt("COLUMN_SIZE")).thenReturn(0);
        when(colsRs.getInt("DECIMAL_DIGITS")).thenReturn(0);
        when(colsRs.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNullable);
        when(colsRs.getInt("ORDINAL_POSITION")).thenReturn(1);
        // wasNull 调用序：COLUMN_SIZE→true, DECIMAL_DIGITS→true, NULLABLE→false, ORDINAL_POSITION→false
        when(colsRs.wasNull()).thenReturn(true, true, false, false);
        when(metaData.getColumns(any(), any(), any(), any())).thenReturn(colsRs);

        List<ExternalTableInfo> tables = reader.read(null, metaData, null);
        assertEquals(1, tables.size());
        assertEquals(1, tables.get(0).getColumns().size());
        ExternalColumnInfo col = tables.get(0).getColumns().get(0);
        // Integer 局部变量保证修复前后均可编译（修复前 getPrecision() 返回 int 装箱为 0 → assertNull red）
        Integer precision = col.getPrecision();
        Integer scale = col.getScale();
        assertNull(precision, "NULL COLUMN_SIZE must map to null precision (was 0 before AR-23⑤)");
        assertNull(scale, "NULL DECIMAL_DIGITS must map to null scale (was 0 before AR-23⑤)");
        assertEquals(1, col.getOrdinal(), "ORDINAL_POSITION has no NULL semantics - stays int");
        assertTrue(col.isNullable());
    }
}
