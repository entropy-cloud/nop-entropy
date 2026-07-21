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
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 NopMetaLineageEdgeBizModel 的血缘填充机制（Phase 1，架构基线 §2.6.1）：
 * recordLineage（表级+列级 + 不存在 ID 显式失败）+ extractLineageFromSql（表级抽取 + 幂等 +
 * unresolved 标记 + 非 sql 类型/不存在 ID 显式失败）。
 *
 * <p>Anti-Hollow：extractLineageFromSql 真实调用 SQL 抽取器解析 sourceSql 并写入可查的表级边
 * （断言 edgeCount>0 且 edge 可在 findPage 查到），证明运行时确实执行了 SQL 解析与目录匹配，
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

    @Inject
    io.nop.orm.IOrmTemplate ormTemplate;

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
                        "]) { edgeCount } }");
        assertFalse(resp.hasError(), "recordLineage should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=2"),
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
                        "{sourceTableId: \"__not_exist__\", targetTableId: \"" + t2 + "\"}]) { edgeCount } }");
        assertTrue(resp.hasError(),
                "non-existent sourceTableId must fast-fail (no dangling edge): " + resp);
        assertEquals(0L, countEdges("__not_exist__", t2), "no edge must be persisted on failure");
    }

    /** 空 edges 必须显式失败（不静默返回 0）。 */
    @Test
    public void testRecordLineageEmptyRejected() {
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__recordLineage(edges: []) { edgeCount } }");
        assertTrue(resp.hasError(), "empty edges must fast-fail: " + resp);
    }

    // ===== extractLineageFromSql =====

    /**
     * 端到端：tableType=sql 的视图 sourceSql（FROM/JOIN）→ 抽取器解析 → 匹配目录表 → 写入表级边
     * （target=该 sql 表，sourceColumn/targetColumn 空，lineageSource=sql_parse）。
     *
     * <p>Anti-Hollow：真实解析含 FROM + LEFT JOIN 的 SQL，断言 edgeCount=2 且两条表级边可查，
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
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "extractLineageFromSql should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=2"),
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

        execute("mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        long countAfterFirst = countSqlParseEdges(findTableId("SRC_A"), sqlViewId);
        assertEquals(1L, countAfterFirst, "exactly 1 sql_parse edge after first extract");

        // 第二次抽取（幂等，不追加）
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
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
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "extract should not error (unresolved collected): " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=1"),
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
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + entityId + "\") { edgeCount } }");
        assertTrue(resp.hasError(),
                "non-sql table type must fast-fail (not silently return 0): " + resp);
    }

    /** 不存在的 metaTableId 抽取必须显式失败（不 NPE、不静默）。 */
    @Test
    public void testExtractLineageFromSqlNotFound() {
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"__not_exist__\") { edgeCount } }");
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
                "{sourceTableId: \"" + b + "\", targetTableId: \"" + c + "\"}]) { edgeCount } }");

        // getDownstream(A) 含 B, C
        List<String> downstream = lineageBiz.getDownstream(a, null);
        assertEquals(2, downstream.size(), "downstream of A must be [B, C]: " + downstream);
        assertTrue(downstream.contains(b) && downstream.contains(c), "downstream must contain B and C");

        // getUpstream(C) 含 A, B
        List<String> upstream = lineageBiz.getUpstream(c, null);
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
                "{sourceTableId: \"" + b + "\", targetTableId: \"" + c + "\"}]) { edgeCount } }");

        // 路径 A→C = [A, B, C]
        List<String> path = lineageBiz.getLineagePath(a, c, null);
        assertEquals(3, path.size(), "path A→C must have 3 nodes: " + path);
        assertEquals(a, path.get(0));
        assertEquals(b, path.get(1));
        assertEquals(c, path.get(2));

        // 无路径返回显式空（不报错）
        List<String> noPath = lineageBiz.getLineagePath(a, z, null);
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
                "{sourceTableId: \"" + b + "\", targetTableId: \"" + a + "\"}]) { edgeCount } }");

        // visited 环检测：A 的下游为 B（B 的下游 A 已 visited，不重复、不死循环）
        List<String> downstream = lineageBiz.getDownstream(a, null);
        assertEquals(1, downstream.size(), "cycle: downstream of A is just [B]");
        assertEquals(b, downstream.get(0));

        // 路径 A→B = [A, B]（最短路径，不绕环）
        List<String> path = lineageBiz.getLineagePath(a, b, null);
        assertEquals(2, path.size(), "cycle: shortest path A→B is [A, B]: " + path);
        assertEquals(a, path.get(0));
        assertEquals(b, path.get(1));

        // 起点到自身
        List<String> selfPath = lineageBiz.getLineagePath(a, a, null);
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
                "{sourceTableId: \"" + t1 + "\", targetTableId: \"" + t3 + "\"}]) { edgeCount } }");

        // 按列过滤：col=x → [T2]
        List<String> byCol = lineageBiz.getImpactAnalysis(t1, "x", null);
        assertEquals(1, byCol.size(), "impact by col=x must be [T2]: " + byCol);
        assertEquals(t2, byCol.get(0));

        // 全表级（无 col）：[T2, T3]
        List<String> tableLevel = lineageBiz.getImpactAnalysis(t1, null, null);
        assertEquals(2, tableLevel.size(), "table-level impact must be [T2, T3]: " + tableLevel);
        assertTrue(tableLevel.contains(t2) && tableLevel.contains(t3));

        // 回退表级（col 不存在任何列级边）：不静默返回空，回退为全表级
        List<String> fallback = lineageBiz.getImpactAnalysis(t1, "no_such_col", null);
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
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "extractColumnLineageFromSql should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=2"),
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
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=2"),
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
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "should not error: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains("edgeCount=1"),
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

        execute("mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        long countAfterFirst = countColumnSqlParseEdges(src1, sqlViewId);
        assertEquals(2L, countAfterFirst, "2 column edges after first extract (x<-a, y<-b)");

        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
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
        execute("mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertEquals(1L, countColumnSqlParseEdges(src1, sqlViewId),
                "1 column-level edge after column extract");

        // 再抽取表级血缘（写入表级边）
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(r2.hasError(), "table-level extract should not error: " + r2);
        assertTrue(String.valueOf(r2.getData()).contains("edgeCount=1"),
                "table-level extract must create 1 table-level edge: " + r2.getData());

        // 表级边（sourceColumn=null）与列级边（sourceColumn=a）共存，互不干扰
        assertEquals(1L, countTableLevelSqlParseEdges(src1, sqlViewId),
                "1 table-level edge (sourceColumn null) must coexist");
        assertEquals(1L, countColumnSqlParseEdges(src1, sqlViewId),
                "1 column-level edge (sourceColumn non-null) must still exist after table extract");

        // 重跑表级不追加（幂等且隔离）
        execute("mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
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
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewSingle + "\") { edgeCount unresolved errors } }");
        assertFalse(r1.hasError(), "single-table unqualified should not error: " + r1);
        NopMetaLineageEdge e1 = findColumnEdge(singleSrc, sqlViewSingle, "col", "col");
        assertNotNull(e1, "unqualified col on single table must be attributed to the single source");

        // 多表：col 歧义 → unresolved
        saveTable(moduleId, "UNQ_A");
        saveTable(moduleId, "UNQ_B");
        String sqlViewMulti = saveSqlTable(moduleId, "V_UNQ_MULTI",
                "SELECT col FROM UNQ_A, UNQ_B");
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewMulti + "\") { edgeCount unresolved errors } }");
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
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "dangling should not error (collected as unresolved): " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=0"),
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
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewWildcard + "\") { edgeCount unresolved errors } }");
        assertFalse(rWild.hasError(), "wildcard should not hard-error (collected as unresolved): " + rWild);
        assertTrue(String.valueOf(rWild.getData()).contains("wildcard-projection"),
                "wildcard projection must be explicitly unresolved (not silent skip)");

        // 非 sql 类型 → 显式失败
        String entityId = saveTable(moduleId, "ENT_FAIL");
        GraphQLResponseBean rNotSql = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + entityId + "\") { edgeCount } }");
        assertTrue(rNotSql.hasError(), "non-sql table must fast-fail: " + rNotSql);

        // 不存在 → 显式失败
        GraphQLResponseBean rNotFound = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"__not_exist__\") { edgeCount } }");
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

        execute("mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");

        // 变更 src 的列 a → 影响下游 view（列级过滤生效）
        List<String> impactA = lineageBiz.getImpactAnalysis(src1, "a", null);
        assertEquals(1, impactA.size(), "impact(src, a) must be [view] via column-level filter: " + impactA);
        assertEquals(sqlViewId, impactA.get(0));

        // 变更 src 的列 b → 影响下游 view（列级过滤对 b 也生效）
        List<String> impactB = lineageBiz.getImpactAnalysis(src1, "b", null);
        assertEquals(1, impactB.size(), "impact(src, b) must be [view] via column-level filter: " + impactB);
        assertEquals(sqlViewId, impactB.get(0));

        // 变更 src 的不存在的列 → 回退表级下游（不静默空）
        List<String> impactFallback = lineageBiz.getImpactAnalysis(src1, "no_such_col", null);
        assertEquals(1, impactFallback.size(), "fallback must return table-level downstream: " + impactFallback);

        // GraphQL 暴露验证
        GraphQLResponseBean resp = execute(
                "query { NopMetaLineageEdge__getImpactAnalysis(metaTableId: \"" + src1 + "\", columnName: \"a\") }");
        assertFalse(resp.hasError(), "getImpactAnalysis column filter must be exposed via GraphQL: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains(sqlViewId),
                "GraphQL getImpactAnalysis(src, a) must contain view: " + resp.getData());
    }

    // ===== extractColumnLineageFromSql × CTE / 派生表列穿透（P2-5++）=====

    /**
     * CTE 列穿透端到端（架构基线 §2.6.1 列级 sql_parse + §4.2.1 CTE 支持，P2-5++ Phase 1）：
     * 建 sql 视图表（WITH cte AS (SELECT t.x, t.y FROM SRC t) SELECT c.x FROM cte c）→ 抽取列级血缘
     * → 列级边穿透到底层 SRC.x → 边 sourceTableId 指向 SRC（非 CTE 名，目录可匹配）。
     *
     * <p>Anti-Hollow：经 BizModel action 入口 extractColumnLineageFromSql → extractor 真实解析 CTE
     * → upsert 写边，断言边 sourceTableId 指向底层物理 SRC（非悬空到 CTE 名因目录 miss 而丢失）。
     */
    @Test
    public void testExtractColumnLineageCtePassthrough() {
        String moduleId = ensureModule("mod-col-cte");
        String src1 = saveTable(moduleId, "CTE_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_CTE",
                "WITH cte AS (SELECT t.x, t.y FROM CTE_SRC t) SELECT c.x FROM cte c");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "extractColumnLineageFromSql with CTE should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=1"),
                "CTE passthrough must produce 1 column-level edge to underlying SRC: " + data);

        // 关键断言：边 sourceTableId 指向底层 SRC（非 CTE 名）—— Anti-Hollow：穿透真实生效
        NopMetaLineageEdge e = findColumnEdge(src1, sqlViewId, "x", "x");
        assertNotNull(e, "CTE passthrough: edge SRC.x -> view.x must exist (penetrated to underlying source)");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE, e.getLineageSource());
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT, e.getTransformType(),
                "pure passthrough column must be direct");
    }

    /** CTE 内聚合列 SUM(t.a) AS s → 经引用穿透产出 aggregated 候选（端到端）。 */
    @Test
    public void testExtractColumnLineageCteAggregatePassthrough() {
        String moduleId = ensureModule("mod-col-cte-agg");
        String src1 = saveTable(moduleId, "CTEAGG_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_CTE_AGG",
                "WITH cte AS (SELECT SUM(t.a) AS s FROM CTEAGG_SRC t) SELECT c.s FROM cte c");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "should not error: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains("edgeCount=1"),
                "CTE aggregate passthrough must produce 1 edge: " + resp.getData());

        NopMetaLineageEdge e = findColumnEdge(src1, sqlViewId, "a", "s");
        assertNotNull(e, "SUM(t.a) AS s via CTE must produce edge SRC.a -> view.s");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED, e.getTransformType(),
                "CTE aggregate passthrough must be transformType=aggregated");
    }

    /**
     * CTE 穿透 + 列重命名：CTE 内 SELECT t.x AS out_x → 引用 c.out_x 穿透到 SRC.x（端到端）。
     * 这也是接线验证的关键用例：穿透后 sourceColumn 是底层 x（非 out_x），证明真实列映射而非简单复制。
     */
    @Test
    public void testExtractColumnLineageCteAliasedColumnPassthrough() {
        String moduleId = ensureModule("mod-col-cte-alias");
        String src1 = saveTable(moduleId, "CTEALIAS_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_CTE_ALIAS",
                "WITH cte AS (SELECT t.x AS out_x FROM CTEALIAS_SRC t) SELECT c.out_x FROM cte c");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "should not error: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains("edgeCount=1"),
                "aliased CTE column passthrough must produce 1 edge: " + resp.getData());

        // sourceColumn 是底层 SRC.x（非 CTE 内别名 out_x），targetColumn 是视图输出列 out_x
        NopMetaLineageEdge e = findColumnEdge(src1, sqlViewId, "x", "out_x");
        assertNotNull(e, "CTE alias passthrough must map sourceColumn=SRC.x, targetColumn=out_x");
    }

    /**
     * 派生表（subquery）列穿透端到端（P2-5++ Phase 2）：
     * SELECT d.x FROM (SELECT t.x FROM DERIVED_SRC t) d → 边指向 DERIVED_SRC（端到端）。
     */
    @Test
    public void testExtractColumnLineageDerivedTablePassthrough() {
        String moduleId = ensureModule("mod-col-derived");
        String src1 = saveTable(moduleId, "DERIVED_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_DERIVED",
                "SELECT d.x FROM (SELECT t.x FROM DERIVED_SRC t) d");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "extractColumnLineageFromSql with derived table should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=1"),
                "derived-table passthrough must produce 1 column-level edge to underlying SRC: " + data);

        NopMetaLineageEdge e = findColumnEdge(src1, sqlViewId, "x", "x");
        assertNotNull(e, "derived-table passthrough: edge SRC.x -> view.x must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DIRECT, e.getTransformType());
    }

    /**
     * 嵌套 CTE + 派生表组合穿透（端到端）：派生表内引用顶层 CTE，穿透到最底层源表。
     */
    @Test
    public void testExtractColumnLineageNestedCtePlusDerivedTable() {
        String moduleId = ensureModule("mod-col-nested");
        String src1 = saveTable(moduleId, "NESTED_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_NESTED",
                "WITH cte AS (SELECT t.x FROM NESTED_SRC t) "
                        + "SELECT d.x FROM (SELECT c.x FROM cte c) d");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "nested CTE + derived table should not error: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains("edgeCount=1"),
                "nested CTE+derived passthrough must produce 1 edge to underlying source: " + resp.getData());

        // Anti-Hollow：穿透到最底层 NESTED_SRC（经派生表 alias d → CTE cte → SRC）
        NopMetaLineageEdge e = findColumnEdge(src1, sqlViewId, "x", "x");
        assertNotNull(e, "nested CTE+derived passthrough: edge SRC.x -> view.x must exist (penetrated to deepest source)");
    }

    /**
     * 混合真表 + 派生表无限定符列归属不回归（P2-5++ Phase 2 tableCount 边界）：
     * 派生表 alias 不计入 tableCount，故单真表 + 派生表场景下无 owner 列仍归属唯一真表（不歧义）。
     */
    @Test
    public void testMixedRealTableAndDerivedTableUnqualifiedAttribution() {
        String moduleId = ensureModule("mod-col-mixed");
        String src1 = saveTable(moduleId, "MIXED_SRC");
        saveTable(moduleId, "MIXED_SRC2");
        String sqlViewId = saveSqlTable(moduleId, "V_MIXED",
                "SELECT a FROM MIXED_SRC, (SELECT b AS a FROM MIXED_SRC2 t) d");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "mixed real+derived table should not error: " + resp);
        // 无 owner 列 a：tableCount=1（只 MIXED_SRC 计数）→ 归属 MIXED_SRC（不歧义）
        NopMetaLineageEdge e = findColumnEdge(src1, sqlViewId, "a", "a");
        assertNotNull(e, "unqualified column on single real table + derived table must be attributed to real table");
    }

    /**
     * CTE 通配符输出 → unresolved（不伪造、不静默丢弃，端到端验证）。
     * CTE 内 SELECT * → 引用列穿透产 unresolved（不伪造边）。
     */
    @Test
    public void testExtractColumnLineageCteWildcardOutputUnresolved() {
        String moduleId = ensureModule("mod-col-cte-wild");
        saveTable(moduleId, "CTEW_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_CTE_W",
                "WITH cte AS (SELECT * FROM CTEW_SRC t) SELECT c.x FROM cte c");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "wildcard CTE output should not hard-error (collected as unresolved): " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=0"),
                "wildcard CTE output must not fabricate edges: " + data);
        // unresolved 中含明确原因（不静默丢弃）
        assertTrue(data.contains("cte-wildcard") || data.contains("cte-or-derived-column-not-found")
                        || data.contains("passthrough-no-source-column"),
                "wildcard CTE output must be explicitly unresolved with reason: " + data);
    }

    // ===== extractMeasureLineage（expression 型 Measure 列级血缘，架构基线 §2.6.1 D1-D6）=====

    /**
     * 端到端测试 1（成功路径，D1+D3+D4）：建表（含 4 字段 A/B/C/D）+ 2 个 expression measure（M1=A+B, M2=C+D）
     * → 调 action → 断言 edgeCount **==4**（严格，flat-collect：A→M1, B→M1, C→M2, D→M2）
     * + 4 条 measure_parse 自环边可经 DAO 查到 + targetColumn == measureName + transformType == aggregated。
     *
     * <p>Anti-Hollow：真实经 validator 分词 + resolver 归属 + 边落盘，断言入口到出口连通，非空壳。
     */
    @Test
    public void testExtractMeasureLineageSuccessFlatCollect() {
        String moduleId = ensureModule("mod-measure-success");
        String entityId = saveEntity(moduleId, "MeasureSuccessEnt", "A", "B", "C", "D");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_OK", entityId);

        saveMeasure(tableId, "M1", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);
        saveMeasure(tableId, "M2", "C + D", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertFalse(resp.hasError(), "extractMeasureLineage should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=4"),
                "flat-collect: 4 edges (A->M1, B->M1, C->M2, D->M2), strict ==4: " + data);

        // 4 条 measure_parse 自环边可查
        assertEquals(4L, countMeasureParseEdges(tableId),
                "exactly 4 measure_parse self-loop edges persisted");

        // targetColumn == measureName + transformType == aggregated
        NopMetaLineageEdge aToM1 = findColumnEdge(tableId, tableId, "A", "M1");
        assertNotNull(aToM1, "A->M1 self-loop edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE, aToM1.getLineageSource());
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED, aToM1.getTransformType(),
                "aggFunc non-null -> transformType=aggregated (D4)");
        assertEquals(tableId, aToM1.getSourceTableId(), "self-loop: sourceTableId == targetTableId");
        assertEquals(tableId, aToM1.getTargetTableId());

        NopMetaLineageEdge dToM2 = findColumnEdge(tableId, tableId, "D", "M2");
        assertNotNull(dToM2, "D->M2 self-loop edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_AGGREGATED, dToM2.getTransformType());
    }

    /**
     * 端到端测试 2（召回路径，D2）：基于测试 1 的数据，调直接边查询召回——
     * `where sourceTableId=T AND sourceColumn=A AND lineageSource=measure_parse` 返回边的
     * targetColumn == 引用 A 的 measure 的 measureName（M1），验证 D2 召回完整性。
     */
    @Test
    public void testExtractMeasureLineageRecallByDirectEdgeQuery() {
        String moduleId = ensureModule("mod-measure-recall");
        String entityId = saveEntity(moduleId, "MeasureRecallEnt", "A", "B");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_RECALL", entityId);
        saveMeasure(tableId, "M_RECALL", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");

        // D2 召回：直接边查询——列 A 影响哪些 measure
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, "A"));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        List<NopMetaLineageEdge> edges = dao.findAllByQuery(q);
        assertEquals(1, edges.size(), "recall: A affects exactly 1 measure: " + edges);
        assertEquals("M_RECALL", edges.get(0).getTargetColumn(),
                "targetColumn == measureName (D2 recall integrity)");

        // D2：仅经既有 NopMetaLineageEdge CRUD 直接查询召回（无新 API、无 BFS 改动）。
        // 多列复合查询条件同样召回（验证 sourceTableId+sourceColumn+lineageSource 三条件命中）：
        QueryBean q2 = new QueryBean();
        q2.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q2.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, "B"));
        q2.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        List<NopMetaLineageEdge> edges2 = dao.findAllByQuery(q2);
        assertEquals(1, edges2.size(), "recall: B affects exactly 1 measure: " + edges2);
        assertEquals("M_RECALL", edges2.get(0).getTargetColumn(),
                "D2 recall integrity for column B");
    }

    /**
     * 端到端测试 3（per-measure 失败隔离，D5 (b)）：建表 + 2 个 measure，measure1 expression 合法（A+B），
     * measure2 expression 含关键字黑名单（DROP，触发 unsafe）→ 调 action → 断言
     * edgeCount == measure1 的边数（2）+ errors 含 measure2 条目（标 measureName + 错误）
     * + measure1 的边已落盘（per-measure 隔离不中断整批）。
     */
    @Test
    public void testExtractMeasureLineagePerMeasureIsolation() {
        String moduleId = ensureModule("mod-measure-iso");
        String entityId = saveEntity(moduleId, "MeasureIsoEnt", "A", "B");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_ISO", entityId);
        saveMeasure(tableId, "M_OK", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);
        // measure2 expression 含 DROP（关键字黑名单），validator 会抛 unsafe
        saveMeasure(tableId, "M_BAD", "DROP", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertFalse(resp.hasError(), "per-measure failure must not break the whole action: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=2"),
                "M_OK produces 2 edges (A->M_OK, B->M_OK), M_BAD produces none: " + data);
        assertTrue(data.contains("M_BAD"),
                "errors must contain M_BAD measureName (per-measure isolation label): " + data);

        // M_OK 的边已落盘（per-measure 隔离不中断整批）
        assertEquals(2L, countMeasureParseEdges(tableId),
                "M_OK edges persisted (per-measure isolation): 2 edges");
        assertNotNull(findColumnEdge(tableId, tableId, "A", "M_OK"),
                "A->M_OK edge must persist despite M_BAD failure");
    }

    /**
     * 端到端测试 4（BFS 非污染，D1 语义隔离）：基于测试 1 数据，调 getDownstream(T) →
     * 断言返回列表**不含 T 自身**（自环边 BFS 不可达）；调 getImpactAnalysis(T) → 断言不含 T。
     *
     * <p>Anti-Hollow：经既有 BFS 实现验证 self-loop 边在 BFS 不可达（visited.add(T) 恒 false）。
     */
    @Test
    public void testExtractMeasureLineageBfsNotPolluted() {
        String moduleId = ensureModule("mod-measure-bfs");
        String entityId = saveEntity(moduleId, "MeasureBfsEnt", "A", "B");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_BFS", entityId);
        saveMeasure(tableId, "M_BFS", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");

        // 自环边已落盘
        assertEquals(2L, countMeasureParseEdges(tableId), "self-loop edges persisted");

        // BFS 不可达：getDownstream(T) 不含 T 自身
        List<String> downstream = lineageBiz.getDownstream(tableId, null);
        assertFalse(downstream.contains(tableId),
                "BFS semantic isolation: self-loop edge not reachable in getDownstream: " + downstream);

        // getImpactAnalysis(T) 不含 T
        List<String> impact = lineageBiz.getImpactAnalysis(tableId, null, null);
        assertFalse(impact.contains(tableId),
                "BFS semantic isolation: self-loop edge not reachable in getImpactAnalysis: " + impact);
        // 按列过滤也不可达
        List<String> impactByCol = lineageBiz.getImpactAnalysis(tableId, "A", null);
        assertFalse(impactByCol.contains(tableId),
                "BFS semantic isolation by column: self-loop edge not reachable: " + impactByCol);
    }

    /**
     * 端到端测试 5（dict 值生效，D4）：断言落盘边的 lineageSource == measure_parse
     * （经 `_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE` 常量比对），
     * 验证 Phase 1 常量生成正确 + action 正确使用。
     */
    @Test
    public void testExtractMeasureLineageDictValueEffective() {
        String moduleId = ensureModule("mod-measure-dict");
        String entityId = saveEntity(moduleId, "MeasureDictEnt", "X", "Y");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_DICT", entityId);
        saveMeasure(tableId, "M_DICT", "X + Y", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");

        NopMetaLineageEdge e = findColumnEdge(tableId, tableId, "X", "M_DICT");
        assertNotNull(e, "X->M_DICT edge must exist");
        // 经生成的 _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE 常量比对
        assertEquals(_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE, e.getLineageSource(),
                "lineageSource == measure_parse (Phase 1 常量生成 + action 正确使用)");
        assertEquals("measure_parse", e.getLineageSource(),
                "lineageSource string value must be 'measure_parse'");
    }

    /**
     * 端到端测试 6（重抽 replace 幂等，D6）：基于测试 1 数据，修改某 measure 的 expression 不再引用列 A
     * → 再调 action → 断言 A→该 measure 的旧边已删（不存在）+ 新引用列的边已插
     * + **总边数 == 重抽后所有 expression measure 的列依赖数总和**（含未修改 measure 的边被 replace 重插）+ 无重复。
     */
    @Test
    public void testExtractMeasureLineageReplaceIdempotent() {
        String moduleId = ensureModule("mod-measure-replace");
        String entityId = saveEntity(moduleId, "MeasureReplaceEnt", "A", "B", "C", "D");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_REP", entityId);
        // M_REP1 原 expression 引用 A,B；M_REP2 引用 C,D —— 原总边数 4
        saveMeasure(tableId, "M_REP1", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);
        saveMeasure(tableId, "M_REP2", "C + D", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertEquals(4L, countMeasureParseEdges(tableId), "initial total: 4 edges");
        assertNotNull(findColumnEdge(tableId, tableId, "A", "M_REP1"),
                "A->M_REP1 edge exists initially");

        // 修改 M_REP1 的 expression 不再引用 A，改为引用 B,C
        updateMeasureExpression(tableId, "M_REP1", "B + C");

        // 重抽
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertFalse(resp.hasError(), "re-extract should not error: " + resp);

        // A->M_REP1 旧边已删（不存在）—— replace 语义生效
        assertNull(findColumnEdge(tableId, tableId, "A", "M_REP1"),
                "D6 replace: stale A->M_REP1 edge must be deleted");

        // 新引用列的边已插
        assertNotNull(findColumnEdge(tableId, tableId, "B", "M_REP1"),
                "new B->M_REP1 edge inserted after replace");
        assertNotNull(findColumnEdge(tableId, tableId, "C", "M_REP1"),
                "new C->M_REP1 edge inserted after replace");

        // 总边数 == 重抽后所有 expression measure 的列依赖数总和
        // M_REP1: B,C → 2；M_REP2: C,D → 2 → 总 4
        assertEquals(4L, countMeasureParseEdges(tableId),
                "D6 replace: total edges == sum of all measure deps after re-extract: 4");

        // 无重复（同样调用再抽一次，边数仍为 4）
        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertEquals(4L, countMeasureParseEdges(tableId),
                "D6 replace idempotent: second re-extract keeps total at 4, no duplicates");
    }

    /**
     * 端到端测试 7（边界：aggFunc null → derived，D4 边界裁定）：建表 + expression measure 且 aggFunc=null
     * → 调 action → 断言产出边 transformType == derived（无聚合包裹）。
     */
    @Test
    public void testExtractMeasureLineageAggFuncNullDerived() {
        String moduleId = ensureModule("mod-measure-derived");
        String entityId = saveEntity(moduleId, "MeasureDerivedEnt", "P", "Q");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_DERIVED", entityId);
        // aggFunc=null 的 expression measure（极端 case）
        saveMeasure(tableId, "M_DERIVED", "P + Q", null);

        execute("mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");

        NopMetaLineageEdge e = findColumnEdge(tableId, tableId, "P", "M_DERIVED");
        assertNotNull(e, "P->M_DERIVED edge must exist");
        assertEquals(_NopMetadataCoreConstants.LINEAGE_TRANSFORM_DERIVED, e.getTransformType(),
                "D4 boundary: aggFunc null -> transformType=derived");
    }

    /**
     * 端到端测试 8（resolver 表级前置失败，D5 (a)）：建一张 entity 表但 baseEntityId=null → 调 action
     * → 断言**表级前置失败**显式抛 `ERR_FIELD_RESOLVE_BASE_ENTITY_NULL`（resolver per-table 失败，
     * 不进 per-measure 隔离、不静默空集）。
     */
    @Test
    public void testExtractMeasureLineageResolverTableLevelFailure() {
        String moduleId = ensureModule("mod-measure-resolver-fail");
        // entity 表但 baseEntityId=null（区别于既有 saveSqlTable：不设 sourceSql/buildSql）
        String tableId = saveEntityTable(moduleId, "T_MEASURE_NO_ENTITY", null);
        saveMeasure(tableId, "M_RESOLVER_FAIL", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractMeasureLineage(metaTableId: \"" + tableId + "\") { edgeCount errors } }");
        assertTrue(resp.hasError(),
                "table-level pre-condition failure (baseEntityId null) must fast-fail "
                        + "(not silent empty, not per-measure isolation): " + resp);
        // ErrorCode 标识检查（baseEntityId null 抛 ERR_FIELD_RESOLVE_BASE_ENTITY_NULL）
        String errorCode = resp.getErrorCode();
        assertNotNull(errorCode, "GraphQL response must carry errorCode extension: " + resp);
        assertTrue(errorCode.contains("field-resolve-base-entity-null"),
                "error must be ERR_FIELD_RESOLVE_BASE_ENTITY_NULL (table-level pre-condition failure), "
                        + "got: " + errorCode);
        // 不产任何边
        assertEquals(0L, countMeasureParseEdges(tableId),
                "table-level pre-condition failure must not produce any edges");
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

    /**
     * 保存一张 tableType=entity 的逻辑表，可指定 baseEntityId（区别于既有 {@link #saveTable}）。
     * 用于 expression measure 列级血缘测试：baseEntityId 非 null 时 resolver 成功解析字段集合；
     * baseEntityId=null 时 resolver 抛 ERR_FIELD_RESOLVE_BASE_ENTITY_NULL（表级前置失败）。
     */
    private String saveEntityTable(String moduleId, String tableName, String baseEntityId) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_ENTITY);
        if (baseEntityId != null) {
            t.setBaseEntityId(baseEntityId);
        }
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    /**
     * 建一个 NopMetaOrmModel + NopMetaEntity + 若干 NopMetaEntityField，返回 entityId。
     * OrmModel 是 NopMetaEntity.ormModelId 的 mandatory 引用，必须先建。
     */
    private String saveEntity(String moduleId, String entityName, String... fieldNames) {
        IEntityDao<NopMetaOrmModel> ormDao = daoProvider.daoFor(NopMetaOrmModel.class);
        NopMetaOrmModel ormModel = ormDao.newEntity();
        ormModel.setMetaModuleId(moduleId);
        ormModel.setModelName(entityName + "_model");
        ormModel.setIsDelta((byte) 0);
        ormDao.saveEntity(ormModel);
        String ormModelId = ormModel.getOrmModelId();

        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity entity = dao.newEntity();
        entity.setOrmModelId(ormModelId);
        entity.setEntityName(entityName);
        entity.setTableName("tbl_" + entityName);
        entity.setDisplayName(entityName);
        entity.setClassName("io.test." + entityName);
        dao.saveEntity(entity);
        String entityId = entity.getMetaEntityId();

        IEntityDao<NopMetaEntityField> fdao = daoProvider.daoFor(NopMetaEntityField.class);
        int propId = 1;
        for (String fn : fieldNames) {
            NopMetaEntityField f = fdao.newEntity();
            f.setMetaEntityId(entityId);
            f.setFieldName(fn);
            f.setColumnCode(fn.toUpperCase());
            f.setPropId(propId++);
            fdao.saveEntity(f);
        }
        dao.flushSession();
        return entityId;
    }

    /** 保存一条 expression 型 NopMetaTableMeasure（aggFunc 可为 null 测试 D4 边界）。 */
    @SuppressWarnings("UnusedReturnValue")
    private String saveMeasure(String tableId, String measureName, String expression, String aggFunc) {
        IEntityDao<NopMetaTableMeasure> dao = daoProvider.daoFor(NopMetaTableMeasure.class);
        NopMetaTableMeasure m = dao.newEntity();
        m.setMetaTableId(tableId);
        m.setMeasureName(measureName);
        m.setExpression(expression);
        if (aggFunc != null) {
            m.setAggFunc(aggFunc);
        }
        dao.saveEntity(m);
        dao.flushSession();
        return m.getMeasureId();
    }

    /** 更新某 measure 的 expression（用于重抽 replace 测试）。经 raw SQL update + evict 避免会话挂载问题。 */
    @SuppressWarnings("unchecked")
    private void updateMeasureExpression(String tableId, String measureName, String newExpression) {
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("update NOP_META_TABLE_MEASURE set EXPRESSION=? where META_TABLE_ID=? and MEASURE_NAME=?",
                        newExpression, tableId, measureName)
                .end();
        ormTemplate.executeUpdate(upd);
        ormTemplate.evictAll(NopMetaTableMeasure.class.getName());
    }

    /** 计数某表的 measure_parse 自环边（sourceTableId==targetTableId==tableId）。 */
    private long countMeasureParseEdges(String tableId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        return dao.countByQuery(q);
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
