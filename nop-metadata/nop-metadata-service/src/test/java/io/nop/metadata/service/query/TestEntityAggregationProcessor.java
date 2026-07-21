package io.nop.metadata.service.query;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.metadata.service.query.AggregationContext.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestEntityAggregationProcessor {

    // ===== 基础构造 =====

    @Test
    public void testImplementsAggregationProcessor() {
        assertTrue(new EntityAggregationProcessor() instanceof AggregationProcessor);
    }

    @Test
    public void testExecuteWithNullContextThrowsNpe() {
        EntityAggregationProcessor processor = new EntityAggregationProcessor();
        assertThrows(NullPointerException.class, () -> processor.execute(null));
    }

    @Test
    public void testCanInstantiate() {
        EntityAggregationProcessor processor = new EntityAggregationProcessor();
        assertNotNull(processor);
    }

    // ===== safeAlias 边缘用例 =====

    @Test
    public void testSafeAliasNullReturnsDefault() {
        assertEquals("v", safeAlias(null));
    }

    @Test
    public void testSafeAliasEmptyString() {
        assertEquals("V_", safeAlias(""));
    }

    @Test
    public void testSafeAliasNormal() {
        assertEquals("MY_COLUMN", safeAlias("my-column"));
    }

    @Test
    public void testSafeAliasLeadingDigit() {
        assertEquals("V_1ABC", safeAlias("1abc"));
    }

    @Test
    public void testSafeAliasSpecialChars() {
        assertEquals("A_B_C", safeAlias("a.b c"));
    }

    // ===== aggSqlOf 边缘用例 =====

    @Test
    public void testAggSqlOfSum() {
        assertEquals("SUM(col)", aggSqlOf(_NopMetadataCoreConstants.AGG_FUNC_SUM, "col", "m1"));
    }

    @Test
    public void testAggSqlOfCount() {
        assertEquals("COUNT(col)", aggSqlOf(_NopMetadataCoreConstants.AGG_FUNC_COUNT, "col", "m1"));
    }

    @Test
    public void testAggSqlOfAvg() {
        assertEquals("AVG(col)", aggSqlOf(_NopMetadataCoreConstants.AGG_FUNC_AVG, "col", "m1"));
    }

    @Test
    public void testAggSqlOfMin() {
        assertEquals("MIN(col)", aggSqlOf(_NopMetadataCoreConstants.AGG_FUNC_MIN, "col", "m1"));
    }

    @Test
    public void testAggSqlOfMax() {
        assertEquals("MAX(col)", aggSqlOf(_NopMetadataCoreConstants.AGG_FUNC_MAX, "col", "m1"));
    }

    @Test
    public void testAggSqlOfCountDistinct() {
        assertEquals("COUNT(DISTINCT col)",
                aggSqlOf(_NopMetadataCoreConstants.AGG_FUNC_COUNT_DISTINCT, "col", "m1"));
    }

    @Test
    public void testAggSqlOfNullFuncThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> aggSqlOf(null, "col", "m1"));
        assertEquals(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testAggSqlOfUnsupportedFuncThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> aggSqlOf("UNSUPPORTED", "col", "m1"));
        assertEquals(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED.getErrorCode(), ex.getErrorCode());
    }

    // ===== buildNameToExprTable 边缘用例 =====

    @Test
    public void testBuildNameToExprTableEmpty() {
        Map<String, String> result = buildNameToExprTable(
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), new NopMetaTable());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testBuildNameToExprTableMeasuresLengthMismatch() {
        List<MeasureSpec> measures = Arrays.asList(new MeasureSpec("ALIAS", "SUM(x)"));
        NopException ex = assertThrows(NopException.class,
                () -> buildNameToExprTable(measures, Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), new NopMetaTable()));
        assertEquals(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testBuildNameToExprTableDimsLengthMismatch() {
        List<DimensionSpec> dims = Arrays.asList(new DimensionSpec("D_ALIAS", "col", "categorical", null));
        NopException ex = assertThrows(NopException.class,
                () -> buildNameToExprTable(Collections.emptyList(), dims,
                        Collections.emptyList(), Collections.emptyList(), new NopMetaTable()));
        assertEquals(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME.getErrorCode(), ex.getErrorCode());
    }

    // ===== buildOrderByClause 边缘用例 =====

    @Test
    public void testBuildOrderByClauseNullReturnsEmpty() {
        assertEquals("", buildOrderByClause(null, new LinkedHashMap<>(),
                new NopMetaTable(), Collections.emptyList(), Collections.emptyList(), "ORDER_BY"));
    }

    @Test
    public void testBuildOrderByClauseEmptyReturnsEmpty() {
        assertEquals("", buildOrderByClause(Collections.emptyList(), new LinkedHashMap<>(),
                new NopMetaTable(), Collections.emptyList(), Collections.emptyList(), "ORDER_BY"));
    }

    @Test
    public void testBuildOrderByClauseAscDesc() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");
        List<OrderFieldBean> orderBy = Arrays.asList(
                OrderFieldBean.desc("m1"));
        String clause = buildOrderByClause(orderBy, nameToExpr,
                new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY");
        assertTrue(clause.contains("DESC"), "should contain DESC: " + clause);
    }

    @Test
    public void testBuildOrderByClauseUnknownName() {
        NopException ex = assertThrows(NopException.class,
                () -> buildOrderByClause(
                        Arrays.asList(OrderFieldBean.asc("unknown")),
                        new LinkedHashMap<>(),
                        new NopMetaTable(), Collections.emptyList(), Collections.emptyList(), "ORDER_BY"));
        assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_UNKNOWN_NAME.getErrorCode(), ex.getErrorCode());
    }

    // ===== buildResult 边缘用例 =====

    @Test
    public void testBuildResultNullItems() {
        Map<String, Object> result = buildResult(null);
        assertNotNull(result.get("items"));
        assertTrue(((List<?>) result.get("items")).isEmpty());
    }

    @Test
    public void testBuildResultEmptyItems() {
        Map<String, Object> result = buildResult(new ArrayList<>());
        assertNotNull(result.get("items"));
        assertTrue(((List<?>) result.get("items")).isEmpty());
    }

    @Test
    public void testBuildResultNonEmpty() {
        List<Map<String, Object>> items = Arrays.asList(
                new LinkedHashMap<>(Map.of("k", "v")));
        Map<String, Object> result = buildResult(items);
        assertEquals(1, ((List<?>) result.get("items")).size());
    }

    // ===== buildFromClause 边缘用例 =====

    @Test
    public void testBuildFromClauseSqlTypeEmptySourceSqlThrows() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("t1");
        table.setTableType("sql");
        NopException ex = assertThrows(NopException.class, () -> buildFromClause(table));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXEC_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testBuildFromClauseSqlTypeWithSource() {
        NopMetaTable table = new NopMetaTable();
        table.setTableType("sql");
        table.setSourceSql("SELECT * FROM t");
        assertEquals("(SELECT * FROM t) _t", buildFromClause(table));
    }

    @Test
    public void testBuildFromClauseExternalType() {
        NopMetaTable table = new NopMetaTable();
        table.setTableType("external");
        table.setTableName("MY_TABLE");
        assertEquals("MY_TABLE", buildFromClause(table));
    }
}
