package io.nop.metadata.service.query;

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
import static io.nop.metadata.service.query.AggregationHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestSqlAggregationProcessor {

    // ===== 基础构造 =====

    @Test
    public void testImplementsAggregationProcessor() {
        assertTrue(new SqlAggregationProcessor() instanceof AggregationProcessor);
    }

    @Test
    public void testExecuteWithNullContextThrowsNpe() {
        SqlAggregationProcessor processor = new SqlAggregationProcessor();
        assertThrows(NullPointerException.class, () -> processor.execute(null));
    }

    @Test
    public void testCanInstantiate() {
        SqlAggregationProcessor processor = new SqlAggregationProcessor();
        assertNotNull(processor);
    }

    @Test
    public void testExecuteWithUnsupportedTableTypeThrowsNopException() {
        AggregationContext context = mock(AggregationContext.class);
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("unsupported");
        when(context.getTable()).thenReturn(table);

        SqlAggregationProcessor processor = new SqlAggregationProcessor();
        NopException ex = assertThrows(NopException.class, () -> processor.execute(context));
        ErrorCode expected = NopMetadataErrors.ERR_AGGR_UNSUPPORTED_TABLE_TYPE;
        assertEquals(expected.getErrorCode(), ex.getErrorCode());
    }

    // ===== buildFromClause 边缘用例 =====

    @Test
    public void testBuildFromClauseSqlTypeWithNullSourceSql() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("sql");
        assertThrows(RuntimeException.class, () -> buildFromClause(table));
    }

    @Test
    public void testBuildFromClauseSqlTypeWithSourceSql() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("sql");
        table.setSourceSql("SELECT * FROM dual");
        String fromClause = buildFromClause(table);
        assertEquals("(SELECT * FROM dual) _t", fromClause);
    }

    @Test
    public void testBuildFromClauseExternalType() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("external");
        table.setTableName("MY_TABLE");
        String fromClause = buildFromClause(table);
        assertEquals("MY_TABLE", fromClause);
    }

    @Test
    public void testBuildFromClauseSqlTypeEmptySourceSqlThrows() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("t1");
        table.setTableType("sql");
        table.setSourceSql("");
        NopException ex = assertThrows(NopException.class, () -> buildFromClause(table));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXEC_FAILED.getErrorCode(), ex.getErrorCode());
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
    public void testSafeAliasSpecialChars() {
        assertEquals("A_B_C", safeAlias("a.b c"));
    }

    @Test
    public void testSafeAliasLeadingDigit() {
        assertEquals("V_1ABC", safeAlias("1abc"));
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
    public void testAggSqlOfCountDistinct() {
        assertEquals("COUNT(DISTINCT col)",
                aggSqlOf(_NopMetadataCoreConstants.AGG_FUNC_COUNT_DISTINCT, "col", "m1"));
    }

    @Test
    public void testAggSqlOfUnsupportedFuncThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> aggSqlOf("BAD", "col", "m1"));
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

    // ===== buildResult 边缘用例 =====

    @Test
    public void testBuildResultNullItems() {
        Map<String, Object> result = buildResult(null);
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

    // ===== requireName 边缘用例 =====

    @Test
    public void testRequireNameNonNull() {
        assertEquals("hello", requireName("hello", "test"));
    }

    @Test
    public void testRequireNameEmptyThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> requireName("", "test"));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXEC_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testRequireNameNullThrows() {
        NopException ex = assertThrows(NopException.class,
                () -> requireName(null, "test"));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXEC_FAILED.getErrorCode(), ex.getErrorCode());
    }

    // ===== containsIgnoreCase 边缘用例 =====

    @Test
    public void testContainsIgnoreCaseNullSetReturnsFalse() {
        assertFalse(containsIgnoreCase(null, "x"));
    }

    @Test
    public void testContainsIgnoreCaseNullNameReturnsFalse() {
        assertFalse(containsIgnoreCase(Collections.singleton("x"), null));
    }

    @Test
    public void testContainsIgnoreCaseMatch() {
        assertTrue(containsIgnoreCase(Collections.singleton("ABC"), "abc"));
    }

    @Test
    public void testContainsIgnoreCaseNoMatch() {
        assertFalse(containsIgnoreCase(Collections.singleton("ABC"), "xyz"));
    }
}
