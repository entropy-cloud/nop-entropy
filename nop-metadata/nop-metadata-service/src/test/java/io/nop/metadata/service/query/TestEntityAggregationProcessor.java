package io.nop.metadata.service.query;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntity;
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

public class TestEntityAggregationProcessor {

    // ===== execute() 分派行为（P1-MA4-601：空洞测试 → 行为断言） =====

    /** execute() 对未注册实体显式失败（ERR_AGGR_ENTITY_NOT_REGISTERED），非静默返回。 */
    @Test
    public void testExecuteWithUnregisteredEntityThrows() {
        AggregationContext context = mock(AggregationContext.class);
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("test-table");
        table.setTableType("entity");
        when(context.getTable()).thenReturn(table);

        IDaoProvider daoProvider = mock(IDaoProvider.class);
        IEntityDao<NopMetaEntity> entityDao = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaEntity.class)).thenReturn(entityDao);
        when(entityDao.getEntityById(any())).thenReturn(null);
        MetaQueryContext ctx = newMetaQueryContext(daoProvider);
        when(context.ctx()).thenReturn(ctx);

        EntityAggregationProcessor processor = new EntityAggregationProcessor();
        NopException ex = assertThrows(NopException.class, () -> processor.execute(context));
        assertEquals(NopMetadataErrors.ERR_AGGR_ENTITY_NOT_REGISTERED.getErrorCode(), ex.getErrorCode());
    }

    private static MetaQueryContext newMetaQueryContext(IDaoProvider daoProvider) {
        return new MetaQueryContext(daoProvider, mock(IOrmTemplate.class),
                mock(IMetaDataSourceConnectionProcessor.class),
                new TableReferenceExecutor(mock(IMetaDataSourceConnectionProcessor.class), mock(IOrmTemplate.class)),
                new MetaDataSourceResolver(), new MetaTableFieldResolver(), new FilterToSqlTranslator());
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
                new NopMetaTable(), Collections.emptyList(), Collections.emptyList(), "ORDER_BY", null));
    }

    @Test
    public void testBuildOrderByClauseEmptyReturnsEmpty() {
        assertEquals("", buildOrderByClause(Collections.emptyList(), new LinkedHashMap<>(),
                new NopMetaTable(), Collections.emptyList(), Collections.emptyList(), "ORDER_BY", null));
    }

    @Test
    public void testBuildOrderByClauseAscDesc() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");
        List<OrderFieldBean> orderBy = Arrays.asList(
                OrderFieldBean.desc("m1"));
        String clause = buildOrderByClause(orderBy, nameToExpr,
                new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", null);
        assertTrue(clause.contains("DESC"), "should contain DESC: " + clause);
    }

    @Test
    public void testBuildOrderByClauseUnknownName() {
        NopException ex = assertThrows(NopException.class,
                () -> buildOrderByClause(
                        Arrays.asList(OrderFieldBean.asc("unknown")),
                        new LinkedHashMap<>(),
                        new NopMetaTable(), Collections.emptyList(), Collections.emptyList(), "ORDER_BY", null));
        assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_UNKNOWN_NAME.getErrorCode(), ex.getErrorCode());
    }

    // ===== AR-20a（plan 2026-08-06-1228-1 Phase 1）：NULLS FIRST/LAST 方言感知 =====

    /** ORM 路径裁定（dialect=null，via-EQL / entity-entity JOIN）：保持既有 H2 语义拼接子句。 */
    @Test
    public void testBuildOrderByClauseNullDialectKeepsNullsClause() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");

        List<OrderFieldBean> ascNullsFirst = new ArrayList<>();
        OrderFieldBean f1 = OrderFieldBean.asc("m1");
        f1.setNullsFirst(true);
        ascNullsFirst.add(f1);
        String clause = buildOrderByClause(ascNullsFirst, nameToExpr,
                new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", null);
        assertTrue(clause.contains("NULLS FIRST"),
                "dialect=null (ORM path) must keep NULLS FIRST clause: " + clause);

        List<OrderFieldBean> descNullsLast = new ArrayList<>();
        OrderFieldBean f2 = OrderFieldBean.desc("m1");
        f2.setNullsFirst(false);
        descNullsLast.add(f2);
        String clause2 = buildOrderByClause(descNullsLast, nameToExpr,
                new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", null);
        assertTrue(clause2.contains("NULLS LAST"),
                "dialect=null (ORM path) must keep NULLS LAST clause: " + clause2);
    }

    /** MySQL + nullsFirst 与 MySQL 默认排序一致（ASC 默认 NULL 在前 / DESC 默认 NULL 在后）→ 省略子句（语义不变）。 */
    @Test
    public void testBuildOrderByClauseMySqlDefaultSemanticsOmitsClause() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");

        List<OrderFieldBean> ascNullsFirst = new ArrayList<>();
        OrderFieldBean f1 = OrderFieldBean.asc("m1");
        f1.setNullsFirst(true);
        ascNullsFirst.add(f1);
        String clause = buildOrderByClause(ascNullsFirst, nameToExpr,
                new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "MySQL");
        assertFalse(clause.contains("NULLS"),
                "MySQL + nullsFirst=true+ASC equals MySQL default (NULLs first in ASC) -> omit clause: " + clause);

        List<OrderFieldBean> descNullsLast = new ArrayList<>();
        OrderFieldBean f2 = OrderFieldBean.desc("m1");
        f2.setNullsFirst(false);
        descNullsLast.add(f2);
        String clause2 = buildOrderByClause(descNullsLast, nameToExpr,
                new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "MySQL");
        assertFalse(clause2.contains("NULLS"),
                "MySQL + nullsFirst=false+DESC equals MySQL default (NULLs last in DESC) -> omit clause: " + clause2);
    }

    /** MySQL 无法表达的组合（NULLS LAST in ASC / NULLS FIRST in DESC）→ 显式 fail-fast（无静默跳过）。 */
    @Test
    public void testBuildOrderByClauseMySqlInexpressibleFailsFast() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");

        List<OrderFieldBean> ascNullsLast = new ArrayList<>();
        OrderFieldBean f1 = OrderFieldBean.asc("m1");
        f1.setNullsFirst(false);
        ascNullsLast.add(f1);
        NopException ex1 = assertThrows(NopException.class,
                () -> buildOrderByClause(ascNullsLast, nameToExpr,
                        new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "MySQL"),
                "MySQL cannot express NULLS LAST in ASC -> must fail loudly");
        assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_NULLS_UNSUPPORTED.getErrorCode(), ex1.getErrorCode());

        List<OrderFieldBean> descNullsFirst = new ArrayList<>();
        OrderFieldBean f2 = OrderFieldBean.desc("m1");
        f2.setNullsFirst(true);
        descNullsFirst.add(f2);
        NopException ex2 = assertThrows(NopException.class,
                () -> buildOrderByClause(descNullsFirst, nameToExpr,
                        new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "MySQL"),
                "MySQL cannot express NULLS FIRST in DESC -> must fail loudly");
        assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_NULLS_UNSUPPORTED.getErrorCode(), ex2.getErrorCode());
        assertEquals(true, ex2.getParam("desc"), "param desc must be true: " + ex2.getParams());
    }

    /** H2/PostgreSQL 支持 NULLS FIRST/LAST → 子句保留（keep-green）。 */
    @Test
    public void testBuildOrderByClauseH2PostgresqlKeepNullsClause() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");
        List<OrderFieldBean> orderBy = new ArrayList<>();
        OrderFieldBean f = OrderFieldBean.asc("m1");
        f.setNullsFirst(true);
        orderBy.add(f);

        String h2 = buildOrderByClause(orderBy, nameToExpr,
                new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "H2");
        assertTrue(h2.contains("NULLS FIRST"), "H2 must keep NULLS FIRST: " + h2);

        String pg = buildOrderByClause(orderBy, nameToExpr,
                new NopMetaTable(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "PostgreSQL");
        assertTrue(pg.contains("NULLS FIRST"), "PostgreSQL must keep NULLS FIRST: " + pg);
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
