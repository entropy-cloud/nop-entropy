package io.nop.metadata.service;

import io.nop.api.core.beans.ApiResponse;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.api.dto.AggregationResultDTO;
import io.nop.metadata.api.dto.CollectCatalogResultDTO;
import io.nop.metadata.api.dto.CollectCatalogTableDTO;
import io.nop.metadata.api.dto.CreateSqlTableResultDTO;
import io.nop.metadata.api.dto.ErrorDTO;
import io.nop.metadata.api.dto.KeyValueDTO;
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

    @Test
    public void testDtoJsonRoundTrip() {
        ErrorDTO original = new ErrorDTO("metadata.test", "msg", "ctx");
        String json = JsonTool.stringify(original);
        assertNotNull(json);
        assertTrue(json.contains("metadata.test"));

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

    @Test
    public void testDtoJsonRoundTripAllTypes() {
        assertNotNull(JsonTool.stringify(new KeyValueDTO("k", "v")));
        assertNotNull(JsonTool.stringify(new TestConnectionResultDTO()));
        assertNotNull(JsonTool.stringify(new SyncExternalTablesResultDTO()));
        assertNotNull(JsonTool.stringify(new CollectCatalogResultDTO()));
        assertNotNull(JsonTool.stringify(new CreateSqlTableResultDTO()));
        assertNotNull(JsonTool.stringify(new QueryTableDataResultDTO()));
        assertNotNull(JsonTool.stringify(new QualityRuleResultDTO()));
    }
}
