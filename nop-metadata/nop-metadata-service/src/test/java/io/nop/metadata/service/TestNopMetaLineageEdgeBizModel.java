package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 NopMetaLineageEdgeBizModel 的血缘填充机制（Phase 1，架构基线 §2.6.1）：
 * recordLineage（表级+列级 + 不存在 ID 显式失败）+ extractLineageFromSql（表级抽取 + 幂等 +
 * unresolved 标记 + 非 sql 类型/不存在 ID 显式失败）。
 *
 * <p>Anti-Hollow：extractLineageFromSql 真实调用 SQL 抽取器解析 sourceSql 并写入可查的表级边
 * （断言 extractedEdgeCount>0 且 edge 可在 findPage 查到），证明运行时确实执行了 SQL 解析与目录匹配，
 * 非空壳实现。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaLineageEdgeBizModel extends JunitBaseTestCase {

    public TestNopMetaLineageEdgeBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    io.nop.metadata.service.entity.NopMetaLineageEdgeBizModel lineageBiz;

    // ===== recordLineage =====

    /** 表级 + 列级边录入，可在 findPage 查到。 */
    @Test
    public void testRecordLineageTableAndColumnLevel() {
        String moduleId = ensureModule("mod-rec-tc");
        String t1 = saveTable(moduleId, "T_REC_T1");
        String t2 = saveTable(moduleId, "T_REC_T2");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__recordLineage(edges: [" +
                        "{sourceTableId: \"" + t1 + "\", targetTableId: \"" + t2 + "\"}, " +
                        "{sourceTableId: \"" + t1 + "\", targetTableId: \"" + t2 + "\", " +
                        "sourceColumn: \"c1\", targetColumn: \"c2\", transformType: \"derived\", confidence: 0.9}" +
                        "]) }");
        assertFalse(resp.hasError(), "recordLineage should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("recordedEdgeCount=2"),
                "should record 2 edges: " + data);

        // 录入的边可在 NopMetaLineageEdge 查到（含表级与列级）
        assertEquals(2L, countEdges(t1, t2), "both table-level and column-level edges must be persisted");
        // lineageSource 缺省 manual
        NopMetaLineageEdge colEdge = findEdge(t1, t2, "c1", "c2");
        assertNotNull(colEdge, "column-level edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_SOURCE_MANUAL, colEdge.getLineageSource(),
                "default lineageSource must be manual");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED, colEdge.getTransformType(),
                "transformType must be derived");
        assertEquals(0.9d, colEdge.getConfidence(), 0.0001, "confidence must be 0.9");
    }

    /** sourceTableId 不存在必须显式失败（不静默建悬空边）。 */
    @Test
    public void testRecordLineageSourceNotFound() {
        String moduleId = ensureModule("mod-rec-nf");
        String t2 = saveTable(moduleId, "T_REC_NF_T2");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__recordLineage(edges: [" +
                        "{sourceTableId: \"__not_exist__\", targetTableId: \"" + t2 + "\"}]) }");
        assertTrue(resp.hasError(),
                "non-existent sourceTableId must fast-fail (no dangling edge): " + resp);
        assertEquals(0L, countEdges("__not_exist__", t2), "no edge must be persisted on failure");
    }

    /** 空 edges 必须显式失败（不静默返回 0）。 */
    @Test
    public void testRecordLineageEmptyRejected() {
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__recordLineage(edges: []) }");
        assertTrue(resp.hasError(), "empty edges must fast-fail: " + resp);
    }

    // ===== extractLineageFromSql =====

    /**
     * 端到端：tableType=sql 的视图 sourceSql（FROM/JOIN）→ 抽取器解析 → 匹配目录表 → 写入表级边
     * （target=该 sql 表，sourceColumn/targetColumn 空，lineageSource=sql_parse）。
     *
     * <p>Anti-Hollow：真实解析含 FROM + LEFT JOIN 的 SQL，断言 extractedEdgeCount=2 且两条表级边可查，
     * 证明运行时确实调用了 SQL 抽取器并匹配目录表写入边。
     */
    @Test
    public void testExtractLineageFromSqlWritesTableLevelEdges() {
        String moduleId = ensureModule("mod-ext-sql");
        // 两个源表
        saveTable(moduleId, "ORDERS");
        saveTable(moduleId, "CUSTOMERS");
        // 一个 sql 视图表，sourceSql 引用 ORDERS + CUSTOMERS
        String sqlViewId = saveSqlTable(moduleId, "V_ORDER_CUST",
                "SELECT o.id, c.name FROM ORDERS o LEFT JOIN CUSTOMERS c ON o.cust_id = c.id");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertFalse(resp.hasError(), "extractLineageFromSql should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("extractedEdgeCount=2"),
                "should extract 2 table-level edges (ORDERS + CUSTOMERS): " + data);
        assertTrue(data.contains("unresolved=[]") || data.contains("unresolved=]"),
                "all refs should resolve, unresolved empty: " + data);

        // 表级边写入验证：ORDERS→V_ORDER_CUST, CUSTOMERS→V_ORDER_CUST，lineageSource=sql_parse，列空
        String ordersId = findTableId("ORDERS");
        String customersId = findTableId("CUSTOMERS");
        NopMetaLineageEdge e1 = findEdge(ordersId, sqlViewId, null, null);
        assertNotNull(e1, "ORDERS→view table-level edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE, e1.getLineageSource());
        NopMetaLineageEdge e2 = findEdge(customersId, sqlViewId, null, null);
        assertNotNull(e2, "CUSTOMERS→view table-level edge must exist");
    }

    /**
     * 幂等：按 (sourceTableId, targetTableId, lineageSource='sql_parse') 去重，重复抽取不追加。
     */
    @Test
    public void testExtractLineageFromSqlIdempotent() {
        String moduleId = ensureModule("mod-ext-idem");
        saveTable(moduleId, "SRC_A");
        String sqlViewId = saveSqlTable(moduleId, "V_IDEM", "SELECT a.id FROM SRC_A a");

        execute("mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        long countAfterFirst = countSqlParseEdges(findTableId("SRC_A"), sqlViewId);
        assertEquals(1L, countAfterFirst, "exactly 1 sql_parse edge after first extract");

        // 第二次抽取（幂等，不追加）
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertFalse(r2.hasError(), "second extract should not error: " + r2);
        long countAfterSecond = countSqlParseEdges(findTableId("SRC_A"), sqlViewId);
        assertEquals(1L, countAfterSecond,
                "idempotent: sql_parse edge count must stay 1 after second extract: " + countAfterSecond);
    }

    /**
     * sourceSql 引用的表不在目录中 → 进 unresolved（不静默丢弃），不建边。
     */
    @Test
    public void testExtractLineageFromSqlUnresolvedNotDropped() {
        String moduleId = ensureModule("mod-ext-unr");
        saveTable(moduleId, "KNOWN_TBL");
        // GHOST_TBL 不在目录中
        String sqlViewId = saveSqlTable(moduleId, "V_UNRESOLVED",
                "SELECT k.id, g.x FROM KNOWN_TBL k JOIN GHOST_TBL g ON k.id = g.id");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertFalse(resp.hasError(), "extract should not error (unresolved collected): " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("extractedEdgeCount=1"),
                "KNOWN_TBL should resolve into 1 edge: " + data);
        // GHOST_TBL 必须显式出现在 unresolved（不静默丢弃）
        assertTrue(data.contains("GHOST_TBL"),
                "GHOST_TBL must appear in unresolved (not silently dropped): " + data);
        // GHOST_TBL 不在目录，不建边
        assertEquals(1L, countEdgesByTarget(sqlViewId),
                "only KNOWN_TBL→view edge should exist, GHOST must not create a dangling edge");
    }

    /** 非 sql 类型表抽取必须显式失败（不静默返回 0）。 */
    @Test
    public void testExtractLineageFromSqlNotSqlTable() {
        String moduleId = ensureModule("mod-ext-nosql");
        String entityId = saveTable(moduleId, "ENT_TABLE"); // tableType=entity（默认）

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + entityId + "\") }");
        assertTrue(resp.hasError(),
                "non-sql table type must fast-fail (not silently return 0): " + resp);
    }

    /** 不存在的 metaTableId 抽取必须显式失败（不 NPE、不静默）。 */
    @Test
    public void testExtractLineageFromSqlNotFound() {
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"__not_exist__\") }");
        assertTrue(resp.hasError(),
                "non-existent metaTableId must fast-fail (no NPE): " + resp);
    }

    // ===== 图遍历查询（Phase 2） =====

    /**
     * 多跳传递闭包：建一条 A→B→C 链。
     * - getDownstream(A) 含 B, C
     * - getUpstream(C) 含 A, B
     *
     * <p>Anti-Hollow：直接调用 BizModel BFS，断言多跳结果正确（证明运行时确实查询了 MetaLineageEdge 多跳，
     * 非空壳）。同时 GraphQL query 暴露验证。
     */
    @Test
    public void testUpstreamDownstreamMultiHop() {
        String moduleId = ensureModule("mod-trav");
        String a = saveTable(moduleId, "T_A");
        String b = saveTable(moduleId, "T_B");
        String c = saveTable(moduleId, "T_C");

        // 建链 A→B→C
        execute("mutation { NopMetaLineageEdge__recordLineage(edges: [" +
                "{sourceTableId: \"" + a + "\", targetTableId: \"" + b + "\"}, " +
                "{sourceTableId: \"" + b + "\", targetTableId: \"" + c + "\"}]) }");

        // getDownstream(A) 含 B, C
        List<String> downstream = lineageBiz.getDownstream(a);
        assertEquals(2, downstream.size(), "downstream of A must be [B, C]: " + downstream);
        assertTrue(downstream.contains(b) && downstream.contains(c), "downstream must contain B and C");

        // getUpstream(C) 含 A, B
        List<String> upstream = lineageBiz.getUpstream(c);
        assertEquals(2, upstream.size(), "upstream of C must be [A, B]: " + upstream);
        assertTrue(upstream.contains(a) && upstream.contains(b), "upstream must contain A and B");

        // GraphQL 暴露验证（query 可调用且无错）
        GraphQLResponseBean resp = execute("query { NopMetaLineageEdge__getDownstream(metaTableId: \"" + a + "\") }");
        assertFalse(resp.hasError(), "getDownstream must be exposed via GraphQL: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains(b) && data.contains(c), "GraphQL getDownstream(A) must contain B, C: " + data);
    }

    /**
     * 路径查找：A→B→C 链，getLineagePath(A,C) 返回 [A,B,C]；无路径返回显式空（不报错）。
     */
    @Test
    public void testLineagePathAndNoPath() {
        String moduleId = ensureModule("mod-path");
        String a = saveTable(moduleId, "P_A");
        String b = saveTable(moduleId, "P_B");
        String c = saveTable(moduleId, "P_C");
        String z = saveTable(moduleId, "P_Z"); // 孤立表

        execute("mutation { NopMetaLineageEdge__recordLineage(edges: [" +
                "{sourceTableId: \"" + a + "\", targetTableId: \"" + b + "\"}, " +
                "{sourceTableId: \"" + b + "\", targetTableId: \"" + c + "\"}]) }");

        // 路径 A→C = [A, B, C]
        List<String> path = lineageBiz.getLineagePath(a, c);
        assertEquals(3, path.size(), "path A→C must have 3 nodes: " + path);
        assertEquals(a, path.get(0));
        assertEquals(b, path.get(1));
        assertEquals(c, path.get(2));

        // 无路径返回显式空（不报错）
        List<String> noPath = lineageBiz.getLineagePath(a, z);
        assertNotNull(noPath, "no-path must return explicit empty list, not null");
        assertTrue(noPath.isEmpty(), "no path A→Z must be empty (not error): " + noPath);

        // GraphQL 暴露验证：无路径也返回空、不报错
        GraphQLResponseBean resp = execute(
                "query { NopMetaLineageEdge__getLineagePath(sourceTableId: \"" + a + "\", targetTableId: \"" + z + "\") }");
        assertFalse(resp.hasError(), "getLineagePath with no path must not error: " + resp);
    }

    /**
     * 环图不死循环：A→B, B→A（成环）。getDownstream/getUpstream/getLineagePath 均正常终止。
     */
    @Test
    public void testCycleNoInfiniteLoop() {
        String moduleId = ensureModule("mod-cycle");
        String a = saveTable(moduleId, "CY_A");
        String b = saveTable(moduleId, "CY_B");

        execute("mutation { NopMetaLineageEdge__recordLineage(edges: [" +
                "{sourceTableId: \"" + a + "\", targetTableId: \"" + b + "\"}, " +
                "{sourceTableId: \"" + b + "\", targetTableId: \"" + a + "\"}]) }");

        // visited 环检测：A 的下游为 B（B 的下游 A 已 visited，不重复、不死循环）
        List<String> downstream = lineageBiz.getDownstream(a);
        assertEquals(1, downstream.size(), "cycle: downstream of A is just [B]");
        assertEquals(b, downstream.get(0));

        // 路径 A→B = [A, B]（最短路径，不绕环）
        List<String> path = lineageBiz.getLineagePath(a, b);
        assertEquals(2, path.size(), "cycle: shortest path A→B is [A, B]: " + path);
        assertEquals(a, path.get(0));
        assertEquals(b, path.get(1));

        // 起点到自身
        List<String> selfPath = lineageBiz.getLineagePath(a, a);
        assertEquals(1, selfPath.size(), "path A→A is [A]");
    }

    /**
     * 影响分析：有列级边时按列过滤、无列级边时回退表级（不静默返回空）。
     *
     * <p>设置：T1→T2（列级 col=x）、T1→T3（表级）。
     * - getImpactAnalysis(T1, "x") = [T2]（按列过滤）
     * - getImpactAnalysis(T1, null) = [T2, T3]（全表级）
     * - getImpactAnalysis(T1, "no_such_col") = [T2, T3]（该列无列级边，回退表级，不静默空）
     */
    @Test
    public void testImpactAnalysisColumnFilterAndTableFallback() {
        String moduleId = ensureModule("mod-impact");
        String t1 = saveTable(moduleId, "IM_T1");
        String t2 = saveTable(moduleId, "IM_T2");
        String t3 = saveTable(moduleId, "IM_T3");

        // T1→T2 列级（col=x），T1→T3 表级
        execute("mutation { NopMetaLineageEdge__recordLineage(edges: [" +
                "{sourceTableId: \"" + t1 + "\", targetTableId: \"" + t2 + "\", sourceColumn: \"x\", targetColumn: \"y\"}, " +
                "{sourceTableId: \"" + t1 + "\", targetTableId: \"" + t3 + "\"}]) }");

        // 按列过滤：col=x → [T2]
        List<String> byCol = lineageBiz.getImpactAnalysis(t1, "x");
        assertEquals(1, byCol.size(), "impact by col=x must be [T2]: " + byCol);
        assertEquals(t2, byCol.get(0));

        // 全表级（无 col）：[T2, T3]
        List<String> tableLevel = lineageBiz.getImpactAnalysis(t1, null);
        assertEquals(2, tableLevel.size(), "table-level impact must be [T2, T3]: " + tableLevel);
        assertTrue(tableLevel.contains(t2) && tableLevel.contains(t3));

        // 回退表级（col 不存在任何列级边）：不静默返回空，回退为全表级
        List<String> fallback = lineageBiz.getImpactAnalysis(t1, "no_such_col");
        assertEquals(2, fallback.size(), "fallback must return table-level (not silent empty): " + fallback);
        assertTrue(fallback.contains(t2) && fallback.contains(t3),
                "fallback impact must contain both downstream tables: " + fallback);

        // GraphQL 暴露验证
        GraphQLResponseBean resp = execute(
                "query { NopMetaLineageEdge__getImpactAnalysis(metaTableId: \"" + t1 + "\", columnName: \"x\") }");
        assertFalse(resp.hasError(), "getImpactAnalysis must be exposed via GraphQL: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains(t2),
                "GraphQL getImpactAnalysis(T1, x) must contain T2: " + resp.getData());
    }

    // ===== extractColumnLineageFromSql（列级 sql_parse，P2-5+）=====

    /**
     * 列级血缘端到端（架构基线 §2.6.1 列级 sql_parse）：建 sql 视图表（SELECT t1.a AS x, t2.b FROM src1 t1 JOIN src2 t2）
     * → 抽取列级血缘 → 列级边可查（x←src1.a direct、b←src2.b direct），表别名归属解析正确。
     *
     * <p>Anti-Hollow：真实解析含 JOIN + 别名的 SQL，断言列级边含真实 sourceColumn/targetColumn/transformType，
     * 证明运行时确实执行了 AST 列引用解析与归属（非空壳）。
     */
    @Test
    public void testExtractColumnLineageDirectAndAliasAttribution() {
        String moduleId = ensureModule("mod-col-direct");
        String src1 = saveTable(moduleId, "COL_SRC1");
        String src2 = saveTable(moduleId, "COL_SRC2");
        String sqlViewId = saveSqlTable(moduleId, "V_COL_DIRECT",
                "SELECT t1.a AS x, t2.b FROM COL_SRC1 t1 JOIN COL_SRC2 t2 ON t1.k = t2.k");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertFalse(resp.hasError(), "extractColumnLineageFromSql should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("extractedEdgeCount=2"),
                "should extract 2 column-level edges (x<-a, b<-b): " + data);

        // 列级边写入验证：x←src1.a direct、b←src2.b direct
        NopMetaLineageEdge e1 = findColumnEdge(src1, sqlViewId, "a", "x");
        assertNotNull(e1, "src1.a -> view.x column-level edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE, e1.getLineageSource());
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT, e1.getTransformType(),
                "direct column reference must be transformType=direct");

        NopMetaLineageEdge e2 = findColumnEdge(src2, sqlViewId, "b", "b");
        assertNotNull(e2, "src2.b -> view.b column-level edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT, e2.getTransformType());

        // 表别名归属：a 归属 src1，b 归属 src2（不混淆）
        assertEquals(src1, e1.getSourceTableId(), "t1.a must be attributed to src1");
        assertEquals(src2, e2.getSourceTableId(), "t2.b must be attributed to src2");
    }

    /**
     * 跨表表达式列多源列边（D3）：SELECT t1.a + t2.b AS total → 产出 2 条 derived 边
     * （total←src1.a + total←src2.b），一个表达式输出列引用 N 个源列产出 N 条边。
     */
    @Test
    public void testExtractColumnLineageExpressionMultiSource() {
        String moduleId = ensureModule("mod-col-expr");
        String src1 = saveTable(moduleId, "EXPR_SRC1");
        String src2 = saveTable(moduleId, "EXPR_SRC2");
        String sqlViewId = saveSqlTable(moduleId, "V_EXPR",
                "SELECT t1.a + t2.b AS total FROM EXPR_SRC1 t1 JOIN EXPR_SRC2 t2 ON t1.k = t2.k");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertFalse(resp.hasError(), "should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("extractedEdgeCount=2"),
                "expression referencing 2 source columns must produce 2 derived edges: " + data);

        // 两条 derived 边：total←src1.a、total←src2.b
        NopMetaLineageEdge e1 = findColumnEdge(src1, sqlViewId, "a", "total");
        assertNotNull(e1, "total<-src1.a derived edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED, e1.getTransformType(),
                "expression column must be transformType=derived");

        NopMetaLineageEdge e2 = findColumnEdge(src2, sqlViewId, "b", "total");
        assertNotNull(e2, "total<-src2.b derived edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED, e2.getTransformType());
    }

    /**
     * transformType aggregated（D3）：SUM(t1.a) → aggregated。聚合检测优先 instanceof SqlAggregateFunction。
     */
    @Test
    public void testExtractColumnLineageAggregate() {
        String moduleId = ensureModule("mod-col-agg");
        String src1 = saveTable(moduleId, "AGG_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_AGG",
                "SELECT SUM(t1.a) AS s FROM AGG_SRC t1");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertFalse(resp.hasError(), "should not error: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains("extractedEdgeCount=1"),
                "SUM(a) should produce 1 aggregated edge");

        NopMetaLineageEdge e = findColumnEdge(src1, sqlViewId, "a", "s");
        assertNotNull(e, "s<-src.a aggregated edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED, e.getTransformType(),
                "SUM(col) must be transformType=aggregated");
    }

    /**
     * 幂等（D3 列级五元组）：重复抽取同一 sql 表，列级边不无限追加。
     */
    @Test
    public void testExtractColumnLineageIdempotent() {
        String moduleId = ensureModule("mod-col-idem");
        String src1 = saveTable(moduleId, "CIDEM_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_CIDEM",
                "SELECT t1.a AS x, t1.b AS y FROM CIDEM_SRC t1");

        execute("mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        long countAfterFirst = countColumnSqlParseEdges(src1, sqlViewId);
        assertEquals(2L, countAfterFirst, "2 column edges after first extract (x<-a, y<-b)");

        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertFalse(r2.hasError(), "second extract should not error: " + r2);
        long countAfterSecond = countColumnSqlParseEdges(src1, sqlViewId);
        assertEquals(2L, countAfterSecond,
                "idempotent: column edge count must stay 2 after second extract: " + countAfterSecond);
    }

    /**
     * 表级/列级 upsert 隔离（D2）：列级边存在后，重跑表级 extractLineageFromSql 仍正确创建表级边
     * （表级查询补 sourceColumn IS NULL，不误匹配列级边），二者共存正确。
     */
    @Test
    public void testTableAndColumnLevelUpsertIsolation() {
        String moduleId = ensureModule("mod-col-iso");
        String src1 = saveTable(moduleId, "ISO_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_ISO",
                "SELECT t1.a AS x FROM ISO_SRC t1");

        // 先抽取列级血缘（写入列级边）
        execute("mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertEquals(1L, countColumnSqlParseEdges(src1, sqlViewId),
                "1 column-level edge after column extract");

        // 再抽取表级血缘（写入表级边）
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertFalse(r2.hasError(), "table-level extract should not error: " + r2);
        assertTrue(String.valueOf(r2.getData()).contains("extractedEdgeCount=1"),
                "table-level extract must create 1 table-level edge: " + r2.getData());

        // 表级边（sourceColumn=null）与列级边（sourceColumn=a）共存，互不干扰
        assertEquals(1L, countTableLevelSqlParseEdges(src1, sqlViewId),
                "1 table-level edge (sourceColumn null) must coexist");
        assertEquals(1L, countColumnSqlParseEdges(src1, sqlViewId),
                "1 column-level edge (sourceColumn non-null) must still exist after table extract");

        // 重跑表级不追加（幂等且隔离）
        execute("mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertEquals(1L, countTableLevelSqlParseEdges(src1, sqlViewId),
                "table-level edge count stays 1 after re-extract (isolation + idempotent)");
    }

    /**
     * 无别名单表归属 vs 多表歧义（D1）：单表 SELECT col → 归属唯一源表；多表 SELECT col FROM a,b → unresolved。
     */
    @Test
    public void testUnqualifiedColumnSingleVsMultiTable() {
        String moduleId = ensureModule("mod-col-unq");
        // 单表：col 归属 single_src
        String singleSrc = saveTable(moduleId, "UNQ_SINGLE");
        String sqlViewSingle = saveSqlTable(moduleId, "V_UNQ_SINGLE",
                "SELECT col FROM UNQ_SINGLE");
        GraphQLResponseBean r1 = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewSingle + "\") }");
        assertFalse(r1.hasError(), "single-table unqualified should not error: " + r1);
        NopMetaLineageEdge e1 = findColumnEdge(singleSrc, sqlViewSingle, "col", "col");
        assertNotNull(e1, "unqualified col on single table must be attributed to the single source");

        // 多表：col 歧义 → unresolved
        saveTable(moduleId, "UNQ_A");
        saveTable(moduleId, "UNQ_B");
        String sqlViewMulti = saveSqlTable(moduleId, "V_UNQ_MULTI",
                "SELECT col FROM UNQ_A, UNQ_B");
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewMulti + "\") }");
        assertFalse(r2.hasError(), "multi-table unqualified should not error (collected as unresolved): " + r2);
        String data2 = String.valueOf(r2.getData());
        assertTrue(data2.contains("ambiguous-column-multi-table"),
                "unqualified column on multi-table must be explicitly unresolved (not silent skip): " + data2);
        assertEquals(0L, countEdgesByTarget(sqlViewMulti),
                "no column-level edge for ambiguous unqualified column (no fabrication)");
    }

    /**
     * dangling 一致：源列所属表未匹配目录 → 进 unresolved，不建悬空边（sourceTableId mandatory）。
     */
    @Test
    public void testExtractColumnLineageDangling() {
        String moduleId = ensureModule("mod-col-dangle");
        String sqlViewId = saveSqlTable(moduleId, "V_DANGLE",
                "SELECT t1.a AS x FROM GHOST_TBL_DANGLE t1");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");
        assertFalse(resp.hasError(), "dangling should not error (collected as unresolved): " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("extractedEdgeCount=0"),
                "no edge for dangling source table: " + data);
        assertTrue(data.contains("GHOST_TBL_DANGLE"),
                "dangling source table must appear in unresolved (not silently dropped): " + data);
        assertTrue(data.contains("source-table-not-in-catalog"),
                "dangling reason must be explicit: " + data);
        assertEquals(0L, countEdgesByTarget(sqlViewId),
                "no dangling edge created (sourceTableId mandatory)");
    }

    /**
     * 降级显式（不静默跳过）：通配符 t.* → 显式 unresolved（不伪造、不静默丢弃）。
     * 非 sql 类型/不存在/空 sourceSql → 显式失败（抛 ErrorCode，不静默）。
     */
    @Test
    public void testExtractColumnLineageWildcardAndFailures() {
        String moduleId = ensureModule("mod-col-wild");
        saveTable(moduleId, "WILD_SRC");
        // 通配符 t.* → unresolved candidate（不静默跳过）
        String sqlViewWildcard = saveSqlTable(moduleId, "V_WILD",
                "SELECT t.* FROM WILD_SRC t");
        GraphQLResponseBean rWild = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewWildcard + "\") }");
        assertFalse(rWild.hasError(), "wildcard should not hard-error (collected as unresolved): " + rWild);
        assertTrue(String.valueOf(rWild.getData()).contains("wildcard-projection"),
                "wildcard projection must be explicitly unresolved (not silent skip)");

        // 非 sql 类型 → 显式失败
        String entityId = saveTable(moduleId, "ENT_FAIL");
        GraphQLResponseBean rNotSql = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + entityId + "\") }");
        assertTrue(rNotSql.hasError(), "non-sql table must fast-fail: " + rNotSql);

        // 不存在 → 显式失败
        GraphQLResponseBean rNotFound = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"__not_exist__\") }");
        assertTrue(rNotFound.hasError(), "non-existent metaTableId must fast-fail: " + rNotFound);
    }

    /**
     * 列级边被遍历消费：列级边写入后 getImpactAnalysis(srcId, columnName) 按 columnName 过滤生效
     * （无需改遍历代码）。变更源表 src 的列 a → 影响下游视图的列 x。
     */
    @Test
    public void testColumnLineageConsumedByImpactAnalysis() {
        String moduleId = ensureModule("mod-col-impact");
        String src1 = saveTable(moduleId, "IMP_COL_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_IMP_COL",
                "SELECT t1.a AS x, t1.b AS y FROM IMP_COL_SRC t1");

        execute("mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") }");

        // 变更 src 的列 a → 影响下游 view（列级过滤生效）
        List<String> impactA = lineageBiz.getImpactAnalysis(src1, "a");
        assertEquals(1, impactA.size(), "impact(src, a) must be [view] via column-level filter: " + impactA);
        assertEquals(sqlViewId, impactA.get(0));

        // 变更 src 的列 b → 影响下游 view（列级过滤对 b 也生效）
        List<String> impactB = lineageBiz.getImpactAnalysis(src1, "b");
        assertEquals(1, impactB.size(), "impact(src, b) must be [view] via column-level filter: " + impactB);
        assertEquals(sqlViewId, impactB.get(0));

        // 变更 src 的不存在的列 → 回退表级下游（不静默空）
        List<String> impactFallback = lineageBiz.getImpactAnalysis(src1, "no_such_col");
        assertEquals(1, impactFallback.size(), "fallback must return table-level downstream: " + impactFallback);

        // GraphQL 暴露验证
        GraphQLResponseBean resp = execute(
                "query { NopMetaLineageEdge__getImpactAnalysis(metaTableId: \"" + src1 + "\", columnName: \"a\") }");
        assertFalse(resp.hasError(), "getImpactAnalysis column filter must be exposed via GraphQL: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains(sqlViewId),
                "GraphQL getImpactAnalysis(src, a) must contain view: " + resp.getData());
    }

    // ===== helpers =====

    private String ensureModule(String moduleName) {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleName, moduleName));
        NopMetaModule module = dao.findFirstByQuery(q);
        if (module != null) {
            return module.getMetaModuleId();
        }
        module = dao.newEntity();
        module.setModuleId("nop/" + moduleName);
        module.setModuleName(moduleName);
        module.setDisplayName(moduleName);
        module.setModuleVersion(1L);
        module.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(module);
        dao.flushSession();
        return module.getMetaModuleId();
    }

    /** 保存一张 tableType=entity 的目录表，返回生成的 metaTableId。 */
    private String saveTable(String moduleId, String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_ENTITY);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    /** 保存一张 tableType=sql 的视图表，含 sourceSql，返回生成的 metaTableId。 */
    private String saveSqlTable(String moduleId, String tableName, String sourceSql) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_SQL);
        t.setSourceSql(sourceSql);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    private String findTableId(String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        NopMetaTable t = dao.findFirstByQuery(q);
        assertNotNull(t, "table " + tableName + " must exist");
        return t.getMetaTableId();
    }

    private long countEdges(String sourceId, String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        return dao.countByQuery(q);
    }

    private long countEdgesByTarget(String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        return dao.countByQuery(q);
    }

    private long countSqlParseEdges(String sourceId, String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        return dao.countByQuery(q);
    }

    /** 计数列级 sql_parse 边（sourceColumn 非空）。 */
    private long countColumnSqlParseEdges(String sourceId, String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        q.addFilter(FilterBeans.notNull(NopMetaLineageEdge.PROP_NAME_sourceColumn));
        return dao.countByQuery(q);
    }

    /** 计数表级 sql_parse 边（sourceColumn 为 null，D2 隔离）。 */
    private long countTableLevelSqlParseEdges(String sourceId, String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        q.addFilter(FilterBeans.isNull(NopMetaLineageEdge.PROP_NAME_sourceColumn));
        return dao.countByQuery(q);
    }

    /** 查找列级 sql_parse 边（按 source/target table + source/target column 精确匹配）。 */
    private NopMetaLineageEdge findColumnEdge(String sourceId, String targetId, String sourceCol, String targetCol) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, sourceCol));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetColumn, targetCol));
        return dao.findFirstByQuery(q);
    }

    private NopMetaLineageEdge findEdge(String sourceId, String targetId, String sourceCol, String targetCol) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        if (sourceCol != null) {
            q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, sourceCol));
        }
        if (targetCol != null) {
            q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetColumn, targetCol));
        }
        return dao.findFirstByQuery(q);
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
