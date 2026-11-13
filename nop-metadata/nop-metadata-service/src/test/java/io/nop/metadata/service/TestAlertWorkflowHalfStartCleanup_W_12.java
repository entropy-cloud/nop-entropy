
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.service.quality.QualityAlertWorkflowProcessor;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.core.NopWfCoreErrors;
import io.nop.wf.dao.entity.NopWfInstance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W-12 回归钉（plan 2528 红⑨，CRA 5.5 C7-R3-001）：告警工作流半个成功归一（R10 族分支 (a) 补偿）。
 *
 * <p>缺陷形态（pre-fix）：{@code wf.start} save-then-finish 窗口——引擎 {@code initCreateStatus}
 * 先落库 CREATED 实例行 → 起始步 owner-investigate 的 actor 解析（role=metadata-admin，本测试
 * <b>不播种</b>该角色）抛 {@code ERR_WF_ACTOR_NOT_EXISTS} → 调用方既有 catch 吞异常 → 事务照常提交
 * CREATED 零 step 孤儿实例，规则每次重试累积一个不可见孤儿。
 *
 * <p>修复后（裁定⑨分支 (a)）：{@code createAlertWorkflow} 内半程自清理——经公开
 * {@code IWorkflowStore#removeWfRecord} 删除半程实例后<b>重抛原异常</b>（调用方隔离语义零变化），
 * 孤儿行不存在。前置验证（设计评审 m4）：nop-wf 引擎 bean + ORM 在 nop-metadata-service autotest
 * 上下文可用——TestNopMetadataWorkflowModels 已实证（同上下文真实启动 qualityBreachApproval）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/metadata/beans/test-mock.beans.xml")
public class TestAlertWorkflowHalfStartCleanup_W_12 extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate orm;

    @Inject
    QualityAlertWorkflowProcessor alertWorkflowService;

    /**
     * 半程失败 fixture：起始步角色（metadata-admin）未播种 → getActors 确定性抛
     * ERR_WF_ACTOR_NOT_EXISTS（WfActorAssignSupport resolveActor 返回 null）。
     */
    @Test
    public void testHalfStartFailureLeavesNoOrphanWfInstance() {
        String ruleId = "w12-orphan-rule-001";
        saveQualityRule(ruleId, "w12-entity-001");
        NopMetaQualityResult result = newResult("w12-orphan-result-001", ruleId);

        orm.runInSession(session -> {
            // 原异常必须显式可见（fail-visible 非吞——重抛语义与分支 (b) 的差异：调用方既有
            // catch 隔离保持零变化，本钉直接调生产入口故异常可达）
            NopException ex = assertThrows(NopException.class,
                    () -> alertWorkflowService.createAlertWorkflow(result, new ServiceContextImpl()),
                    "actor resolution failure must propagate (fail-visible, W-12 half-start)");
            assertEquals(NopWfCoreErrors.ERR_WF_ACTOR_NOT_EXISTS.getErrorCode(), ex.getErrorCode(),
                    "original ERR_WF_ACTOR_NOT_EXISTS must not be replaced by the cleanup path");
            session.flush();
            return null;
        });

        // 核心判据：无 CREATED 零 step 孤儿实例残留（pre-fix 此处为 1 → 红）
        int orphanCount = countWfInstances("qualityBreachApproval");
        assertEquals(0, orphanCount,
                "half-started alert workflow instance must be cleaned up in the same transaction"
                        + " (no CREATED zero-step orphan, W-12): found " + orphanCount);
    }

    /** 半程失败后重试可恢复：角色补播种 → 同一入口再次创建成功（半程清理不破坏正常路径）。 */
    @Test
    public void testRetryAfterHalfStartFailureSucceedsOnceRoleSeeded() {
        String ruleId = "w12-retry-rule-001";
        saveQualityRule(ruleId, "w12-entity-002");
        NopMetaQualityResult result = newResult("w12-retry-result-001", ruleId);

        orm.runInSession(session -> {
            // 第一次：角色未播种 → 半程失败（原异常传播）
            assertThrows(NopException.class,
                    () -> alertWorkflowService.createAlertWorkflow(result, new ServiceContextImpl()));
            assertEquals(0, countWfInstances("qualityBreachApproval"), "no orphan after failed attempt");
            session.flush();
            return null;
        });

        // 第二次（种子角色后）：正常启动成功——清理路径零副作用
        ensureRole("metadata-admin");
        ensureUser("wf-admin");
        linkUserRole("wf-admin", "metadata-admin");
        orm.runInSession(session -> {
            io.nop.wf.api.WfReference ref =
                    alertWorkflowService.createAlertWorkflow(result, new ServiceContextImpl());
            assertNotNull(ref, "retry with seeded role must start the alert workflow");
            session.flush();
            return null;
        });
        assertEquals(1, countWfInstances("qualityBreachApproval"),
                "exactly one instance from the successful retry (half-start cleanup must not over-delete)");
    }

    // ===== helpers =====

    private void ensureRole(String roleId) {
        IEntityDao<io.nop.auth.dao.entity.NopAuthRole> roleDao =
                daoProvider.daoFor(io.nop.auth.dao.entity.NopAuthRole.class);
        if (roleDao.getEntityById(roleId) == null) {
            io.nop.auth.dao.entity.NopAuthRole role = roleDao.newEntity();
            role.setRoleId(roleId);
            role.setRoleName(roleId);
            roleDao.saveEntity(role);
        }
    }

    private void ensureUser(String userId) {
        IEntityDao<io.nop.auth.dao.entity.NopAuthUser> userDao =
                daoProvider.daoFor(io.nop.auth.dao.entity.NopAuthUser.class);
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

    private int countWfInstances(String wfName) {
        IEntityDao<NopWfInstance> wfDao = daoProvider.daoFor(NopWfInstance.class);
        List<NopWfInstance> rows = wfDao.findAll();
        int count = 0;
        for (NopWfInstance row : rows) {
            if (wfName.equals(row.getWfName())) {
                count++;
            }
        }
        return count;
    }

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

    private NopMetaQualityResult newResult(String resultId, String ruleId) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        NopMetaQualityResult result = dao.newEntity();
        result.setQualityResultId(resultId);
        result.setQualityRuleId(ruleId);
        result.setExecuteTime(new Timestamp(System.currentTimeMillis()));
        result.setStatus("FAIL");
        result.setMessage("alert for " + ruleId);
        result.setVersion(1L);
        result.setCreatedBy("autotest");
        result.setUpdatedBy("autotest");
        dao.saveEntity(result);
        return dao.getEntityById(resultId);
    }
}
