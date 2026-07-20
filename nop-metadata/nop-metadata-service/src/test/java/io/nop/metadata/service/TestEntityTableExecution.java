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
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 端到端验证：entity 类型表 × Catalog/Quality/Profiling 三大执行器（架构基线 §4.4.3 D1/D4）。
 *
 * <p>Anti-Hollow：所有成功路径用本模块已注册实体（{@code io.nop.metadata.dao.entity.NopMetaModule}）作 fixture，
 * 经 BizModel action 入口（collectCatalogForTable / executeQualityRule / profileTable）→ table-reference 解析 →
 * 平台 IJdbcTransaction 取 Connection → executor 原样执行（不经 EQL）→ 产出真实统计值。
 *
 * <p>关键断言：entity 路径 profiling 产出真实 STDDEV_SAMP / median / percentile / distribution（非 null、非伪造），
 * 证明 entity 路径统计能力与 external 完全一致、无降级（D4 能力边界）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestEntityTableExecution extends JunitBaseTestCase {

    public TestEntityTableExecution() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    /** 已注册的平台实体名 + 物理表名（本模块自身实体）。 */
    private static final String REGISTERED_ENTITY_NAME = "io.nop.metadata.dao.entity.NopMetaModule";
    private static final String REGISTERED_TABLE_NAME = "nop_meta_module";

    // ===== Catalog × entity =====

    @Test
    public void testEntityCatalogCollectsRowCount() {
        // 先插入若干模块行（保证表非空）
        seedModules(2);
        String tableId = prepareEntityTable();

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataSource__collectCatalogForTable(metaTableId: \"" + tableId + "\") }")));
        assertFalse(resp.hasError(), "entity catalog should not error: " + resp);

        NopMetaCatalog row = findCatalogRow(tableId);
        assertNotNull(row, "catalog row must be written for entity table");
        assertNotNull(row.getRowCount(), "rowCount must be non-null");
        assertTrue(row.getRowCount().longValue() >= 2, "rowCount must reflect real platform table rows");
    }

    // ===== Quality × entity =====

    @Test
    public void testEntityQualityVolume() {
        seedModules(3);
        String tableId = prepareEntityTable();
        saveQualityRule("qr-ent-vol", "volume", "table", tableId, 2.0, "{\"minRows\":2}");

        GraphQLResponseBean resp = execRule("qr-ent-vol");
        assertFalse(resp.hasError(), "entity quality volume should not error: " + resp);
        assertStatus(resp, "PASS");
    }

    @Test
    public void testEntityQualityCustomSqlSupported() {
        // D4：custom_sql 在 entity 路径 supported（raw SQL 跑在物理表上）
        seedModules(1);
        String tableId = prepareEntityTable();
        // custom_sql 返回 0 → 默认 pass
        saveQualityRule("qr-ent-csql", "custom_sql", "table", tableId, null,
                "{\"sql\":\"SELECT COUNT(*) FROM nop_meta_module WHERE 1=0\"}");

        GraphQLResponseBean resp = execRule("qr-ent-csql");
        assertFalse(resp.hasError(), "entity custom_sql should not error: " + resp);
        assertStatus(resp, "PASS");
    }

    // ===== Profiling × entity（核心：STDDEV/median/percentiles/distribution 无降级）=====

    @Test
    public void testEntityProfilingStddevMedianNoDegradation() {
        // 插入 4 行 moduleVersion = 10,20,30,40 → mean=25, stddev≈12.9, median=25
        seedModulesWithVersion(10, 20, 30, 40);
        String tableId = prepareEntityTable();

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__profileTable(metaTableId: \"" + tableId + "\", "
                        + "columns: \"MODULE_VERSION\") }")));
        assertFalse(resp.hasError(), "entity profiling should not error: " + resp);

        NopMetaProfilingResult row = findProfilingResult(tableId);
        assertNotNull(row, "profiling result must be written for entity table");
        Map<String, Object> tableStats = parseMap(row.getTableStats());
        assertTrue(toLong(tableStats.get("rowCount")) >= 4, "rowCount must reflect real platform table rows");

        List<Map<String, Object>> cols = parseList(row.getColumnStats());
        Map<String, Object> versionCol = findColumn(cols, "MODULE_VERSION");
        assertNotNull(versionCol, "MODULE_VERSION column must be profiled on entity table");

        Map<String, Object> numeric = (Map<String, Object>) versionCol.get("numericStats");
        assertNotNull(numeric, "entity path must produce numericStats (no degradation)");
        // 真实 STDDEV_SAMP 非空且 > 0（证明 entity 路径经平台 Connection 执行，不经 EQL——EQL 会丢 STDDEV 函数）
        Double stddev = toDoubleOrNull(numeric.get("stddevValue"));
        assertNotNull(stddev, "STDDEV_SAMP must be real (non-null), proving entity path uses platform Connection not EQL");
        assertTrue(stddev > 0, "STDDEV_SAMP must be positive (real data variation): " + stddev);
        // median 非空（in-app 计算，证明 entity 路径全统计能力与 external 一致、无降级）
        assertNotNull(numeric.get("medianValue"), "median must be real (in-app computation, not unavailable)");
        Map<String, Object> pct = (Map<String, Object>) numeric.get("percentiles");
        assertNotNull(pct, "percentiles must be computed (not unavailable)");
        assertNotNull(pct.get("50"), "p50 must be real");
    }

    // ===== entity 前置校验显式失败 =====

    @Test
    public void testEntityProfilingUnregisteredEntityFails() {
        // 实体未注册 → 显式失败（不静默空集）
        NopMetaEntity entity = saveMetaEntity("not.a.registered.entity", "NOP_FAKE_TBL");
        String tableId = saveEntityTable(entity.getMetaEntityId());

        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaTable__profileTable(metaTableId: \"" + tableId + "\") }")));
        assertTrue(resp.hasError(), "unregistered entity must explicitly fail (no silent empty set): " + resp);
    }

    // ===== helpers =====

    /** 插入 N 个 NopMetaModule 行（经 ORM，保证平台库有真实数据）。 */
    private void seedModules(int count) {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        for (int i = 0; i < count; i++) {
            NopMetaModule m = dao.newEntity();
            m.setModuleId("nop/test-ent-" + System.nanoTime() + "-" + i);
            m.setModuleName("test-ent-" + i);
            m.setDisplayName("test-entity-" + i);
            m.setModuleVersion(1L);
            m.setStatus("RELEASED");
            m.setImportedAt(new Timestamp(System.currentTimeMillis()));
            dao.saveEntity(m);
        }
        dao.flushSession();
    }

    /** 插入指定 moduleVersion 值的 NopMetaModule 行（用于 profiling 数值统计）。 */
    private void seedModulesWithVersion(long... versions) {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        for (long v : versions) {
            NopMetaModule m = dao.newEntity();
            m.setModuleId("nop/test-ver-" + System.nanoTime() + "-" + v);
            m.setModuleName("test-ver-" + v);
            m.setDisplayName("ver-" + v);
            m.setModuleVersion(v);
            m.setStatus("RELEASED");
            m.setImportedAt(new Timestamp(System.currentTimeMillis()));
            dao.saveEntity(m);
        }
        dao.flushSession();
    }

    /** 创建 entity 类型 NopMetaTable，baseEntityId 指向已注册的 NopMetaModule 实体。返回 metaTableId。 */
    private String prepareEntityTable() {
        NopMetaEntity entity = saveMetaEntity(REGISTERED_ENTITY_NAME, REGISTERED_TABLE_NAME);
        return saveEntityTable(entity.getMetaEntityId());
    }

    private String saveEntityTable(String baseEntityId) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureModuleId());
        t.setTableName("ENT_T_" + System.nanoTime());
        t.setDisplayName("entity-table");
        t.setTableType("entity");
        t.setBaseEntityId(baseEntityId);
        t.setVersion(1L);
        dao.saveEntity(t);
        return t.getMetaTableId();
    }

    private NopMetaEntity saveMetaEntity(String entityName, String tableName) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity e = dao.newEntity();
        e.setOrmModelId(ensureOrmModelId());
        e.setEntityName(entityName);
        e.setDisplayName(entityName);
        e.setTableName(tableName);
        e.setQuerySpace("default");
        e.setVersion(1L);
        dao.saveEntity(e);
        return e;
    }

    private String ensureModuleId() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = dao.newEntity();
        m.setModuleId("nop/test-ent-mod-" + System.nanoTime());
        m.setModuleName("test-ent-mod");
        m.setDisplayName("test-ent-mod");
        m.setModuleVersion(1L);
        m.setStatus("RELEASED");
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(m);
        return m.getMetaModuleId();
    }

    private String ensureOrmModelId() {
        IEntityDao<NopMetaOrmModel> dao = daoProvider.daoFor(NopMetaOrmModel.class);
        NopMetaOrmModel m = dao.newEntity();
        m.setMetaModuleId(ensureModuleId());
        m.setModelName("test-model-" + System.nanoTime());
        m.setIsDelta((byte) 0);
        m.setVersion(1L);
        dao.saveEntity(m);
        return m.getOrmModelId();
    }

    private void saveQualityRule(String ruleId, String ruleType, String entityType,
                                 String tableId, Double threshold, String params) {
        IEntityDao<NopMetaQualityRule> dao = daoProvider.daoFor(NopMetaQualityRule.class);
        NopMetaQualityRule r = dao.newEntity();
        r.setQualityRuleId(ruleId);
        r.setRuleName(ruleId);
        r.setDisplayName(ruleId);
        r.setRuleType(ruleType);
        r.setEntityType(entityType);
        r.setEntityId(tableId);
        r.setSeverity("WARNING");
        r.setThreshold(threshold);
        r.setParams(params);
        r.setVersion(1L);
        dao.saveEntity(r);
    }

    private GraphQLResponseBean execRule(String ruleId) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaQualityRule__executeQualityRule(qualityRuleId: \"" + ruleId + "\") }")));
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean r = new GraphQLRequestBean();
        r.setQuery(query);
        return r;
    }

    private void assertStatus(GraphQLResponseBean resp, String expected) {
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        Map<String, Object> result = (Map<String, Object>) data.get("NopMetaQualityRule__executeQualityRule");
        assertEquals(expected, result.get("status"), "status mismatch: " + resp);
    }

    private NopMetaCatalog findCatalogRow(String metaTableId) {
        IEntityDao<NopMetaCatalog> dao = daoProvider.daoFor(NopMetaCatalog.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaCatalog.PROP_NAME_metaTableId, metaTableId));
        return dao.findFirstByQuery(q);
    }

    private NopMetaProfilingResult findProfilingResult(String metaTableId) {
        IEntityDao<NopMetaProfilingResult> dao = daoProvider.daoFor(NopMetaProfilingResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaProfilingResult.PROP_NAME_metaTableId, metaTableId));
        return dao.findFirstByQuery(q);
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

    private static Double toDoubleOrNull(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }
}
