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
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-h2-ok\") }");
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
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"__not_exist__\") }");
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
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-disabled\") }");
        assertTrue(response.hasError(),
                "DISABLED datasource must be rejected (no silent pass): " + response);
    }

    @Test
    public void testConnectionNonJdbcThrows() {
        // 非 jdbc 类型（http）必须显式抛 UnsupportedOperationException（不静默返回成功）
        saveDataSource("ds-http", "qs_http", "http", "ACTIVE", "{}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-http\") }");
        assertTrue(response.hasError(),
                "non-jdbc datasource must error (UnsupportedOperationException): " + response);
    }

    @Test
    public void testConnectionConfigMissingFastFail() {
        // connectionConfig 缺 jdbc 必填字段 jdbcUrl 必须快速失败（不静默建连）
        saveDataSource("ds-bad-cfg", "qs_bad_cfg", "jdbc", "ACTIVE",
                "{\"username\":\"sa\",\"password\":\"\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-bad-cfg\") }");
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
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-write\", schemaPattern: \"PUBLIC\") }");
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
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-idem\", schemaPattern: \"PUBLIC\") }");
        assertFalse(r1.hasError(), "first sync should not error: " + r1);
        long countAfterFirst = countExternalTables("EXT_EMP");
        assertEquals(1L, countAfterFirst, "exactly one EXT_EMP after first sync: " + countAfterFirst);

        // 第二次同步同一数据源（幂等，不追加）
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-idem\", schemaPattern: \"PUBLIC\") }");
        assertFalse(r2.hasError(), "second sync should not error: " + r2);
        long countAfterSecond = countExternalTables("EXT_EMP");
        assertEquals(1L, countAfterSecond,
                "idempotent: EXT_EMP count must stay 1 after second sync, got: " + countAfterSecond);
        assertTrue(String.valueOf(r2.getData()).contains("syncedTableCount=1"),
                "second sync still reports syncedTableCount=1 (update, not skip): " + r2.getData());
    }

    /** DISABLED 数据源同步必须显式拒绝（不静默通过）。 */
    @Test
    public void testSyncExternalTablesDisabledRejected() {
        saveDataSource("ds-sync-disabled", "qs_sync_disabled", "jdbc", "DISABLED",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_sync_disabled;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"sa\",\"password\":\"\",\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-disabled\") }");
        assertTrue(response.hasError(),
                "DISABLED datasource sync must be rejected (no silent pass): " + response);
    }

    /** 非 jdbc 类型同步必须显式失败（不静默返回成功）。 */
    @Test
    public void testSyncExternalTablesNonJdbcThrows() {
        saveDataSource("ds-sync-http", "qs_sync_http", "http", "ACTIVE", "{}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-sync-http\") }");
        assertTrue(response.hasError(),
                "non-jdbc datasource sync must error (UnsupportedOperationException): " + response);
    }

    /** 不存在的 dataSourceId 同步必须抛 metadata.datasource-not-found（不 NPE）。 */
    @Test
    public void testSyncExternalTablesNotFound() {
        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"__not_exist__\") }");
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
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-collect\", schemaPattern: \"PUBLIC\") }");
        assertFalse(syncResp.hasError(), "sync should not error: " + syncResp);

        NopMetaTable table = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "EXT_DEPT");
        assertNotNull(table, "EXT_DEPT must be synced before collect");

        // 收集运行时统计（限定 PUBLIC schema）
        GraphQLResponseBean collectResp = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect\", schemaPattern: \"PUBLIC\") }");
        assertFalse(collectResp.hasError(), "collect should not error: " + collectResp);
        assertTrue(String.valueOf(collectResp.getData()).contains("collectedCount=1"),
                "should report collectedCount=1: " + collectResp.getData());

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

        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-collect-ts\", schemaPattern: \"PUBLIC\") }");
        NopMetaTable table = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "EXT_EMP");
        assertNotNull(table, "EXT_EMP must be synced");

        // 第一次收集
        execute("mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-ts\", schemaPattern: \"PUBLIC\") }");
        long countAfterFirst = countCatalogRows(table.getMetaTableId());
        assertEquals(1L, countAfterFirst, "exactly 1 catalog row after first collect: " + countAfterFirst);

        // 第二次收集同一数据源（时序追加，不覆盖）
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-ts\", schemaPattern: \"PUBLIC\") }");
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

        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-collect-def\", schemaPattern: \"PUBLIC\") }");
        NopMetaTable table = findExternalTable(daoProvider.daoFor(NopMetaTable.class), "EXT_DEF");
        assertNotNull(table, "EXT_DEF must be synced");

        // 不传 schemaPattern：COUNT 用 <tableName> 依赖连接默认 schema（H2 默认 PUBLIC）
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-def\") }");
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
        execute("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-collect-err\", schemaPattern: \"PUBLIC\") }");
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
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-err\", schemaPattern: \"PUBLIC\") }");
        assertFalse(resp.hasError(), "collect should not globally error (batched): " + resp);
        String data = String.valueOf(resp.getData());
        // EXT_OK 收集成功，EXT_GONE 失败进 errors
        assertTrue(data.contains("collectedCount=1"),
                "EXT_OK must be collected (collectedCount=1): " + data);
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
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-disabled\") }");
        assertTrue(response.hasError(),
                "DISABLED datasource collect must be rejected (no silent pass): " + response);
    }

    /** 非 jdbc 类型收集必须显式失败（不静默返回成功）。 */
    @Test
    public void testCollectCatalogNonJdbcThrows() {
        saveDataSource("ds-collect-http", "qs_collect_http", "http", "ACTIVE", "{}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"ds-collect-http\") }");
        assertTrue(response.hasError(),
                "non-jdbc datasource collect must error (UnsupportedOperationException): " + response);
    }

    /** 不存在的 dataSourceId 收集必须抛 metadata.datasource-not-found（不 NPE）。 */
    @Test
    public void testCollectCatalogNotFound() {
        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__collectCatalog(dataSourceId: \"__not_exist__\") }");
        assertTrue(response.hasError(),
                "non-existent dataSourceId collect must error (no NPE): " + response);
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
