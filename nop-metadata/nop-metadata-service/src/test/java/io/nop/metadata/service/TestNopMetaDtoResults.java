package io.nop.metadata.service;

import io.nop.api.core.beans.ApiResponse;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.api.dto.AggregationResultDTO;
import io.nop.metadata.api.dto.CollectCatalogResultDTO;
import io.nop.metadata.api.dto.CollectCatalogTableDTO;
import io.nop.metadata.api.dto.CreateSqlTableResultDTO;
import io.nop.metadata.api.dto.ErrorDTO;
import io.nop.metadata.api.dto.ProfileResultDTO;
import io.nop.metadata.api.dto.ProfilingColumnStatsDTO;
import io.nop.metadata.api.dto.QualityRuleResultDTO;
import io.nop.metadata.api.dto.QualityScoreResultDTO;
import io.nop.metadata.api.dto.QueryTableDataResultDTO;
import io.nop.metadata.api.dto.SyncExternalTablesResultDTO;
import io.nop.metadata.api.dto.TestConnectionResultDTO;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify @DataBean DTO JSON serialization round-trip.
 */
public class TestNopMetaDtoResults {

    /**
     * P2-35 项 6（plan 2026-08-16-0549-2）：首方法补 parse-back（F18 形态——stringify → parse →
     * 关键字段相等，非仅 assertNotNull 序列化 smoke）。同批覆盖 P2-20 新增承接字段
     * （ErrorDTO.source/refType/refValue）的 round-trip。
     */
    @Test
    public void testDtoJsonRoundTrip() {
        ErrorDTO original = new ErrorDTO("metadata.test", "msg", "ctx");
        original.setSource("resolution");
        original.setRefType("ruleId");
        original.setRefValue("r-1");
        String json = JsonTool.stringify(original);
        assertNotNull(json);
        assertTrue(json.contains("metadata.test"));

        ErrorDTO back = JsonTool.parseBeanFromText(json, ErrorDTO.class);
        assertEquals("metadata.test", back.getCode());
        assertEquals("msg", back.getMessage());
        assertEquals("ctx", back.getDetail());
        assertEquals("resolution", back.getSource());
        assertEquals("ruleId", back.getRefType());
        assertEquals("r-1", back.getRefValue());

        ApiResponse<ErrorDTO> apiResp = ApiResponse.buildSuccess(original);
        String apiJson = JsonTool.stringify(apiResp);
        assertNotNull(apiJson);
        assertTrue(apiJson.contains("metadata.test"));
    }

    @Test
    public void testProfileResultDtoFields() {
        ProfilingColumnStatsDTO col = new ProfilingColumnStatsDTO();
        col.setColumnName("AMOUNT");
        col.setRowCount(100L);
        col.setNullCount(5L);
        col.setNullRatio(0.05);
        col.setMinValue(1);
        col.setMaxValue(999);
        ProfileResultDTO dto = new ProfileResultDTO();
        dto.setProfilingResultId("p-1");
        dto.setColumnCount(1);
        dto.getColumns().add(col);
        dto.getUnavailable().add("BLOB_COL");
        dto.getErrors().add(new ErrorDTO("metadata.foo", "fail"));

        String json = JsonTool.stringify(dto);
        ProfileResultDTO parsed = JsonTool.parseBeanFromText(json, ProfileResultDTO.class);
        assertEquals("p-1", parsed.getProfilingResultId());
        assertEquals(1, parsed.getColumnCount());
        assertEquals(1, parsed.getColumns().size());
        assertEquals("AMOUNT", parsed.getColumns().get(0).getColumnName());
    }

    @Test
    public void testAggregationResultDtoJson() {
        AggregationResultDTO dto = new AggregationResultDTO();
        dto.getItems().add(new java.util.LinkedHashMap<>(Collections.singletonMap("m1", 100L)));
        String json = JsonTool.stringify(dto);
        AggregationResultDTO parsed = JsonTool.parseBeanFromText(json, AggregationResultDTO.class);
        assertEquals(1, parsed.getItems().size());
    }

    @Test
    public void testQualityScoreResultDtoJson() {
        QualityScoreResultDTO dto = new QualityScoreResultDTO();
        dto.setOverallScore(95.5);
        dto.getDimensionScores().put("completeness", 0.9);
        String json = JsonTool.stringify(dto);
        QualityScoreResultDTO parsed = JsonTool.parseBeanFromText(json, QualityScoreResultDTO.class);
        assertEquals(95.5, parsed.getOverallScore(), 0.01);
    }

    /**
     * F18（plan 2026-08-14-1448-1）：真实 round-trip——对每个 DTO set 有区分度的字段后
     * {@code stringify} → {@code parseBeanFromText} → 断言关键字段回放，而非仅 {@code assertNotNull(serialize)}。
     * 命名与实际行为一致（genuine round-trip，非空壳 smoke）。
     */
    @Test
    public void testDtoJsonRoundTripAllTypes() {
        // TestConnectionResultDTO
        TestConnectionResultDTO conn = new TestConnectionResultDTO();
        conn.setConnected(true);
        conn.setDatabaseProductName("H2");
        TestConnectionResultDTO connBack = JsonTool.parseBeanFromText(
                JsonTool.stringify(conn), TestConnectionResultDTO.class);
        assertTrue(connBack.isConnected());
        assertEquals("H2", connBack.getDatabaseProductName());

        // SyncExternalTablesResultDTO
        SyncExternalTablesResultDTO sync = new SyncExternalTablesResultDTO();
        sync.setSyncedTableCount(7);
        SyncExternalTablesResultDTO syncBack = JsonTool.parseBeanFromText(
                JsonTool.stringify(sync), SyncExternalTablesResultDTO.class);
        assertEquals(7, syncBack.getSyncedTableCount());

        // CollectCatalogResultDTO
        CollectCatalogResultDTO catalog = new CollectCatalogResultDTO();
        catalog.setTableCount(3);
        CollectCatalogResultDTO catalogBack = JsonTool.parseBeanFromText(
                JsonTool.stringify(catalog), CollectCatalogResultDTO.class);
        assertEquals(3, catalogBack.getTableCount());

        // CreateSqlTableResultDTO
        CreateSqlTableResultDTO sql = new CreateSqlTableResultDTO();
        sql.setMetaTableId("t-1");
        sql.setTableName("orders");
        CreateSqlTableResultDTO sqlBack = JsonTool.parseBeanFromText(
                JsonTool.stringify(sql), CreateSqlTableResultDTO.class);
        assertEquals("t-1", sqlBack.getMetaTableId());
        assertEquals("orders", sqlBack.getTableName());

        // QueryTableDataResultDTO
        QueryTableDataResultDTO query = new QueryTableDataResultDTO();
        query.setTableType("external");
        QueryTableDataResultDTO queryBack = JsonTool.parseBeanFromText(
                JsonTool.stringify(query), QueryTableDataResultDTO.class);
        assertEquals("external", queryBack.getTableType());

        // QualityRuleResultDTO（P2-20：含新增承接字段 ruleName/actualValue/expectedValue 的 round-trip）
        QualityRuleResultDTO rule = new QualityRuleResultDTO();
        rule.setQualityRuleId("qr-1");
        rule.setStatus("PASS");
        rule.setRuleName("row-count-check");
        rule.setActualValue(42.0);
        rule.setExpectedValue(10.0);
        QualityRuleResultDTO ruleBack = JsonTool.parseBeanFromText(
                JsonTool.stringify(rule), QualityRuleResultDTO.class);
        assertEquals("qr-1", ruleBack.getQualityRuleId());
        assertEquals("PASS", ruleBack.getStatus());
        assertEquals("row-count-check", ruleBack.getRuleName());
        assertEquals(42.0, ruleBack.getActualValue(), 0.001);
        assertEquals(10.0, ruleBack.getExpectedValue(), 0.001);
    }
}
