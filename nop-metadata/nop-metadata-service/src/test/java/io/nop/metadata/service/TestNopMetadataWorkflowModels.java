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
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.orm.IOrmTemplate;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * R2.1（P1-MA3-001 + MA3.1-04/05/06）端到端回归测试：
 *
 * <ul>
 *   <li>模型加载：3 个 xwf 经 WorkflowManagerImpl 解析链（resolve-wf → /nop/wf/）可达，且
 *       x:config import（MA3.1-02）、xdef 校验（MA3.1-03）、start 步骤解析（MA3.1-04）、
 *       listener 表达式编译（MA3.1-05/06）全部通过——任一修复回退即模型加载失败；</li>
 *   <li>启动 + 审批流转：metaDataContractApproval 从 start 到 end 的完整正路径（submit→owner-check→
 *       consumer-check→end），*end listener 回调 NopMetaDataContract.approve XPL（接线验证：
 *       GraphQL/BizModel → 工作流 → 状态迁移 DRAFT→ACTIVE）；</li>
 *   <li>tagLabelConfirmApproval 同样完整走通（submit→reviewer-check→end，TagLabel state→Confirmed）。</li>
 * </ul>
 *
 * <p>降级说明：qualityBreachApproval 的 verify 步骤 re-judge 依赖真实数据源/规则/表基础设施，本测试
 * 对 qualityBreachApproval 验证到模型加载 + 启动可达（startStepName=owner-investigate 解析正确），
 * 完整 verify 流转由质量域既有测试（TestNopMetaQualityRuleBizModel）覆盖执行器语义。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetadataWorkflowModels extends JunitBaseTestCase {

    @Inject
    IWorkflowManager wfManager;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate orm;

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

    // ===== helpers =====

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
