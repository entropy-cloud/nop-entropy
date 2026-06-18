package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.task.impl.TaskStepRuntimeImpl;
import io.nop.task.state.TaskStepStateBean;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 263 Phase 2 cross-restart E2E：经真实 {@link DaoTaskStateStore}（localDb，实体↔DB 列 JSON 序列化，非 in-memory 引用）
 * 端到端验证 mainStep envelope intermediate-state restore 经 DB 序列化边界存活。
 *
 * <p>cross-restart 模型：store A（模拟进程 1）save task instance + mainStep state（bodyStepIndex=N）到 DB →
 * store B（fresh 实例，模拟进程重启）loadTaskState + newMainStepRuntime 加载持久化 mainStep 状态（findStepEntity
 * 查 DB 行 + toStepStateBean 反序列化恢复 bodyStepIndex=N + afterLoad）→ mainStep 从 N 续跑完成。
 *
 * <p>Anti-Hollow 断言（#22/#23/#24）：
 * <ul>
 *   <li>端到端路径连通：seed → DB → fresh load → resume 续跑完成（非空壳、非静默跳过）</li>
 *   <li>前置已完成子步骤 body 执行计数为 0（证明 mainStep 控制流位置本身被恢复）</li>
 *   <li>后续子步骤正常执行（续跑真实推进，非跳过全部）</li>
 *   <li>续跑语义经 DB 序列化边界存活（bodyStepIndex 经 copyStepStateToEntity 写 / toStepStateBean 读 round-trip）</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/task/test/beans/test-reliability.beans.xml")
public class TestMainStepEnvelopeResumeCrossRestart extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private ExecutionCounterBean counter;

    @BeforeEach
    public void setUpCrossRestart() {
        counter = (ExecutionCounterBean) io.nop.api.core.ioc.BeanContainer.instance()
                .getBean("testExecutionCounter");
        counter.reset();
    }

    private DaoTaskStateStore newDaoStore() {
        DaoTaskStateStore store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);
        return store;
    }

    private TaskFlowManagerImpl newTaskFlowManager(DaoTaskStateStore store) {
        TaskFlowManagerImpl mgr = new TaskFlowManagerImpl();
        mgr.setTaskStateStore(store);
        mgr.setNonPersistStateStore(store);
        return mgr;
    }

    /**
     * 进程 1（store A）：构造并持久化 ACTIVE task + mainStep state（bodyStepIndex=N）到 DB。
     * 模拟 composite mainStep 执行至中间位置后进程中断。
     */
    private String seedWithStoreA(int bodyStepIndex) {
        DaoTaskStateStore storeA = newDaoStore();
        TaskFlowManagerImpl mgrA = newTaskFlowManager(storeA);

        TaskRuntimeImpl seedRuntime = new TaskRuntimeImpl(mgrA, storeA, null, XLang.newEvalScope(), false);
        ITaskState taskState = storeA.newTaskState("mainstep-envelope-resume", 0, seedRuntime);
        seedRuntime.setTaskState(taskState);

        ITaskStepState mainState = storeA.newMainStepState(taskState);
        mainState.setBodyStepIndex(bodyStepIndex);

        TaskStepRuntimeImpl seedStepRt = new TaskStepRuntimeImpl(seedRuntime, storeA, seedRuntime.getEvalScope());
        seedStepRt.setState(mainState);
        seedStepRt.saveState();

        return taskState.getTaskInstanceId();
    }

    /**
     * 进程 2（store B，fresh 实例模拟进程重启）：fresh load + resume 续跑。
     */
    private Map<String, Object> resumeWithStoreB(String taskInstanceId) {
        DaoTaskStateStore storeB = newDaoStore();
        TaskFlowManagerImpl mgrB = newTaskFlowManager(storeB);

        ITask task = mgrB.getTask("test/mainstep-envelope-resume", 0);
        ITaskRuntime taskRt = mgrB.getTaskRuntime(taskInstanceId, null, null);
        assertTrue(taskRt.isRecoverMode(), "cross-restart: getTaskRuntime must set recoverMode=true");
        return task.execute(taskRt).syncGetOutputs();
    }

    // ==================== cross-restart E2E: bodyStepIndex survives DB serialization boundary ====================

    /**
     * 端到端验证（#22）：cross-restore 从 fresh seed（store A 写 bodyStepIndex=2 经 copyStepStateToEntity 到 DB 列）
     * → fresh store B（模拟进程重启）loadTaskState + newMainStepRuntime（findStepEntity 查 DB + toStepStateBean 反序列化恢复 bodyStepIndex=2）
     * → mainStep 从 index=2 续跑完成 完整路径连通（真实 DaoTaskStateStore，非 in-memory 引用）。
     *
     * <p>Anti-Hollow 断言：
     * <ul>
     *   <li>前置已完成子步骤（s1, s2）body 执行计数为 0（counter=1，仅 s3）——证明 mainStep 控制流位置本身被恢复（bodyStepIndex=2 从 DB 加载），非从 0 重跑</li>
     *   <li>后续子步骤（s3）正常执行（counter 递增 + 返回 "OK"）——续跑真实推进</li>
     *   <li>续跑语义经 DB 序列化边界存活（bodyStepIndex=2 写入 DB → fresh store 读取恢复为 2）</li>
     * </ul>
     */
    @Test
    public void crossRestart_bodyStepIndex2_survivesDbSerializationBoundary_mainStepResumesFrom2() {
        // === 进程 1（store A）：seed task ACTIVE + mainStep bodyStepIndex=2 ===
        String taskInstanceId = seedWithStoreA(2);

        // 验证 seed 确实写入 DB（bodyStepIndex 经 copyStepStateToEntity:314 写入 entity 列）
        DaoTaskStateStore verifyStore = newDaoStore();
        TaskRuntimeImpl verifyRt = new TaskRuntimeImpl(newTaskFlowManager(verifyStore), verifyStore, null, XLang.newEvalScope(), true);
        ITaskState verifyTask = verifyStore.loadTaskState(taskInstanceId, verifyRt);
        verifyRt.setTaskState(verifyTask);
        ITaskStepState verifyMain = verifyStore.loadMainStepState(verifyTask, verifyRt);
        assertNotNull(verifyMain, "seed must persist @main step to DB");
        assertEquals(2, verifyMain.getBodyStepIndex(),
                "bodyStepIndex=2 must be in DB (copyStepStateToEntity writes bodyStepIndex column)");

        counter.reset();

        // === 进程 2（store B，fresh 实例）：cross-restart resume ===
        Map<String, Object> ret = resumeWithStoreB(taskInstanceId);

        // Anti-Hollow: 前置子步骤不重复执行（counter=1 仅 s3），后续子步骤正常执行（返回 "OK"）
        assertEquals(1, counter.get(),
                "Anti-Hollow cross-restart: preceding completed sub-steps (s1,s2) must NOT be re-executed "
                        + "(counter=1, only s3). If mainStep restarted from 0, counter would be 3. "
                        + "counter=1 proves bodyStepIndex=2 survived DB serialization boundary and was loaded by fresh store B.");
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "cross-restart resume: s3 executes normally and returns 'OK' (subsequent step runs, not all skipped)");
    }

    /**
     * Anti-Hollow 续跑推进验证：cross-restart 从 bodyStepIndex=1 续跑——s1 跳过（counter 不递增），s2+s3 执行（counter=2）。
     * 证明不同 bodyStepIndex 下 cross-restart 续跑语义均生效。
     */
    @Test
    public void crossRestart_bodyStepIndex1_survivesDbSerializationBoundary_mainStepResumesFrom1() {
        String taskInstanceId = seedWithStoreA(1);
        counter.reset();

        Map<String, Object> ret = resumeWithStoreB(taskInstanceId);

        assertEquals(2, counter.get(),
                "Anti-Hollow cross-restart from bodyStepIndex=1: s1 skipped, s2+s3 run (counter=2). "
                        + "Proves cross-restart bodyStepIndex restore works for different N values.");
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "cross-restart resume from bodyStepIndex=1: s2+s3 run, returns 'OK'");
    }

    /**
     * 对照组：fresh execute（无 cross-restart，bodyStepIndex=0）全部 3 子步执行（counter=3）。
     * 证明 cross-restart counter=1/2 是 bodyStepIndex restore 的效果，非其他原因。
     */
    @Test
    public void crossRestart_freshExecute_controlGroup_allStepsRun_counterThree() {
        DaoTaskStateStore store = newDaoStore();
        TaskFlowManagerImpl mgr = newTaskFlowManager(store);

        ITask task = mgr.getTask("test/mainstep-envelope-resume", 0);
        ITaskRuntime taskRt = mgr.newTaskRuntime(task, true, null);
        Map<String, Object> ret = task.execute(taskRt).syncGetOutputs();

        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT));
        assertEquals(3, counter.get(),
                "fresh execute control group: all 3 sub-steps run (counter=3). "
                        + "This proves cross-restart counter=1/2 is due to bodyStepIndex restore.");
    }

    /**
     * 零回归：cross-restart 终态 task resume 短路（plans 259/260）行为不变——
     * COMPLETED task 经 store A 持久化 → store B fresh load → 终态短路 → mainStep 不重跑（counter=0）。
     * loadMainStepState wiring 不影响终态短路路径。
     */
    @Test
    public void crossRestart_zeroRegression_terminalTaskShortCircuit_unchanged() {
        // 进程 1: fresh execute 至 COMPLETED（经 store A）
        DaoTaskStateStore storeA = newDaoStore();
        TaskFlowManagerImpl mgrA = newTaskFlowManager(storeA);

        ITask task = mgrA.getTask("test/no-decorator-baseline", 0);
        ITaskRuntime taskRt = mgrA.newTaskRuntime(task, true, null);
        task.execute(taskRt).syncGetOutputs();
        String taskInstanceId = taskRt.getTaskInstanceId();
        assertEquals(1, counter.get(), "first execute: step body executes once");

        counter.reset();

        // 进程 2: store B fresh load → COMPLETED → 终态短路 → mainStep 不重跑
        DaoTaskStateStore storeB = newDaoStore();
        TaskFlowManagerImpl mgrB = newTaskFlowManager(storeB);

        ITask taskB = mgrB.getTask("test/no-decorator-baseline", 0);
        ITaskRuntime resumeRt = mgrB.getTaskRuntime(taskInstanceId, null, null);
        assertTrue(resumeRt.isRecoverMode());
        assertTrue(resumeRt.getTaskState().isTerminal(),
                "COMPLETED task must be terminal → cross-restart resume short-circuits");

        Map<String, Object> ret = taskB.execute(resumeRt).syncGetOutputs();
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT));
        assertEquals(0, counter.get(),
                "zero regression: cross-restart terminal task short-circuit unchanged — mainStep NOT re-run. "
                        + "loadMainStepState wiring does not affect terminal short-circuit path.");
    }
}
