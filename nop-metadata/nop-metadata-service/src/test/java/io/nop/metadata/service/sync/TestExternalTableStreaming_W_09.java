
package io.nop.metadata.service.sync;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W-09 回归钉（plan 2528 红⑦，CRA 5.5 C5-001）：syncExternalTables 流式化——逐表产出即消费。
 *
 * <p>缺陷形态（pre-fix）：{@code read} 全量累积 {@code List<ExternalTableInfo>}（每表全列）后才
 * 逐表 upsert——万表×40 列≈100MB+ 峰值堆 + 外部连接占用；规模不受平台配置约束。第 k 表读列失败
 * 时已读完成的 1..k−1 表<b>不交付</b>（全量物化对正确性无贡献）。
 *
 * <p>修复后（裁定⑦，M3 形态）：流式逐表产出——第 k 表读列 SQLException 时表 1..k−1 已交付消费方
 * （upsert 已执行），执行以 {@code ERR_EXTERNAL_TABLE_SCAN_FAILED} 终止（scan 级 SQLException wrap
 * 落点保持在 reader 内部——AR-23⑤ 抛出面零漂移；第 k 表不进 per-table errors——该通道只承载
 * upsert 自身失败）。变异锚定：拆除流式（回退全量物化）→ 本钉复红。
 */
public class TestExternalTableStreaming_W_09 {

    /** 3 表 catalog fixture：T1/T2 列读取正常，T3 读列注入 SQLException（第 k=3 表 scan 级故障）。 */
    private DatabaseMetaData threeTableCatalogWithFailureAtThird() throws SQLException {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        ResultSet tablesRs = mock(ResultSet.class);
        when(tablesRs.next()).thenReturn(true, true, true, false);
        when(tablesRs.getString("TABLE_NAME")).thenReturn("T1", "T2", "T3");
        when(tablesRs.getString("TABLE_SCHEM")).thenReturn("PUBLIC");
        when(tablesRs.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(tablesRs.getString("REMARKS")).thenReturn("r");

        ResultSet colRs = mock(ResultSet.class);
        when(colRs.next()).thenReturn(true, false);
        when(colRs.getString("COLUMN_NAME")).thenReturn("ID");
        when(colRs.getString("TYPE_NAME")).thenReturn("INTEGER");
        when(colRs.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNullable);
        when(colRs.getInt("ORDINAL_POSITION")).thenReturn(1);
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(tablesRs);
        when(metaData.getColumns(any(), anyString(), anyString(), any()))
                .thenReturn(colRs)
                .thenReturn(colRs)
                .thenThrow(new SQLException("catalog cursor lost at T3"));
        return metaData;
    }

    /** 红⑦ 主形态：第 3 表读列失败时表 1..2 已流式交付（pre-fix 全量物化 0 表交付 → red）。 */
    @Test
    public void testTablesDeliveredBeforeScanFailure() throws Exception {
        DatabaseMetaData metaData = threeTableCatalogWithFailureAtThird();
        List<String> delivered = new ArrayList<>();

        NopException ex = assertThrows(NopException.class,
                () -> new ExternalTableStructureReader()
                        .forEachTable(null, metaData, null, table -> delivered.add(table.getTableName())),
                "scan-level failure at table k must terminate with ERR_EXTERNAL_TABLE_SCAN_FAILED (AR-23⑤)");

        assertEquals(NopMetadataErrors.ERR_EXTERNAL_TABLE_SCAN_FAILED.getErrorCode(), ex.getErrorCode(),
                "scan failure must keep its AR-23⑤ wrap (wrap point stays inside the reader)");
        assertEquals(List.of("T1", "T2"), delivered,
                "tables 1..k-1 must already be delivered (streamed) before the failure at table k"
                        + " -- pre-fix full materialization delivers none");
        assertTrue(!delivered.contains("T3"),
                "the failing table k must not be delivered (per-table errors channel is upsert-only)");
    }

    /** 全量成功路径：全部表逐表交付（流式不丢表）。 */
    @Test
    public void testAllTablesDeliveredOnSuccess() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        ResultSet tablesRs = mock(ResultSet.class);
        when(tablesRs.next()).thenReturn(true, true, false);
        when(tablesRs.getString("TABLE_NAME")).thenReturn("TA", "TB");
        when(tablesRs.getString("TABLE_SCHEM")).thenReturn("PUBLIC");
        when(tablesRs.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(tablesRs.getString("REMARKS")).thenReturn(null);

        ResultSet colRs = mock(ResultSet.class);
        when(colRs.next()).thenReturn(false);
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(tablesRs);
        when(metaData.getColumns(any(), anyString(), anyString(), any())).thenReturn(colRs);

        List<String> delivered = new ArrayList<>();
        new ExternalTableStructureReader().forEachTable(null, metaData, null,
                table -> delivered.add(table.getTableName()));

        assertEquals(List.of("TA", "TB"), delivered, "all tables must be delivered on a clean scan");
    }

    /** read→List 兼容形态（既有 AR-23⑤/方言/NULL 精度钉载体）与流式入口同语义。 */
    @Test
    public void testListFormStillAccumulatesSameTables() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        ResultSet tablesRs = mock(ResultSet.class);
        when(tablesRs.next()).thenReturn(true, false);
        when(tablesRs.getString("TABLE_NAME")).thenReturn("T1");
        when(tablesRs.getString("TABLE_SCHEM")).thenReturn("PUBLIC");
        when(tablesRs.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(tablesRs.getString("REMARKS")).thenReturn(null);

        ResultSet colRs = mock(ResultSet.class);
        when(colRs.next()).thenReturn(false);
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(tablesRs);
        when(metaData.getColumns(any(), anyString(), anyString(), any())).thenReturn(colRs);

        List<ExternalTableInfo> tables = new ExternalTableStructureReader().read(null, metaData, null);
        assertEquals(1, tables.size(), "List compatibility form keeps its accumulate semantics");
        assertEquals("T1", tables.get(0).getTableName());
    }
}
