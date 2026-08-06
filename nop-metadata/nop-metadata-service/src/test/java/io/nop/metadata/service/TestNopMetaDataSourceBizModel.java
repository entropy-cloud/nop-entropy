package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModelChangedEvent;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.entity.NopMetaDataSourceBizModel;
import io.nop.metadata.service.event.MetaModelChangedEventPublisher;
import io.nop.metadata.service.sync.ExternalColumnInfo;
import io.nop.metadata.service.sync.ExternalTableInfo;
import io.nop.metadata.service.sync.ExternalTableStructureReader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataSourceBizModel extends JunitBaseTestCase {

    public TestNopMetaDataSourceBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopMetaDataSourceBizModel dataSourceBizModel;

    @Test
    public void testConnectionSuccessRealConnect() {
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
        assertTrue(data.contains("databaseProductName=H2"),
                "should contain real databaseProductName=H2 from live metadata: " + data);
        assertTrue(data.contains("databaseProductVersion="),
                "should contain databaseProductVersion: " + data);
    }

    @Test
    public void testConnectionNotFound() {
        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"__not_exist__\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "non-existent dataSourceId must error (no NPE): " + response);
    }

    @Test
    public void testConnectionDisabledRejected() {
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
        saveDataSource("ds-http", "qs_http", "http", "ACTIVE", "{}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-http\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "non-jdbc datasource must error (NopException ERR_DATASOURCE_TYPE_NOT_SUPPORTED): " + response);
    }

    @Test
    public void testConnectionConfigMissingFastFail() {
        saveDataSource("ds-bad-cfg", "qs_bad_cfg", "jdbc", "ACTIVE",
                "{\"username\":\"sa\",\"password\":\"\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-bad-cfg\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "missing required jdbc field (jdbcUrl) must fast-fail: " + response);
    }

    /**
     * AR-23⑤（R8.2）消费者兼容实证：ExternalColumnInfo.precision/scale 为 null 时
     * serializeColumns 输出合法 JSON（precision/scale 为 JSON null），非数值被伪造为 0。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testSerializeColumnsNullPrecisionScaleOutputsJsonNull() throws Exception {
        ExternalColumnInfo col = new ExternalColumnInfo();
        col.setColumnName("C1");
        col.setDataType("VARCHAR");
        col.setPrecision(null);
        col.setScale(null);
        col.setNullable(true);
        col.setOrdinal(1);
        col.setRemark("col");

        String json = serializeColumnsReflectively(List.of(col));
        assertTrue(json.contains("\"precision\":null"),
                "null precision must serialize as JSON null (was 0 before AR-23⑤): " + json);
        assertTrue(json.contains("\"scale\":null"),
                "null scale must serialize as JSON null (was 0 before AR-23⑤): " + json);
        // JSON 合法性：可完整回解析且语义保持
        List<Map<String, Object>> parsed = (List<Map<String, Object>>) JsonTool.parse(json);
        assertEquals(1, parsed.size());
        assertNull(parsed.get(0).get("precision"));
        assertNull(parsed.get(0).get("scale"));
        assertEquals(1, parsed.get(0).get("ordinal"));
    }

    /** 非 NULL 精度不受影响（既有数值语义保持）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testSerializeColumnsNonNullPrecisionScaleKept() throws Exception {
        ExternalColumnInfo col = new ExternalColumnInfo();
        col.setColumnName("C2");
        col.setDataType("DECIMAL");
        col.setPrecision(10);
        col.setScale(2);
        col.setNullable(false);
        col.setOrdinal(2);

        String json = serializeColumnsReflectively(List.of(col));
        assertTrue(json.contains("\"precision\":10"), "non-null precision must be kept: " + json);
        assertTrue(json.contains("\"scale\":2"), "non-null scale must be kept: " + json);
        List<Map<String, Object>> parsed = (List<Map<String, Object>>) JsonTool.parse(json);
        assertEquals(10, ((Number) parsed.get(0).get("precision")).intValue());
        assertEquals(2, ((Number) parsed.get(0).get("scale")).intValue());
    }

    private static String serializeColumnsReflectively(List<ExternalColumnInfo> columns) throws Exception {
        // serializeColumns 为 private（沿 TestMetaQualityRuleExecutorCustomSqlSandbox 反射先例）
        Method m = NopMetaDataSourceBizModel.class.getDeclaredMethod("serializeColumns", List.class);
        m.setAccessible(true);
        return (String) m.invoke(new NopMetaDataSourceBizModel(), columns);
    }

    // ===== AR-17（R8.4b）：syncExternalTables 契约——部分持久化 + scan 级失败事件面 =====

    /**
     * scan 级失败判别性测试（失败注入裁定 = 反射替换 private final structureReader——structureReader 为
     * private final 内联 new，无注入缝；Java 21 允许 setAccessible + set 实例 final 字段）：
     * <ul>
     *   <li>scan 级失败（structureReader.read 抛）→ 原始异常向上传播（fail-loud，不吞、不被事件发布掩盖）</li>
     *   <li><b>事件仍发布且独立提交存活</b>：修复前事件缺失（publishEventWithSnapshots 在 withConnection
     *       之后不达）——实测 red；修复后事件行在 REQUIRES_NEW 独立事务中提交，重抛后仍可查询</li>
     * </ul>
     */
    @Test
    public void testSyncExternalTablesScanFailurePublishesEventAndRethrows() throws Exception {
        saveDataSource("ds-scan-fail", "qs_scan_fail", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_scan_fail;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"sa\",\"password\":\"\",\"driverClassName\":\"org.h2.Driver\"}");

        Field structureReaderField = NopMetaDataSourceBizModel.class.getDeclaredField("structureReader");
        structureReaderField.setAccessible(true);
        ExternalTableStructureReader original =
                (ExternalTableStructureReader) structureReaderField.get(dataSourceBizModel);
        ExternalTableStructureReader failingReader = new ExternalTableStructureReader() {
            @Override
            public List<ExternalTableInfo> read(Connection conn, DatabaseMetaData metaData, String schemaPattern) {
                throw new NopMetadataException(NopMetadataErrors.ERR_EXTERNAL_TABLE_SCAN_FAILED)
                        .param(NopMetadataErrors.ARG_DATABASE_PRODUCT_NAME, "H2")
                        .param(NopMetadataErrors.ARG_ERROR, "simulated scan failure");
            }
        };
        structureReaderField.set(dataSourceBizModel, failingReader);
        try {
            NopException ex = assertThrows(NopException.class,
                    () -> dataSourceBizModel.syncExternalTables("ds-scan-fail", "PUBLIC", null),
                    "scan-level failure must propagate (fail-loud, no silent swallow)");
            assertEquals(NopMetadataErrors.ERR_EXTERNAL_TABLE_SCAN_FAILED.getErrorCode(), ex.getErrorCode(),
                    "original scan failure must surface (not masked by event publish)");

            List<NopMetaModelChangedEvent> events = findSyncEvents("ds-scan-fail");
            assertEquals(1, events.size(),
                    "scan failure must still publish an event row surviving the rethrow "
                            + "(before AR-17 the event was missing): " + events);
            assertEquals(MetaModelChangedEventPublisher.CHANGE_SOURCE_SYNC, events.get(0).getChangeSource());
            assertEquals(_NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_UPDATED, events.get(0).getEventType());
            assertEquals("ds-scan-fail", events.get(0).getEntityId());
        } finally {
            // 共享 bean 恢复原 reader（防测试间泄漏）
            structureReaderField.set(dataSourceBizModel, original);
        }
    }

    /**
     * per-table 部分失败 keep-green（真实入口完整走通，Minimum Rules #22）：两表同步，一表因 REMARK
     * 超 description 列长（VARCHAR(1000)）失败 → 已同步表持久化（REQUIRES_NEW 独立提交存活，不被失败表
     * clearSession 影响）+ 失败表进 errors 不中断整批 + 事件正常发布。
     */
    @Test
    public void testSyncExternalTablesPerTableFailureIsolatedAndEventPublished() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_sync_ptf;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_pt_ok (id INT NOT NULL)", "INSERT INTO ext_pt_ok VALUES (1)");
        // REMARK 3000 字符 > description VARCHAR(1000) → upsert 时 DB 报 value-too-long
        seedTable(dbUrl, "CREATE TABLE ext_pt_bad (id INT NOT NULL)",
                "COMMENT ON TABLE ext_pt_bad IS '" + "x".repeat(3000) + "'");
        saveDataSource("ds-pt-fail", "qs_pt_fail", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-pt-fail\", "
                        + "schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message } } }");
        assertFalse(resp.hasError(), "per-table failure must not globally error: " + resp);

        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("syncedTableCount=1"),
                "OK table must sync (syncedTableCount=1): " + data);
        assertTrue(data.contains("EXT_PT_BAD"),
                "failing table must be recorded in errors (not silently skipped): " + data);

        // 已同步表持久化（per-table REQUIRES_NEW 独立提交，不被失败表 clearSession 影响）
        assertNotNull(findExternalTable("EXT_PT_OK"), "OK table must be persisted despite sibling failure");
        assertNull(findExternalTable("EXT_PT_BAD"), "failing table must not be persisted");

        // 成功路径事件正常发布
        List<NopMetaModelChangedEvent> events = findSyncEvents("ds-pt-fail");
        assertEquals(1, events.size(), "success-path sync must publish one event: " + events);
    }

    private List<NopMetaModelChangedEvent> findSyncEvents(String entityId) {
        IEntityDao<NopMetaModelChangedEvent> dao = daoProvider.daoFor(NopMetaModelChangedEvent.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModelChangedEvent.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaModelChangedEvent.PROP_NAME_changeSource,
                MetaModelChangedEventPublisher.CHANGE_SOURCE_SYNC));
        return dao.findAllByQuery(q);
    }

    private NopMetaTable findExternalTable(String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        return dao.findFirstByQuery(q);
    }

    private void seedTable(String dbUrl, String createDdl, String... extraStatements) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute(createDdl);
            for (String stmt : extraStatements) {
                st.execute(stmt);
            }
        }
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
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
}
