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
import io.nop.metadata.dao.entity.NopMetaCatalog;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证数据契约状态生命周期 + 契约检查（设计 04 §2.3/§5.2，plan 0900-1 Phase 2）：
 *
 * <p>状态流转：合法流转成功更新 status；非法流转显式失败抛 ErrorCode。
 *
 * <p>checkContract 端到端（D2 钉死算法）：
 * <ul>
 *   <li>质量路径：聚合 qualityRuleIds 引用的质量规则最新 QualityResult → PASS/FAIL。</li>
 *   <li>SLA 路径：基于 Catalog 最新快照的 refreshFrequency↔collectedAt 新鲜度判断 → fresh/stale。</li>
 *   <li>混合归并：质量 PASS+SLA stale→FAIL；全部通过→PASS。</li>
 *   <li>无可检查项 → ERROR（不静默 pass）。</li>
 *   <li>ruleId 不存在 → ERROR。</li>
 *   <li>契约不存在 → 显式失败（不 NPE）。</li>
 * </ul>
 *
 * <p>Anti-Hollow：checkContract 端到端用真实 QualityRule + QualityResult + Catalog 行数据，
 * 断言 latestResult 写入且 status/failedRules/slaFresh 由 D2 钉死算法唯一确定，证明 MetaContractChecker
 * 在运行时确实聚合了真实数据并写回，非空壳实现。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataContractBizModel extends JunitBaseTestCase {

    public TestNopMetaDataContractBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    // ===== 状态生命周期 =====

    /** 合法流转：DRAFT→ACTIVE→DEPRECATED→RETIRED 全链路成功。 */
    @Test
    public void testLegalTransitionsFullLifecycle() {
        String id = saveContract("c-lc", "DRAFT", null, null, null);
        assertStatus(activate(id), "ACTIVE");
        assertStatus(deprecate(id), "DEPRECATED");
        assertStatus(retire(id), "RETIRED");
        // 持久化验证
        assertEquals("RETIRED", daoProvider.daoFor(NopMetaDataContract.class).getEntityById(id).getStatus());
    }

    /** 非法流转：DRAFT→DEPRECATED（跳过 ACTIVE）显式失败，status 不变。 */
    @Test
    public void testIllegalTransitionDeprecateFromDraft() {
        String id = saveContract("c-illegal-1", "DRAFT", null, null, null);
        GraphQLResponseBean resp = deprecate(id);
        assertTrue(resp.hasError(), "DRAFT→DEPRECATED must explicitly fail: " + resp);
        assertEquals("DRAFT", daoProvider.daoFor(NopMetaDataContract.class).getEntityById(id).getStatus());
    }

    /** 非法流转：DRAFT→RETIRED 显式失败。 */
    @Test
    public void testIllegalTransitionRetireFromDraft() {
        String id = saveContract("c-illegal-2", "DRAFT", null, null, null);
        GraphQLResponseBean resp = retire(id);
        assertTrue(resp.hasError(), "DRAFT→RETIRED must explicitly fail: " + resp);
    }

    /** 非法流转：已 RETIRED 再 activate 显式失败（终态不可再流转）。 */
    @Test
    public void testIllegalTransitionFromRetired() {
        String id = saveContract("c-illegal-3", "DEPRECATED", null, null, null);
        assertStatus(retire(id), "RETIRED");
        GraphQLResponseBean resp = activate(id);
        assertTrue(resp.hasError(), "RETIRED→ACTIVE must explicitly fail (terminal state): " + resp);
        assertEquals("RETIRED", daoProvider.daoFor(NopMetaDataContract.class).getEntityById(id).getStatus());
    }

    // ===== checkContract：质量路径 =====

    /** 质量 PASS：两条规则最新结果都 PASS → checkContract status=PASS。 */
    @Test
    public void testCheckContractQualityPass() {
        String ruleId1 = saveQualityRule("qr-pass-1");
        String ruleId2 = saveQualityRule("qr-pass-2");
        saveQualityResult(ruleId1, "PASS", "ok");
        saveQualityResult(ruleId2, "PASS", "ok");

        String id = saveContract("c-qc-pass", "ACTIVE", null,
                "{\"qualityRuleIds\":[\"" + ruleId1 + "\",\"" + ruleId2 + "\"]}", null);
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "PASS");
        assertLatestResultContains(id, "\"passedRules\":2");
        assertLatestResultContains(id, "\"failedRules\":0");
        assertLatestResultWritten(id, "PASS");
    }

    /** 质量 FAIL：一条规则最新结果 FAIL → checkContract status=FAIL（failedRules=1）。 */
    @Test
    public void testCheckContractQualityFail() {
        String ruleId1 = saveQualityRule("qr-fail-1");
        String ruleId2 = saveQualityRule("qr-fail-2");
        saveQualityResult(ruleId1, "PASS", "ok");
        saveQualityResult(ruleId2, "FAIL", "nullCount=1");

        String id = saveContract("c-qc-fail", "ACTIVE", null,
                "{\"qualityRuleIds\":[\"" + ruleId1 + "\",\"" + ruleId2 + "\"]}", null);
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "FAIL");
        assertLatestResultContains(id, "\"failedRules\":1");
        assertLatestResultWritten(id, "FAIL");
    }

    // ===== checkContract：SLA 路径 =====

    /** SLA fresh：Catalog 刚收集（collectedAt=now），refreshFrequency=1 day → slaFresh=true → status=PASS。 */
    @Test
    public void testCheckContractSlaFresh() {
        String tableId = saveExternalTable("EXT_SLA_FRESH");
        saveCatalog(tableId, System.currentTimeMillis(), null); // lastModified=null（v1 恒 null）

        String id = saveContract("c-sla-fresh", "ACTIVE", tableId, null,
                "{\"refreshFrequency\":{\"interval\":1,\"unit\":\"day\"}}");
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "PASS");
        assertLatestResultContains(id, "\"slaFresh\":true");
        assertLatestResultContains(id, "\"collectionStale\":false");
    }

    /** SLA stale：Catalog collectedAt=2小时前，refreshFrequency=1 hour → collectionStale=true → slaFresh=false → status=FAIL。 */
    @Test
    public void testCheckContractSlaStale() {
        String tableId = saveExternalTable("EXT_SLA_STALE");
        long twoHoursAgo = System.currentTimeMillis() - 2L * 60L * 60L * 1000L;
        saveCatalog(tableId, twoHoursAgo, null);

        String id = saveContract("c-sla-stale", "ACTIVE", tableId, null,
                "{\"refreshFrequency\":{\"interval\":1,\"unit\":\"hour\"}}");
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "FAIL");
        assertLatestResultContains(id, "\"slaFresh\":false");
        assertLatestResultContains(id, "\"collectionStale\":true");
        assertLatestResultWritten(id, "FAIL");
    }

    // ===== checkContract：混合归并 =====

    /** 混合：质量 PASS + SLA stale → FAIL（D2：SLA stale 任一成立 → FAIL）。 */
    @Test
    public void testCheckContractQualityPassSlaStaleFail() {
        String ruleId = saveQualityRule("qr-mix-1");
        saveQualityResult(ruleId, "PASS", "ok");
        String tableId = saveExternalTable("EXT_MIX");
        long twoHoursAgo = System.currentTimeMillis() - 2L * 60L * 60L * 1000L;
        saveCatalog(tableId, twoHoursAgo, null);

        String id = saveContract("c-mix", "ACTIVE", tableId,
                "{\"qualityRuleIds\":[\"" + ruleId + "\"]}",
                "{\"refreshFrequency\":{\"interval\":1,\"unit\":\"hour\"}}");
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "FAIL");
        // 质量本身通过，但因 SLA stale 而整体 FAIL
        assertLatestResultContains(id, "\"passedRules\":1");
        assertLatestResultContains(id, "\"slaFresh\":false");
    }

    // ===== checkContract：失败路径（不静默 pass / 不吞异常） =====

    /** 无可检查项：qualityExpectations 为空且 sla 为空 → status=ERROR（不静默 pass）。 */
    @Test
    public void testCheckContractNoCheckableItemsError() {
        String id = saveContract("c-empty", "ACTIVE", null, null, null);
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "no-checkable should return ERROR result, not global error: " + resp);
        assertCheckStatus(resp, "ERROR");
        assertLatestResultWritten(id, "ERROR");
    }

    /** ruleId 不存在 → status=ERROR（不静默 pass）。 */
    @Test
    public void testCheckContractRuleIdNotExistError() {
        String id = saveContract("c-bad-rule", "ACTIVE", null,
                "{\"qualityRuleIds\":[\"__nope_rule__\"]}", null);
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "bad-rule should return ERROR result, not global error: " + resp);
        assertCheckStatus(resp, "ERROR");
        assertLatestResultWritten(id, "ERROR");
    }

    /** 契约不存在 → 显式失败（不 NPE）。 */
    @Test
    public void testCheckContractNotFound() {
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__checkContract(contractId: \"__nope_contract__\") }")));
        assertTrue(resp.hasError(), "non-existent contract must error (no NPE): " + resp);
    }

    /** DRAFT 可预检：checkContract 不受 status 阻断。 */
    @Test
    public void testCheckContractDraftPreCheck() {
        String ruleId = saveQualityRule("qr-draft-1");
        saveQualityResult(ruleId, "PASS", "ok");
        String id = saveContract("c-draft", "DRAFT", null,
                "{\"qualityRuleIds\":[\"" + ruleId + "\"]}", null);
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "DRAFT pre-check should not error: " + resp);
        assertCheckStatus(resp, "PASS");
    }

    // ===== helpers =====

    private GraphQLResponseBean activate(String id) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__activateContract(contractId: \"" + id + "\") { status } }")));
    }

    private GraphQLResponseBean deprecate(String id) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__deprecateContract(contractId: \"" + id + "\") { status } }")));
    }

    private GraphQLResponseBean retire(String id) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__retireContract(contractId: \"" + id + "\") { status } }")));
    }

    private GraphQLResponseBean check(String id) {
        // checkContract 返回 Map<String,Object>（GraphQL 标量），不使用 selection set（同 executeQualityRule 先例）
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__checkContract(contractId: \"" + id + "\") }")));
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }

    private String saveContract(String contractId, String status, String entityTableId,
                                String qualityExpectations, String sla) {
        IEntityDao<NopMetaDataContract> dao = daoProvider.daoFor(NopMetaDataContract.class);
        NopMetaDataContract c = dao.newEntity();
        c.setContractId(contractId);
        c.setContractName(contractId + "-name");
        c.setDisplayName(contractId + "-name");
        c.setStatus(status);
        c.setEntityTableId(entityTableId);
        c.setQualityExpectations(qualityExpectations);
        c.setSla(sla);
        c.setOwnerUserId("autotest");
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
        return contractId;
    }

    private String saveQualityRule(String ruleName) {
        IEntityDao<NopMetaQualityRule> dao = daoProvider.daoFor(NopMetaQualityRule.class);
        NopMetaQualityRule r = dao.newEntity();
        String ruleId = "rid-" + ruleName + "-" + UUID.randomUUID().toString().substring(0, 8);
        r.setQualityRuleId(ruleId);
        r.setRuleName(ruleName);
        r.setDisplayName(ruleName);
        r.setRuleType("volume");
        r.setEntityType("table");
        r.setEntityId("auto-table");
        r.setSeverity("WARNING");
        r.setVersion(1L);
        r.setCreatedBy("autotest");
        r.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        r.setCreateTime(now);
        r.setUpdateTime(now);
        dao.saveEntity(r);
        return ruleId;
    }

    private void saveQualityResult(String ruleId, String status, String message) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult r = dao.newEntity();
        r.setQualityRuleId(ruleId);
        r.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        r.setStatus(status);
        r.setMessage(message);
        r.setVersion(1L);
        r.setCreatedBy("autotest");
        r.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        r.setCreateTime(now);
        r.setUpdateTime(now);
        dao.saveEntity(r);
    }

    private String saveExternalTable(String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureExternalSystemModuleId());
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType("external");
        t.setQuerySpace("qs-contract-test");
        t.setVersion(1L);
        dao.saveEntity(t);
        return t.getMetaTableId();
    }

    private void saveCatalog(String metaTableId, long collectedAtMs, Long lastModifiedMs) {
        IEntityDao<NopMetaCatalog> dao = daoProvider.daoFor(NopMetaCatalog.class);
        NopMetaCatalog c = dao.newEntity();
        c.setMetaTableId(metaTableId);
        c.setRowCount(100L);
        c.setCollectedAt(new Timestamp(collectedAtMs));
        if (lastModifiedMs != null) {
            c.setLastModified(new Timestamp(lastModifiedMs));
        }
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
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

    private void assertStatus(GraphQLResponseBean resp, String expected) {
        assertFalse(resp.hasError(), "transition should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("status=" + expected), "expected status=" + expected + " but got: " + data);
    }

    private void assertCheckStatus(GraphQLResponseBean resp, String expected) {
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("status=" + expected), "expected check status=" + expected + " but got: " + data);
    }

    private void assertLatestResultContains(String contractId, String fragment) {
        NopMetaDataContract c = daoProvider.daoFor(NopMetaDataContract.class).getEntityById(contractId);
        assertNotNull(c, "contract must exist");
        String latest = c.getLatestResult();
        assertNotNull(latest, "latestResult must be written back");
        assertTrue(latest.contains(fragment),
                "expected latestResult to contain [" + fragment + "]: " + latest);
    }

    private void assertLatestResultWritten(String contractId, String expectedStatus) {
        NopMetaDataContract c = daoProvider.daoFor(NopMetaDataContract.class).getEntityById(contractId);
        assertNotNull(c, "contract must exist");
        String latest = c.getLatestResult();
        assertNotNull(latest, "latestResult must be written back");
        assertTrue(latest.contains("\"status\":\"" + expectedStatus + "\""),
                "latestResult must contain status=" + expectedStatus + ": " + latest);
    }
}
