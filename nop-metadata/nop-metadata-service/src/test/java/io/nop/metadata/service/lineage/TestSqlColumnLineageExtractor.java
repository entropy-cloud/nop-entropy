package io.nop.metadata.service.lineage;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.core._NopMetadataCoreConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析器级单元测试（架构基线 §2.6.1 列级 sql_parse）：覆盖 {@link SqlColumnLineageExtractor} 的所有可解析形态
 * （直查源表 / JOIN / 别名 / 表达式 / 聚合 / 无别名单表 vs 多表歧义 / 通配符 / dangling）以及
 * P2-5++ CTE 与派生表列穿透。
 *
 * <p>纯解析器测试，不依赖 ORM session / BizModel / 目录，只验证候选列表语义。
 */
public class TestSqlColumnLineageExtractor {

    private final SqlColumnLineageExtractor extractor = new SqlColumnLineageExtractor();

    private static ColumnLineageCandidate firstResolved(List<ColumnLineageCandidate> cs) {
        return cs.stream().filter(c -> !c.isUnresolvable()).findFirst()
                .orElseThrow(() -> new AssertionError("no resolved candidate in " + cs));
    }

    private static ColumnLineageCandidate firstUnresolved(List<ColumnLineageCandidate> cs) {
        return cs.stream().filter(ColumnLineageCandidate::isUnresolvable).findFirst()
                .orElseThrow(() -> new AssertionError("no unresolved candidate in " + cs));
    }

    // ===== 既有形态（回归基线）=====

    @Test
    public void directColumnSingleTable() {
        List<ColumnLineageCandidate> cs = extractor.extract("SELECT t.a AS x FROM SRC t");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("x", r.getTargetColumn());
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("a", r.getSourceColumn());
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT, r.getTransformType());
    }

    @Test
    public void directColumnJoinAliasAttribution() {
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT t1.a AS x, t2.b FROM SRC1 t1 JOIN SRC2 t2 ON t1.k = t2.k");
        assertEquals(2, cs.stream().filter(c -> !c.isUnresolvable()).count());
        ColumnLineageCandidate e1 = cs.stream().filter(c -> "x".equals(c.getTargetColumn()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("SRC1", e1.getSourceTableName());
        ColumnLineageCandidate e2 = cs.stream().filter(c -> "b".equals(c.getTargetColumn()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("SRC2", e2.getSourceTableName());
    }

    @Test
    public void expressionMultiSourceDerived() {
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT t1.a + t2.b AS total FROM SRC1 t1 JOIN SRC2 t2 ON t1.k = t2.k");
        assertEquals(2, cs.size());
        assertTrue(cs.stream().allMatch(c ->
                _NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED.equals(c.getTransformType())));
        assertTrue(cs.stream().anyMatch(c -> c.getSourceTableName().equals("SRC1") && c.getSourceColumn().equals("a")));
        assertTrue(cs.stream().anyMatch(c -> c.getSourceTableName().equals("SRC2") && c.getSourceColumn().equals("b")));
    }

    @Test
    public void aggregateTransformType() {
        List<ColumnLineageCandidate> cs = extractor.extract("SELECT SUM(t.a) AS s FROM SRC t");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("aggregated", r.getTransformType());
    }

    @Test
    public void unqualifiedSingleVsMultiTable() {
        // 单表 → 归属唯一源表
        List<ColumnLineageCandidate> single = extractor.extract("SELECT col FROM UNQ_SINGLE");
        assertEquals("UNQ_SINGLE", firstResolved(single).getSourceTableName());

        // 多表 → 歧义 unresolved
        List<ColumnLineageCandidate> multi = extractor.extract("SELECT col FROM UNQ_A, UNQ_B");
        ColumnLineageCandidate u = firstUnresolved(multi);
        assertEquals("ambiguous-column-multi-table", u.getUnresolvedReason());
    }

    @Test
    public void wildcardProjectionUnresolved() {
        List<ColumnLineageCandidate> cs = extractor.extract("SELECT t.* FROM SRC t");
        ColumnLineageCandidate u = firstUnresolved(cs);
        assertEquals("wildcard-projection", u.getUnresolvedReason());
    }

    @Test
    public void ownerNotMatchedUnresolved() {
        List<ColumnLineageCandidate> cs = extractor.extract("SELECT ghost.x AS y FROM SRC t");
        ColumnLineageCandidate u = firstUnresolved(cs);
        assertEquals("owner-not-matched:ghost", u.getUnresolvedReason());
    }

    @Test
    public void emptySqlFailsExplicitly() {
        assertThrows(NopException.class, () -> extractor.extract(""));
        assertThrows(NopException.class, () -> extractor.extract("   "));
        assertThrows(NopException.class, () -> extractor.extract(null));
    }

    @Test
    public void multiStatementFails() {
        assertThrows(NopException.class, () -> extractor.extract("SELECT 1; SELECT 2"));
    }

    @Test
    public void nonSelectFails() {
        // DELETE 不在 select 范畴
        assertThrows(NopException.class, () -> extractor.extract("DELETE FROM t WHERE x=1"));
    }

    // ===== P2-5++ CTE 列穿透（Phase 1）=====

    @Test
    public void ctePassthroughSingleLayerDirect() {
        // WITH cte AS (SELECT t.x, t.y FROM SRC t) SELECT c.x FROM cte c
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH cte AS (SELECT t.x, t.y FROM SRC t) SELECT c.x FROM cte c");
        ColumnLineageCandidate r = firstResolved(cs);
        // 穿透到底层 SRC.x，transformType direct（纯透传）
        assertEquals("x", r.getTargetColumn());
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("x", r.getSourceColumn());
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT, r.getTransformType(),
                "pure passthrough column must inherit direct");
    }

    @Test
    public void ctePassthroughAggregatedColumn() {
        // CTE 内聚合 SUM(t.a) AS s → 引用穿透产 aggregated
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH cte AS (SELECT SUM(t.a) AS s FROM SRC t) SELECT c.s FROM cte c");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("s", r.getTargetColumn());
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("a", r.getSourceColumn());
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED, r.getTransformType(),
                "CTE aggregate column passthrough must be aggregated");
    }

    @Test
    public void ctePassthroughAliasedColumn() {
        // CTE 内列重命名：SELECT t.x AS out_x FROM SRC t → 引用 c.out_x 穿透到 SRC.x
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH cte AS (SELECT t.x AS out_x FROM SRC t) SELECT c.out_x FROM cte c");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("x", r.getSourceColumn());
        assertEquals("out_x", r.getTargetColumn());
    }

    @Test
    public void ctePassthroughJoinMultiSource() {
        // CTE 内 JOIN 两张源表，SELECT c.a, c.b 各自归属对应底层源表
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH cte AS (SELECT t1.a AS a, t2.b AS b FROM SRC1 t1 JOIN SRC2 t2 ON t1.k=t2.k) "
                        + "SELECT c.a, c.b FROM cte c");
        assertEquals(2, cs.stream().filter(c -> !c.isUnresolvable()).count());
        assertTrue(cs.stream().anyMatch(c -> "SRC1".equals(c.getSourceTableName()) && "a".equals(c.getSourceColumn())));
        assertTrue(cs.stream().anyMatch(c -> "SRC2".equals(c.getSourceTableName()) && "b".equals(c.getSourceColumn())));
    }

    @Test
    public void ctePassthroughNestedCteReferencesEarlierCte() {
        // 第二个 CTE 引用第一个 CTE：双层穿透
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH cte1 AS (SELECT t.x FROM SRC t), cte2 AS (SELECT c1.x FROM cte1 c1) "
                        + "SELECT c2.x FROM cte2 c2");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("x", r.getSourceColumn());
    }

    @Test
    public void ctePassthroughRecursiveCteUnresolved() {
        // WITH RECURSIVE 自引用 CTE → 整体 unsupported（引用列产 unresolved，不伪造）
        // EQL 文法仅支持 `WITH RECURSIVE name AS (SELECT...)`，不支持 CTE 列清单 `name(col)`
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH RECURSIVE rcte AS (SELECT 1 AS a UNION ALL SELECT c.a + 1 AS a FROM rcte c) "
                        + "SELECT c.a FROM rcte c");
        ColumnLineageCandidate u = firstUnresolved(cs);
        // recursive CTE 被标 wildcard（不支持展开），上层引用列产 unresolved:cte-wildcard:...
        assertTrue(u.getUnresolvedReason().startsWith("cte-wildcard:") || u.getUnresolvedReason().startsWith("derived-table-wildcard:"),
                "recursive CTE passthrough must be explicitly unresolved, got: " + u.getUnresolvedReason());
    }

    @Test
    public void ctePassthroughColumnNotFoundUnresolved() {
        // CTE 只输出 x，引用不存在的列 → unresolved（不伪造）
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH cte AS (SELECT t.x FROM SRC t) SELECT c.ghost_col FROM cte c");
        ColumnLineageCandidate u = firstUnresolved(cs);
        assertTrue(u.getUnresolvedReason().startsWith("cte-or-derived-column-not-found:"),
                "missing CTE output column must be unresolved, got: " + u.getUnresolvedReason());
    }

    @Test
    public void cteWildcardOutputUnresolved() {
        // CTE 内 SELECT *（通配符输出）→ 引用列穿透产 unresolved（不伪造）
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH cte AS (SELECT * FROM SRC t) SELECT c.x FROM cte c");
        ColumnLineageCandidate u = firstUnresolved(cs);
        // 体里 SELECT * 在 AST 层不一定被解析为 projection（可能为空列表），但无论如何引用列必须 unresolved
        assertFalse(cs.stream().anyMatch(c -> !c.isUnresolvable()
                && "SRC".equals(c.getSourceTableName()) && "x".equals(c.getSourceColumn())),
                "wildcard CTE output must not fabricate resolved passthrough");
    }

    @Test
    public void cteSelfReferenceNoInfiniteRecursion() {
        // 自引用 CTE（非 RECURSIVE 关键字，但仍存在 cte1 引用 cte1 自身）→ 环路守卫，不无限递归，引用列 unresolved
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH cte1 AS (SELECT t.x, c1.x AS y FROM SRC t, cte1 c1) SELECT c.x FROM cte1 c");
        // x 是 SRC 透传列（resolved），y 是自引用 cte1（unresolved）
        assertTrue(cs.stream().anyMatch(c -> !c.isUnresolvable()
                && "SRC".equals(c.getSourceTableName()) && "x".equals(c.getSourceColumn())),
                "non-recursive self-reference: passthrough columns that resolve to physical tables must still resolve");
        // 但执行未抛栈溢出/未挂起
    }

    // ===== P2-5++ 派生表（subquery）列穿透（Phase 2）=====

    @Test
    public void derivedTablePassthroughDirect() {
        // SELECT d.x FROM (SELECT t.x FROM SRC t) d
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT d.x FROM (SELECT t.x FROM SRC t) d");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("x", r.getTargetColumn());
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("x", r.getSourceColumn());
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT, r.getTransformType());
    }

    @Test
    public void derivedTablePassthroughAggregated() {
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT d.s FROM (SELECT SUM(t.a) AS s FROM SRC t) d");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED, r.getTransformType());
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("a", r.getSourceColumn());
    }

    @Test
    public void derivedTablePassthroughAliasedColumn() {
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT d.out_x FROM (SELECT t.x AS out_x FROM SRC t) d");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("x", r.getSourceColumn());
        assertEquals("out_x", r.getTargetColumn());
    }

    @Test
    public void derivedTablePassthroughJoinMultiSource() {
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT d.a, d.b FROM (SELECT t1.a AS a, t2.b AS b FROM SRC1 t1 JOIN SRC2 t2 ON t1.k=t2.k) d");
        assertEquals(2, cs.stream().filter(c -> !c.isUnresolvable()).count());
        assertTrue(cs.stream().anyMatch(c -> "SRC1".equals(c.getSourceTableName()) && "a".equals(c.getSourceColumn())));
        assertTrue(cs.stream().anyMatch(c -> "SRC2".equals(c.getSourceTableName()) && "b".equals(c.getSourceColumn())));
    }

    @Test
    public void nestedCtePlusDerivedTablePassthrough() {
        // 嵌套：派生表内引用顶层 CTE，派生表输出穿透到底层源表
        List<ColumnLineageCandidate> cs = extractor.extract(
                "WITH cte AS (SELECT t.x FROM SRC t) "
                        + "SELECT d.x FROM (SELECT c.x FROM cte c) d");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("x", r.getSourceColumn());
    }

    @Test
    public void nestedDerivedTableInsideDerivedTable() {
        // 派生表内再嵌套派生表
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT d.x FROM (SELECT inner_d.x FROM (SELECT t.x FROM SRC t) inner_d) d");
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("x", r.getSourceColumn());
    }

    @Test
    public void mixedRealTableAndDerivedTableUnqualifiedAttribution() {
        // 混合真表 + 派生表：无 owner 列引用 → 多表歧义 unresolved（不归属任一源表）
        // 这里 SRC（真表）+ d（派生表）= tableCount 1（仅真表计入），但 aliasMap 中真表只有 1 个 → 实际上不会歧义
        // 故本用例验证：混合场景下无 owner 列归属直查真表（而非穿透派生表，因派生表 alias 需显式 owner）
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT a FROM SRC, (SELECT b AS a FROM SRC2 t) d");
        // 无 owner 列 a：tableCount=1（只 SRC 计数），归属 SRC（不会进入派生表 alias）
        // 注意派生表 alias 不计入 tableCount（D1 单表归属判定）
        ColumnLineageCandidate r = firstResolved(cs);
        assertEquals("SRC", r.getSourceTableName());
        assertEquals("a", r.getSourceColumn());
    }

    @Test
    public void derivedTableWildcardOutputUnresolved() {
        // 派生表内 SELECT * 通配符输出 → 引用列穿透产 unresolved
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT d.x FROM (SELECT * FROM SRC t) d");
        assertFalse(cs.stream().anyMatch(c -> !c.isUnresolvable()
                && "SRC".equals(c.getSourceTableName()) && "x".equals(c.getSourceColumn())),
                "wildcard derived-table output must not fabricate resolved passthrough");
    }

    @Test
    public void derivedTableColumnNotFoundUnresolved() {
        // 派生表只输出 x，引用不存在的列 → unresolved
        List<ColumnLineageCandidate> cs = extractor.extract(
                "SELECT d.ghost FROM (SELECT t.x FROM SRC t) d");
        ColumnLineageCandidate u = firstUnresolved(cs);
        assertTrue(u.getUnresolvedReason().startsWith("cte-or-derived-column-not-found:"),
                "missing derived-table output column must be unresolved, got: " + u.getUnresolvedReason());
    }

    @Test
    public void noFromSourceUnresolved() {
        // SELECT 1 AS x（无 FROM）→ 表达式无列引用，无候选（空列表），覆盖空 From 路径不报错
        List<ColumnLineageCandidate> cs = extractor.extract("SELECT 1 AS x");
        assertTrue(cs.isEmpty(), "constant column has no lineage candidate");
    }
}
