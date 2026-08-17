package io.nop.metadata.service.lineage;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表级 SQL 源表抽取器单元测试（架构基线 §2.6.1）。
 *
 * <p>纯解析器测试，不依赖 ORM session / BizModel / 目录。
 *
 * <p>覆盖 AR-07（跨 schema 同名表不塌缩）+ AR-08（CTE 名排除）+ 既有形态回归。
 */
public class TestSqlSourceTableExtractor {

    private final SqlSourceTableExtractor extractor = new SqlSourceTableExtractor();

    private static Set<String> fullNames(List<SqlTableReference> refs) {
        return refs.stream().map(SqlTableReference::getFullName).collect(Collectors.toSet());
    }

    private static Set<String> simpleNames(List<SqlTableReference> refs) {
        return refs.stream().map(SqlTableReference::getSimpleName).collect(Collectors.toSet());
    }

    // ===== AR-07：跨 schema 同名表不塌缩 =====

    /** dbo.users 与 sales.users 的 fullName 不同 → 两条独立边，不因 simpleName="users" 相同而塌缩。 */
    @Test
    public void crossSchemaSameSimpleNameNotCollapsed() {
        List<SqlTableReference> refs = extractor.extract(
                "SELECT * FROM dbo.users JOIN sales.users ON dbo.users.id = sales.users.id");
        assertEquals(2, refs.size(), "dbo.users and sales.users must be 2 separate edges");
        assertTrue(fullNames(refs).contains("dbo.users"), "must contain dbo.users");
        assertTrue(fullNames(refs).contains("sales.users"), "must contain sales.users");
    }

    /** 同一 schema-qualified 表多次引用（不同别名）仍只算一条边（fullName 去重）。 */
    @Test
    public void sameTableDifferentAliasDedupByFullName() {
        List<SqlTableReference> refs = extractor.extract(
                "SELECT * FROM dbo.users u1 JOIN dbo.users u2 ON u1.id = u2.id");
        assertEquals(1, refs.size(), "same dbo.users referenced twice must be 1 edge (dedup by fullName)");
        assertEquals("dbo.users", refs.get(0).getFullName());
    }

    /** 无 schema 的同名表多次引用 → 一条边。 */
    @Test
    public void unqualifiedSameTableDedup() {
        List<SqlTableReference> refs = extractor.extract(
                "SELECT * FROM users u1 JOIN users u2 ON u1.id = u2.id");
        assertEquals(1, refs.size(), "same unqualified users twice must be 1 edge");
    }

    /** schema-qualified 与非限定名混合：dbo.users 与 users 的 fullName 不同 → 两条边。 */
    @Test
    public void mixedQualifiedAndUnqualifiedNotCollapsed() {
        List<SqlTableReference> refs = extractor.extract(
                "SELECT * FROM dbo.users JOIN other_table ON dbo.users.id = other_table.id");
        assertEquals(2, refs.size());
        assertTrue(fullNames(refs).contains("dbo.users"));
        assertTrue(fullNames(refs).contains("other_table"));
    }

    // ===== AR-08：CTE 名排除 =====

    /** WITH t AS (...) SELECT * FROM t JOIN real_table → 结果只含 real_table，不含 CTE 名 t。 */
    @Test
    public void cteNameExcludedFromPhysicalSourceTables() {
        List<SqlTableReference> refs = extractor.extract(
                "WITH t AS (SELECT 1) SELECT * FROM t JOIN real_table ON 1=1");
        assertFalse(fullNames(refs).contains("t"),
                "CTE name 't' must not be reported as a physical source table");
        assertTrue(fullNames(refs).contains("real_table"),
                "real_table must be reported");
    }

    /** CTE 体内部的物理源表仍被正常收集。 */
    @Test
    public void cteBodyPhysicalTablesCollected() {
        List<SqlTableReference> refs = extractor.extract(
                "WITH t AS (SELECT id FROM inner_table) SELECT * FROM t");
        assertTrue(fullNames(refs).contains("inner_table"),
                "CTE body's physical table 'inner_table' must be collected");
        assertFalse(fullNames(refs).contains("t"),
                "CTE name 't' must not be reported");
    }

    /** CTE 名遮蔽同名物理表 → CTE 名被排除（符合 SQL 作用域语义），物理表引用不补回。 */
    @Test
    public void cteNameShadowsPhysicalTableExcluded() {
        // CTE 名为 real_table，同时 JOIN real_table（实际上是引用 CTE，不是物理表）
        List<SqlTableReference> refs = extractor.extract(
                "WITH real_table AS (SELECT 1) SELECT * FROM real_table");
        assertFalse(fullNames(refs).contains("real_table"),
                "CTE name 'real_table' shadows physical table reference, must be excluded");
    }

    /** 多 CTE：全部 CTE 名被排除，CTE 体内部和主查询的物理表被收集。 */
    @Test
    public void multipleCtesAllExcluded() {
        List<SqlTableReference> refs = extractor.extract(
                "WITH cte1 AS (SELECT id FROM src1), cte2 AS (SELECT id FROM src2) "
                        + "SELECT * FROM cte1 JOIN cte2 ON cte1.id = cte2.id");
        assertFalse(fullNames(refs).contains("cte1"), "cte1 must be excluded");
        assertFalse(fullNames(refs).contains("cte2"), "cte2 must be excluded");
        assertTrue(fullNames(refs).contains("src1"), "src1 in cte1 body must be collected");
        assertTrue(fullNames(refs).contains("src2"), "src2 in cte2 body must be collected");
    }

    /** CTE 名大小写不敏感排除（CTE 名大写、引用小写）。 */
    @Test
    public void cteNameCaseInsensitiveExclusion() {
        List<SqlTableReference> refs = extractor.extract(
                "WITH MyCTE AS (SELECT 1) SELECT * FROM mycte JOIN real_tbl ON 1=1");
        assertFalse(simpleNames(refs).contains("mycte"),
                "CTE name 'mycte' (case-insensitive match to 'MyCTE') must be excluded");
        assertTrue(simpleNames(refs).contains("real_tbl"));
    }

    // ===== 无 WITH 的 SQL 不受影响（无静默跳过）=====

    /** 无 WITH 子句的普通 SQL 行为不变。 */
    @Test
    public void noWithClauseUnchanged() {
        List<SqlTableReference> refs = extractor.extract(
                "SELECT * FROM src1 JOIN src2 ON src1.id = src2.id");
        assertEquals(2, refs.size());
        assertTrue(fullNames(refs).contains("src1"));
        assertTrue(fullNames(refs).contains("src2"));
    }

    /** 子查询内的源表仍被收集（无 CTE 场景）。 */
    @Test
    public void subquerySourceTablesCollected() {
        List<SqlTableReference> refs = extractor.extract(
                "SELECT * FROM (SELECT id FROM inner_src) sub JOIN outer_src ON sub.id = outer_src.id");
        assertTrue(fullNames(refs).contains("inner_src"), "subquery's inner_src must be collected");
        assertTrue(fullNames(refs).contains("outer_src"), "outer_src must be collected");
    }

    /** UNION 的两分支源表均被收集。 */
    @Test
    public void unionBothBranchesCollected() {
        List<SqlTableReference> refs = extractor.extract(
                "SELECT id FROM src1 UNION ALL SELECT id FROM src2");
        assertTrue(fullNames(refs).contains("src1"));
        assertTrue(fullNames(refs).contains("src2"));
    }

    // ===== 失败路径 =====

    @Test
    public void emptySqlFailsExplicitly() {
        assertThrows(NopException.class, () -> extractor.extract(""));
        assertThrows(NopException.class, () -> extractor.extract("   "));
        assertThrows(NopException.class, () -> extractor.extract(null));
    }
}
