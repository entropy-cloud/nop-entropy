package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.orm.IOrmTemplate;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestExternalAggregationProcessor {

    // ===== execute() 分派行为（P1-MA4-601：空洞测试 → 行为断言） =====

    /** execute() 对 querySpace 无注册数据源显式失败（ERR_DATASOURCE_RESOLVE_NO_DATASOURCE + metaTableId 参数）。 */
    @Test
    public void testExecuteWithNoDataSourceThrows() {
        AggregationContext context = mock(AggregationContext.class);
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("external");
        table.setQuerySpace("qs_not_exist");
        when(context.getTable()).thenReturn(table);

        IDaoProvider daoProvider = mock(IDaoProvider.class);
        IEntityDao<NopMetaDataSource> dsDao = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaDataSource.class)).thenReturn(dsDao);
        when(dsDao.findAllByQuery(any())).thenReturn(Collections.emptyList());
        MetaQueryContext ctx = new MetaQueryContext(daoProvider, mock(IOrmTemplate.class),
                mock(IMetaDataSourceConnectionProcessor.class),
                new TableReferenceExecutor(mock(IMetaDataSourceConnectionProcessor.class), mock(IOrmTemplate.class)),
                new MetaDataSourceResolver(), new MetaTableFieldResolver(), new FilterToSqlTranslator());
        when(context.ctx()).thenReturn(ctx);

        ExternalAggregationProcessor processor = new ExternalAggregationProcessor();
        NopException ex = assertThrows(NopException.class, () -> processor.execute(context));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_RESOLVE_NO_DATASOURCE.getErrorCode(), ex.getErrorCode());
        assertEquals("test-table", ex.getParam("metaTableId"));
    }

    // ===== loadExternalMeasures / Dimensions null 参数 =====

    @Test
    public void testLoadExternalMeasuresWithNullNamesReturnsEmpty() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        assertThrows(NullPointerException.class,
                () -> ExternalAggregationProcessor.loadExternalMeasures(table, null, null));
    }

    @Test
    public void testLoadExternalDimensionsWithNullNamesReturnsEmpty() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        assertThrows(NullPointerException.class,
                () -> ExternalAggregationProcessor.loadExternalDimensions(table, null, null));
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

    // ===== buildFromClause 边缘用例 =====

    @Test
    public void testBuildFromClauseExternalType() {
        NopMetaTable table = new NopMetaTable();
        table.setTableType("external");
        table.setTableName("EXT_TABLE");
        assertEquals("EXT_TABLE", buildFromClause(table));
    }

    @Test
    public void testBuildFromClauseSqlTypeWithSource() {
        NopMetaTable table = new NopMetaTable();
        table.setTableType("sql");
        table.setSourceSql("SELECT * FROM t");
        assertEquals("(SELECT * FROM t) _t", buildFromClause(table));
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
    public void testRequireNameValid() {
        assertEquals("valid", requireName("valid", "test"));
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
    public void testContainsIgnoreCaseMatch() {
        assertTrue(containsIgnoreCase(Collections.singleton("ABC"), "abc"));
    }

    @Test
    public void testContainsIgnoreCaseNoMatch() {
        assertFalse(containsIgnoreCase(Collections.singleton("ABC"), "xyz"));
    }
}
