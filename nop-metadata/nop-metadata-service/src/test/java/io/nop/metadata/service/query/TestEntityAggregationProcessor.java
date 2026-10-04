package io.nop.metadata.service.query;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaEntityFieldResolver;
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
        NopMetaEntity table = new NopMetaEntity();
        table.setMetaEntityId("test-table");
        table.setEntityKind("PHYSICAL");
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
                new MetaDataSourceResolver(), new MetaEntityFieldResolver(), new FilterToSqlTranslator());
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
                Collections.emptyList(), Collections.emptyList(), new NopMetaEntity());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testBuildNameToExprTableMeasuresLengthMismatch() {
        List<MeasureSpec> measures = Arrays.asList(new MeasureSpec("ALIAS", "SUM(x)"));
        NopException ex = assertThrows(NopException.class,
                () -> buildNameToExprTable(measures, Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), new NopMetaEntity()));
        assertEquals(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testBuildNameToExprTableDimsLengthMismatch() {
        List<DimensionSpec> dims = Arrays.asList(new DimensionSpec("D_ALIAS", "col", "categorical", null));
        NopException ex = assertThrows(NopException.class,
                () -> buildNameToExprTable(Collections.emptyList(), dims,
                        Collections.emptyList(), Collections.emptyList(), new NopMetaEntity()));
        assertEquals(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME.getErrorCode(), ex.getErrorCode());
    }

    // ===== buildOrderByClause 边缘用例 =====

    @Test
    public void testBuildOrderByClauseNullReturnsEmpty() {
        assertEquals("", buildOrderByClause(null, new LinkedHashMap<>(),
                new NopMetaEntity(), Collections.emptyList(), Collections.emptyList(), "ORDER_BY", null));
    }

    @Test
    public void testBuildOrderByClauseEmptyReturnsEmpty() {
        assertEquals("", buildOrderByClause(Collections.emptyList(), new LinkedHashMap<>(),
                new NopMetaEntity(), Collections.emptyList(), Collections.emptyList(), "ORDER_BY", null));
    }

    @Test
    public void testBuildOrderByClauseAscDesc() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");
        List<OrderFieldBean> orderBy = Arrays.asList(
                OrderFieldBean.desc("m1"));
        String clause = buildOrderByClause(orderBy, nameToExpr,
                new NopMetaEntity(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", null);
        assertTrue(clause.contains("DESC"), "should contain DESC: " + clause);
    }

    @Test
    public void testBuildOrderByClauseUnknownName() {
        NopException ex = assertThrows(NopException.class,
                () -> buildOrderByClause(
                        Arrays.asList(OrderFieldBean.asc("unknown")),
                        new LinkedHashMap<>(),
                        new NopMetaEntity(), Collections.emptyList(), Collections.emptyList(), "ORDER_BY", null));
        assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_UNKNOWN_NAME.getErrorCode(), ex.getErrorCode());
    }

    // ===== AR-20a（plan 2026-08-06-1228-1 Phase 1）：NULLS FIRST/LAST 方言感知 =====

    /**
     * plan 2309 D3/D8：<b>取不到方言时不再无条件拼接原生 NULLS 子句，改为响亮失败。</b>
     *
     * <p><b>这是行为变更，不是测试削弱。</b>原断言要求「dialect=null 也保留 NULLS 子句」，
     * 锁定的正是本计划要取代的行为——在底层为 MySQL 的 ORM 部署上它会产出语法错误 SQL。
     * 替换后的断言覆盖两件事：
     * <ol>
     *   <li>{@code dialect=null} → fail-fast（无法判定方言就不产出未校验的 SQL）</li>
     *   <li>真实方言名（h2 / postgresql / oracle / duckdb）→ 原生直出，取代原先
     *       「靠 null 走 else 分支」的隐式行为——现在 ORM 路径传的是真实方言名</li>
     * </ol>
     */
    @Test
    public void testBuildOrderByClauseNullDialectFailsFast() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");

        for (boolean desc : new boolean[]{false, true}) {
            List<OrderFieldBean> orderBy = new ArrayList<>();
            OrderFieldBean f = desc ? OrderFieldBean.desc("m1") : OrderFieldBean.asc("m1");
            f.setNullsFirst(true);
            orderBy.add(f);
            NopException err = assertThrows(NopException.class,
                    () -> buildOrderByClause(orderBy, nameToExpr, new NopMetaEntity(),
                            Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", null));
            assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_NULLS_UNSUPPORTED.getErrorCode(),
                    err.getErrorCode());
        }
    }

    /** plan 2309 D8：ORM 路径现在传真实方言名；支持原生 NULLS 子句的方言直出。 */
    @Test
    public void testBuildOrderByClauseNativeDialectEmitsClause() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");

        // 含 h2gis / postgis / oracle-reverse：三者本身不声明该位但经 x:extends 继承为 true，
        // 若按名字白名单判定会漏判，把本来可执行的查询变成硬失败（closure audit BLOCKER-1）。
        for (String dialect : new String[]{"H2", "h2", "PostgreSQL", "postgresql", "Oracle",
                "DuckDB", "h2gis", "postgis", "oracle-reverse", "db2", "DB2"}) {
            List<OrderFieldBean> ascNullsFirst = new ArrayList<>();
            OrderFieldBean f1 = OrderFieldBean.asc("m1");
            f1.setNullsFirst(true);
            ascNullsFirst.add(f1);
            String clause = buildOrderByClause(ascNullsFirst, nameToExpr, new NopMetaEntity(),
                    Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", dialect);
            assertTrue(clause.contains("NULLS FIRST"), dialect + " 应原生输出 NULLS FIRST: " + clause);

            List<OrderFieldBean> descNullsLast = new ArrayList<>();
            OrderFieldBean f2 = OrderFieldBean.desc("m1");
            f2.setNullsFirst(false);
            descNullsLast.add(f2);
            String clause2 = buildOrderByClause(descNullsLast, nameToExpr, new NopMetaEntity(),
                    Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", dialect);
            assertTrue(clause2.contains("NULLS LAST"), dialect + " 应原生输出 NULLS LAST: " + clause2);
        }
    }

    /** plan 2309 D3：既非 mysql 家族、又未声明支持原生子句的方言 -> fail-fast，不得拼接非法 SQL。 */
    @Test
    public void testBuildOrderByClauseUnsupportedDialectFailsFast() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");
        // 注：不含 mariadb / mysql5.7——它们与 mysql 同族、无 NULLS 子句语法，
        // 由 isMySqlFamily 走 MySQL 分支（与缺省一致则省略、不一致则失败），见下方专门用例。
        for (String dialect : new String[]{"dm", "TDengine", "SQLServer", "Oracle11"}) {
            List<OrderFieldBean> orderBy = new ArrayList<>();
            OrderFieldBean f = OrderFieldBean.asc("m1");
            f.setNullsFirst(true);
            orderBy.add(f);
            NopException err = assertThrows(NopException.class,
                    () -> buildOrderByClause(orderBy, nameToExpr, new NopMetaEntity(),
                            Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", dialect),
                    dialect + " 应 fail-fast");
            assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_NULLS_UNSUPPORTED.getErrorCode(),
                    err.getErrorCode(), dialect + " 不支持原生 NULLS 子句，必须失败而非拼接");
        }
    }

    /**
     * plan 2309 D8：mysql 家族（mysql / mysql5.7 / mariadb）都无原生 NULLS 子句语法，
     * 都应走 MySQL 分支——与缺省一致则省略子句，无法表达的组合则失败。
     *
     * <p>此前判据只比对字面量 {@code "MySQL"}，而 ORM 路径传入的是
     * {@code IDialect.getName()}（即 {@code mysql5.7} / {@code mariadb}），匹配不上，
     * 会落进「原生直出」分支产出语法错误 SQL。
     */
    @Test
    public void testBuildOrderByClauseMysqlFamilyTakesMysqlBranch() {
        Map<String, String> nameToExpr = new LinkedHashMap<>();
        nameToExpr.put("m1", "SUM(x)");
        for (String dialect : new String[]{"MySQL", "mysql", "mysql5.7", "MySQL5.7", "MariaDB", "mariadb"}) {
            // ASC + NULLS FIRST 与 mysql 缺省一致 -> 省略子句，可执行
            List<OrderFieldBean> matching = new ArrayList<>();
            OrderFieldBean asc = OrderFieldBean.asc("m1");
            asc.setNullsFirst(true);
            matching.add(asc);
            String clause = buildOrderByClause(matching, nameToExpr, new NopMetaEntity(),
                    Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", dialect);
            assertFalse(clause.contains("NULLS"), dialect + " 应省略 NULLS 子句: " + clause);

            // DESC + NULLS FIRST 与 mysql 缺省冲突 -> 响亮失败
            List<OrderFieldBean> conflicting = new ArrayList<>();
            OrderFieldBean desc = OrderFieldBean.desc("m1");
            desc.setNullsFirst(true);
            conflicting.add(desc);
            NopException err = assertThrows(NopException.class,
                    () -> buildOrderByClause(conflicting, nameToExpr, new NopMetaEntity(),
                            Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", dialect),
                    dialect + " 的 DESC+NULLS FIRST 应 fail-fast");
            assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_NULLS_UNSUPPORTED.getErrorCode(),
                    err.getErrorCode());
        }
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
                new NopMetaEntity(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "MySQL");
        assertFalse(clause.contains("NULLS"),
                "MySQL + nullsFirst=true+ASC equals MySQL default (NULLs first in ASC) -> omit clause: " + clause);

        List<OrderFieldBean> descNullsLast = new ArrayList<>();
        OrderFieldBean f2 = OrderFieldBean.desc("m1");
        f2.setNullsFirst(false);
        descNullsLast.add(f2);
        String clause2 = buildOrderByClause(descNullsLast, nameToExpr,
                new NopMetaEntity(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "MySQL");
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
                        new NopMetaEntity(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "MySQL"),
                "MySQL cannot express NULLS LAST in ASC -> must fail loudly");
        assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_NULLS_UNSUPPORTED.getErrorCode(), ex1.getErrorCode());

        List<OrderFieldBean> descNullsFirst = new ArrayList<>();
        OrderFieldBean f2 = OrderFieldBean.desc("m1");
        f2.setNullsFirst(true);
        descNullsFirst.add(f2);
        NopException ex2 = assertThrows(NopException.class,
                () -> buildOrderByClause(descNullsFirst, nameToExpr,
                        new NopMetaEntity(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "MySQL"),
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
                new NopMetaEntity(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "H2");
        assertTrue(h2.contains("NULLS FIRST"), "H2 must keep NULLS FIRST: " + h2);

        String pg = buildOrderByClause(orderBy, nameToExpr,
                new NopMetaEntity(), Arrays.asList("m1"), Collections.emptyList(), "ORDER_BY", "PostgreSQL");
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
        NopMetaEntity table = new NopMetaEntity();
        table.setMetaEntityId("t1");
        table.setEntityKind("SQL_VIEW");
        NopException ex = assertThrows(NopException.class, () -> buildFromClause(table));
        assertEquals(NopMetadataErrors.ERR_AGGR_EXEC_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testBuildFromClauseSqlTypeWithSource() {
        NopMetaEntity table = new NopMetaEntity();
        table.setEntityKind("SQL_VIEW");
        table.setSourceSql("SELECT * FROM t");
        assertEquals("(SELECT * FROM t) _t", buildFromClause(table));
    }

    @Test
    public void testBuildFromClauseExternalType() {
        NopMetaEntity table = new NopMetaEntity();
        table.setEntityKind("EXTERNAL");
        table.setTableName("MY_TABLE");
        assertEquals("MY_TABLE", buildFromClause(table));
    }
}
