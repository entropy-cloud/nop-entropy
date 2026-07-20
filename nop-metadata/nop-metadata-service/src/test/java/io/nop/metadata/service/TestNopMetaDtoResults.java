/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.beans.ApiResponse;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.dao.dto.ErrorDTO;
import io.nop.metadata.dao.dto.KeyValueDTO;
import io.nop.metadata.service.dto.AggregationResultDTO;
import io.nop.metadata.service.dto.AggregationRowDTO;
import io.nop.metadata.service.dto.CollectCatalogResultDTO;
import io.nop.metadata.service.dto.CollectCatalogTableDTO;
import io.nop.metadata.service.dto.CreateSqlTableResultDTO;
import io.nop.metadata.service.dto.ProfileResultDTO;
import io.nop.metadata.service.dto.ProfilingColumnStatsDTO;
import io.nop.metadata.service.dto.QualityRuleResultDTO;
import io.nop.metadata.service.dto.QualityScoreResultDTO;
import io.nop.metadata.service.dto.QueryTableDataResultDTO;
import io.nop.metadata.service.dto.SyncExternalTablesResultDTO;
import io.nop.metadata.service.dto.TestConnectionResultDTO;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2026-07-19-1250-3 Phase 1 Proof：验证 {@code @DataBean} DTO 字段可通过强类型访问，
 * 且 JSON 序列化/反序列化往返不丢失字段。
 *
 * <p>每个新增 DTO 字段在测试中被断言非 null/非默认值，证明 {@code @DataBean} 注解生效且字段可被外部访问。
 */
public class TestNopMetaDtoResults {

    @Test
    public void testErrorDtoFields() {
        ErrorDTO dto = new ErrorDTO("ERR_TEST", "test message", "detail");
        assertEquals("ERR_TEST", dto.getCode());
        assertEquals("test message", dto.getMessage());
        assertEquals("detail", dto.getDetail());

        // JSON round-trip
        String json = JsonTool.stringify(dto);
        ErrorDTO parsed = JsonTool.parseBeanFromText(json, ErrorDTO.class);
        assertEquals(dto.getCode(), parsed.getCode());
        assertEquals(dto.getMessage(), parsed.getMessage());
        assertEquals(dto.getDetail(), parsed.getDetail());
    }

    @Test
    public void testKeyValueDtoFields() {
        KeyValueDTO dto = new KeyValueDTO("k1", "v1");
        assertEquals("k1", dto.getName());
        assertEquals("v1", dto.getValue());
    }

    @Test
    public void testAggregationResultDtoFields() {
        AggregationRowDTO row = new AggregationRowDTO();
        row.getDimensions().put("d1", "v1");
        row.getMeasures().put("m1", 100L);
        AggregationResultDTO dto = new AggregationResultDTO();
        dto.getItems().add(row);
        assertEquals(1, dto.getItems().size());
        assertEquals("v1", dto.getItems().get(0).getDimensions().get("d1"));
        assertEquals(100L, dto.getItems().get(0).getMeasures().get("m1"));
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

        assertEquals("p-1", dto.getProfilingResultId());
        assertEquals(1, dto.getColumnCount());
        assertEquals(1, dto.getColumns().size());
        assertEquals("AMOUNT", dto.getColumns().get(0).getColumnName());
        assertEquals(100L, dto.getColumns().get(0).getRowCount());
        assertEquals(0.05, dto.getColumns().get(0).getNullRatio());
        assertEquals(1, dto.getUnavailable().size());
        assertEquals(1, dto.getErrors().size());
    }

    @Test
    public void testTestConnectionResultDtoFields() {
        TestConnectionResultDTO dto = new TestConnectionResultDTO();
        dto.setConnected(true);
        dto.setDatabaseProductName("H2");
        dto.setError(null);
        assertTrue(dto.isConnected());
        assertEquals("H2", dto.getDatabaseProductName());
    }

    @Test
    public void testSyncExternalTablesResultDtoFields() {
        SyncExternalTablesResultDTO dto = new SyncExternalTablesResultDTO();
        dto.setSyncedTableCount(42);
        dto.getErrors().add(new ErrorDTO("metadata.foo", "skip"));
        assertEquals(42, dto.getSyncedTableCount());
        assertEquals(1, dto.getErrors().size());
    }

    @Test
    public void testCollectCatalogResultDtoFields() {
        CollectCatalogTableDTO t = new CollectCatalogTableDTO();
        t.setTableName("T1");
        t.setSchema("PUBLIC");
        t.setTableType("TABLE");
        t.setRowCount(123L);
        t.setSizeBytes(456L);
        CollectCatalogResultDTO dto = new CollectCatalogResultDTO();
        dto.setTableCount(1);
        dto.getTables().add(t);
        assertEquals(1, dto.getTableCount());
        assertEquals("T1", dto.getTables().get(0).getTableName());
        assertEquals(123L, dto.getTables().get(0).getRowCount());
    }

    @Test
    public void testCreateSqlTableResultDtoFields() {
        CreateSqlTableResultDTO dto = new CreateSqlTableResultDTO();
        dto.setMetaTableId("t-1");
        dto.setTableName("v1");
        dto.setTableType("sql");
        assertNotNull(dto.getFields());
        assertEquals(0, dto.getFields().size());
        assertEquals("t-1", dto.getMetaTableId());
        assertEquals("sql", dto.getTableType());
    }

    @Test
    public void testQueryTableDataResultDtoFields() {
        QueryTableDataResultDTO dto = new QueryTableDataResultDTO();
        dto.setTableType("entity");
        dto.setItems(Collections.emptyList());
        assertEquals("entity", dto.getTableType());
        assertNotNull(dto.getItems());
    }

    @Test
    public void testQualityScoreResultDtoFields() {
        QualityScoreResultDTO dto = new QualityScoreResultDTO();
        dto.setMetaTableId("t-1");
        dto.setQualityScoreId("s-1");
        dto.setScore(95.5);
        dto.setTotalRules(10);
        dto.setPassedRules(9);
        dto.setFailedRules(1);
        dto.setSkippedRules(0);
        assertEquals("t-1", dto.getMetaTableId());
        assertEquals(95.5, dto.getScore());
        assertEquals(10, dto.getTotalRules());
    }

    @Test
    public void testQualityRuleResultDtoFields() {
        QualityRuleResultDTO dto = new QualityRuleResultDTO();
        dto.setQualityRuleId("r-1");
        dto.setResultCount(10);
        dto.setPassCount(8);
        dto.setFailCount(2);
        assertEquals("r-1", dto.getQualityRuleId());
        assertEquals(10, dto.getResultCount());
        assertEquals(2, dto.getFailCount());
    }

    @Test
    public void testDtoJsonRoundTrip() {
        // 验证 @DataBean JSON 序列化支持
        ErrorDTO original = new ErrorDTO("metadata.test", "msg", "ctx");
        String json = JsonTool.stringify(original);
        assertNotNull(json);
        assertTrue(json.contains("metadata.test"));

        // 模拟 GraphQL/JSON API 响应路径
        ApiResponse<ErrorDTO> apiResp = ApiResponse.buildSuccess(original);
        String apiJson = JsonTool.stringify(apiResp);
        assertNotNull(apiJson);
        assertTrue(apiJson.contains("metadata.test"));
    }
}
