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
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationEntity;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.reconciliation.IReconciliationProcessor;
import io.nop.metadata.service.reconciliation.LocalReconciliationProcessor;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证对账执行 + 匹配服务 + 人工确认（设计 08-reconciliation.md §3.2/§3.3/§3.4，plan 0900-2 Phase 2）。
 *
 * <p>Anti-Hollow：executeReconciliation 端到端用真实 external 表行数据（H2 造数 → queryTableData 取 items）
 * + 真实候选实体（NopMetaReconciliationEntity 播种）→ LocalReconciliationProcessor 匹配 → D5 阈值判定 →
 * Result 写入，断言 statistics/details 反映真实匹配（MATCHED/UNMATCHED/MULTIPLE 计数与种子一致，非空壳）。
 *
 * <p>覆盖：EXACT/FUZZY 匹配、阈值上/下、UNMATCHED、MULTIPLE、autoMatch=false→MULTIPLE、人工确认单条/批量、
 * 失败路径（config/columnName/越界）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaReconciliationBizModel extends JunitBaseTestCase {

    public TestNopMetaReconciliationBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    LocalReconciliationProcessor localReconciliationService;

    // ===== LocalReconciliationProcessor 单元（候选检索 + score） =====

    /** EXACT：完全相等 score=1.0，否则不进候选。空候选返回空列表（不静默伪造）。 */
    @Test
    public void testLocalServiceExactScoring() {
        seedCandidate("re-svc-exact-1", "Q1", "Microsoft", "company", "wikidata");
        seedCandidate("re-svc-exact-2", "Q2", "Apple", "company", "wikidata");

        List<IReconciliationProcessor.ReconciliationCandidate> matched =
                localReconciliationService.reconcile("Microsoft", "company", "wikidata", "exact", 10);
        assertEquals(1, matched.size(), "exact match Microsoft must return 1 candidate");
        assertEquals(1.0, matched.get(0).getScore(), 0.0001, "exact match score must be 1.0");

        List<IReconciliationProcessor.ReconciliationCandidate> none =
                localReconciliationService.reconcile("NonExistent", "company", "wikidata", "exact", 10);
        assertTrue(none.isEmpty(), "no exact match must return empty list (no fake candidates)");
    }

    /** FUZZY：levenshtein 归一化相似度。空候选池返回空列表。 */
    @Test
    public void testLocalServiceFuzzyScoring() {
        seedCandidate("re-svc-fuzzy-1", "Q1", "Microsoft", "company", "wikidata");
        // "Microsoft" 完全相等 → 1.0
        List<IReconciliationProcessor.ReconciliationCandidate> exactLike =
                localReconciliationService.reconcile("Microsoft", "company", "wikidata", "fuzzy", 10);
        assertEquals(1, exactLike.size());
        assertEquals(1.0, exactLike.get(0).getScore(), 0.0001);

        // "Microsoftx" 拼写差异 → 距离 1 / maxLen 10 = 0.9
        List<IReconciliationProcessor.ReconciliationCandidate> fuzzyLike =
                localReconciliationService.reconcile("Microsoftx", "company", "wikidata", "fuzzy", 10);
        assertEquals(1, fuzzyLike.size());
        assertEquals(0.9, fuzzyLike.get(0).getScore(), 0.0001, "fuzzy similarity must be ~0.9");
    }

    /** 候选池为空 → 返回空列表（不静默伪造候选）。 */
    @Test
    public void testLocalServiceEmptyCandidatePool() {
        List<IReconciliationProcessor.ReconciliationCandidate> matched =
                localReconciliationService.reconcile("anything", "company", "empty-space", "exact", 10);
        assertTrue(matched.isEmpty(), "empty candidate pool must return empty list");
    }

    // ===== executeReconciliation 端到端（external 表 + 真实候选） =====

    /** EXACT 端到端：Microsoft/Apple 匹配，UnknownCo 无候选。statistics 反映真实匹配。 */
    @Test
    public void testExecuteExactMatching() throws Exception {
        String tableId = prepareExternalTableWithData("jdbc:h2:mem:recon_exact;DB_CLOSE_DELAY=-1",
                "qs_recon_exact", "EXT_RECON_EXACT",
                "CREATE TABLE EXT_RECON_EXACT (id INT NOT NULL, name VARCHAR(50))",
                "INSERT INTO EXT_RECON_EXACT VALUES (1, 'Microsoft')",
                "INSERT INTO EXT_RECON_EXACT VALUES (2, 'Apple')",
                "INSERT INTO EXT_RECON_EXACT VALUES (3, 'UnknownCo')");
        seedCandidate("re-exact-c1", "Q1", "Microsoft", "company", "wikidata");
        seedCandidate("re-exact-c2", "Q2", "Apple", "company", "wikidata");

        String configId = saveConfig("rc-exact", tableId, "NAME", "exact", true, 0.8);
        GraphQLResponseBean resp = executeReconciliation(configId);
        assertFalse(resp.hasError(), "executeReconciliation should succeed: " + resp);

        NopMetaReconciliationResult result = loadLatestResult(configId);
        Map<String, Object> stats = parseJson(result.getStatistics());
        assertEquals(3, ((Number) stats.get("totalRows")).intValue(), "totalRows must be 3");
        assertEquals(2, ((Number) stats.get("matchedRows")).intValue(), "matchedRows must be 2 (Microsoft, Apple)");
        assertEquals(1, ((Number) stats.get("unmatchedRows")).intValue(), "unmatchedRows must be 1 (UnknownCo)");
        assertEquals(0, ((Number) stats.get("multipleMatches")).intValue(), "multipleMatches must be 0");
    }

    /** FUZZY 端到端：Microsoft 完全匹配(1.0)，Microsoftx 模糊匹配(0.9)，阈值 0.85 都 MATCHED。 */
    @Test
    public void testExecuteFuzzyMatchingAboveThreshold() throws Exception {
        String tableId = prepareExternalTableWithData("jdbc:h2:mem:recon_fuzzy;DB_CLOSE_DELAY=-1",
                "qs_recon_fuzzy", "EXT_RECON_FUZZY",
                "CREATE TABLE EXT_RECON_FUZZY (id INT NOT NULL, name VARCHAR(50))",
                "INSERT INTO EXT_RECON_FUZZY VALUES (1, 'Microsoft')",
                "INSERT INTO EXT_RECON_FUZZY VALUES (2, 'Microsoftx')");
        seedCandidate("re-fuzzy-c1", "Q1", "Microsoft", "company", "wikidata");

        String configId = saveConfig("rc-fuzzy", tableId, "NAME", "fuzzy", true, 0.85);
        GraphQLResponseBean resp = executeReconciliation(configId);
        assertFalse(resp.hasError(), "executeReconciliation should succeed: " + resp);

        NopMetaReconciliationResult result = loadLatestResult(configId);
        Map<String, Object> stats = parseJson(result.getStatistics());
        assertEquals(2, ((Number) stats.get("matchedRows")).intValue(),
                "both rows must MATCHED (similarity 1.0 and 0.9 >= 0.85)");
    }

    /** FUZZY 阈值下：Microsoftx(0.9) 单候选但 score < 0.95 阈值 → MULTIPLE（D5 规则）。 */
    @Test
    public void testExecuteFuzzySingleCandidateBelowThresholdMultiple() throws Exception {
        String tableId = prepareExternalTableWithData("jdbc:h2:mem:recon_fuzzy_thr;DB_CLOSE_DELAY=-1",
                "qs_recon_fuzzy_thr", "EXT_RECON_FUZZY_THR",
                "CREATE TABLE EXT_RECON_FUZZY_THR (id INT NOT NULL, name VARCHAR(50))",
                "INSERT INTO EXT_RECON_FUZZY_THR VALUES (1, 'Microsoftx')");
        seedCandidate("re-fuzzy-thr-c1", "Q1", "Microsoft", "company", "wikidata");

        // Microsoftx vs Microsoft: distance 1 / maxLen 10 = 0.9 < 0.95 → 单候选低于阈值 → MULTIPLE
        String configId = saveConfig("rc-fuzzy-thr", tableId, "NAME", "fuzzy", true, 0.95);
        GraphQLResponseBean resp = executeReconciliation(configId);
        assertFalse(resp.hasError(), "executeReconciliation should succeed: " + resp);

        NopMetaReconciliationResult result = loadLatestResult(configId);
        Map<String, Object> stats = parseJson(result.getStatistics());
        assertEquals(1, ((Number) stats.get("multipleMatches")).intValue(),
                "single candidate below threshold must be MULTIPLE");
        assertEquals(0, ((Number) stats.get("matchedRows")).intValue());
    }

    /** MULTIPLE：EXACT 策略下同名的两个候选 → 2 候选 → MULTIPLE。 */
    @Test
    public void testExecuteMultipleCandidates() throws Exception {
        String tableId = prepareExternalTableWithData("jdbc:h2:mem:recon_multi;DB_CLOSE_DELAY=-1",
                "qs_recon_multi", "EXT_RECON_MULTI",
                "CREATE TABLE EXT_RECON_MULTI (id INT NOT NULL, name VARCHAR(50))",
                "INSERT INTO EXT_RECON_MULTI VALUES (1, 'Apple')");
        // 两个同名候选（不同 entityId）
        seedCandidate("re-multi-c1", "Q1", "Apple", "company", "wikidata");
        seedCandidate("re-multi-c2", "Q2", "Apple", "company", "wikidata");

        String configId = saveConfig("rc-multi", tableId, "NAME", "exact", true, 0.8);
        GraphQLResponseBean resp = executeReconciliation(configId);
        assertFalse(resp.hasError(), "executeReconciliation should succeed: " + resp);

        NopMetaReconciliationResult result = loadLatestResult(configId);
        Map<String, Object> stats = parseJson(result.getStatistics());
        assertEquals(1, ((Number) stats.get("multipleMatches")).intValue(),
                "2 candidates with same name must be MULTIPLE");
    }

    /** autoMatch=false：即使精确匹配也一律 → MULTIPLE（交人工）。 */
    @Test
    public void testExecuteAutoMatchFalseAlwaysMultiple() throws Exception {
        String tableId = prepareExternalTableWithData("jdbc:h2:mem:recon_auto;DB_CLOSE_DELAY=-1",
                "qs_recon_auto", "EXT_RECON_AUTO",
                "CREATE TABLE EXT_RECON_AUTO (id INT NOT NULL, name VARCHAR(50))",
                "INSERT INTO EXT_RECON_AUTO VALUES (1, 'Microsoft')");
        seedCandidate("re-auto-c1", "Q1", "Microsoft", "company", "wikidata");

        String configId = saveConfig("rc-auto", tableId, "NAME", "exact", false, 0.8);
        GraphQLResponseBean resp = executeReconciliation(configId);
        assertFalse(resp.hasError(), "executeReconciliation should succeed: " + resp);

        NopMetaReconciliationResult result = loadLatestResult(configId);
        Map<String, Object> stats = parseJson(result.getStatistics());
        assertEquals(1, ((Number) stats.get("multipleMatches")).intValue(),
                "autoMatch=false with candidates must be MULTIPLE (manual)");
        assertEquals(0, ((Number) stats.get("matchedRows")).intValue(), "no auto MATCHED when autoMatch=false");
    }

    /** 空候选→UNMATCHED 不静默 pass 整体（体现在结果，非异常）。 */
    @Test
    public void testExecuteNoCandidatesUnmatched() throws Exception {
        String tableId = prepareExternalTableWithData("jdbc:h2:mem:recon_empty;DB_CLOSE_DELAY=-1",
                "qs_recon_empty", "EXT_RECON_EMPTY",
                "CREATE TABLE EXT_RECON_EMPTY (id INT NOT NULL, name VARCHAR(50))",
                "INSERT INTO EXT_RECON_EMPTY VALUES (1, 'Nobody')");
        // 无候选播种

        String configId = saveConfig("rc-empty", tableId, "NAME", "exact", true, 0.8);
        GraphQLResponseBean resp = executeReconciliation(configId);
        assertFalse(resp.hasError(), "executeReconciliation should succeed (UNMATCHED is not an error): " + resp);

        NopMetaReconciliationResult result = loadLatestResult(configId);
        Map<String, Object> stats = parseJson(result.getStatistics());
        assertEquals(1, ((Number) stats.get("unmatchedRows")).intValue(), "no candidates must be UNMATCHED");
        assertEquals(0.0, ((Number) stats.get("matchRate")).doubleValue(), 0.0001);
    }

    // ===== 人工确认 =====

    /** 单条确认：更新 details[rowIndex].status=MANUAL + selectedId。 */
    @Test
    public void testConfirmMatchSingle() throws Exception {
        NopMetaReconciliationResult result = prepareResultWithDetails(
                "[{\"rowIndex\":0,\"originalValue\":\"A\",\"status\":\"UNMATCHED\",\"candidates\":[]},"
                        + "{\"rowIndex\":1,\"originalValue\":\"B\",\"status\":\"MULTIPLE\",\"candidates\":[]}]");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaReconciliationResult__confirmMatch(resultId: \"" + result.getResultId()
                        + "\", rowIndex: 0, selectedEntityId: \"Q-CHOSEN\") { resultId } }");
        assertFalse(resp.hasError(), "confirmMatch should succeed: " + resp);

        NopMetaReconciliationResult updated = loadResult(result.getResultId());
        List<Object> details = parseJsonList(updated.getDetails());
        @SuppressWarnings("unchecked")
        Map<String, Object> row0 = (Map<String, Object>) details.get(0);
        assertEquals("MANUAL", row0.get("status"), "row 0 status must be MANUAL after confirm");
        assertEquals("Q-CHOSEN", row0.get("selectedId"), "selectedId must be written");
        // 其它行不变
        @SuppressWarnings("unchecked")
        Map<String, Object> row1 = (Map<String, Object>) details.get(1);
        assertEquals("MULTIPLE", row1.get("status"), "row 1 status must be unchanged");
    }

    /** 批量确认：多个 rowIndex 同时更新。 */
    @Test
    public void testConfirmMatchBatch() throws Exception {
        NopMetaReconciliationResult result = prepareResultWithDetails(
                "[{\"rowIndex\":0,\"originalValue\":\"A\",\"status\":\"UNMATCHED\",\"candidates\":[]},"
                        + "{\"rowIndex\":1,\"originalValue\":\"B\",\"status\":\"MULTIPLE\",\"candidates\":[]}]");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaReconciliationResult__batchConfirmMatches(resultId: \"" + result.getResultId()
                        + "\", selections: [{rowIndex: 0, selectedEntityId: \"Q1\"},"
                        + "{rowIndex: 1, selectedEntityId: \"Q2\"}]) { resultId } }");
        assertFalse(resp.hasError(), "batchConfirmMatches should succeed: " + resp);

        NopMetaReconciliationResult updated = loadResult(result.getResultId());
        List<Object> details = parseJsonList(updated.getDetails());
        @SuppressWarnings("unchecked")
        Map<String, Object> row0 = (Map<String, Object>) details.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> row1 = (Map<String, Object>) details.get(1);
        assertEquals("MANUAL", row0.get("status"));
        assertEquals("Q1", row0.get("selectedId"));
        assertEquals("MANUAL", row1.get("status"));
        assertEquals("Q2", row1.get("selectedId"));
    }

    // ===== 失败路径（显式失败，不静默跳过） =====

    /** config 不存在 → 显式失败。 */
    @Test
    public void testExecuteConfigNotFound() {
        GraphQLResponseBean resp = executeReconciliation("__nope_config__");
        assertTrue(resp.hasError(), "non-existent config must error (no NPE)");
    }

    /** columnName 非法（不在可用字段集合）→ 显式失败。 */
    @Test
    public void testExecuteColumnNameInvalid() throws Exception {
        String tableId = prepareExternalTableWithData("jdbc:h2:mem:recon_badcol;DB_CLOSE_DELAY=-1",
                "qs_recon_badcol", "EXT_RECON_BADCOL",
                "CREATE TABLE EXT_RECON_BADCOL (id INT NOT NULL, name VARCHAR(50))",
                "INSERT INTO EXT_RECON_BADCOL VALUES (1, 'X')");
        String configId = saveConfig("rc-badcol", tableId, "__NON_EXISTENT_COL__", "exact", true, 0.8);
        GraphQLResponseBean resp = executeReconciliation(configId);
        assertTrue(resp.hasError(), "invalid columnName must explicitly fail (not silently skip)");
    }

    /** 越界 rowIndex → 显式失败（单条确认）。 */
    @Test
    public void testConfirmMatchRowIndexOutOfRange() {
        NopMetaReconciliationResult result = prepareResultWithDetails(
                "[{\"rowIndex\":0,\"originalValue\":\"A\",\"status\":\"UNMATCHED\",\"candidates\":[]}]");
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaReconciliationResult__confirmMatch(resultId: \"" + result.getResultId()
                        + "\", rowIndex: 99, selectedEntityId: \"Q\") { resultId } }");
        assertTrue(resp.hasError(), "out-of-range rowIndex must explicitly fail (not silently ignored)");
    }

    /** result 不存在 → 显式失败。 */
    @Test
    public void testConfirmMatchResultNotFound() {
        GraphQLResponseBean resp = execute(
                "mutation { NopMetaReconciliationResult__confirmMatch(resultId: \"__nope_result__\","
                        + " rowIndex: 0, selectedEntityId: \"Q\") { resultId } }");
        assertTrue(resp.hasError(), "non-existent result must error (no NPE)");
    }

    // ===== helpers =====

    private GraphQLResponseBean executeReconciliation(String configId) {
        return execute("mutation { NopMetaReconciliationConfig__executeReconciliation(configId: \""
                + configId + "\") { resultId configId } }");
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return (Map<String, Object>) io.nop.core.lang.json.JsonTool.parse(json);
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseJsonList(String json) {
        return (List<Object>) io.nop.core.lang.json.JsonTool.parse(json);
    }

    private NopMetaReconciliationResult loadLatestResult(String configId) {
        IEntityDao<NopMetaReconciliationResult> dao = daoProvider.daoFor(NopMetaReconciliationResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaReconciliationResult.PROP_NAME_configId, configId));
        q.addOrderField(NopMetaReconciliationResult.PROP_NAME_executeTime, true);
        q.setLimit(1);
        List<NopMetaReconciliationResult> list = dao.findAllByQuery(q);
        Assertions.assertFalse(list.isEmpty(), "result must be written for config " + configId);
        return list.get(0);
    }

    private NopMetaReconciliationResult loadResult(String resultId) {
        return daoProvider.daoFor(NopMetaReconciliationResult.class).getEntityById(resultId);
    }

    private NopMetaReconciliationResult prepareResultWithDetails(String details) {
        IEntityDao<NopMetaReconciliationResult> dao = daoProvider.daoFor(NopMetaReconciliationResult.class);
        NopMetaReconciliationResult r = dao.newEntity();
        r.setConfigId("rc-test-result");
        r.setMetaTableId("dummy-table-id");
        r.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        r.setStatistics("{\"totalRows\":2,\"matchedRows\":0,\"unmatchedRows\":1,\"multipleMatches\":1,\"matchRate\":0.0}");
        r.setDetails(details);
        r.setVersion(1L);
        r.setCreatedBy("autotest");
        r.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        r.setCreateTime(now);
        r.setUpdateTime(now);
        dao.saveEntity(r);
        return r;
    }

    private String saveConfig(String configId, String metaTableId, String columnName,
                              String matchStrategy, boolean autoMatch, double threshold) {
        IEntityDao<NopMetaReconciliationConfig> dao = daoProvider.daoFor(NopMetaReconciliationConfig.class);
        NopMetaReconciliationConfig c = dao.newEntity();
        c.setConfigId(configId);
        c.setConfigName(configId + "-name");
        c.setDisplayName(configId + "-name");
        c.setMetaTableId(metaTableId);
        c.setColumnName(columnName);
        c.setIdentifierSpace("wikidata");
        c.setTargetEntityType("company");
        c.setMatchStrategy(matchStrategy);
        c.setAutoMatch(autoMatch ? (byte) 1 : (byte) 0);
        c.setAutoMatchThreshold(threshold);
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
        return configId;
    }

    private void seedCandidate(String reconEntityId, String entityId, String entityName,
                               String entityType, String identifierSpace) {
        IEntityDao<NopMetaReconciliationEntity> dao = daoProvider.daoFor(NopMetaReconciliationEntity.class);
        NopMetaReconciliationEntity e = dao.newEntity();
        e.setReconEntityId(reconEntityId);
        e.setEntityId(entityId);
        e.setEntityName(entityName);
        e.setEntityType(entityType);
        e.setIdentifierSpace(identifierSpace);
        e.setLastSyncedAt(new Timestamp(System.currentTimeMillis()));
        e.setVersion(1L);
        e.setCreatedBy("autotest");
        e.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        e.setCreateTime(now);
        e.setUpdateTime(now);
        dao.saveEntity(e);
    }

    private String prepareExternalTableWithData(String dbUrl, String querySpace, String tableName,
                                                String createDdl, String... inserts) throws Exception {
        seedH2(dbUrl, createDdl, inserts);
        saveDataSource("ds-" + querySpace, querySpace, dbUrl);
        GraphQLResponseBean syncResp = execute(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-" + querySpace
                        + "\", schemaPattern: \"PUBLIC\") }");
        assertFalse(syncResp.hasError(), "sync should not error: " + syncResp);
        return findExternalTableId(tableName);
    }

    private void seedH2(String dbUrl, String createDdl, String... inserts) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute(createDdl);
            for (String ins : inserts) {
                st.execute(ins);
            }
        }
    }

    private void saveDataSource(String id, String querySpace, String dbUrl) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    private String findExternalTableId(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        NopMetaTable t = tableDao.findFirstByQuery(q);
        Assertions.assertNotNull(t, "external table " + tableName + " must be synced");
        return t.getMetaTableId();
    }
}
