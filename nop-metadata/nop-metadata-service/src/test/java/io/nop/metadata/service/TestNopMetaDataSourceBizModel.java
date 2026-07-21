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
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 NopMetaDataSourceBizModel.testConnection 的 5 条路径：
 * 成功连通（真实 H2 建连 + DatabaseMetaData）/ dataSourceId 不存在 / DISABLED 拒绝 /
 * 非 jdbc 显式失败 / connectionConfig 缺必填字段快速失败。
 *
 * <p>成功路径用真实 H2 内存库（H2 + org.h2.Driver 在 test classpath），证明连接服务确实
 * 打开了真实连接并读取了 DatabaseMetaData（返回 Map 含真实 productName "H2"），非空壳实现。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataSourceBizModel extends JunitBaseTestCase {

    public TestNopMetaDataSourceBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testConnectionSuccessRealConnect() {
        // 端到端：GraphQL mutation → BizModel → 连接服务 → 真实 H2 建连 → DatabaseMetaData → 返回 Map
        saveDataSource("ds-h2-ok", "qs_h2_ok", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_test_ok;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-h2-ok\") { connected databaseProductName databaseProductVersion error } }");
        assertFalse(response.hasError(), "success path should not error: " + response);

        String data = String.valueOf(response.getData());
        assertTrue(data.contains("connected=true"),
                "should report connected=true: " + data);
        // 真实 productName "H2" 证明连接服务确实读取了真实 DatabaseMetaData（anti-hollow）
        assertTrue(data.contains("databaseProductName=H2"),
                "should contain real databaseProductName=H2 from live metadata: " + data);
        assertTrue(data.contains("databaseProductVersion="),
                "should contain databaseProductVersion: " + data);
    }

    @Test
    public void testConnectionNotFound() {
        // 不存在的 dataSourceId 必须抛 metadata.datasource-not-found（不 NPE）
        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"__not_exist__\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "non-existent dataSourceId must error (no NPE): " + response);
    }

    @Test
    public void testConnectionDisabledRejected() {
        // DISABLED 数据源必须显式拒绝（不静默通过）
        saveDataSource("ds-disabled", "qs_disabled", "jdbc", "DISABLED",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_test_disabled;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-disabled\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "DISABLED datasource must be rejected (no silent pass): " + response);
    }

    @Test
    public void testConnectionNonJdbcThrows() {
        // 非 jdbc 类型（http）必须显式失败（NopException(Err_DATASOURCE_TYPE_NOT_SUPPORTED)，不静默返回成功）
        saveDataSource("ds-http", "qs_http", "http", "ACTIVE", "{}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-http\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "non-jdbc datasource must error (NopException ERR_DATASOURCE_TYPE_NOT_SUPPORTED): " + response);
    }

    @Test
    public void testConnectionConfigMissingFastFail() {
        // connectionConfig 缺 jdbc 必填字段 jdbcUrl 必须快速失败（不静默建连）
        saveDataSource("ds-bad-cfg", "qs_bad_cfg", "jdbc", "ACTIVE",
                "{\"username\":\"sa\",\"password\":\"\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-bad-cfg\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "missing required jdbc field (jdbcUrl) must fast-fail: " + response);
    }

    // ===== syncExternalTables：端到端扫描写入 =====

    /**
     * 端到端：GraphQL mutation → BizModel → P2-1 withConnection callback 建连 → 读取器扫描
     * information_schema → 写入 MetaTable(tableType=external) + buildSql 列快照。
     *
     * <p>Anti-Hollow：真实 H2 建连扫描 EXT_DEPT 表，断言 syncedTableCount>0 且 buildSql 含真实
     * 列名/类型，证明运行时确实执行了 information_schema 查询（非空壳）。
     */
    @Test
    public void testSyncExternalTablesWritesStructure() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_sync_write;DB_CLOSE_DELAY=-1";
        seedExternalTable(dbUrl, "CREATE TABLE ext_dept (dept_id INT NOT NULL, dept_name VARCHAR(100), budget DECIMAL(10,2))");

        saveDataSource("ds-sync-write", "qs_sync_write", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-write\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        assertFalse(response.hasError(), "sync should not error: " + response);

        String data = String.valueOf(response.getData());
        // Anti-Hollow：真实扫描到 PUBLIC schema 下的 ext_dept 表（限定 schema 排除 H2 系统表）
        assertTrue(data.contains("syncedTableCount=1"),
                "should report syncedTableCount=1 for PUBLIC schema: " + data);

        // 目录写入验证：MetaTable(tableType=external) 存在，buildSql 含列结构
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable table = findExternalTable(tableDao, "EXT_DEPT");
        assertNotNull(table, "external table EXT_DEPT should be synced into catalog");
        assertEquals("external", table.getTableType(), "tableType must be external");
        assertEquals("qs_sync_write", table.getQuerySpace(), "querySpace must follow datasource");
        String buildSql = table.getBuildSql();
        assertNotNull(buildSql, "buildSql (column snapshot) must be written");
        // H2 将无引号标识符大写：列名 DEPT_ID/DEPT_NAME，类型 INTEGER/CHARACTER VARYING/DECIMAL
        assertTrue(buildSql.contains("DEPT_ID"), "buildSql must contain column DEPT_ID: " + buildSql);
        assertTrue(buildSql.contains("DEPT_NAME"), "buildSql must contain column DEPT_NAME: " + buildSql);
        assertTrue(buildSql.contains("DECIMAL"), "buildSql must contain native type DECIMAL (BUDGET): " + buildSql);
        assertTrue(buildSql.contains("\"nullable\":false"),
                "buildSql must capture nullable flag (DEPT_ID NOT NULL): " + buildSql);
        assertTrue(buildSql.contains("columnName"),
                "buildSql must be structured column JSON: " + buildSql);

        // 系统模块 nop/meta-external 已创建（方案 A 归属）
        assertNotNull(findSystemExternalModule(), "system module nop/meta-external must be created");
    }

    /**
     * 幂等：按 (metaModuleId, tableName) 复合键 upsert，重复同步更新而非追加（记录数稳定）。
     */
    @Test
    public void testSyncExternalTablesIdempotent() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_sync_idem;DB_CLOSE_DELAY=-1";
        seedExternalTable(dbUrl, "CREATE TABLE ext_emp (emp_id INT NOT NULL, emp_name VARCHAR(50))");

        saveDataSource("ds-sync-idem", "qs_sync_idem", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        // 第一次同步（限定 PUBLIC schema）
        GraphQLResponseBean r1 = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-idem\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        assertFalse(r1.hasError(), "first sync should not error: " + r1);
        long countAfterFirst = countExternalTables("EXT_EMP");
        assertEquals(1L, countAfterFirst, "exactly one EXT_EMP after first sync: " + countAfterFirst);

        // 第二次同步同一数据源（幂等，不追加）
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-idem\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        assertFalse(r2.hasError(), "second sync should not error: " + r2);
        long countAfterSecond = countExternalTables("EXT_EMP");
        assertEquals(1L, countAfterSecond,
                "idempotent: EXT_EMP count must stay 1 after second sync, got: " + countAfterSecond);
        assertTrue(String.valueOf(r2.getData()).contains("syncedTableCount=1"),
                "second sync still reports syncedTableCount=1 (update, not skip): " + r2.getData());
    }

    // ===== syncExternalTables：多 schema 数据源支持（plan 2026-07-17-0852-3） =====

    /**
     * 接线验证：sync 写库路径真实持久化 schema 列（plan 0852-3 Phase 2 anti-hollow #23）。
     *
     * <p>限定 PUBLIC schema 同步 → 断言 NopMetaTable.schema == "PUBLIC"（H2 返回的 TABLE_SCHEM），
     * 证明 sync 链路从 reader → ExternalTableInfo → upsertExternalTable 真实写入 schema 字段，
     * 非仅读到内存丢弃。
     */
    @Test
    public void testSyncExternalTablesPersistsSchemaColumn() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_sync_schema;DB_CLOSE_DELAY=-1";
        seedExternalTable(dbUrl, "CREATE TABLE ext_schema_t (id INT NOT NULL)");

        saveDataSource("ds-sync-schema", "qs_sync_schema", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-schema\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        assertFalse(response.hasError(), "sync should not error: " + response);

        NopMetaTable table = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "EXT_SCHEMA_T");
        assertNotNull(table, "EXT_SCHEMA_T must be synced");
        assertEquals("PUBLIC", table.getMetaSchema(),
                "schema column must persist H2 TABLE_SCHEM=PUBLIC (anti-hollow: written, not just read into memory)");
    }

    /**
     * 端到端：同一数据源下两个 schema 的同名表 sync 后产生两条 NopMetaTable（schema 各异，互不覆盖）。
     *
     * <p>核心 anti-hollow（plan 0852-3 Phase 2 Exit Criteria #22）：去重键收敛为
     * {@code (metaModuleId, schema, tableName)}，schema 维度真实生效——非空壳、非退化为旧键。
     */
    @Test
    public void testSyncMultiSchemaSameNameTablesDistinct() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_sync_multi;DB_CLOSE_DELAY=-1";
        // 创建两个 schema + 同名表（同名是关键，旧去重键会覆盖）
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA SCH_A");
            st.execute("CREATE SCHEMA SCH_B");
            st.execute("CREATE TABLE SCH_A.shared_t (id INT NOT NULL)");
            st.execute("CREATE TABLE SCH_B.shared_t (id INT NOT NULL)");
        }

        saveDataSource("ds-multi-schema", "qs_multi_schema", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        // 扫 SCH_A → 1 行 (schema="SCH_A", tableName="SHARED_T")
        GraphQLResponseBean r1 = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-multi-schema\", schemaPattern: \"SCH_A\") { syncedTableCount errors { code message detail } } }");
        assertFalse(r1.hasError(), "SCH_A sync should not error: " + r1);
        assertEquals(1L, countExternalTables("SHARED_T"),
                "exactly 1 SHARED_T after SCH_A sync");

        // 扫 SCH_B → 第 2 行 (schema="SCH_B", tableName="SHARED_T")，不应覆盖 SCH_A 的行
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-multi-schema\", schemaPattern: \"SCH_B\") { syncedTableCount errors { code message detail } } }");
        assertFalse(r2.hasError(), "SCH_B sync should not error: " + r2);
        assertEquals(2L, countExternalTables("SHARED_T"),
                "2 SHARED_T rows after SCH_B sync (dedup key now includes schema, no overwrite)");

        // 断言两行 schema 不同（anti-hollow：去重 filter 真实含 schema 维度）
        List<NopMetaTable> tables = findAllExternalTables("SHARED_T");
        Set<String> schemas = tables.stream().map(NopMetaTable::getMetaSchema).collect(Collectors.toSet());
        assertTrue(schemas.contains("SCH_A"),
                "row with schema=SCH_A must exist (schemas seen: " + schemas + ")");
        assertTrue(schemas.contains("SCH_B"),
                "row with schema=SCH_B must exist (schemas seen: " + schemas + ")");
        assertEquals(2, schemas.size(), "schemas must be distinct for the two rows");
    }

    /**
     * 端到端验证：单 schema 多次同步保持幂等（schema 维度不破坏既有幂等语义）。
     * 即同一 (metaModuleId, schema, tableName) 重复同步不追加。
     */
    @Test
    public void testSyncMultiSchemaIdempotentSameSchema() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_sync_idem_sch;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA SCH_IDEM");
            st.execute("CREATE TABLE SCH_IDEM.t_idem (id INT NOT NULL)");
        }

        saveDataSource("ds-idem-sch", "qs_idem_sch", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-idem-sch\", schemaPattern: \"SCH_IDEM\") { syncedTableCount errors { code message detail } } }");
        assertEquals(1L, countExternalTables("T_IDEM"), "1 T_IDEM after first sync");

        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-idem-sch\", schemaPattern: \"SCH_IDEM\") { syncedTableCount errors { code message detail } } }");
        assertEquals(1L, countExternalTables("T_IDEM"),
                "still 1 T_IDEM after second sync (idempotent: dedup key (modId,SCH_IDEM,T_IDEM) matches)");
    }

    /**
     * Phase 2 跨数据源行为裁定（Decision）：去重键含 schema 但**不含 querySpace**，
     * 跨数据源、同名同 schema 表会互相覆盖（与 plan 0852-3 Phase 2 裁定一致，沿用 1905-1 follow-up）。
     *
     * <p>本测试钉住此行为：两个数据源 querySpace 不同但 schema 都是 PUBLIC、表名相同，
     * 第二次 sync 覆盖第一次（querySpace 更新为第二次的）。
     */
    @Test
    public void testSyncCrossDataSourceSameSchemaOverwritesPerDecision() throws Exception {
        String dbUrlA = "jdbc:h2:mem:meta_xds_a;DB_CLOSE_DELAY=-1";
        String dbUrlB = "jdbc:h2:mem:meta_xds_b;DB_CLOSE_DELAY=-1";
        // 列名不同以验证 buildSql 真实被更新（anti-hollow：非仅 querySpace 字段写）
        seedExternalTable(dbUrlA, "CREATE TABLE SHARED_XDS (id_a INT NOT NULL)");
        seedExternalTable(dbUrlB, "CREATE TABLE SHARED_XDS (id_b INT NOT NULL)");

        saveDataSource("ds-xds-a", "qs_xds_a", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrlA + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");
        saveDataSource("ds-xds-b", "qs_xds_b", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrlB + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-xds-a\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        NopMetaTable firstRow = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "SHARED_XDS");
        assertNotNull(firstRow, "first sync creates row");
        assertEquals("qs_xds_a", firstRow.getQuerySpace(), "querySpace from ds-a");
        assertTrue(firstRow.getBuildSql().contains("ID_A"),
                "buildSql from ds-a should contain ID_A column: " + firstRow.getBuildSql());

        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-xds-b\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        assertEquals(1L, countExternalTables("SHARED_XDS"),
                "cross-ds same-name same-schema: still 1 row (dedup key has no querySpace, 2nd sync overwrites)");

        NopMetaTable overwritten = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "SHARED_XDS");
        assertEquals("qs_xds_b", overwritten.getQuerySpace(),
                "querySpace updated to qs_xds_b per Phase 2 cross-ds decision");
        assertTrue(overwritten.getBuildSql().contains("ID_B"),
                "buildSql updated with ID_B column (anti-hollow: real overwrite, not just no-op)");
    }

    /** DISABLED 数据源同步必须显式拒绝（不静默通过）。 */
    @Test
    public void testSyncExternalTablesDisabledRejected() {
        saveDataSource("ds-sync-disabled", "qs_sync_disabled", "jdbc", "DISABLED",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_sync_disabled;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"sa\",\"password\":\"\",\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-disabled\") { syncedTableCount errors { code message detail } } }");
        assertTrue(response.hasError(),
                "DISABLED datasource sync must be rejected (no silent pass): " + response);
    }

    /** 非 jdbc 类型同步必须显式失败（不静默返回成功）。 */
    @Test
    public void testSyncExternalTablesNonJdbcThrows() {
        saveDataSource("ds-sync-http", "qs_sync_http", "http", "ACTIVE", "{}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-http\") { syncedTableCount errors { code message detail } } }");
        assertTrue(response.hasError(),
                "non-jdbc datasource sync must error (NopException ERR_DATASOURCE_TYPE_NOT_SUPPORTED): " + response);
    }

    /** 不存在的 dataSourceId 同步必须抛 metadata.datasource-not-found（不 NPE）。 */
    @Test
    public void testSyncExternalTablesNotFound() {
        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"__not_exist__\") { syncedTableCount errors { code message detail } } }");
        assertTrue(response.hasError(),
                "non-existent dataSourceId sync must error (no NPE): " + response);
    }

    /**
     * 不支持的方言必须显式失败（不静默跳过）。
     * 验证见 {@code io.nop.metadata.service.sync.TestExternalTableStructureReader}（同包，直访读取器门禁）。
     */

    // ===== collectCatalog：端到端运行时统计收集 =====

    /**
     * 端到端：syncExternalTables 建外部表 → collectCatalog → NopMetaCatalog 写入真实 rowCount。
     *
     * <p>Anti-Hollow：真实 H2 建连，EXT_DEPT 插入 5 行，断言 NopMetaCatalog.rowCount==5（真实 COUNT 结果，
     * 证明运行时确实通过 P2-1 withConnection callback 建连并执行了 COUNT 查询，非空壳）。
     * 同时验证降级策略：H2 的 sizeBytes/partitionCount/lastModified 为 null 且 details.unavailable 显式列出。
     */
    @Test
    public void testCollectCatalogWritesRealRowCount() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_collect;DB_CLOSE_DELAY=-1";
        seedExternalTableWithRows(dbUrl,
                "CREATE TABLE ext_dept (dept_id INT NOT NULL)",
                "CREATE INDEX ix_dept ON ext_dept(dept_id)",
                5);

        saveDataSource("ds-collect", "qs_collect", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        // 先同步外部表结构（建目录）
        GraphQLResponseBean syncResp = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-collect\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        assertFalse(syncResp.hasError(), "sync should not error: " + syncResp);

        NopMetaTable table = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "EXT_DEPT");
        assertNotNull(table, "EXT_DEPT must be synced before collect");

        // 收集运行时统计（限定 PUBLIC schema）
        GraphQLResponseBean collectResp = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect\", schemaPattern: \"PUBLIC\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertFalse(collectResp.hasError(), "collect should not error: " + collectResp);
        assertTrue(String.valueOf(collectResp.getData()).contains("tableCount=1"),
                "should report tableCount=1: " + collectResp.getData());

        // 目录写入验证：NopMetaCatalog 行存在，rowCount 为真实 COUNT 结果（5）
        NopMetaCatalog row = findCatalogRow(table.getMetaTableId());
        assertNotNull(row, "NopMetaCatalog row must be written for EXT_DEPT");
        assertEquals(5L, row.getRowCount(), "rowCount must be real COUNT(*) result = 5");
        // 索引存在（ix_dept），indexCount >= 1
        assertNotNull(row.getIndexCount(), "indexCount must be collected via JDBC getIndexInfo");
        assertTrue(row.getIndexCount() >= 1, "indexCount must be >= 1 (ix_dept exists): " + row.getIndexCount());

        // 降级策略：H2 不支持的统计为 null + details.unavailable 显式列出（不静默跳过、不伪造 0）
        assertNull(row.getSizeBytes(), "sizeBytes must be null on H2 (not implemented, not faked 0)");
        assertNull(row.getPartitionCount(), "partitionCount must be null on H2 (not implemented, not faked 0)");
        assertNull(row.getLastModified(), "lastModified must be null on H2 (LAST_MODIFICATION is a counter, not timestamp)");
        String details = row.getDetails();
        assertNotNull(details, "details JSON must be written");
        assertTrue(details.contains("unavailable"), "details must contain unavailable marker: " + details);
        assertTrue(details.contains("sizeBytes"), "details.unavailable must list sizeBytes: " + details);
        assertTrue(details.contains("partitionCount"), "details.unavailable must list partitionCount: " + details);
        assertTrue(details.contains("lastModified"), "details.unavailable must list lastModified: " + details);
        assertTrue(details.contains("H2"), "details must record dialect (databaseProductName=H2): " + details);
    }

    /**
     * 时序语义：重复收集同一数据源追加为新的快照行（collectedAt 区分），不覆盖旧行。
     */
    @Test
    public void testCollectCatalogAppendsWithSchemaPattern() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_collect_ts;DB_CLOSE_DELAY=-1";
        seedExternalTableWithRows(dbUrl,
                "CREATE TABLE ext_emp (emp_id INT NOT NULL)",
                null, 3);

        saveDataSource("ds-collect-ts", "qs_collect_ts", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-collect-ts\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        NopMetaTable table = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "EXT_EMP");
        assertNotNull(table, "EXT_EMP must be synced");

        // 第一次收集
        execute("mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-ts\", schemaPattern: \"PUBLIC\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        long countAfterFirst = countCatalogRows(table.getMetaTableId());
        assertEquals(1L, countAfterFirst, "exactly 1 catalog row after first collect: " + countAfterFirst);

        // 第二次收集同一数据源（时序追加，不覆盖）
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-ts\", schemaPattern: \"PUBLIC\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertFalse(r2.hasError(), "second collect should not error: " + r2);
        long countAfterSecond = countCatalogRows(table.getMetaTableId());
        assertEquals(2L, countAfterSecond,
                "time-series: 2 catalog rows after second collect (appended, not overwritten): " + countAfterSecond);
    }

    /**
     * schema 限定：不传 schemaPattern 时依赖连接默认 schema（H2 默认 PUBLIC），COUNT 仍能正确执行。
     * 证明 schemaPattern=null 路径成立（多 schema 限制已在设计 D1 记录为已知 follow-up）。
     */
    @Test
    public void testCollectCatalogDefaultSchemaNoPattern() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_collect_def;DB_CLOSE_DELAY=-1";
        seedExternalTableWithRows(dbUrl,
                "CREATE TABLE ext_def (id INT NOT NULL)", null, 2);

        saveDataSource("ds-collect-def", "qs_collect_def", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-collect-def\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        NopMetaTable table = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "EXT_DEF");
        assertNotNull(table, "EXT_DEF must be synced");

        // 不传 schemaPattern：COUNT 用 <tableName> 依赖连接默认 schema（H2 默认 PUBLIC）
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-def\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertFalse(resp.hasError(), "collect without schemaPattern should work via default schema: " + resp);
        NopMetaCatalog row = findCatalogRow(table.getMetaTableId());
        assertNotNull(row, "NopMetaCatalog row must be written via default schema");
        assertEquals(2L, row.getRowCount(), "rowCount must be 2 via default schema COUNT");
    }

    /**
     * 单表失败收集到 errors 不中断整批：EXT_OK 存在可收集，EXT_GONE 不存在物理库（COUNT 失败）。
     * 断言 errors 含 EXT_GONE 且 EXT_OK 仍写入 NopMetaCatalog。
     */
    @Test
    public void testCollectCatalogSingleTableErrorBatched() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_collect_err;DB_CLOSE_DELAY=-1";
        seedExternalTableWithRows(dbUrl,
                "CREATE TABLE ext_ok (id INT NOT NULL)", null, 4);

        saveDataSource("ds-collect-err", "qs_collect_err", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        // 同步真实存在的表
        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-collect-err\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        NopMetaTable okTable = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "EXT_OK");
        assertNotNull(okTable, "EXT_OK must be synced");

        // 手工注入一条指向不存在物理表的 external 目录行（EXT_GONE 在物理库中不存在）
        String externalModuleId = ensureExternalSystemModuleId();
        NopMetaTable goneTable = daoProvider.daoFor(NopMetaTable.class).newEntity();
        goneTable.setMetaModuleId(externalModuleId);
        goneTable.setTableName("EXT_GONE");
        goneTable.setDisplayName("EXT_GONE");
        goneTable.setTableType("external");
        goneTable.setQuerySpace("qs_collect_err");
        goneTable.setVersion(1L);
        daoProvider.daoFor(NopMetaTable.class).saveEntity(goneTable);

        // 收集：EXT_OK 应成功（rowCount=4），EXT_GONE 的 COUNT 应失败进 errors
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-err\", schemaPattern: \"PUBLIC\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertFalse(resp.hasError(), "collect should not globally error (batched): " + resp);
        String data = String.valueOf(resp.getData());
        // EXT_OK 收集成功，EXT_GONE 失败进 errors
        assertTrue(data.contains("tableCount=1"),
                "EXT_OK must be collected (tableCount=1): " + data);
        assertTrue(data.contains("EXT_GONE"),
                "EXT_GONE failure must be recorded in errors (not silently skipped): " + data);

        // EXT_OK 仍写入 NopMetaCatalog（单表失败不中断整批）
        NopMetaCatalog okRow = findCatalogRow(okTable.getMetaTableId());
        assertNotNull(okRow, "EXT_OK catalog row must be written despite EXT_GONE failure");
        assertEquals(4L, okRow.getRowCount(), "EXT_OK rowCount must be 4");
        // EXT_GONE 不应写入 catalog 行（COUNT 失败）
        assertNull(findCatalogRow(goneTable.getMetaTableId()),
                "EXT_GONE must NOT have a catalog row (COUNT failed, not silently faked)");
    }

    /** DISABLED 数据源收集必须显式拒绝（不静默通过）。 */
    @Test
    public void testCollectCatalogDisabledRejected() {
        saveDataSource("ds-collect-disabled", "qs_collect_disabled", "jdbc", "DISABLED",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_collect_disabled;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"sa\",\"password\":\"\",\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-disabled\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertTrue(response.hasError(),
                "DISABLED datasource collect must be rejected (no silent pass): " + response);
    }

    /** 非 jdbc 类型收集必须显式失败（不静默返回成功）。 */
    @Test
    public void testCollectCatalogNonJdbcThrows() {
        saveDataSource("ds-collect-http", "qs_collect_http", "http", "ACTIVE", "{}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-http\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertTrue(response.hasError(),
                "non-jdbc datasource collect must error (NopException ERR_DATASOURCE_TYPE_NOT_SUPPORTED): " + response);
    }

    /** 不存在的 dataSourceId 收集必须抛 metadata.datasource-not-found（不 NPE）。 */
    @Test
    public void testCollectCatalogNotFound() {
        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"__not_exist__\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertTrue(response.hasError(),
                "non-existent dataSourceId collect must error (no NPE): " + response);
    }

    // ===== collectCatalog/ForTable：默认 schema 从持久化解析（plan 2026-07-17-0852-3 Phase 3） =====

    /**
     * 端到端：sync 持久化 schema → 直接执行 collectCatalogForTable（不传 schemaPattern）→ 默认按
     * 持久化 schema 执行 COUNT，命中正确表（plan 0852-3 Phase 3 anti-hollow #22）。
     *
     * <p>在 H2 自定义 schema SCH_CC_DEF 下建表 + 插入 2 行 → sync → 不传 schemaPattern 执行 → rowCount=2。
     * 证明默认 schema 解析在 BizModel 层生效，被执行器接收（非执行器内部猜）。
     */
    @Test
    public void testCollectCatalogForTableDefaultSchemaFromPersisted() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cc_def;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA SCH_CC_DEF");
            st.execute("CREATE TABLE SCH_CC_DEF.ext_cc_def (id INT NOT NULL)");
            st.execute("INSERT INTO SCH_CC_DEF.ext_cc_def VALUES (1)");
            st.execute("INSERT INTO SCH_CC_DEF.ext_cc_def VALUES (2)");
        }

        saveDataSource("ds-cc-def", "qs_cc_def", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        // sync SCH_CC_DEF → NopMetaTable.schema = "SCH_CC_DEF"
        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-cc-def\", schemaPattern: \"SCH_CC_DEF\") { syncedTableCount errors { code message detail } } }");
        NopMetaTable table = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "EXT_CC_DEF");
        assertNotNull(table, "EXT_CC_DEF must be synced");
        assertEquals("SCH_CC_DEF", table.getMetaSchema(),
                "persisted schema must be SCH_CC_DEF (precondition for default schema test)");

        // 不传 schemaPattern：默认取 table.schema=SCH_CC_DEF → 命中 SCH_CC_DEF.ext_cc_def 的 2 行
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataSource__collectCatalogForTable(metaTableId: \"" + table.getMetaTableId() + "\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertFalse(resp.hasError(), "default schema path should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("rowCount=2"),
                "default schema=SCH_CC_DEF should hit the right table (rowCount=2): " + data);

        // 写入 NopMetaCatalog（anti-hollow：非空壳）
        NopMetaCatalog row = findCatalogRow(table.getMetaTableId());
        assertNotNull(row, "NopMetaCatalog row must be written via default schema path");
        assertEquals(2L, row.getRowCount(), "rowCount=2 via persisted default schema SCH_CC_DEF");
    }

    /**
     * 显式 schemaPattern 覆盖持久化 schema（plan 0852-3 Phase 3 覆盖语义）。
     *
     * <p>sync SCH_A（持久化 schema=SCH_A，2 行）→ 显式传 schemaPattern=SCH_B（覆盖持久化）→ 命中 SCH_B 的 3 行。
     * 证明显式入参优先级高于持久化 schema。
     */
    @Test
    public void testCollectCatalogForTableExplicitSchemaOverridesPersisted() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cc_ovr;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA SCH_CC_A");
            st.execute("CREATE SCHEMA SCH_CC_B");
            st.execute("CREATE TABLE SCH_CC_A.shared_cc (id INT NOT NULL)");
            st.execute("CREATE TABLE SCH_CC_B.shared_cc (id INT NOT NULL)");
            st.execute("INSERT INTO SCH_CC_A.shared_cc VALUES (1)");
            st.execute("INSERT INTO SCH_CC_A.shared_cc VALUES (2)");
            st.execute("INSERT INTO SCH_CC_B.shared_cc VALUES (10)");
            st.execute("INSERT INTO SCH_CC_B.shared_cc VALUES (11)");
            st.execute("INSERT INTO SCH_CC_B.shared_cc VALUES (12)");
        }

        saveDataSource("ds-cc-ovr", "qs_cc_ovr", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-cc-ovr\", schemaPattern: \"SCH_CC_A\") { syncedTableCount errors { code message detail } } }");
        NopMetaTable tableA = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "SHARED_CC");
        assertNotNull(tableA, "SHARED_CC must be synced");
        assertEquals("SCH_CC_A", tableA.getMetaSchema(), "persisted schema = SCH_CC_A");

        // 显式传 SCH_CC_B（覆盖持久化的 SCH_CC_A）→ 命中 SCH_CC_B 的 3 行
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataSource__collectCatalogForTable(metaTableId: \"" + tableA.getMetaTableId()
                        + "\", schemaPattern: \"SCH_CC_B\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertFalse(resp.hasError(), "explicit schema override should not error: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains("rowCount=3"),
                "explicit schema=SCH_CC_B should override persisted SCH_CC_A (rowCount=3): " + resp.getData());
    }

    /**
     * 批量 collectCatalog 多 schema 逐表命中（plan 0852-3 Phase 3 batch 结构变更）。
     *
     * <p>同一数据源下 SCH_CC_B1.t_batch（3 行）+ SCH_CC_B2.t_batch（5 行）。分别 sync 各自 schema
     * 后批量 collectCatalog（不传 schemaPattern）→ 两表各按自身持久化 schema 命中正确行数。
     */
    @Test
    public void testCollectCatalogBatchMultiSchemaPerTable() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cc_batch;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA SCH_CC_B1");
            st.execute("CREATE SCHEMA SCH_CC_B2");
            st.execute("CREATE TABLE SCH_CC_B1.t_batch (id INT NOT NULL)");
            st.execute("CREATE TABLE SCH_CC_B2.t_batch (id INT NOT NULL)");
            for (int i = 0; i < 3; i++) st.execute("INSERT INTO SCH_CC_B1.t_batch VALUES (" + i + ")");
            for (int i = 0; i < 5; i++) st.execute("INSERT INTO SCH_CC_B2.t_batch VALUES (" + i + ")");
        }

        saveDataSource("ds-cc-batch", "qs_cc_batch", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        // 分别 sync 两 schema（同名表 T_BATCH，schema 维度区分）
        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-cc-batch\", schemaPattern: \"SCH_CC_B1\") { syncedTableCount errors { code message detail } } }");
        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-cc-batch\", schemaPattern: \"SCH_CC_B2\") { syncedTableCount errors { code message detail } } }");
        assertEquals(2L, countExternalTables("T_BATCH"),
                "2 T_BATCH rows after both syncs (multi-schema dedup)");

        // 批量 collectCatalog 不传 schemaPattern → 各表按自身持久化 schema 命中正确行数
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-cc-batch\") { tableCount tables { tableName metaSchema tableType rowCount sizeBytes } errors { code message detail } } }");
        assertFalse(resp.hasError(), "batch collect with per-table default schema should not error: " + resp);
        assertTrue(String.valueOf(resp.getData()).contains("tableCount=2"),
                "both T_BATCH rows collected (tableCount=2): " + resp.getData());

        // 两表各按其 schema 命中正确行数（B1=3, B2=5）
        List<NopMetaTable> tables = findAllExternalTables("T_BATCH");
        assertEquals(2, tables.size());
        for (NopMetaTable t : tables) {
            NopMetaCatalog row = findCatalogRow(t.getMetaTableId());
            assertNotNull(row, "catalog row for " + t.getMetaSchema() + ".T_BATCH must exist");
            long expected = "SCH_CC_B1".equals(t.getMetaSchema()) ? 3L : 5L;
            assertEquals(expected, row.getRowCount(),
                    "rowCount for schema=" + t.getMetaSchema() + " must be " + expected
                            + " (per-table default schema resolution in batch)");
        }
    }

    // ===== collectCatalog helpers =====

    private void seedExternalTableWithRows(String dbUrl, String createDdl, String indexDdl, int rowCount) throws Exception {
        String tableName = extractTableName(createDdl);
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute(createDdl);
            if (indexDdl != null) {
                st.execute(indexDdl);
            }
            // 插入 rowCount 行：用第一列（NOT NULL 主键列）赋值，避免 NOT NULL 约束失败
            for (int i = 1; i <= rowCount; i++) {
                st.execute("INSERT INTO " + tableName + " VALUES (" + i + ")");
            }
        }
    }

    /** 从 CREATE TABLE DDL 中提取表名（支持 CREATE TABLE <name> (...)）。 */
    private static String extractTableName(String createDdl) {
        String upper = createDdl.toUpperCase();
        int idx = upper.indexOf("CREATE TABLE");
        String rest = createDdl.substring(idx + "CREATE TABLE".length()).trim();
        int paren = rest.indexOf('(');
        String name = paren > 0 ? rest.substring(0, paren).trim() : rest.trim();
        return name;
    }

    private NopMetaCatalog findCatalogRow(String metaTableId) {
        IEntityDao<NopMetaCatalog> dao = daoProvider.daoFor(NopMetaCatalog.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaCatalog.PROP_NAME_metaTableId, metaTableId));
        return dao.findFirstByQuery(q);
    }

    private long countCatalogRows(String metaTableId) {
        IEntityDao<NopMetaCatalog> dao = daoProvider.daoFor(NopMetaCatalog.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaCatalog.PROP_NAME_metaTableId, metaTableId));
        return dao.countByQuery(q);
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

    private void seedExternalTable(String dbUrl, String ddl) throws Exception {
        // 真实建连到外部 H2 内存库，建一张表，关闭后由 DB_CLOSE_DELAY=-1 保活供 sync 扫描
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute(ddl);
        }
    }

    private NopMetaTable findExternalTable(IEntityDao<NopMetaTable> tableDao, String tableName) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        return tableDao.findFirstByQuery(q);
    }

    /** 查找所有 external 同名表（多 schema 同名场景）。 */
    private List<NopMetaTable> findAllExternalTables(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        return tableDao.findAllByQuery(q);
    }

    private long countExternalTables(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        return tableDao.countByQuery(q);
    }

    private NopMetaModule findSystemExternalModule() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, "nop/meta-external"));
        return moduleDao.findFirstByQuery(q);
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

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
