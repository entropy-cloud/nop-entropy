
package io.nop.metadata.service.cleanup;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.dao.entity.NopWfInstance;
import io.nop.wf.dao.entity.NopWfStatusHistory;
import io.nop.wf.dao.entity.NopWfStepInstance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-02 第七面回归钉（plan 2528 红⑩，CRA 5.5 C7-101 衍生面——plan 2502 裁定④ 显式归属 R3-05 的承接补钉，
 * retention 四件套）：
 * <ul>
 * <li>(i) 清理——存量终态超期实例（status ∈ {COMPLETED,EXPIRED,FAILED,KILLED} 且 endTime 超窗）
 *     被清，子表（steps/statusHistories，nop-wf.orm.xml cascadeDelete 面）随父行 wfId IN 集传递清扫</li>
 * <li>(ii) 保留——活跃实例（ACTIVATED / CREATED 已含 step）与未到期终态行零误删
 *     （活跃审批红线：审批中告警实例不可 TTL 误删）+ CREATED 零步超期残量被通道 2 清除</li>
 * <li>(iii) fail-fast 包络——负保留期启动期拒绝（第七通道与六表共享同一调度包络）</li>
 * <li>(iv) 接线——CapturingTimer 形态断言第七通道经同一周期链真实调度（防 C7-005 空壳先例）+ 幂等复跑</li>
 * </ul>
 * <p>红判据（构造性当前即红）：pre-fix 无任何清理通道——终态超期实例行与 CREATED 零步残量持久留存。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestWfInstanceRetention_W_02_Face7 extends JunitAutoTestCase {

    private static final long DAY_MS = 24L * 3600_000L;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    /** (i)+(ii)：终态超期双实例+子表级联清除；活跃实例与未到期终态行保留。 */
    @Test
    void testFace7_expiredTerminalInstancesPurgedWithChildren_activeRetained() {
        String expiredCompleted = seedInstance("w02f7-exp-completed", NopWfCoreConstants.WF_STATUS_COMPLETED);
        String expiredKilled = seedInstance("w02f7-exp-killed", NopWfCoreConstants.WF_STATUS_KILLED);
        String unexpiredTerminal = seedInstance("w02f7-fresh-completed", NopWfCoreConstants.WF_STATUS_COMPLETED);
        String activeExpired = seedInstance("w02f7-active-act", NopWfCoreConstants.WF_STATUS_ACTIVATED);

        // 终态超期锚列 endTime：31 天前（保留窗 30 天之外）；未到期终态 10 天前；活跃实例无 endTime
        backdateEndTime(expiredCompleted, 31 * DAY_MS);
        backdateEndTime(expiredKilled, 40 * DAY_MS);
        backdateEndTime(unexpiredTerminal, 10 * DAY_MS);

        // 子表面：终态超期实例挂 1 step + 1 statusHistory（级联随父行清扫）
        String childStep = seedStep("w02f7-step-exp", expiredCompleted);
        String childHistory = seedStatusHistory("w02f7-hist-exp", expiredCompleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        NopMetaLedgerCleaner cleaner = newCleaner();
        long deleted = cleaner.purgeExpiredWfInstances(30 * DAY_MS, 1000);
        assertEquals(2, deleted, "exactly the two expired terminal instances must be purged, deleted=" + deleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(instanceDao().getEntityById(expiredCompleted), "expired COMPLETED instance must be purged");
        assertNull(instanceDao().getEntityById(expiredKilled), "expired KILLED instance must be purged");
        assertNull(stepDao().getEntityById(childStep),
                "step child row must follow the parent instance (wfId IN-set propagation)");
        assertNull(historyDao().getEntityById(childHistory),
                "statusHistory child row must follow the parent instance");

        assertNotNull(instanceDao().getEntityById(unexpiredTerminal),
                "unexpired terminal instance retained (within retention window)");
        assertNotNull(instanceDao().getEntityById(activeExpired),
                "active (ACTIVATED) instance must never be TTL-deleted (approval red line)");
    }

    /** 通道 2：CREATED 零步超期残量清除；CREATED 已含 step（在途流程）零误删。 */
    @Test
    void testFace7_createdZeroStepOrphanPurged_createdWithStepsRetained() {
        String orphan = seedInstance("w02f7-orphan-created", NopWfCoreConstants.WF_STATUS_CREATED);
        String inFlight = seedInstance("w02f7-inflight-created", NopWfCoreConstants.WF_STATUS_CREATED);
        backdateCreateTimeOfInstance(orphan, 31 * DAY_MS);
        backdateCreateTimeOfInstance(inFlight, 31 * DAY_MS);
        String inFlightStep = seedStep("w02f7-step-inflight", inFlight);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        NopMetaLedgerCleaner cleaner = newCleaner();
        long deleted = cleaner.purgeExpiredWfInstances(30 * DAY_MS, 1000);
        assertEquals(1, deleted, "only the zero-step CREATED residue must be purged, deleted=" + deleted);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        assertNull(instanceDao().getEntityById(orphan),
                "CREATED zero-step expired residue (W-12 legacy orphan / crash window) must be purged");
        assertNotNull(instanceDao().getEntityById(inFlight),
                "CREATED instance that already has a step record is an in-flight flow and must be retained"
                        + " (active-instance red line)");
        assertNotNull(stepDao().getEntityById(inFlightStep), "in-flight step row must be untouched");
    }

    /** (iv) 接线：第七通道经生产生命周期链真实调度（CapturingTimer），被调度命令在真实 DB 链上清除；幂等复跑。 */
    @Test
    void testFace7_lifecycleChainInvokesWfInstanceCleanup() {
        String expired = seedInstance("w02f7-wiring-completed", NopWfCoreConstants.WF_STATUS_COMPLETED);
        backdateEndTime(expired, 31 * DAY_MS);

        ormTemplate.flushSession();
        ormTemplate.clearSession();

        TestMetaLedgerRetention_W_02.CapturingTimer timer = new TestMetaLedgerRetention_W_02.CapturingTimer();
        NopMetaLedgerCleaner cleaner = newCleaner();
        cleaner.setTimer(timer);
        cleaner.setWorker(Runnable::run);
        cleaner.setLedgerRetentionDays(30);
        cleaner.setLedgerCleanupInterval(java.time.Duration.ofHours(1));
        cleaner.setLedgerDeleteBatchSize(500);

        cleaner.start();

        assertEquals(NopMetaLedgerCleaner.TASK_NAME, timer.taskName.get(),
                "wiring: the seventh channel rides the same lifecycle-scheduled cleanup chain");
        timer.command.get().run();

        ormTemplate.flushSession();
        ormTemplate.clearSession();
        assertNull(instanceDao().getEntityById(expired),
                "expired wf instance must be purged through the scheduled cleanup command (wiring chain)");

        // 幂等复跑：第二轮零删除
        long second = timer.command.get() == null ? 0
                : newCleaner().purgeExpiredWfInstances(30 * DAY_MS, 1000);
        assertEquals(0L, second, "second round must be a no-op (idempotent predicate)");

        cleaner.stop();
        assertTrue(timer.cancelled.get(), "doStop must cancel the cleanup chain");
    }

    /** (iii) fail-fast 包络：负保留期启动期拒绝（第七通道共享同一包络——无静默降级）。 */
    @Test
    void testFace7_negativeRetentionStillFailsFastWithSeventhChannel() {
        NopMetaLedgerCleaner cleaner = newCleaner();
        cleaner.setTimer(new TestMetaLedgerRetention_W_02.CapturingTimer());
        cleaner.setWorker(Runnable::run);
        cleaner.setLedgerRetentionDays(-1);
        NopException ex = assertThrows(NopException.class, cleaner::start,
                "negative retention must fail fast at startup (envelope shared with the seventh channel)");
        assertEquals("nop.err.metadata.ledger-config-invalid", ex.getErrorCode());
    }

    // ==================== helpers ====================

    private NopMetaLedgerCleaner newCleaner() {
        NopMetaLedgerCleaner cleaner = new NopMetaLedgerCleaner();
        cleaner.setDaoProvider(daoProvider);
        return cleaner;
    }

    private IEntityDao<NopWfInstance> instanceDao() {
        return daoProvider.daoFor(NopWfInstance.class);
    }

    private IEntityDao<NopWfStepInstance> stepDao() {
        return daoProvider.daoFor(NopWfStepInstance.class);
    }

    private IEntityDao<NopWfStatusHistory> historyDao() {
        return daoProvider.daoFor(NopWfStatusHistory.class);
    }

    private String seedInstance(String wfId, int status) {
        NopWfInstance inst = instanceDao().newEntity();
        inst.setWfId(wfId);
        inst.setWfName("qualityBreachApproval");
        inst.setWfVersion(1L);
        inst.setWfGroup("default");
        inst.setTitle("face7-seed-" + wfId);
        inst.setStatus(status);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        inst.setStartTime(now);
        if (status != NopWfCoreConstants.WF_STATUS_CREATED) {
            // 非 CREATED 状态需 endTime（终态锚列）；ACTIVATED 场景测试用例用 backdate 控制或保持 null
            inst.setEndTime(now);
        }
        inst.setPriority(0);
        inst.setBizObjName("NopMetaQualityResult");
        inst.setBizObjId("w02f7-biz-" + wfId);
        inst.setVersion(1);
        inst.setCreatedBy("autotest");
        inst.setUpdatedBy("autotest");
        inst.setCreateTime(now);
        inst.setUpdateTime(now);
        instanceDao().saveEntity(inst);
        return wfId;
    }

    private String seedStep(String stepId, String wfId) {
        NopWfStepInstance step = stepDao().newEntity();
        step.setStepId(stepId);
        step.setWfId(wfId);
        step.setStepType("start");
        step.setStepName("owner-investigate");
        step.setDisplayName("owner-investigate");
        step.setPriority(0);
        step.setExecOrder(0);
        step.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        step.setVersion(1);
        step.setCreatedBy("autotest");
        step.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        step.setCreateTime(now);
        step.setUpdateTime(now);
        stepDao().saveEntity(step);
        return stepId;
    }

    private String seedStatusHistory(String sid, String wfId) {
        NopWfStatusHistory history = historyDao().newEntity();
        history.setSid(sid);
        history.setWfId(wfId);
        history.setFromStatus(NopWfCoreConstants.WF_STATUS_CREATED);
        history.setToStatus(NopWfCoreConstants.WF_STATUS_COMPLETED);
        history.setChangeTime(new Timestamp(System.currentTimeMillis()));
        history.setVersion(1);
        history.setCreatedBy("autotest");
        history.setCreateTime(new Timestamp(System.currentTimeMillis()));
        historyDao().saveEntity(history);
        return sid;
    }

    private void backdateEndTime(String wfId, long ageMillis) {
        SQL sql = SQL.begin()
                .sql("update NopWfInstance o set o.endTime = ? where o.wfId = ?",
                        new Timestamp(System.currentTimeMillis() - ageMillis), wfId)
                .end();
        ormTemplate.executeUpdate(sql);
    }

    private void backdateCreateTimeOfInstance(String wfId, long ageMillis) {
        SQL sql = SQL.begin()
                .sql("update NopWfInstance o set o.createTime = ? where o.wfId = ?",
                        new Timestamp(System.currentTimeMillis() - ageMillis), wfId)
                .end();
        ormTemplate.executeUpdate(sql);
    }
}
