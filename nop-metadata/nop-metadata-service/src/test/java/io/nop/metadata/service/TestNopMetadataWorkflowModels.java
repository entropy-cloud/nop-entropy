package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.context.ContextProvider;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaDataContract;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.metadata.service.quality.QualityAlertWorkflowProcessor;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.api.WfReference;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.model.IWorkflowModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * R2.1（P1-MA3-001 + MA3.1-04/05/06）端到端回归测试：
 *
 * <ul>
 *   <li>模型加载：3 个 xwf 经 WorkflowManagerImpl 解析链（resolve-wf → /nop/wf/）可达，且
 *       listener 重写为 c:script（内置 taglib，无 xlib import 依赖；MA7.6-06 修正原「x:config
 *       import」描述）、xdef 校验（MA3.1-03）、start 步骤解析（MA3.1-04）、
 *       listener 表达式编译（MA3.1-05/06）全部通过——任一修复回退即模型加载失败；</li>
 *   <li>启动 + 审批流转：metaDataContractApproval 从 start 到 end 的完整正路径（submit→owner-check→
 *       consumer-check→end），*end listener 回调 NopMetaDataContract.approve XPL（接线验证：
 *       GraphQL/BizModel → 工作流 → 状态迁移 DRAFT→ACTIVE）；disagree 结束路径不得触发 approve
 *       （MA7.6-01：驳回即通过/发起人自批修复）；</li>
 *   <li>tagLabelConfirmApproval 同样完整走通（submit→reviewer-check→end，TagLabel state→Confirmed）；</li>
 *   <li>qualityBreachApproval 容器内启动 + verify 失败路径：经生产入口 createAlertWorkflow 真实启动
 *       （null ctx 兜底不 NPE，owner-investigate 激活断言；MA7.6-02/MA7.6-07），verify 步骤在
 *       ruleId 缺失 / reJudge 异常时 fail-closed 置 reject 回退 owner-investigate，流程不卡死不静默关闭
 *       （MA7.6-03）。</li>
 * </ul>
 *
 * <p>MA7.6-07：qualityBreachApproval 的"启动可达"已由下方容器内启动测试真实覆盖（不再只是模型加载）。
 * 完整 verify 的 agree 正路径（reJudge 依赖真实数据源/规则/表基础设施）由质量域既有测试
 * （TestNopMetaQualityRuleBizModel）覆盖执行器语义。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetadataWorkflowModels extends JunitBaseTestCase {

    @Inject
    IWorkflowManager wfManager;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate orm;

    @Inject
    QualityAlertWorkflowProcessor alertWorkflowService;

    // ===== 模型加载（MA3.1-01/02/03/04/05/06 的模型级验证） =====

    @Test
    public void testAllWorkflowModelsLoadFromResolveChain() {
        for (String wfName : List.of("metaDataContractApproval", "qualityBreachApproval", "tagLabelConfirmApproval")) {
            IWorkflowModel model = wfManager.getWorkflowModel(wfName, 1L);
            assertNotNull(model, "workflow model must resolve and load: " + wfName);
            assertEquals(wfName, model.getWfName());
        }
    }

    // ===== metaDataContractApproval 端到端：start → approve 流转 → end → approve XPL 回调 =====

    @Test
    public void testDataContractApprovalFlowEndToEnd() {
        ensureRole("metadata-admin");
        ensureRole("metadata-user");
        ensureUser("wf-starter");
        ensureUser("wf-admin");
        ensureUser("wf-user");
        linkUserRole("wf-admin", "metadata-admin");
        linkUserRole("wf-user", "metadata-user");

        String contractId = "wf-contract-e2e-001";
        saveContract(contractId, "DRAFT", "SUBMITTED");

        IServiceContext starterCtx = newContext("wf-starter");
        orm.runInSession(session -> {
            IWorkflow wf = wfManager.newWorkflow("metaDataContractApproval", 1L);
            wf.getRecord().setBizObjName("NopMetaDataContract");
            wf.getRecord().setBizObjId(contractId);
            wf.start(Map.of(), starterCtx);
            while (wf.runAutoTransitions(starterCtx)) {
            }

            // submit（starter）→ agree
            IWorkflowStep submit = requireActiveStep(wf, "submit");
            submit.invokeAction("agree", null, starterCtx);
            while (wf.runAutoTransitions(starterCtx)) {
            }

            // owner-check（role metadata-admin）→ agree
            IWorkflowStep owner = requireActiveStep(wf, "owner-check");
            IServiceContext adminCtx = newContext("wf-admin");
            owner.invokeAction("agree", null, adminCtx);
            while (wf.runAutoTransitions(adminCtx)) {
            }

            // consumer-check（role metadata-user）→ agree → end
            IWorkflowStep consumer = requireActiveStep(wf, "consumer-check");
            IServiceContext userCtx = newContext("wf-user");
            consumer.invokeAction("agree", null, userCtx);
            while (wf.runAutoTransitions(userCtx)) {
            }

            assertEquals(Integer.valueOf(40), wf.getRecord().getStatus(),
                    "workflow must end in COMPLETED status: " + wf.getRecord().getStatus());
            session.flush();
            return null;
        });

        // *end listener 回调 NopMetaDataContract approve XPL：approveStatus=APPROVED + status=DRAFT→ACTIVE
        NopMetaDataContract saved = daoProvider.daoFor(NopMetaDataContract.class).getEntityById(contractId);
        assertNotNull(saved);
        assertEquals("APPROVED", saved.getApproveStatus(), "listener must call approve via biz object");
        assertEquals("ACTIVE", saved.getStatus(), "approve XPL must advance DRAFT→ACTIVE");
    }

    // ===== tagLabelConfirmApproval 端到端：start → approve 流转 → end → TagLabel 回调 =====

    @Test
    public void testTagLabelConfirmApprovalFlowEndToEnd() {
        ensureRole("metadata-admin");
        ensureUser("wf-starter");
        ensureUser("wf-admin");
        linkUserRole("wf-admin", "metadata-admin");
        saveTagLabel("wf-tlabel-e2e-001", "SUBMITTED");

        IServiceContext starterCtx = newContext("wf-starter");
        orm.runInSession(session -> {
            IWorkflow wf = wfManager.newWorkflow("tagLabelConfirmApproval", 1L);
            wf.getRecord().setBizObjName("NopMetaTagLabel");
            wf.getRecord().setBizObjId("wf-tlabel-e2e-001");
            wf.start(Map.of(), starterCtx);
            while (wf.runAutoTransitions(starterCtx)) {
            }

            IWorkflowStep submit = requireActiveStep(wf, "submit");
            submit.invokeAction("agree", null, starterCtx);
            while (wf.runAutoTransitions(starterCtx)) {
            }

            IWorkflowStep reviewer = requireActiveStep(wf, "reviewer-check");
            IServiceContext adminCtx = newContext("wf-admin");
            reviewer.invokeAction("agree", null, adminCtx);
            while (wf.runAutoTransitions(adminCtx)) {
            }

            assertEquals(Integer.valueOf(40), wf.getRecord().getStatus(),
                    "workflow must end in COMPLETED status: " + wf.getRecord().getStatus());
            session.flush();
            return null;
        });

        // *end listener 回调 NopMetaTagLabel approve XPL：state → Confirmed
        NopMetaTagLabel saved = daoProvider.daoFor(NopMetaTagLabel.class).getEntityById("wf-tlabel-e2e-001");
        assertNotNull(saved);
        assertEquals("APPROVED", saved.getApproveStatus());
        assertEquals("Confirmed", saved.getState(), "TagLabel approve XPL must set state=Confirmed");
    }

    // ===== MA7.6-01：disagree 结束不得触发 approve（驳回即通过 / 发起人自批修复） =====

    /**
     * 发起人在 submit 步骤直接调用基模板 oa.xwf 的 disagree 公共动作 → to-end → 工作流 COMPLETED。
     * *end listener 必须带结束原因守卫：wf.record.appState='disagree' 时不得调用 entity approve
     * （修复前驳回的合约被标记为 APPROVED + DRAFT→ACTIVE，审批语义完全反转）。
     */
    @Test
    public void testDataContractApprovalDisagreeDoesNotApprove() {
        ensureRole("metadata-admin");
        ensureRole("metadata-user");
        ensureUser("wf-starter");
        ensureUser("wf-admin");
        ensureUser("wf-user");
        linkUserRole("wf-admin", "metadata-admin");
        linkUserRole("wf-user", "metadata-user");

        String contractId = "wf-contract-disagree-001";
        saveContract(contractId, "DRAFT", "SUBMITTED");

        IServiceContext starterCtx = newContext("wf-starter");
        orm.runInSession(session -> {
            IWorkflow wf = wfManager.newWorkflow("metaDataContractApproval", 1L);
            wf.getRecord().setBizObjName("NopMetaDataContract");
            wf.getRecord().setBizObjId(contractId);
            wf.start(Map.of(), starterCtx);
            while (wf.runAutoTransitions(starterCtx)) {
            }

            // 发起人在 submit 步骤 disagree → to-end → 工作流以 COMPLETED 结束（appState='disagree'）
            IWorkflowStep submit = requireActiveStep(wf, "submit");
            submit.invokeAction("disagree", null, starterCtx);
            while (wf.runAutoTransitions(starterCtx)) {
            }

            assertEquals(Integer.valueOf(40), wf.getRecord().getStatus(),
                    "disagree must end the workflow in COMPLETED status: " + wf.getRecord().getStatus());
            session.flush();
            return null;
        });

        // *end listener 守卫：disagree 结束不得调用 approve（MA7.6-01）
        NopMetaDataContract saved = daoProvider.daoFor(NopMetaDataContract.class).getEntityById(contractId);
        assertNotNull(saved);
        assertEquals("SUBMITTED", saved.getApproveStatus(),
                "disagree end must NOT approve the contract (approveStatus stays SUBMITTED)");
        assertEquals("DRAFT", saved.getStatus(),
                "disagree end must NOT activate the contract (status stays DRAFT)");
    }

    // ===== MA7.6-02 + MA7.6-07：qualityBreachApproval 容器内启动（createAlertWorkflow null ctx 不 NPE） =====

    /**
     * 生产入口 createAlertWorkflow(result, null)：修复前 wf.start(vars, null) → WfRuntime 构造
     * serviceContext.getEvalScope() 直接解引用 null → 确定性 NPE → 告警流静默不创建（MA7.6-02）。
     * 修复后 null ctx 兜底（IServiceContext.getCtx() → 新建 ServiceContextImpl），工作流必须真实启动：
     * <ul>
     *   <li>createAlertWorkflow 返回非 null WfReference（wfName=qualityBreachApproval）</li>
     *   <li>wfManager.getWorkflow(wfId) 可加载，bizObjName/bizObjId 正确绑定结果行</li>
     *   <li>工作流状态 ACTIVATED（非 COMPLETED），owner-investigate 步骤激活（MA7.6-07 启动覆盖）</li>
     * </ul>
     */
    @Test
    public void testQualityBreachApprovalStartupViaCreateAlertWorkflow() {
        ensureRole("metadata-admin");
        ensureUser("wf-admin");
        linkUserRole("wf-admin", "metadata-admin");

        String ruleId = "wf-alert-rule-001";
        saveQualityRule(ruleId, "entity-001");
        String resultId = "wf-alert-result-001";
        saveQualityResult(resultId, ruleId, "FAIL");

        orm.runInSession(session -> {
            // null ctx：必须不抛异常（修复前此处 NPE）
            WfReference ref = alertWorkflowService.createAlertWorkflow(
                    daoProvider.daoFor(NopMetaQualityResult.class).getEntityById(resultId), null);
            assertNotNull(ref, "createAlertWorkflow must return a workflow reference (was NPE before fix)");
            assertEquals("qualityBreachApproval", ref.getWfName());

            IWorkflow wf = wfManager.getWorkflow(ref.getWfId());
            assertNotNull(wf, "started workflow must be loadable from the manager");
            assertEquals("NopMetaQualityResult", wf.getRecord().getBizObjName());
            assertEquals(resultId, wf.getRecord().getBizObjId());
            assertEquals(Integer.valueOf(30), wf.getRecord().getStatus(),
                    "workflow must be ACTIVATED after start: " + wf.getRecord().getStatus());
            requireActiveStep(wf, "owner-investigate");
            session.flush();
            return null;
        });
    }

    // ===== MA7.6-03：qualityBreachApproval verify 失败路径（fail-closed） =====

    /**
     * ruleId 缺失（start 未携带 wfVars.ruleId）→ verify 脚本 fail-closed 置 appState='reject'
     * （修复前 else 分支静默置 agree 假通过）→ 回退 owner-investigate 重新激活，流程不完成。
     */
    @Test
    public void testQualityBreachApprovalVerifyMissingRuleIdRejects() {
        ensureRole("metadata-admin");
        ensureUser("wf-admin");
        linkUserRole("wf-admin", "metadata-admin");

        String resultId = "wf-alert-result-norule";
        saveQualityResult(resultId, "wf-alert-rule-norule", "FAIL");

        IServiceContext adminCtx = newContext("wf-admin");
        orm.runInSession(session -> {
            IWorkflow wf = wfManager.newWorkflow("qualityBreachApproval", 1L);
            wf.getRecord().setBizObjName("NopMetaQualityResult");
            wf.getRecord().setBizObjId(resultId);
            // 不携带 ruleId 的 start（模拟畸形 wfVars）
            wf.start(Map.of(), adminCtx);
            while (wf.runAutoTransitions(adminCtx)) {
            }

            IWorkflowStep owner = requireActiveStep(wf, "owner-investigate");
            owner.invokeAction("agree", null, adminCtx);
            while (wf.runAutoTransitions(adminCtx)) {
            }

            // verify 步骤脚本已执行：ruleId 缺失 → fail-closed appState='reject'
            List<? extends IWorkflowStep> verifySteps = wf.getStepsByName("verify", true);
            assertFalse(verifySteps.isEmpty(), "verify step must have run");
            assertEquals("reject", verifySteps.get(0).getRecord().getAppState(),
                    "missing ruleId must fail-closed to reject (was silent agree before fix)");
            assertNotEquals(Integer.valueOf(40), wf.getRecord().getStatus(),
                    "workflow must NOT complete on missing ruleId (stays open for owner re-handling)");
            // 回退后 owner-investigate 重新激活（有恢复路径，流程不卡死不静默关闭）
            requireActiveStep(wf, "owner-investigate");
            session.flush();
            return null;
        });
    }

    /**
     * reJudge 抛异常（规则目标表不存在 → ERR_QUALITY_TABLE_NOT_FOUND）→ verify 脚本 try/catch
     * fail-closed 置 appState='reject'（修复前异常穿过 runStepAutoTransition 使 verify 步骤永久卡死）
     * → 回退 owner-investigate 重新激活。
     */
    @Test
    public void testQualityBreachApprovalVerifyReJudgeFailureRejects() {
        ensureRole("metadata-admin");
        ensureUser("wf-admin");
        linkUserRole("wf-admin", "metadata-admin");

        // 规则指向不存在的目标表：createAlertWorkflow 可正常启动（不解析表），verify 的 reJudge 必然抛错
        String ruleId = "wf-alert-rule-deleted";
        saveQualityRule(ruleId, "__missing_table__");
        String resultId = "wf-alert-result-rejudge";
        saveQualityResult(resultId, ruleId, "FAIL");

        IServiceContext adminCtx = newContext("wf-admin");
        orm.runInSession(session -> {
            // 经生产入口启动（携带 ruleId）
            WfReference ref = alertWorkflowService.createAlertWorkflow(
                    daoProvider.daoFor(NopMetaQualityResult.class).getEntityById(resultId), adminCtx);
            IWorkflow wf = wfManager.getWorkflow(ref.getWfId());
            while (wf.runAutoTransitions(adminCtx)) {
            }

            IWorkflowStep owner = requireActiveStep(wf, "owner-investigate");
            owner.invokeAction("agree", null, adminCtx);
            while (wf.runAutoTransitions(adminCtx)) {
            }

            // reJudge 异常被 verify 脚本捕获 → fail-closed appState='reject'（修复前流程卡死在 verify）
            List<? extends IWorkflowStep> verifySteps = wf.getStepsByName("verify", true);
            assertFalse(verifySteps.isEmpty(), "verify step must have run");
            assertEquals("reject", verifySteps.get(0).getRecord().getAppState(),
                    "reJudge failure must fail-closed to reject (was stuck before fix)");
            assertNotEquals(Integer.valueOf(40), wf.getRecord().getStatus(),
                    "workflow must NOT complete on reJudge failure");
            requireActiveStep(wf, "owner-investigate");
            session.flush();
            return null;
        });
    }

    // ===== helpers =====

    private void saveQualityRule(String ruleId, String entityId) {
        IEntityDao<NopMetaQualityRule> dao = daoProvider.daoFor(NopMetaQualityRule.class);
        NopMetaQualityRule rule = dao.newEntity();
        rule.setQualityRuleId(ruleId);
        rule.setRuleName(ruleId);
        rule.setDisplayName(ruleId);
        rule.setRuleType("volume");
        rule.setEntityType("table");
        rule.setEntityId(entityId);
        rule.setSeverity("ERROR");
        rule.setVersion(1L);
        rule.setCreatedBy("autotest");
        rule.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        dao.saveEntity(rule);
    }

    private void saveQualityResult(String resultId, String ruleId, String status) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult result = dao.newEntity();
        result.setQualityResultId(resultId);
        result.setQualityRuleId(ruleId);
        result.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        result.setStatus(status);
        result.setMessage("alert for " + ruleId);
        result.setVersion(1L);
        result.setCreatedBy("autotest");
        result.setUpdatedBy("autotest");
        dao.saveEntity(result);
    }

    private IServiceContext newContext(String userId) {
        IServiceContext context = new ServiceContextImpl();
        ContextProvider.getOrCreateContext().setUserId(userId);
        context.getContext().setUserId(userId);
        return context;
    }

    private IWorkflowStep requireActiveStep(IWorkflow wf, String stepName) {
        List<? extends IWorkflowStep> steps = wf.getActivatedSteps();
        for (IWorkflowStep step : steps) {
            if (step.getStepName().equals(stepName)) {
                return step;
            }
        }
        throw new AssertionError("active step not found: " + stepName + " in " + wf.getActivatedSteps());
    }

    private void ensureRole(String roleId) {
        IEntityDao<io.nop.auth.dao.entity.NopAuthRole> roleDao = daoProvider.daoFor(io.nop.auth.dao.entity.NopAuthRole.class);
        if (roleDao.getEntityById(roleId) == null) {
            io.nop.auth.dao.entity.NopAuthRole role = roleDao.newEntity();
            role.setRoleId(roleId);
            role.setRoleName(roleId);
            roleDao.saveEntity(role);
        }
    }

    private void ensureUser(String userId) {
        IEntityDao<io.nop.auth.dao.entity.NopAuthUser> userDao = daoProvider.daoFor(io.nop.auth.dao.entity.NopAuthUser.class);
        if (userDao.getEntityById(userId) == null) {
            io.nop.auth.dao.entity.NopAuthUser user = userDao.newEntity();
            user.setUserId(userId);
            user.setUserName("user_" + userId);
            user.setNickName(userId);
            user.setPassword("123");
            user.setOpenId(userId);
            user.setTenantId("0");
            user.setUserType(1);
            user.setStatus(1);
            user.setGender(1);
            userDao.saveEntity(user);
        }
    }

    private void linkUserRole(String userId, String roleId) {
        IEntityDao<io.nop.auth.dao.entity.NopAuthUserRole> linkDao =
                daoProvider.daoFor(io.nop.auth.dao.entity.NopAuthUserRole.class);
        io.nop.auth.dao.entity.NopAuthUserRole link = linkDao.newEntity();
        link.setUserId(userId);
        link.setRoleId(roleId);
        linkDao.saveEntity(link);
    }

    private void saveContract(String contractId, String status, String approveStatus) {
        IEntityDao<NopMetaDataContract> dao = daoProvider.daoFor(NopMetaDataContract.class);
        NopMetaDataContract c = dao.newEntity();
        c.setContractId(contractId);
        c.setContractName(contractId + "-name");
        c.setDisplayName(contractId + "-name");
        c.setStatus(status);
        c.setApproveStatus(approveStatus);
        c.setOwnerUserId("wf-starter");
        c.setVersion(1L);
        c.setCreatedBy("autotest");
        c.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreateTime(now);
        c.setUpdateTime(now);
        dao.saveEntity(c);
    }

    private void saveTagLabel(String tagLabelId, String approveStatus) {
        IEntityDao<NopMetaTagLabel> dao = daoProvider.daoFor(NopMetaTagLabel.class);
        NopMetaTagLabel label = dao.newEntity();
        label.setTagLabelId(tagLabelId);
        label.setSource("Classification");
        label.setTagId("wf-e2e-tag");
        label.setLabelType("Manual");
        label.setState("Suggested");
        label.setApproveStatus(approveStatus);
        label.setEntityType("NopMetaEntityField");
        label.setEntityId("wf-e2e-field");
        label.setVersion(1L);
        label.setCreatedBy("autotest");
        label.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        label.setCreateTime(now);
        label.setUpdateTime(now);
        dao.saveEntity(label);
    }
}
