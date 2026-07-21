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

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataContractBizModel extends JunitBaseTestCase {

    public TestNopMetaDataContractBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    // ===== 审批流 - 守卫测试（approve/reject 在非 SUBMITTED 状态应失败） =====

    /** approve 在非 SUBMITTED 状态应失败 */
    @Test
    public void testApproveGuardOnWrongState() {
        String id = saveContract("c-guard-1", "DRAFT", null, null, null);
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__approve(id: \"" + id + "\") { status } }")));
        assertTrue(resp.hasError(), "approve on non-SUBMITTED must fail: " + resp);
    }

    /** reject 在非 SUBMITTED 状态应失败 */
    @Test
    public void testRejectGuardOnWrongState() {
        String id = saveContract("c-guard-2", "DRAFT", null, null, null);
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__reject(id: \"" + id + "\") { status } }")));
        assertTrue(resp.hasError(), "reject on non-SUBMITTED must fail: " + resp);
    }

    // ===== checkContract：质量路径 =====

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

    @Test
    public void testCheckContractSlaFresh() {
        String tableId = saveExternalTable("EXT_SLA_FRESH");
        saveCatalog(tableId, System.currentTimeMillis(), null);

        String id = saveContract("c-sla-fresh", "ACTIVE", tableId, null,
                "{\"refreshFrequency\":{\"interval\":1,\"unit\":\"day\"}}");
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "PASS");
        assertLatestResultContains(id, "\"slaFresh\":true");
        assertLatestResultContains(id, "\"collectionStale\":false");
    }

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
        assertLatestResultContains(id, "\"passedRules\":1");
        assertLatestResultContains(id, "\"slaFresh\":false");
    }

    @Test
    public void testCheckContractNoCheckableItemsError() {
        String id = saveContract("c-empty", "ACTIVE", null, null, null);
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "no-checkable should return ERROR result, not global error: " + resp);
        assertCheckStatus(resp, "ERROR");
        assertLatestResultWritten(id, "ERROR");
    }

    @Test
    public void testCheckContractRuleIdNotExistError() {
        String id = saveContract("c-bad-rule", "ACTIVE", null,
                "{\"qualityRuleIds\":[\"__nope_rule__\"]}", null);
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "bad-rule should return ERROR result, not global error: " + resp);
        assertCheckStatus(resp, "ERROR");
        assertLatestResultWritten(id, "ERROR");
    }

    @Test
    public void testCheckContractNotFound() {
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__checkContract(contractId: \"__nope_contract__\") "
                        + "{ timestamp status message qualitySummary slaSummary } }")));
        assertTrue(resp.hasError(), "non-existent contract must error (no NPE): " + resp);
    }

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

    private GraphQLResponseBean check(String id) {
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__checkContract(contractId: \"" + id + "\") "
                        + "{ timestamp status message qualitySummary slaSummary } }")));
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
