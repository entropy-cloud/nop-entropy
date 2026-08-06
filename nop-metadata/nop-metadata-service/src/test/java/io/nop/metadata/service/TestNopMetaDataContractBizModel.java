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
import io.nop.orm.IOrmTemplate;
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

    @Inject
    IOrmTemplate orm;

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

    // ===== R2.2 单一事实源（保留层 XPL）正路径断言：approve/reject 经 GraphQL 驱动状态生命周期 =====

    /** approve 正路径：SUBMITTED+DRAFT → APPROVED+ACTIVE（状态生命周期经 XPL 推进） */
    @Test
    public void testApprovePositivePathDraftToActive() {
        String id = saveContract("c-approve-1", "DRAFT", null, null, null);
        setApproveStatus(id, "SUBMITTED");
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__approve(id: \"" + id + "\") "
                        + "{ contractId status approveStatus approvedBy } }")));
        assertFalse(resp.hasError(), "approve must not error: " + resp);
        NopMetaDataContract saved = daoProvider.daoFor(NopMetaDataContract.class).getEntityById(id);
        assertEquals("ACTIVE", saved.getStatus(), "DRAFT must advance to ACTIVE on approve");
        assertEquals("APPROVED", saved.getApproveStatus());
        assertNotNull(saved.getApprovedAt(), "approvedAt must be set");
    }

    /** approve 正路径：ACTIVE → DEPRECATED（生命周期链推进） */
    @Test
    public void testApprovePositivePathActiveToDeprecated() {
        String id = saveContract("c-approve-2", "ACTIVE", null, null, null);
        setApproveStatus(id, "SUBMITTED");
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__approve(id: \"" + id + "\") { status } }")));
        assertFalse(resp.hasError(), "approve must not error: " + resp);
        NopMetaDataContract saved = daoProvider.daoFor(NopMetaDataContract.class).getEntityById(id);
        assertEquals("DEPRECATED", saved.getStatus(), "ACTIVE must advance to DEPRECATED on approve");
    }

    /** approve 正路径：DEPRECATED → RETIRED（生命周期链终点） */
    @Test
    public void testApprovePositivePathDeprecatedToRetired() {
        String id = saveContract("c-approve-3", "DEPRECATED", null, null, null);
        setApproveStatus(id, "SUBMITTED");
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__approve(id: \"" + id + "\") { status } }")));
        assertFalse(resp.hasError(), "approve must not error: " + resp);
        NopMetaDataContract saved = daoProvider.daoFor(NopMetaDataContract.class).getEntityById(id);
        assertEquals("RETIRED", saved.getStatus(), "DEPRECATED must advance to RETIRED on approve");
    }

    /** reject 正路径：SUBMITTED+ACTIVE → REJECTED + 回 DRAFT + remark 前缀 */
    @Test
    public void testRejectPositivePathBackToDraftWithRemark() {
        String id = saveContract("c-reject-1", "ACTIVE", null, null, "Some reason");
        setApproveStatus(id, "SUBMITTED");
        GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataContract__reject(id: \"" + id + "\") "
                        + "{ status approveStatus remark } }")));
        assertFalse(resp.hasError(), "reject must not error: " + resp);
        NopMetaDataContract saved = daoProvider.daoFor(NopMetaDataContract.class).getEntityById(id);
        assertEquals("DRAFT", saved.getStatus(), "reject must revert status to DRAFT");
        assertEquals("REJECTED", saved.getApproveStatus());
        assertNotNull(saved.getRemark());
        assertTrue(saved.getRemark().startsWith("Rejected:"), "reject must prefix remark: " + saved.getRemark());
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

    // ===== AR-22（plan 2026-08-06-1228-1 Phase 4）：SLA 时间单位 week/w + 未知单位 fail-fast =====

    /** week = 7 天：catalog 6 天前收集 + 1 week 频率 → 新鲜（修复前 week 落 default 按 1ms 解析 → 恒 stale → FAIL，实测 red） */
    @Test
    public void testCheckContractSlaWeekUnitFresh() {
        String tableId = saveExternalTable("EXT_SLA_WEEK_FRESH");
        long sixDaysAgo = System.currentTimeMillis() - 6L * 24L * 60L * 60L * 1000L;
        saveCatalog(tableId, sixDaysAgo, null);

        String id = saveContract("c-sla-week-fresh", "ACTIVE", tableId, null,
                "{\"refreshFrequency\":{\"interval\":1,\"unit\":\"week\"}}");
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "PASS");
        assertLatestResultContains(id, "\"slaFresh\":true");
        assertLatestResultContains(id, "\"collectionStale\":false");
    }

    /** week = 7 天（非无穷）：catalog 8 天前收集 → 超期 FAIL（week 语义边界） */
    @Test
    public void testCheckContractSlaWeekUnitStale() {
        String tableId = saveExternalTable("EXT_SLA_WEEK_STALE");
        long eightDaysAgo = System.currentTimeMillis() - 8L * 24L * 60L * 60L * 1000L;
        saveCatalog(tableId, eightDaysAgo, null);

        String id = saveContract("c-sla-week-stale", "ACTIVE", tableId, null,
                "{\"refreshFrequency\":{\"interval\":1,\"unit\":\"week\"}}");
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "FAIL");
        assertLatestResultContains(id, "\"collectionStale\":true");
    }

    /** "w" 缩写 = week：interval 2 w = 14 天，catalog 13 天前 → 新鲜（修复前按 1ms 解析实测 red） */
    @Test
    public void testCheckContractSlaWShortUnit() {
        String tableId = saveExternalTable("EXT_SLA_W_SHORT");
        long thirteenDaysAgo = System.currentTimeMillis() - 13L * 24L * 60L * 60L * 1000L;
        saveCatalog(tableId, thirteenDaysAgo, null);

        String id = saveContract("c-sla-w-short", "ACTIVE", tableId, null,
                "{\"refreshFrequency\":{\"interval\":2,\"unit\":\"w\"}}");
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "PASS");
        assertLatestResultContains(id, "\"slaFresh\":true");
    }

    /** 未知单位（如 "fortnight"）→ 显式 ERR_CONTRACT_SLA_INVALID（修复前静默按 1ms 解析实测 red） */
    @Test
    public void testCheckContractSlaUnknownUnitFailsLoud() {
        String tableId = saveExternalTable("EXT_SLA_UNKNOWN_UNIT");
        saveCatalog(tableId, System.currentTimeMillis(), null);

        String id = saveContract("c-sla-unknown-unit", "ACTIVE", tableId, null,
                "{\"refreshFrequency\":{\"interval\":1,\"unit\":\"fortnight\"}}");
        GraphQLResponseBean resp = check(id);
        assertTrue(resp.hasError(),
                "unknown sla time unit must fail loudly (no silent 1ms parsing): " + resp);
        String errorCode = resp.getErrorCode();
        assertNotNull(errorCode, "error must carry an error code: " + resp);
        assertTrue(errorCode.contains("nop.err.metadata.contract-sla-invalid"),
                "unknown unit must reuse ERR_CONTRACT_SLA_INVALID, got: " + errorCode);
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

    // ===== AR-06（plan 2026-08-06-0553-3 Phase 2）：SLA 已配置但无 Catalog 数据 → 不再静默 PASS =====

    /** SLA 已配置 + 无 Catalog 记录（catalogAvailable=false）→ FAIL（修复前静默 PASS） */
    @Test
    public void testCheckContractSlaConfiguredNoCatalogFails() {
        String tableId = saveExternalTable("EXT_SLA_NO_CATALOG");
        // 无 catalog 行：catalogAvailable=false，SLA 配置存在
        String id = saveContract("c-sla-no-cat", "ACTIVE", tableId, null,
                "{\"refreshFrequency\":{\"interval\":1,\"unit\":\"day\"}}");
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "FAIL");
        assertLatestResultContains(id, "\"slaFresh\":false");
        assertLatestResultContains(id, "\"catalogAvailable\":false");
        assertLatestResultContains(id, "no Catalog data");
        assertLatestResultWritten(id, "FAIL");
    }

    /** SLA map 非空但仅含非时间键（如 retention）+ 无 Catalog → 仍 FAIL（裁定：无数据即不满足任何已配置 SLA） */
    @Test
    public void testCheckContractSlaRetentionOnlyNoCatalogFails() {
        String tableId = saveExternalTable("EXT_SLA_RETENTION_ONLY");
        String id = saveContract("c-sla-retention", "ACTIVE", tableId, null,
                "{\"retention\":{\"interval\":30,\"unit\":\"day\"}}");
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "FAIL");
        assertLatestResultContains(id, "\"slaFresh\":false");
        assertLatestResultContains(id, "\"catalogAvailable\":false");
    }

    /** 无 SLA 配置（slaMap 为空）→ 保持既有 pass 语义（无 SLA 不判定） */
    @Test
    public void testCheckContractNoSlaNoCatalogKeepsPass() {
        String ruleId = saveQualityRule("qr-nosla-1");
        saveQualityResult(ruleId, "PASS", "ok");
        String tableId = saveExternalTable("EXT_NOSLA");
        // 无 SLA、无 catalog、有质量规则
        String id = saveContract("c-nosla", "ACTIVE", tableId,
                "{\"qualityRuleIds\":[\"" + ruleId + "\"]}", null);
        GraphQLResponseBean resp = check(id);
        assertFalse(resp.hasError(), "check should not error: " + resp);
        assertCheckStatus(resp, "PASS");
        assertLatestResultContains(id, "\"slaFresh\":true");
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

    private void setApproveStatus(String contractId, String approveStatus) {
        IEntityDao<NopMetaDataContract> dao = daoProvider.daoFor(NopMetaDataContract.class);
        orm.runInSession(session -> {
            NopMetaDataContract c = dao.getEntityById(contractId);
            assertNotNull(c, "contract must exist for status update");
            c.setApproveStatus(approveStatus);
            dao.updateEntity(c);
            session.flush();
            return null;
        });
    }

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
        c.setMetaTableId(entityTableId);
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
