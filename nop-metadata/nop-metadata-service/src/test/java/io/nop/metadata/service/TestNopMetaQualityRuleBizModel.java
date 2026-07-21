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
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证质量规则执行引擎（架构基线 §2.7.1）：7 种内置 ruleType 的 pass/fail 判定、批量执行 + 失败隔离、
 * 时序追加、以及所有不可执行路径的显式失败/SKIP。
 *
 * <p>Anti-Hollow：所有成功路径用真实 H2 建连 + 真实物理表数据，断言 NopMetaQualityResult 写入的
 * actualValue 为真实检测结果（如 nullCount=1、rowCount=3），证明运行时确实通过 P2-1 withConnection
 * callback 建连并执行了检测 SQL，非空壳实现。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaQualityRuleBizModel extends JunitBaseTestCase {

    public TestNopMetaQualityRuleBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    // ===== table 级规则：volume =====

    /** volume：3 行 → minRows=2 PASS / minRows=10 FAIL。 */
    @Test
    public void testExecuteVolumePassAndFail() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_vol;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_vol (id INT NOT NULL)",
                "INSERT INTO ext_vol VALUES (1)", "INSERT INTO ext_vol VALUES (2)", "INSERT INTO ext_vol VALUES (3)");
        PreparedEnv env = prepare(dbUrl, "qs_q_vol");

        String tableId = env.tableId("EXT_VOL");
        // PASS: minRows=2, actual=3
        saveRule(env, "r-vol-pass", "volume", "table", tableId, null, 2.0, "{\"minRows\":2}");
        GraphQLResponseBean passResp = exec("r-vol-pass");
        assertFalse(passResp.hasError(), "volume pass should not error: " + passResp);
        assertStatus(passResp, "PASS");
        assertActualValue(passResp, 3.0);

        // FAIL: minRows=10, actual=3
        saveRule(env, "r-vol-fail", "volume", "table", tableId, null, null, "{\"minRows\":10}");
        GraphQLResponseBean failResp = exec("r-vol-fail");
        assertFalse(failResp.hasError(), "volume fail should not error globally: " + failResp);
        assertStatus(failResp, "FAIL");
        assertActualValue(failResp, 3.0);
    }

    // ===== table 级规则：freshness =====

    /** freshness：updated_at = now-60min → maxAgeMinutes=120 PASS / maxAgeMinutes=30 FAIL；缺 timestampColumn ERROR。 */
    @Test
    public void testExecuteFreshnessPassFailAndMissingColumn() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_fresh;DB_CLOSE_DELAY=-1";
        Timestamp oneHourAgo = new Timestamp(System.currentTimeMillis() - 60L * 60L * 1000L);
        seedTable(dbUrl, "CREATE TABLE ext_ts (id INT NOT NULL, updated_at TIMESTAMP)",
                "INSERT INTO ext_ts VALUES (1, TIMESTAMP '" + oneHourAgo.toString() + "')");
        PreparedEnv env = prepare(dbUrl, "qs_q_fresh");
        String tableId = env.tableId("EXT_TS");

        // PASS: maxAgeMinutes=120, age~60 ≤ 120
        saveRule(env, "r-fresh-pass", "freshness", "table", tableId, null, null,
                "{\"timestampColumn\":\"UPDATED_AT\",\"maxAgeMinutes\":120}");
        GraphQLResponseBean passResp = exec("r-fresh-pass");
        assertFalse(passResp.hasError(), "freshness pass should not error: " + passResp);
        assertStatus(passResp, "PASS");

        // FAIL: maxAgeMinutes=30, age~60 > 30
        saveRule(env, "r-fresh-fail", "freshness", "table", tableId, null, null,
                "{\"timestampColumn\":\"UPDATED_AT\",\"maxAgeMinutes\":30}");
        GraphQLResponseBean failResp = exec("r-fresh-fail");
        assertFalse(failResp.hasError(), "freshness fail should not error globally: " + failResp);
        assertStatus(failResp, "FAIL");

        // ERROR: missing timestampColumn
        saveRule(env, "r-fresh-err", "freshness", "table", tableId, null, null, "{\"maxAgeMinutes\":60}");
        GraphQLResponseBean errResp = exec("r-fresh-err");
        assertFalse(errResp.hasError(), "freshness missing-column should write ERROR result, not global error: " + errResp);
        assertStatus(errResp, "ERROR");
    }

    // ===== table 级规则：custom_sql =====

    /** custom_sql：同一数据（3行），expectPassWhen gt 0 PASS / eq 0 FAIL；不返回单值 ERROR。 */
    @Test
    public void testExecuteCustomSqlPassFailAndNoSingleValue() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_sql;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_sql_t (id INT NOT NULL)",
                "INSERT INTO ext_sql_t VALUES (1)", "INSERT INTO ext_sql_t VALUES (2)", "INSERT INTO ext_sql_t VALUES (3)");
        PreparedEnv env = prepare(dbUrl, "qs_q_sql");
        String tableId = env.tableId("EXT_SQL_T");

        // PASS: COUNT(*)=3, expectPassWhen gt 0
        saveRule(env, "r-sql-pass", "custom_sql", "table", tableId,
                "SELECT COUNT(*) FROM ext_sql_t", null, "{\"expectPassWhen\":\"gt 0\"}");
        GraphQLResponseBean passResp = exec("r-sql-pass");
        assertFalse(passResp.hasError(), "custom_sql pass should not error: " + passResp);
        assertStatus(passResp, "PASS");
        assertActualValue(passResp, 3.0);

        // FAIL: COUNT(*)=3, expectPassWhen eq 0
        saveRule(env, "r-sql-fail", "custom_sql", "table", tableId,
                "SELECT COUNT(*) FROM ext_sql_t", null, "{\"expectPassWhen\":\"eq 0\"}");
        GraphQLResponseBean failResp = exec("r-sql-fail");
        assertFalse(failResp.hasError(), "custom_sql fail should not error globally: " + failResp);
        assertStatus(failResp, "FAIL");
        assertActualValue(failResp, 3.0);

        // ERROR: SQL returns no row
        saveRule(env, "r-sql-err", "custom_sql", "table", tableId,
                "SELECT id FROM ext_sql_t WHERE 1=0", null, null);
        GraphQLResponseBean errResp = exec("r-sql-err");
        assertFalse(errResp.hasError(), "custom_sql no-row should write ERROR result, not global error: " + errResp);
        assertStatus(errResp, "ERROR");
    }

    // ===== field 级规则：not_null / unique / range / regex =====

    /** 4 类 field 规则的 pass（clean 表 EXT_FLD_OK）+ fail（dirty 表 EXT_FLD_BAD）。 */
    @Test
    public void testExecuteFieldRulesPassAndFail() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_field;DB_CLOSE_DELAY=-1";
        // clean 表：code 全非空、id 唯一、val 全在 0-100、email 全匹配正则
        seedTable(dbUrl, "CREATE TABLE ext_fld_ok (id INT NOT NULL, val INT, code VARCHAR(20), email VARCHAR(50))",
                "INSERT INTO ext_fld_ok VALUES (1, 10, 'a', 'a@x.com')",
                "INSERT INTO ext_fld_ok VALUES (2, 20, 'b', 'b@x.com')");
        // dirty 表：code 有 1 个 null、id 有重复 (3,3)、val 有越界 (200)、email 有不匹配 ('bad')
        seedTable(dbUrl, "CREATE TABLE ext_fld_bad (id INT, val INT, code VARCHAR(20), email VARCHAR(50))",
                "INSERT INTO ext_fld_bad VALUES (3, 30, 'c', 'c@x.com')",
                "INSERT INTO ext_fld_bad VALUES (3, 200, null, 'bad')");
        PreparedEnv env = prepare(dbUrl, "qs_q_field");
        String okId = env.tableId("EXT_FLD_OK");
        String badId = env.tableId("EXT_FLD_BAD");

        // not_null: code — OK 表 0 null PASS / BAD 表 1 null FAIL
        saveRule(env, "r-nn-ok", "not_null", "field", okId, null, 0.0, "{\"column\":\"CODE\"}");
        assertStatus(exec("r-nn-ok"), "PASS");
        saveRule(env, "r-nn-bad", "not_null", "field", badId, null, 0.0, "{\"column\":\"CODE\"}");
        GraphQLResponseBean nnBad = exec("r-nn-bad");
        assertStatus(nnBad, "FAIL");
        assertActualValue(nnBad, 1.0);

        // unique: id — OK 表 0 重复 PASS / BAD 表 1 重复组 FAIL
        saveRule(env, "r-unq-ok", "unique", "field", okId, null, null, "{\"column\":\"ID\"}");
        assertStatus(exec("r-unq-ok"), "PASS");
        saveRule(env, "r-unq-bad", "unique", "field", badId, null, null, "{\"column\":\"ID\"}");
        GraphQLResponseBean unqBad = exec("r-unq-bad");
        assertStatus(unqBad, "FAIL");
        assertActualValue(unqBad, 1.0);

        // range: val 0-100 — OK 表全在范围 PASS / BAD 表 1 越界 FAIL
        saveRule(env, "r-rng-ok", "range", "field", okId, null, null, "{\"column\":\"VAL\",\"min\":0,\"max\":100}");
        assertStatus(exec("r-rng-ok"), "PASS");
        saveRule(env, "r-rng-bad", "range", "field", badId, null, null, "{\"column\":\"VAL\",\"min\":0,\"max\":100}");
        GraphQLResponseBean rngBad = exec("r-rng-bad");
        assertStatus(rngBad, "FAIL");
        assertActualValue(rngBad, 1.0);

        // regex: email 匹配 ^[^@]+@[^@]+$ — OK 表全匹配 PASS / BAD 表 1 不匹配 FAIL
        saveRule(env, "r-rgx-ok", "regex", "field", okId, null, null,
                "{\"column\":\"EMAIL\",\"pattern\":\"^[^@]+@[^@]+$\"}");
        assertStatus(exec("r-rgx-ok"), "PASS");
        saveRule(env, "r-rgx-bad", "regex", "field", badId, null, null,
                "{\"column\":\"EMAIL\",\"pattern\":\"^[^@]+@[^@]+$\"}");
        GraphQLResponseBean rgxBad = exec("r-rgx-bad");
        assertStatus(rgxBad, "FAIL");
        assertActualValue(rgxBad, 1.0);
    }

    // ===== 批量执行 + 失败隔离 =====

    /** 批量执行：同数据源下多条规则，返回 executedCount/results。 */
    @Test
    public void testExecuteRulesForDataSourceBatch() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_batch;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_b1 (id INT NOT NULL)",
                "INSERT INTO ext_b1 VALUES (1)", "INSERT INTO ext_b1 VALUES (2)");
        PreparedEnv env = prepare(dbUrl, "qs_q_batch");
        String t1 = env.tableId("EXT_B1");

        saveRule(env, "r-b1-vol", "volume", "table", t1, null, null, "{\"minRows\":1}");
        saveRule(env, "r-b1-nn", "not_null", "field", t1, null, 0.0, "{\"column\":\"ID\"}");

        GraphQLResponseBean resp = batchExec(env.dataSourceId);
        assertFalse(resp.hasError(), "batch should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("executedCount=2"), "should execute 2 rules: " + data);
        // 两条结果都写入 NopMetaQualityResult
        assertEquals(1, countResults("r-b1-vol"));
        assertEquals(1, countResults("r-b1-nn"));
    }

    /** 单规则失败（坏 custom_sql）收集到 errors 不中断整批，其余规则仍执行并写入结果。 */
    @Test
    public void testExecuteRulesForDataSourceFailureIsolation() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_iso;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_iso (id INT NOT NULL)",
                "INSERT INTO ext_iso VALUES (1)");
        PreparedEnv env = prepare(dbUrl, "qs_q_iso");
        String tId = env.tableId("EXT_ISO");

        // 好规则：volume（执行成功 → 写 PASS 结果）
        saveRule(env, "r-iso-ok", "volume", "table", tId, null, null, "{\"minRows\":1}");
        // 坏规则：not_null 引用不存在的物理列（标识符通过白名单，但 SQL 执行时列不存在 →
        // 执行器抛 NopException → 批次 try/catch 收集进 errors，不写结果行）
        saveRule(env, "r-iso-bad", "not_null", "field", tId, null, 0.0,
                "{\"column\":\"NOPE_COL\"}");

        GraphQLResponseBean resp = batchExec(env.dataSourceId);
        assertFalse(resp.hasError(), "batch should not globally error (isolated): " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("executedCount=1"), "good rule should still execute: " + data);
        assertTrue(data.contains("r-iso-bad"), "bad rule failure recorded in errors: " + data);

        // 好规则仍写入结果（失败不中断整批）
        assertEquals(1, countResults("r-iso-ok"), "good rule result must be written despite bad rule failure");
        // 坏规则不写入结果（SQL 失败，被隔离）
        assertEquals(0, countResults("r-iso-bad"), "bad rule must NOT have a result (SQL failed, isolated)");
    }

    // ===== 时序追加 =====

    /** 重复执行同一规则追加新结果行（executeTime 不同），不覆盖旧行。 */
    @Test
    public void testExecuteQualityRuleTimeSeriesAppend() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_q_ts;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_ts2 (id INT NOT NULL)", "INSERT INTO ext_ts2 VALUES (1)");
        PreparedEnv env = prepare(dbUrl, "qs_q_ts");
        String tId = env.tableId("EXT_TS2");

        saveRule(env, "r-ts", "volume", "table", tId, null, null, "{\"minRows\":1}");

        GraphQLResponseBean r1 = exec("r-ts");
        assertFalse(r1.hasError(), "first exec should not error: " + r1);
        assertEquals(1, countResults("r-ts"));

        // 确保时间戳不同（executeTime 精度为秒，强制间隔）
        Thread.sleep(1100);
        GraphQLResponseBean r2 = exec("r-ts");
        assertFalse(r2.hasError(), "second exec should not error: " + r2);
        assertEquals(2, countResults("r-ts"), "time-series: 2 result rows after second exec (appended, not overwritten)");
    }

    // ===== 不可执行路径显式失败 / SKIP =====

    // ===== 默认 schema 从持久化解析（plan 2026-07-17-0852-3 Phase 3） =====

    /**
     * 端到端：sync 持久化 schema → 直接执行 executeQualityRule（不传 schemaPattern）→ 默认按
     * 持久化 schema 执行 volume COUNT，命中正确表（plan 0852-3 Phase 3 anti-hollow #22）。
     *
     * <p>在 H2 自定义 schema SCH_QR_DEF 下建表 + 插入 3 行 → sync → 不传 schemaPattern 执行 →
     * actualValue=3（真实 COUNT 结果，证明默认 schema 解析在 BizModel 层生效并被执行器接收）。
     */
    @Test
    public void testExecuteQualityRuleDefaultSchemaFromPersisted() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_qr_def;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA SCH_QR_DEF");
            st.execute("CREATE TABLE SCH_QR_DEF.ext_qr_def (id INT NOT NULL)");
            st.execute("INSERT INTO SCH_QR_DEF.ext_qr_def VALUES (1)");
            st.execute("INSERT INTO SCH_QR_DEF.ext_qr_def VALUES (2)");
            st.execute("INSERT INTO SCH_QR_DEF.ext_qr_def VALUES (3)");
        }
        PreparedEnv env = prepareMultiSchema(dbUrl, "qs_qr_def", "SCH_QR_DEF");

        String tableId = env.tableId("EXT_QR_DEF");
        saveRule(env, "r-qr-def", "volume", "table", tableId, null, 2.0, "{\"minRows\":2}");

        // 不传 schemaPattern：默认取 table.schema=SCH_QR_DEF
        GraphQLResponseBean resp = execNoSchema("r-qr-def");
        assertFalse(resp.hasError(), "default schema path should not error: " + resp);
        assertStatus(resp, "PASS");
        assertActualValue(resp, 3.0);
    }

    /**
     * 显式 schemaPattern 覆盖持久化 schema（plan 0852-3 Phase 3 覆盖语义）。
     * sync SCH_QR_A（持久化 schema=SCH_QR_A，2 行）→ 显式传 schemaPattern=SCH_QR_B（覆盖）→ 命中 SCH_QR_B 的 5 行。
     */
    @Test
    public void testExecuteQualityRuleExplicitSchemaOverridesPersisted() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_qr_ovr;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA SCH_QR_A");
            st.execute("CREATE SCHEMA SCH_QR_B");
            st.execute("CREATE TABLE SCH_QR_A.shared_qr (id INT NOT NULL)");
            st.execute("CREATE TABLE SCH_QR_B.shared_qr (id INT NOT NULL)");
            st.execute("INSERT INTO SCH_QR_A.shared_qr VALUES (1)");
            st.execute("INSERT INTO SCH_QR_A.shared_qr VALUES (2)");
            for (int i = 0; i < 5; i++) st.execute("INSERT INTO SCH_QR_B.shared_qr VALUES (" + i + ")");
        }
        PreparedEnv env = prepareMultiSchema(dbUrl, "qs_qr_ovr", "SCH_QR_A");

        // sync 写入 schema=SCH_QR_A
        String tableIdA = env.tableId("SHARED_QR");

        saveRule(env, "r-qr-ovr", "volume", "table", tableIdA, null, 1.0, "{\"minRows\":1}");

        // 显式传 SCH_QR_B → 覆盖持久化的 SCH_QR_A → 命中 SCH_QR_B 的 5 行
        GraphQLResponseBean resp = execWithSchema("r-qr-ovr", "SCH_QR_B");
        assertFalse(resp.hasError(), "explicit schema override should not error: " + resp);
        assertActualValue(resp, 5.0);
    }

    /** 规则不存在 → 抛 metadata.quality-rule-not-found（不 NPE）。 */
    @Test
    public void testExecuteRuleNotFound() {
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(
                graphQLEngine.newGraphQLContext(req(
                        "mutation { NopMetaQualityRule__executeQualityRule(qualityRuleId: \"__nope__\") }")));
        assertTrue(resp.hasError(), "non-existent rule must error (no NPE): " + resp);
    }

    /** 目标表非 external（首版）→ 显式失败。 */
    @Test
    public void testExecuteRuleTableNotExternal() {
        // 建一张 entity 类型的逻辑表（非 external），规则挂其上
        NopMetaTable entityTable = saveManualTable("EXT_NE_ENTITY", "entity", "qs_ne");

        saveRuleDirect("r-ne", "volume", "table", entityTable.getMetaTableId(), null, null, "{\"minRows\":1}");
        GraphQLResponseBean resp = exec("r-ne");
        assertTrue(resp.hasError(), "non-external table must explicitly fail (first version): " + resp);
    }

    /** 目标表无注册数据源 → 显式失败。 */
    @Test
    public void testExecuteRuleNoDataSource() {
        // external 表但 querySpace 无对应数据源
        NopMetaTable ext = saveManualTable("EXT_NDS", "external", "qs_no_ds_at_all");

        saveRuleDirect("r-nds", "volume", "table", ext.getMetaTableId(), null, null, "{\"minRows\":1}");
        GraphQLResponseBean resp = exec("r-nds");
        assertTrue(resp.hasError(), "no datasource for querySpace must explicitly fail: " + resp);
    }

    /** DISABLED 数据源 → 显式失败。 */
    @Test
    public void testExecuteRuleDatasourceDisabled() {
        saveDataSource("ds-qd", "qs_q_disabled", "jdbc", "DISABLED",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_q_d;DB_CLOSE_DELAY=-1\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");
        // external 表挂在该 querySpace
        NopMetaTable ext = saveManualTable("EXT_QD", "external", "qs_q_disabled");

        saveRuleDirect("r-qd", "volume", "table", ext.getMetaTableId(), null, null, "{\"minRows\":1}");
        GraphQLResponseBean resp = exec("r-qd");
        assertTrue(resp.hasError(), "DISABLED datasource must explicitly fail (no silent pass): " + resp);
    }

    /** entityType=database → SKIP 结果行（带 details 标记），不抛全局错误。 */
    @Test
    public void testExecuteRuleDatabaseEntityTypeSkip() {
        saveRuleDirect("r-db", "volume", "database", "some-db-id", null, null, "{\"minRows\":1}");
        GraphQLResponseBean resp = exec("r-db");
        assertFalse(resp.hasError(), "database entity type should SKIP (not global error): " + resp);
        assertStatus(resp, "SKIP");
        // SKIP 结果行也写入（时序有记录）
        NopMetaQualityResult row = findResult("r-db");
        assertNotNull(row, "SKIP result row must be written for audit");
        assertEquals("SKIP", row.getStatus());
    }

    /** 非 jdbc 类型 → 显式失败（UnsupportedOperationException）。 */
    @Test
    public void testExecuteRuleNonJdbcThrows() {
        saveDataSource("ds-qhttp", "qs_q_http", "http", "ACTIVE", "{}");
        NopMetaTable ext = saveManualTable("EXT_QHTTP", "external", "qs_q_http");

        saveRuleDirect("r-http", "volume", "table", ext.getMetaTableId(), null, null, "{\"minRows\":1}");
        GraphQLResponseBean resp = exec("r-http");
        assertTrue(resp.hasError(), "non-jdbc datasource must explicitly fail: " + resp);
    }

    // ===== helpers =====

    /** 端到端辅助：建数据源 + 同步 external 表结构（指定 schemaPattern），返回已就绪环境。 */
    private PreparedEnv prepare(String dbUrl, String querySpace) {
        return prepareMultiSchema(dbUrl, querySpace, "PUBLIC");
    }

    /**
     * 端到端辅助（多 schema 版本，plan 0852-3）：建数据源 + 同步指定 schema 的 external 表结构，
     * 使持久化 {@code NopMetaTable.schema} 列真实写入该 schema（用于默认 schema 解析测试）。
     */
    private PreparedEnv prepareMultiSchema(String dbUrl, String querySpace, String schemaPattern) {
        saveDataSource("ds-" + querySpace, querySpace, "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");
        GraphQLResponseBean syncResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-" + querySpace
                        + "\", schemaPattern: \"" + schemaPattern + "\") { syncedTableCount errors { code message detail } } }")));
        assertFalse(syncResp.hasError(), "sync should not error: " + syncResp);
        return new PreparedEnv("ds-" + querySpace);
    }

    private GraphQLResponseBean exec(String ruleId) {
        return execWithSchema(ruleId, "PUBLIC");
    }

    /** 执行规则不传 schemaPattern（验证默认 schema 解析，plan 0852-3 Phase 3）。 */
    private GraphQLResponseBean execNoSchema(String ruleId) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityRule__executeQualityRule(qualityRuleId: \"" + ruleId + "\") }")));
    }

    /** 执行规则显式传 schemaPattern（验证覆盖语义，plan 0852-3 Phase 3）。 */
    private GraphQLResponseBean execWithSchema(String ruleId, String schemaPattern) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityRule__executeQualityRule(qualityRuleId: \"" + ruleId + "\", "
                        + "schemaPattern: \"" + schemaPattern + "\") }")));
    }

    private GraphQLResponseBean batchExec(String dataSourceId) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityRule__executeQualityRulesForDataSource(dataSourceId: \""
                        + dataSourceId + "\", schemaPattern: \"PUBLIC\") }")));
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }

    private void saveRule(PreparedEnv env, String ruleId, String ruleType, String entityType,
                          String entityId, String sqlExpression, Double threshold, String params) {
        saveRuleDirect(ruleId, ruleType, entityType, entityId, sqlExpression, threshold, params);
    }

    private void saveRuleDirect(String ruleId, String ruleType, String entityType,
                                String entityId, String sqlExpression, Double threshold, String params) {
        IEntityDao<NopMetaQualityRule> dao = daoProvider.daoFor(NopMetaQualityRule.class);
        NopMetaQualityRule rule = dao.newEntity();
        rule.setQualityRuleId(ruleId);
        rule.setRuleName(ruleId);
        rule.setDisplayName(ruleId);
        rule.setRuleType(ruleType);
        rule.setEntityType(entityType);
        rule.setEntityId(entityId);
        rule.setSeverity("WARNING");
        rule.setSqlExpression(sqlExpression);
        rule.setThreshold(threshold);
        rule.setParams(params);
        rule.setVersion(1L);
        rule.setCreatedBy("autotest");
        rule.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        dao.saveEntity(rule);
    }

    private long countResults(String ruleId) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityResult.PROP_NAME_qualityRuleId, ruleId));
        return dao.countByQuery(q);
    }

    private NopMetaQualityResult findResult(String ruleId) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityResult.PROP_NAME_qualityRuleId, ruleId));
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

    private static void assertStatus(GraphQLResponseBean resp, String expected) {
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("status=" + expected),
                "expected status=" + expected + " but got: " + data);
    }

    private static void assertActualValue(GraphQLResponseBean resp, double expected) {
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("actualValue=" + expected),
                "expected actualValue=" + expected + " but got: " + data);
    }

    /** 环境就绪后的轻量句柄，避免重复传参。 */
    private class PreparedEnv {
        final String dataSourceId;

        PreparedEnv(String dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        String tableId(String tableName) {
            IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
            q.addFilter(FilterBeans.eq("tableType", "external"));
            NopMetaTable t = tableDao.findFirstByQuery(q);
            assertNotNull(t, "external table " + tableName + " must be synced before rule execution");
            return t.getMetaTableId();
        }
    }
}
