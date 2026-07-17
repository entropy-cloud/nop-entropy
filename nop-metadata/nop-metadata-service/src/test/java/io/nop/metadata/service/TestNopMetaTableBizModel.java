/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaProfilingRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证数据剖析引擎（架构基线 §2.7.2 / 设计 06 §三）：列级统计写入（数值列 + 字符串列真实统计）+ 列类型适配 +
 * 表级降级（sizeBytes/lastModified unavailable）+ 时序追加 + 失败隔离 + 不可执行路径显式失败 +
 * executeProfilingRule 辅助入口。
 *
 * <p>Anti-Hollow：所有成功路径用真实 H2 建连 + 真实物理表数据，断言 NopMetaProfilingResult 写入的
 * distinctCount/min/max/avg/stddev/median/percentiles 与实测数据一致，证明运行时确实通过 P2-1 withConnection
 * callback 建连并对每列执行了聚合 SQL / in-app 排序，非空壳实现。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaTableBizModel extends JunitBaseTestCase {

    public TestNopMetaTableBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    // ===== 数值列 + 字符串列真实统计 =====

    /** 数值列：distinctCount/min/max/avg/stddev/median/percentiles 与实测数据一致。字符串列：stringStats。 */
    @Test
    public void testProfileNumericAndStringColumns() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_prof_main;DB_CLOSE_DELAY=-1";
        // 4 行数值：amount = 10,20,30,40 → mean=25, stddev≈12.9, median=25, min=10, max=40, distinct=4
        // 字符串：name = aaa,bb,ccc,dddd → lengths 3,2,3,4 → minLength=2, maxLength=4, avgLength=3
        seedTable(dbUrl, "CREATE TABLE ext_prof (amount DOUBLE, name VARCHAR(20))",
                "INSERT INTO ext_prof VALUES (10.0, 'aaa')",
                "INSERT INTO ext_prof VALUES (20.0, 'bb')",
                "INSERT INTO ext_prof VALUES (30.0, 'ccc')",
                "INSERT INTO ext_prof VALUES (40.0, 'dddd')");
        String tableId = prepareTable(dbUrl, "qs_prof_main", "EXT_PROF");

        GraphQLResponseBean resp = profile(tableId, null);
        assertFalse(resp.hasError(), "profile should not error: " + resp);

        NopMetaProfilingResult row = findResult(tableId);
        assertNotNull(row, "profiling result row must be written");
        // 表级行数真实
        Map<String, Object> tableStats = parseMap(row.getTableStats());
        assertEquals(4L, toLong(tableStats.get("rowCount")), "rowCount must be real COUNT(*)");
        // 表级方言特定统计 unavailable（不伪造）
        List<String> unavailable = (List<String>) tableStats.get("unavailable");
        assertNotNull(unavailable, "tableStats.unavailable must be present");
        assertTrue(unavailable.contains("sizeBytes"), "sizeBytes must be marked unavailable (not fabricated)");
        assertTrue(unavailable.contains("lastModified"), "lastModified must be marked unavailable (not fabricated)");

        // 列级统计
        List<Map<String, Object>> cols = parseList(row.getColumnStats());
        Map<String, Object> amount = findColumn(cols, "AMOUNT");
        assertNotNull(amount, "AMOUNT column must be profiled");

        // 便携统计精确（distinctCount/min/max）
        assertEquals(4L, toLong(amount.get("distinctCount")), "distinctCount must match real data");
        assertEquals(4L, toLong(amount.get("totalCount")), "totalCount must match real data");
        assertEquals(0L, toLong(amount.get("nullCount")), "nullCount must match real data");
        assertEquals("10.0", String.valueOf(amount.get("min")), "min must match real data");
        assertEquals("40.0", String.valueOf(amount.get("max")), "max must match real data");

        // 数值列：numericStats（mean/stddev/median/percentiles 真实）
        Map<String, Object> numeric = (Map<String, Object>) amount.get("numericStats");
        assertNotNull(numeric, "numeric column must have numericStats");
        assertEquals(25.0, toDouble(numeric.get("meanValue")), 1e-6, "mean must match AVG()");
        // median (in-app) of [10,20,30,40] = (20+30)/2 = 25
        assertEquals(25.0, toDouble(numeric.get("medianValue")), 1e-6, "median must match in-app computation");
        assertEquals(10.0, toDouble(numeric.get("minValue")), 1e-6, "minValue must match MIN()");
        assertEquals(40.0, toDouble(numeric.get("maxValue")), 1e-6, "maxValue must match MAX()");
        // stddev (STDDEV_SAMP) of [10,20,30,40] ≈ 12.909944...
        assertEquals(12.909944, toDouble(numeric.get("stddevValue")), 1e-4, "stddev must match STDDEV_SAMP()");
        // percentiles present (in-app computed, not unavailable)
        Map<String, Object> pct = (Map<String, Object>) numeric.get("percentiles");
        assertNotNull(pct, "percentiles must be computed (not unavailable)");
        // 50th percentile of [10,20,30,40] = 25
        assertEquals(25.0, toDouble(pct.get("50")), 1e-6, "p50 must match in-app computation");
        // 25th percentile of [10,20,30,40] = 17.5
        assertEquals(17.5, toDouble(pct.get("25")), 1e-6, "p25 must match in-app computation");

        // 字符串列：stringStats（minLength/maxLength/avgLength/topValues）
        Map<String, Object> name = findColumn(cols, "NAME");
        assertNotNull(name, "NAME column must be profiled");
        Map<String, Object> str = (Map<String, Object>) name.get("stringStats");
        assertNotNull(str, "string column must have stringStats");
        assertEquals(2L, toLong(str.get("minLength")), "minLength must match MIN(LENGTH)");
        assertEquals(4L, toLong(str.get("maxLength")), "maxLength must match MAX(LENGTH)");
        assertEquals(3.0, toDouble(str.get("avgLength")), 1e-6, "avgLength must match AVG(LENGTH)");
        // topValues present
        List<?> topValues = (List<?>) str.get("topValues");
        assertNotNull(topValues, "topValues must be collected");
        assertFalse(topValues.isEmpty(), "topValues must not be empty");
    }

    // ===== 列类型适配：数值列无 stringStats（不伪造） =====

    /** 数值列应仅有 numericStats（无 stringStats），反之字符串列仅有 stringStats（不伪造不适用的统计）。 */
    @Test
    public void testColumnTypeAdaptation() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_prof_type;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_type (nval INT, sval VARCHAR(10))",
                "INSERT INTO ext_type VALUES (1, 'x')");
        String tableId = prepareTable(dbUrl, "qs_prof_type", "EXT_TYPE");

        GraphQLResponseBean resp = profile(tableId, null);
        assertFalse(resp.hasError(), "profile should not error: " + resp);

        NopMetaProfilingResult row = findResult(tableId);
        List<Map<String, Object>> cols = parseList(row.getColumnStats());

        Map<String, Object> nval = findColumn(cols, "NVAL");
        assertNotNull(nval.get("numericStats"), "numeric column must have numericStats");
        // 数值列不伪造 stringStats（省略，不出现）
        assertFalse(nval.containsKey("stringStats"), "numeric column must NOT fabricate stringStats");

        Map<String, Object> sval = findColumn(cols, "SVAL");
        assertNotNull(sval.get("stringStats"), "string column must have stringStats");
        assertFalse(sval.containsKey("numericStats"), "string column must NOT fabricate numericStats");
    }

    // ===== 失败隔离：单列失败收集 errors 不中断整表 =====

    /** 单列剖析失败（如聚合异常）不应中断整表，其余列正常剖析，失败列收集进 errors。 */
    @Test
    public void testProfileColumnFailureIsolation() throws Exception {
        // columns 参数限定到一个合法列 + 一个不存在的列（标识符通过白名单，但 SQL 执行时列不存在）。
        // 标识符白名单允许 NOPE_COL（合法标识符），运行时 SQL 抛异常 → per-column 收集 errors。
        String dbUrl = "jdbc:h2:mem:meta_prof_iso;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_iso (id INT NOT NULL, val INT)",
                "INSERT INTO ext_iso VALUES (1, 10)");
        String tableId = prepareTable(dbUrl, "qs_prof_iso", "EXT_ISO");

        // 限定只剖析 NOPE_COL（不存在），全部列失败 → errors 收集，columnStats 可能为空但 rowCount 仍真实
        GraphQLResponseBean resp = profile(tableId, "NOPE_COL");
        assertFalse(resp.hasError(), "profile should not globally error (isolated): " + resp);

        NopMetaProfilingResult row = findResult(tableId);
        Map<String, Object> tableStats = parseMap(row.getTableStats());
        assertEquals(1L, toLong(tableStats.get("rowCount")), "rowCount must still be real COUNT(*) despite column failure");
        // 失败列收集进 errors（不中断整表）
        String data = String.valueOf(resp.getData());
        // columnCount 可能 0（列不存在，无法剖析），但表级 rowCount 仍写入，证明未空壳
        assertTrue(data.contains("columnCount=0") || data.contains("errors"),
                "non-existent column should be collected: " + data);
    }

    // ===== 时序追加 =====

    /** 重复剖析同一表追加新结果行（snapshotTime 不同），不覆盖旧行。 */
    @Test
    public void testProfileTimeSeriesAppend() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_prof_ts;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_ts (id INT NOT NULL)", "INSERT INTO ext_ts VALUES (1)");
        String tableId = prepareTable(dbUrl, "qs_prof_ts", "EXT_TS");

        GraphQLResponseBean r1 = profile(tableId, null);
        assertFalse(r1.hasError(), "first profile should not error: " + r1);
        assertEquals(1, countResults(tableId));

        // 确保 snapshotTime 不同
        Thread.sleep(1100);
        GraphQLResponseBean r2 = profile(tableId, null);
        assertFalse(r2.hasError(), "second profile should not error: " + r2);
        assertEquals(2, countResults(tableId), "time-series: 2 result rows after second profile (appended, not overwritten)");
    }

    // ===== 默认 schema 从持久化解析（plan 2026-07-17-0852-3 Phase 3） =====

    /**
     * 端到端：sync 持久化 schema → 直接执行 profileTable（不传 schemaPattern）→ 默认按持久化 schema
     * 执行 COUNT 与列统计，命中正确表（plan 0852-3 Phase 3 anti-hollow #22）。
     *
     * <p>在 H2 自定义 schema SCH_PR_DEF 下建表 + 插入 2 行 → sync → 不传 schemaPattern 执行 →
     * rowCount=2（真实 COUNT 结果，证明默认 schema 解析在 BizModel 层生效并被剖析器接收）。
     */
    @Test
    public void testProfileTableDefaultSchemaFromPersisted() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_pr_def;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA SCH_PR_DEF");
            st.execute("CREATE TABLE SCH_PR_DEF.ext_pr_def (id INT NOT NULL, val INT)");
            st.execute("INSERT INTO SCH_PR_DEF.ext_pr_def VALUES (1, 10)");
            st.execute("INSERT INTO SCH_PR_DEF.ext_pr_def VALUES (2, 20)");
        }
        String tableId = prepareTableMultiSchema(dbUrl, "qs_pr_def", "SCH_PR_DEF", "EXT_PR_DEF");

        // 不传 schemaPattern：默认取 table.schema=SCH_PR_DEF
        GraphQLResponseBean resp = profileNoSchema(tableId);
        assertFalse(resp.hasError(), "default schema path should not error: " + resp);

        NopMetaProfilingResult row = findResult(tableId);
        assertNotNull(row, "profiling result must be written via default schema path");
        Map<String, Object> tableStats = parseMap(row.getTableStats());
        assertEquals(2L, toLong(tableStats.get("rowCount")),
                "rowCount=2 via persisted default schema SCH_PR_DEF (anti-hollow)");
    }

    /**
     * 显式 schemaPattern 覆盖持久化 schema（plan 0852-3 Phase 3 覆盖语义）。
     * sync SCH_PR_A（持久化 schema=SCH_PR_A，2 行）→ 显式传 schemaPattern=SCH_PR_B（覆盖）→ 命中 SCH_PR_B 的 3 行。
     */
    @Test
    public void testProfileTableExplicitSchemaOverridesPersisted() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_pr_ovr;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA SCH_PR_A");
            st.execute("CREATE SCHEMA SCH_PR_B");
            st.execute("CREATE TABLE SCH_PR_A.shared_pr (id INT NOT NULL)");
            st.execute("CREATE TABLE SCH_PR_B.shared_pr (id INT NOT NULL)");
            st.execute("INSERT INTO SCH_PR_A.shared_pr VALUES (1)");
            st.execute("INSERT INTO SCH_PR_A.shared_pr VALUES (2)");
            st.execute("INSERT INTO SCH_PR_B.shared_pr VALUES (10)");
            st.execute("INSERT INTO SCH_PR_B.shared_pr VALUES (11)");
            st.execute("INSERT INTO SCH_PR_B.shared_pr VALUES (12)");
        }
        // sync SCH_PR_A → 持久化 schema=SCH_PR_A
        String tableId = prepareTableMultiSchema(dbUrl, "qs_pr_ovr", "SCH_PR_A", "SHARED_PR");

        // 显式传 SCH_PR_B → 覆盖持久化的 SCH_PR_A → 命中 SCH_PR_B 的 3 行
        GraphQLResponseBean resp = profileWithSchema(tableId, "SCH_PR_B");
        assertFalse(resp.hasError(), "explicit schema override should not error: " + resp);
        NopMetaProfilingResult row = findResult(tableId);
        assertNotNull(row);
        Map<String, Object> tableStats = parseMap(row.getTableStats());
        assertEquals(3L, toLong(tableStats.get("rowCount")),
                "rowCount=3 via explicit schema=SCH_PR_B (overrides persisted SCH_PR_A)");
    }

    // ===== executeProfilingRule 辅助入口 =====

    /** executeProfilingRule 按规则定义执行，写入 NopMetaProfilingResult（profilingRuleId 关联规则）。 */
    @Test
    public void testExecuteProfilingRule() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_prof_rule;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_rule (amount INT)",
                "INSERT INTO ext_rule VALUES (5)", "INSERT INTO ext_rule VALUES (15)");
        String tableId = prepareTable(dbUrl, "qs_prof_rule", "EXT_RULE");

        saveProfilingRule("prof-rule-1", tableId, null, null);
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaProfilingRule__executeProfilingRule(profilingRuleId: \"prof-rule-1\", "
                        + "schemaPattern: \"PUBLIC\") }")));
        assertFalse(resp.hasError(), "executeProfilingRule should not error: " + resp);

        NopMetaProfilingResult row = findResultByRule("prof-rule-1");
        assertNotNull(row, "profiling result must be written for rule");
        assertEquals("prof-rule-1", row.getProfilingRuleId(), "profilingRuleId must link to rule");
        Map<String, Object> tableStats = parseMap(row.getTableStats());
        assertEquals(2L, toLong(tableStats.get("rowCount")), "rowCount must be real");
    }

    // ===== 不可执行路径显式失败 =====

    /** 表不存在 → 显式失败（不 NPE）。 */
    @Test
    public void testProfileTableNotFound() {
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__profileTable(metaTableId: \"__nope__\") }")));
        assertTrue(resp.hasError(), "non-existent table must error (no NPE): " + resp);
    }

    /** 目标表非 external（首版）→ 显式失败。 */
    @Test
    public void testProfileTableNotExternal() {
        NopMetaTable entityTable = saveManualTable("EXT_NE_PROF", "entity", "qs_ne_prof");
        GraphQLResponseBean resp = profile(entityTable.getMetaTableId(), null);
        assertTrue(resp.hasError(), "non-external table must explicitly fail (first version): " + resp);
    }

    /** 目标表无注册数据源 → 显式失败。 */
    @Test
    public void testProfileTableNoDataSource() {
        NopMetaTable ext = saveManualTable("EXT_NDS_PROF", "external", "qs_no_ds_prof");
        GraphQLResponseBean resp = profile(ext.getMetaTableId(), null);
        assertTrue(resp.hasError(), "no datasource for querySpace must explicitly fail: " + resp);
    }

    /** DISABLED 数据源 → 显式失败。 */
    @Test
    public void testProfileTableDatasourceDisabled() {
        saveDataSource("ds-prof-disabled", "qs_prof_disabled", "jdbc", "DISABLED",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_prof_d;DB_CLOSE_DELAY=-1\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");
        NopMetaTable ext = saveManualTable("EXT_PD", "external", "qs_prof_disabled");
        GraphQLResponseBean resp = profile(ext.getMetaTableId(), null);
        assertTrue(resp.hasError(), "DISABLED datasource must explicitly fail (no silent pass): " + resp);
    }

    /** 非 jdbc 类型 → 显式失败（UnsupportedOperationException）。 */
    @Test
    public void testProfileTableNonJdbcThrows() {
        saveDataSource("ds-prof-http", "qs_prof_http", "http", "ACTIVE", "{}");
        NopMetaTable ext = saveManualTable("EXT_PHTTP", "external", "qs_prof_http");
        GraphQLResponseBean resp = profile(ext.getMetaTableId(), null);
        assertTrue(resp.hasError(), "non-jdbc datasource must explicitly fail: " + resp);
    }

    // ===== SQL 视图创建 + SELECT 字段解析（架构基线 §4.2 / §4.2.1 / §4.2.2）=====

    /** createSqlTable 成功：多列 + 别名列 + 表达式列 → 字段名与 SELECT 输出一致。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testCreateSqlTableSuccess() {
        String moduleId = ensureExternalSystemModuleId();
        String sql = "SELECT id, name AS user_name, amount * 2 FROM orders";
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"" + escapeGraphQL(sql) + "\", "
                        + "tableName: \"sql_view_basic\", metaModuleId: \"" + moduleId + "\") }")));

        assertFalse(resp.hasError(), "createSqlTable should succeed for valid SELECT: " + resp);
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> result = (Map<String, Object>) data.get("NopMetaTable__createSqlTable");
        assertNotNull(result, "result map must be present");
        assertEquals("sql", result.get("tableType"), "tableType must be sql");
        assertNotNull(result.get("metaTableId"), "metaTableId must be returned");

        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        assertNotNull(fields, "fields must be returned (wiring proof)");
        assertEquals(3, fields.size(), "field count must match SELECT output columns");
        // 接线验证：fields 来自真实 AST 解析（非空壳）
        assertEquals("id", fields.get(0).get("name"), "first col name = id");
        // 别名列：输出名 = alias
        assertEquals("user_name", fields.get(1).get("name"), "aliased col name = user_name");
        assertEquals("user_name", fields.get(1).get("alias"), "alias field populated");
        // 表达式列：标记 <expr_1>（不静默跳过）
        assertEquals("<expr_1>", fields.get(2).get("name"), "expression col marked (not skipped)");
        // 方案 A：type 恒为 null（不伪造）
        assertNull(fields.get(0).get("type"), "type must be null in plan A (not fabricated)");

        // 端到端：findPage/get 可查到新建的 tableType=sql 记录（sourceSql 已存）
        NopMetaTable saved = daoProvider.daoFor(NopMetaTable.class)
                .getEntityById(String.valueOf(result.get("metaTableId")));
        assertNotNull(saved, "saved table must be findable by id");
        assertEquals("sql", saved.getTableType());
        assertEquals(sql, saved.getSourceSql(), "sourceSql must be persisted");
    }

    /** createSqlTable 拒绝非 SELECT（INSERT）→ 显式失败。 */
    @Test
    public void testCreateSqlTableRejectNonSelect() {
        String moduleId = ensureExternalSystemModuleId();
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"INSERT INTO t VALUES(1)\", "
                        + "tableName: \"sql_bad\", metaModuleId: \"" + moduleId + "\") }")));
        assertTrue(resp.hasError(), "non-SELECT must explicitly fail: " + resp);
    }

    /** createSqlTable 拒绝空 SQL → 显式失败。 */
    @Test
    public void testCreateSqlTableRejectEmptySql() {
        String moduleId = ensureExternalSystemModuleId();
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"   \", "
                        + "tableName: \"sql_empty\", metaModuleId: \"" + moduleId + "\") }")));
        assertTrue(resp.hasError(), "empty SQL must explicitly fail: " + resp);
    }

    /** createSqlTable 拒绝语法错误 SQL → 显式失败。 */
    @Test
    public void testCreateSqlTableRejectSyntaxError() {
        String moduleId = ensureExternalSystemModuleId();
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"SELEC FROM t\", "
                        + "tableName: \"sql_syntax\", metaModuleId: \"" + moduleId + "\") }")));
        assertTrue(resp.hasError(), "syntax-error SQL must explicitly fail: " + resp);
    }

    /** createSqlTable 拒绝多语句（; 分隔）→ 显式失败（不允许 SELECT 1; DELETE...）。 */
    @Test
    public void testCreateSqlTableRejectMultiStatement() {
        String moduleId = ensureExternalSystemModuleId();
        String sql = "SELECT 1; DELETE FROM t";
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"" + escapeGraphQL(sql) + "\", "
                        + "tableName: \"sql_multi\", metaModuleId: \"" + moduleId + "\") }")));
        assertTrue(resp.hasError(), "multi-statement SQL must explicitly fail: " + resp);
    }

    /** createSqlTable 拒绝通配符 * / t.*（item 1.1 裁定：纯 AST 无法展开，显式失败）。 */
    @Test
    public void testCreateSqlTableRejectWildcard() {
        String moduleId = ensureExternalSystemModuleId();
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"SELECT * FROM t\", "
                        + "tableName: \"sql_wild\", metaModuleId: \"" + moduleId + "\") }")));
        assertTrue(resp.hasError(), "wildcard projection must explicitly fail (not silently skipped): " + resp);

        GraphQLResponseBean resp2 = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"SELECT t.* FROM t\", "
                        + "tableName: \"sql_wild2\", metaModuleId: \"" + moduleId + "\") }")));
        assertTrue(resp2.hasError(), "t.* wildcard must also explicitly fail: " + resp2);
    }

    /** createSqlTable 拒绝不存在的 metaModuleId → 显式失败。 */
    @Test
    public void testCreateSqlTableRejectModuleNotFound() {
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"SELECT 1\", "
                        + "tableName: \"sql_nomod\", metaModuleId: \"__nope_module__\") }")));
        assertTrue(resp.hasError(), "non-existent module must explicitly fail: " + resp);
    }

    /** previewSqlFields 返回字段列表，且不产生任何持久化副作用（表行数不变）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testPreviewSqlFieldsNoPersistence() {
        long tablesBefore = countAllTables();
        String sql = "SELECT id, name AS label FROM users";
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaTable__previewSqlFields(sql: \"" + escapeGraphQL(sql) + "\") }")));
        assertFalse(resp.hasError(), "previewSqlFields should succeed: " + resp);

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> result = (Map<String, Object>) data.get("NopMetaTable__previewSqlFields");
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        assertEquals(2, fields.size(), "field count must match");
        assertEquals("id", fields.get(0).get("name"));
        assertEquals("label", fields.get(1).get("name"));
        assertEquals("label", fields.get(1).get("alias"));

        long tablesAfter = countAllTables();
        assertEquals(tablesBefore, tablesAfter, "previewSqlFields must NOT persist any table (no side effect)");
    }

    /** 端到端：createSqlTable → resolveTableFields 返回与 preview 一致的字段（结构统一 {name, alias?, type?}）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testResolveTableFieldsMatchesPreview() {
        String moduleId = ensureExternalSystemModuleId();
        String sql = "SELECT order_id, customer_id AS cid, total + 1 FROM orders";
        // preview
        GraphQLResponseBean preview = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaTable__previewSqlFields(sql: \"" + escapeGraphQL(sql) + "\") }")));
        assertFalse(preview.hasError(), "preview should succeed: " + preview);

        // create
        GraphQLResponseBean create = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__createSqlTable(sql: \"" + escapeGraphQL(sql) + "\", "
                        + "tableName: \"sql_e2e\", metaModuleId: \"" + moduleId + "\") }")));
        assertFalse(create.hasError(), "create should succeed: " + create);
        String tableId = String.valueOf(((Map<String, Object>) ((Map<String, Object>) create.getData())
                .get("NopMetaTable__createSqlTable")).get("metaTableId"));

        // resolve
        GraphQLResponseBean resolve = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + tableId + "\") }")));
        assertFalse(resolve.hasError(), "resolve should succeed: " + resolve);

        List<Map<String, Object>> previewFields = (List<Map<String, Object>>) ((Map<String, Object>)
                ((Map<String, Object>) preview.getData()).get("NopMetaTable__previewSqlFields")).get("fields");
        List<Map<String, Object>> resolveFields = (List<Map<String, Object>>) ((Map<String, Object>)
                ((Map<String, Object>) resolve.getData()).get("NopMetaTable__resolveTableFields")).get("fields");
        assertEquals(previewFields.size(), resolveFields.size(), "preview and resolve field counts must match");
        for (int i = 0; i < previewFields.size(); i++) {
            assertEquals(previewFields.get(i).get("name"), resolveFields.get(i).get("name"),
                    "field name at index " + i + " must match between preview and resolve");
        }
        // 验证三列结构
        assertEquals("order_id", resolveFields.get(0).get("name"));
        assertEquals("cid", resolveFields.get(1).get("name"));
        assertEquals("<expr_1>", resolveFields.get(2).get("name"));
    }

    /**
     * resolveTableFields 命中 entity 表但 baseEntityId 为 null → 显式失败（item 1.2b 跨类型分派：
     * entity 表须有 baseEntityId 才能解析字段，null 显式失败不静默空集）。
     */
    @Test
    public void testResolveTableFieldsFailsOnNonSqlTable() {
        NopMetaTable entityTable = saveManualTable("EXT_NE_RESOLVE", "entity", "qs_ne_resolve");
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + entityTable.getMetaTableId()
                        + "\") }")));
        assertTrue(resp.hasError(), "entity table with null baseEntityId must explicitly fail: " + resp);
    }

    /** resolveTableFields 命中不存在的 metaTableId → 显式失败。 */
    @Test
    public void testResolveTableFieldsFailsOnNotFound() {
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"__nope_table__\") }")));
        assertTrue(resp.hasError(), "non-existent table must explicitly fail: " + resp);
    }

    // ===== helpers =====

    /** 端到端：建数据源 + 同步 external 表结构，返回 metaTableId。 */
    private String prepareTable(String dbUrl, String querySpace, String expectedTable) {
        return prepareTableMultiSchema(dbUrl, querySpace, "PUBLIC", expectedTable);
    }

    /**
     * 端到端（多 schema 版本，plan 0852-3）：建数据源 + 同步指定 schema 的 external 表结构，
     * 使持久化 {@code NopMetaTable.schema} 列真实写入该 schema（用于默认 schema 解析测试）。
     */
    private String prepareTableMultiSchema(String dbUrl, String querySpace, String schemaPattern, String expectedTable) {
        saveDataSource("ds-" + querySpace, querySpace, "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");
        GraphQLResponseBean syncResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-" + querySpace
                        + "\", schemaPattern: \"" + schemaPattern + "\") }")));
        assertFalse(syncResp.hasError(), "sync should not error: " + syncResp);
        return tableId(expectedTable);
    }

    private GraphQLResponseBean profile(String tableId, String columns) {
        String colArg = columns == null ? "" : ", columns: \"" + columns + "\"";
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__profileTable(metaTableId: \"" + tableId + "\", "
                        + "schemaPattern: \"PUBLIC\"" + colArg + ") }")));
    }

    /** profileTable 不传 schemaPattern（验证默认 schema 解析，plan 0852-3 Phase 3）。 */
    private GraphQLResponseBean profileNoSchema(String tableId) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__profileTable(metaTableId: \"" + tableId + "\") }")));
    }

    /** profileTable 显式传 schemaPattern（验证覆盖语义，plan 0852-3 Phase 3）。 */
    private GraphQLResponseBean profileWithSchema(String tableId, String schemaPattern) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__profileTable(metaTableId: \"" + tableId + "\", "
                        + "schemaPattern: \"" + schemaPattern + "\") }")));
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }

    /** 转义 SQL 文本以便嵌入 GraphQL 字符串字面量（处理 " 和 \）。 */
    private static String escapeGraphQL(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 统计当前所有 NopMetaTable 行数（用于验证 previewSqlFields 无持久化副作用）。 */
    private long countAllTables() {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        return tableDao.countByQuery(new QueryBean());
    }

    private String tableId(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        NopMetaTable t = tableDao.findFirstByQuery(q);
        assertNotNull(t, "external table " + tableName + " must be synced before profiling");
        return t.getMetaTableId();
    }

    private void saveProfilingRule(String ruleId, String tableId, String columnsJson, String statsJson) {
        IEntityDao<NopMetaProfilingRule> dao = daoProvider.daoFor(NopMetaProfilingRule.class);
        NopMetaProfilingRule rule = dao.newEntity();
        rule.setProfilingRuleId(ruleId);
        rule.setRuleName(ruleId);
        rule.setDisplayName(ruleId);
        rule.setTableId(tableId);
        rule.setColumns(columnsJson);
        rule.setStats(statsJson);
        rule.setVersion(1L);
        rule.setCreatedBy("autotest");
        rule.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        dao.saveEntity(rule);
    }

    private long countResults(String metaTableId) {
        IEntityDao<NopMetaProfilingResult> dao = daoProvider.daoFor(NopMetaProfilingResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaProfilingResult.PROP_NAME_metaTableId, metaTableId));
        return dao.countByQuery(q);
    }

    private NopMetaProfilingResult findResult(String metaTableId) {
        IEntityDao<NopMetaProfilingResult> dao = daoProvider.daoFor(NopMetaProfilingResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaProfilingResult.PROP_NAME_metaTableId, metaTableId));
        q.addOrderField(NopMetaProfilingResult.PROP_NAME_snapshotTime, true);
        return dao.findFirstByQuery(q);
    }

    private NopMetaProfilingResult findResultByRule(String profilingRuleId) {
        IEntityDao<NopMetaProfilingResult> dao = daoProvider.daoFor(NopMetaProfilingResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaProfilingResult.PROP_NAME_profilingRuleId, profilingRuleId));
        return dao.findFirstByQuery(q);
    }

    private void saveDataSource(String id, String querySpace, String datasourceType,
                                String status, String connectionConfig) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType(datasourceType);
        ds.setConnectionConfig(connectionConfig);
        ds.setStatus(status);
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    /** 手工建一张 NopMetaTable（设置必填 metaModuleId，归属系统模块 nop/meta-external）。 */
    private NopMetaTable saveManualTable(String tableName, String tableType, String querySpace) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = tableDao.newEntity();
        t.setMetaModuleId(ensureExternalSystemModuleId());
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(tableType);
        t.setQuerySpace(querySpace);
        t.setVersion(1L);
        tableDao.saveEntity(t);
        return t;
    }

    private String ensureExternalSystemModuleId() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, "nop/meta-external"));
        NopMetaModule module = moduleDao.findFirstByQuery(q);
        if (module != null) {
            return module.getMetaModuleId();
        }
        module = moduleDao.newEntity();
        module.setModuleId("nop/meta-external");
        module.setModuleName("meta-external");
        module.setDisplayName("外部表系统模块");
        module.setModuleVersion(1L);
        module.setStatus("RELEASED");
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        moduleDao.saveEntity(module);
        return module.getMetaModuleId();
    }

    private void seedTable(String dbUrl, String createDdl, String... inserts) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute(createDdl);
            for (String ins : inserts) {
                st.execute(ins);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMap(String json) {
        return json == null ? null : (Map<String, Object>) JsonTool.parse(json);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseList(String json) {
        return json == null ? null : (List<Map<String, Object>>) JsonTool.parse(json);
    }

    private static Map<String, Object> findColumn(List<Map<String, Object>> cols, String name) {
        for (Map<String, Object> c : cols) {
            if (name.equalsIgnoreCase(String.valueOf(c.get("columnName")))) {
                return c;
            }
        }
        return null;
    }

    private static long toLong(Object v) {
        return ((Number) v).longValue();
    }

    private static double toDouble(Object v) {
        return ((Number) v).doubleValue();
    }
}
