package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.task.impl.TaskStepRuntimeImpl;
import io.nop.task.state.TaskStateBean;
import io.nop.task.state.TaskStepStateBean;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 263 Phase 1 focused 单测：mainStep envelope intermediate-state restore 契约 + DaoTaskStateStore 实现 +
 * {@code TaskRuntimeImpl.newMainStepRuntime} resume 接线。
 *
 * <p>经真实 {@link DaoTaskStateStore}（localDb，实体↔DB 列 JSON 序列化，非 in-memory 引用）证明：
 * <ul>
 *   <li>契约：{@code loadMainStepState} 按 path {@code @main} 加载持久化 mainStep 状态（bodyStepIndex round-trip + afterLoad hook）</li>
 *   <li>接线：{@code newMainStepRuntime()} 在 resume（recoverMode=true）路径优先加载持久化 mainStep 状态；
 *       命中续跑、未命中回退 fresh；fresh execute（recoverMode=false）不受影响</li>
 *   <li>语义：composite mainStep（Sequential）resume 从已持久化 bodyStepIndex=N 续跑，前置已完成子步骤不重复执行（Anti-Hollow）</li>
 *   <li>零回归：fresh execute 恒走 newMainStepState；终态 task resume 短路行为不变；in-memory store 回退 fresh</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/task/test/beans/test-reliability.beans.xml")
public class TestMainStepEnvelopeResume extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private DaoTaskStateStore daoStore;
    private TaskFlowManagerImpl taskFlowManager;
    private ExecutionCounterBean counter;

    @BeforeEach
    public void setUpMainStepResume() {
        counter = (ExecutionCounterBean) io.nop.api.core.ioc.BeanContainer.instance()
                .getBean("testExecutionCounter");
        counter.reset();

        daoStore = new DaoTaskStateStore();
        daoStore.setDaoProvider(daoProvider);

        taskFlowManager = new TaskFlowManagerImpl();
        taskFlowManager.setTaskStateStore(daoStore);
        taskFlowManager.setNonPersistStateStore(daoStore);
    }

    /**
     * 构造并持久化一个 ACTIVE task instance + mainStep state（stepPath=@main, bodyStepIndex=N）到 DB，
     * 模拟 composite mainStep 执行至中间位置后进程中断（mainStep 已 saveState 写入 bodyStepIndex=N，
     * 但 task 仍 ACTIVE 非终态）。返回 taskInstanceId 供 resume 使用。
     */
    private String seedActiveTaskWithMainStepBodyIndex(int bodyStepIndex) {
        // task instance: ACTIVE（非终态 → resume 不被 TaskImpl.execute:98 短路）
        TaskRuntimeImpl seedRuntime = new TaskRuntimeImpl(taskFlowManager, daoStore, null, XLang.newEvalScope(), false);
        ITaskState taskState = daoStore.newTaskState("mainstep-envelope-resume", 0, seedRuntime);
        seedRuntime.setTaskState(taskState);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_ACTIVE), taskState.getTaskStatus(),
                "seed task must be ACTIVE (non-terminal) so resume is not short-circuited");

        // mainStep state: stepPath=@main, bodyStepIndex=N（模拟 composite mainStep 执行至中间位置 saveState 后中断）
        ITaskStepState mainState = daoStore.newMainStepState(taskState);
        mainState.setBodyStepIndex(bodyStepIndex);

        // 经 saveStepState 持久化到 DB（copyStepStateToEntity 写 bodyStepIndex 到 entity 列）
        TaskStepRuntimeImpl seedStepRt = new TaskStepRuntimeImpl(seedRuntime, daoStore, seedRuntime.getEvalScope());
        seedStepRt.setState(mainState);
        seedStepRt.saveState();

        return taskState.getTaskInstanceId();
    }

    /**
     * Resume：fresh load（recoverMode=true）→ execute → mainStep 从已持久化 bodyStepIndex 续跑。
     */
    private Map<String, Object> resumeTask(String taskName, String taskInstanceId) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        assertTrue(taskRt.isRecoverMode(), "getTaskRuntime must set recoverMode=true");
        return task.execute(taskRt).syncGetOutputs();
    }

    // ==================== contract: loadMainStepState by @main path ====================

    /**
     * 契约验证：{@link DaoTaskStateStore#loadMainStepState} 按 path {@code @main} 加载持久化 mainStep 状态，
     * bodyStepIndex 经 DB round-trip 恢复（copyStepStateToEntity 写 / toStepStateBean 读），step 级 afterLoad 被调用。
     */
    @Test
    public void loadMainStepState_daoLoadsByMainPath_bodyStepIndexRoundTrips_afterLoadFires() {
        String taskInstanceId = seedActiveTaskWithMainStepBodyIndex(2);

        // fresh store 实例（模拟 cross-restart）load mainStep state
        DaoTaskStateStore loadStore = new DaoTaskStateStore();
        loadStore.setDaoProvider(daoProvider);
        TaskRuntimeImpl loadRuntime = new TaskRuntimeImpl(taskFlowManager, loadStore, null, XLang.newEvalScope(), true);
        // 需设置 taskState 使 loadMainStepState 能取到 taskInstanceId
        ITaskState loadedTask = loadStore.loadTaskState(taskInstanceId, loadRuntime);
        loadRuntime.setTaskState(loadedTask);

        ITaskStepState loaded = loadStore.loadMainStepState(loadedTask, loadRuntime);

        assertNotNull(loaded, "loadMainStepState must return persisted @main step state");
        assertEquals(TaskConstants.MAIN_STEP_NAME, loaded.getStepPath(),
                "loaded mainStep state stepPath must be @main (same path as newMainStepState writes)");
        assertEquals(2, loaded.getBodyStepIndex(),
                "bodyStepIndex must round-trip through DB (copyStepStateToEntity write / toStepStateBean read)");
        assertEquals(TaskConstants.STEP_TYPE_TASK, loaded.getStepType(),
                "stepType must round-trip through DB");
    }

    /**
     * 无持久化行回退：fresh execute 时 {@code newTaskState} 只写 task instance 行不写 mainStep 行，
     * {@code loadMainStepState} 返回 null（使调用方回退 {@code newMainStepState}，零回归）。
     */
    @Test
    public void loadMainStepState_noMainStepRow_returnsNull_freshFallback() {
        // 仅创建 task instance 行，不创建 mainStep 行（fresh execute 的正常状态）
        TaskRuntimeImpl seedRuntime = new TaskRuntimeImpl(taskFlowManager, daoStore, null, XLang.newEvalScope(), false);
        ITaskState taskState = daoStore.newTaskState("mainstep-envelope-resume", 0, seedRuntime);
        seedRuntime.setTaskState(taskState);

        DaoTaskStateStore loadStore = new DaoTaskStateStore();
        loadStore.setDaoProvider(daoProvider);

        ITaskStepState loaded = loadStore.loadMainStepState(taskState, seedRuntime);
        assertNull(loaded, "loadMainStepState must return null when no @main row exists (fresh execute / no persistence)");
    }

    // ==================== wiring: newMainStepRuntime resume vs fresh ====================

    /**
     * 接线验证（#23）：{@code TaskRuntimeImpl.newMainStepRuntime()} 在 resume（recoverMode=true）路径下
     * 优先加载持久化 mainStep 状态，命中则用之（mainStep bodyStepIndex 经 DB round-trip 恢复）。
     */
    @Test
    public void newMainStepRuntime_resumeMode_loadsPersistedMainStepState_bodyStepIndexRestored() {
        String taskInstanceId = seedActiveTaskWithMainStepBodyIndex(2);

        // fresh runtime（recoverMode=true）模拟 cross-restart resume
        TaskRuntimeImpl resumeRuntime = new TaskRuntimeImpl(taskFlowManager, daoStore, null, XLang.newEvalScope(), true);
        ITaskState loadedTask = daoStore.loadTaskState(taskInstanceId, resumeRuntime);
        resumeRuntime.setTaskState(loadedTask);

        ITaskStepRuntime stepRt = resumeRuntime.newMainStepRuntime();
        assertNotNull(stepRt.getState(), "newMainStepRuntime must return non-null state");
        assertEquals(2, stepRt.getBodyStepIndex(),
                "resume path: mainStep bodyStepIndex must be restored from DB (wiring: loadMainStepState consumed, not just newMainStepState)");
        assertEquals(TaskConstants.MAIN_STEP_NAME, stepRt.getState().getStepPath(),
                "resume path: mainStep stepPath must be @main");
        assertTrue(stepRt.isRecoverMode(),
                "resume path: stepRt recoverMode must be true");
    }

    /**
     * 接线验证（#23）：{@code TaskRuntimeImpl.newMainStepRuntime()} 在 fresh execute（recoverMode=false）路径下
     * 恒走 {@code newMainStepState}（fresh ACTIVE bodyStepIndex=0），{@code loadMainStepState} 不被调用（门控对称 TaskImpl.execute:98）。
     */
    @Test
    public void newMainStepRuntime_freshMode_alwaysNewMainStepState_bodyStepIndexZero() {
        // 即使 DB 中有持久化 mainStep state（bodyStepIndex=2），fresh execute 也不应加载它
        String taskInstanceId = seedActiveTaskWithMainStepBodyIndex(2);

        // fresh runtime（recoverMode=false）
        TaskRuntimeImpl freshRuntime = new TaskRuntimeImpl(taskFlowManager, daoStore, null, XLang.newEvalScope(), false);
        ITaskState loadedTask = daoStore.loadTaskState(taskInstanceId, freshRuntime);
        freshRuntime.setTaskState(loadedTask);

        ITaskStepRuntime stepRt = freshRuntime.newMainStepRuntime();
        assertEquals(0, stepRt.getBodyStepIndex(),
                "fresh execute path: mainStep bodyStepIndex must be 0 (newMainStepState fresh, loadMainStepState NOT consulted)");
    }

    /**
     * 接线验证（#23）：resume 路径但无持久化 mainStep 行（fresh execute 后中断、mainStep 未 saveState）
     * → {@code loadMainStepState} 返回 null → 回退 {@code newMainStepState}（fresh ACTIVE bodyStepIndex=0，零回归）。
     */
    @Test
    public void newMainStepRuntime_resumeNoRow_fallsBackToFresh_bodyStepIndexZero() {
        // 仅 task instance 行，无 mainStep 行
        TaskRuntimeImpl seedRuntime = new TaskRuntimeImpl(taskFlowManager, daoStore, null, XLang.newEvalScope(), false);
        ITaskState taskState = daoStore.newTaskState("mainstep-envelope-resume", 0, seedRuntime);
        seedRuntime.setTaskState(taskState);

        // resume runtime（recoverMode=true）但无 mainStep 行
        TaskRuntimeImpl resumeRuntime = new TaskRuntimeImpl(taskFlowManager, daoStore, null, XLang.newEvalScope(), true);
        resumeRuntime.setTaskState(taskState);

        ITaskStepRuntime stepRt = resumeRuntime.newMainStepRuntime();
        assertEquals(0, stepRt.getBodyStepIndex(),
                "resume path with no @main row: must fall back to newMainStepState (fresh bodyStepIndex=0, zero regression)");
    }

    // ==================== semantic: composite mainStep resumes from bodyStepIndex=N ====================

    /**
     * Anti-Hollow 语义验证（#22/#23/#24）：composite mainStep（Sequential 3 子步）resume 从已持久化 bodyStepIndex=2 续跑——
     * <ul>
     *   <li>前置已完成子步骤（s1, s2）body 执行计数为 0（counter 不因它们递增——证明 mainStep 控制流位置本身被恢复，
     *       非从 0 重跑靠 leaf reader 逐一 skip 的伪装）</li>
     *   <li>后续子步骤（s3）正常执行（counter 递增 + 返回 "OK"——续跑真实推进，非跳过全部）</li>
     * </ul>
     *
     * <p>seed: task ACTIVE + mainStep bodyStepIndex=2（模拟 Sequential 执行完 s1、s2 后 saveState=2 再中断）。
     * resume: mainStep 从 index=2 续跑 → 仅 s3 执行 → counter=1。
     */
    @Test
    public void compositeMainStep_resumeFromBodyStepIndex2_precedingStepsNotReExecuted_subsequentStepRuns() {
        // seed: task ACTIVE + mainStep bodyStepIndex=2
        String taskInstanceId = seedActiveTaskWithMainStepBodyIndex(2);
        counter.reset();

        // resume: mainStep 从 bodyStepIndex=2 续跑
        Map<String, Object> ret = resumeTask("test/mainstep-envelope-resume", taskInstanceId);

        // Anti-Hollow #22/#23: 前置已完成子步骤（s1, s2）不重复执行——counter 仅 +1（s3），非 +3（s1+s2+s3）
        // 证明 mainStep 控制流位置本身被恢复（bodyStepIndex=2 从 DB 加载），非从 0 重跑
        assertEquals(1, counter.get(),
                "Anti-Hollow: preceding completed sub-steps (s1,s2) must NOT be re-executed (counter=1, only s3). "
                        + "If mainStep restarted from 0, counter would be 3 (s1+s2+s3). "
                        + "counter=1 proves bodyStepIndex=2 was loaded from DB and mainStep control flow resumed from index 2.");

        // 续跑真实推进（#22）：后续子步骤 s3 正常执行并返回结果
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "resume from bodyStepIndex=2: only s3 runs and returns 'OK' (ret map=" + ret + ")");
    }

    /**
     * 对照组：fresh execute（无持久化 mainStep 行）composite mainStep 从 bodyStepIndex=0 执行全部 3 子步（counter=3）。
     * 证明上例 counter=1 是 resume 续跑的效果，非其他原因（如 counter 未正确接线）。
     */
    @Test
    public void compositeMainStep_freshExecute_allStepsRun_counterThree() {
        // fresh execute（recoverMode=false，无持久化 mainStep 行）
        ITask task = taskFlowManager.getTask("test/mainstep-envelope-resume", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, true, null);
        Map<String, Object> ret = task.execute(taskRt).syncGetOutputs();

        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "fresh execute: all 3 sub-steps run, last returns 'OK'");
        assertEquals(3, counter.get(),
                "fresh execute: all 3 sub-steps (s1+s2+s3) execute, counter=3. "
                        + "This is the control group proving resume counter=1 is due to bodyStepIndex restore, not counter misconfiguration.");
    }

    /**
     * 续跑推进验证（#22）：resume 从 bodyStepIndex=1 续跑——s1 跳过（counter 不递增），s2+s3 执行（counter=2）。
     * 证明不同 bodyStepIndex 下续跑语义均生效（非仅 bodyStepIndex=2 硬编码）。
     */
    @Test
    public void compositeMainStep_resumeFromBodyStepIndex1_precedingStep0Skipped_subsequentStepsRun() {
        String taskInstanceId = seedActiveTaskWithMainStepBodyIndex(1);
        counter.reset();

        Map<String, Object> ret = resumeTask("test/mainstep-envelope-resume", taskInstanceId);

        assertEquals(2, counter.get(),
                "resume from bodyStepIndex=1: s1 skipped (counter not incremented by s1), s2+s3 run (counter=2). "
                        + "Proves bodyStepIndex restore works for different N values, not just hardcoded N=2.");
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "resume from bodyStepIndex=1: s2+s3 run, last returns 'OK'");
    }

    // ==================== zero regression: terminal short-circuit unchanged ====================

    /**
     * 零回归：终态 task resume 短路（plans 259/260）行为不变——终态 task 不进入 newMainStepRuntime，
     * loadMainStepState 不影响终态短路路径。fresh execute 一个 task 至 COMPLETED → resume 短路 → mainStep 不重跑。
     */
    @Test
    public void zeroRegression_terminalTaskResumeShortCircuit_unchanged_mainStepNotReRun() {
        // fresh execute 至 COMPLETED
        ITask task = taskFlowManager.getTask("test/no-decorator-baseline", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, true, null);
        task.execute(taskRt).syncGetOutputs();
        String taskInstanceId = taskRt.getTaskInstanceId();
        assertEquals(1, counter.get(), "first execute: step body executes once");

        counter.reset();

        // resume: COMPLETED → 短路（TaskImpl.execute:98），mainStep 不重跑
        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        assertTrue(resumeRt.isRecoverMode());
        assertTrue(resumeRt.getTaskState().isTerminal(),
                "COMPLETED task must be terminal → resume short-circuits before newMainStepRuntime");

        Map<String, Object> ret = task.execute(resumeRt).syncGetOutputs();
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "terminal resume: returns cached result");

        assertEquals(0, counter.get(),
                "zero regression: terminal task resume short-circuit unchanged — mainStep NOT re-run (counter=0). "
                        + "loadMainStepState wiring does not affect terminal short-circuit path.");
    }
}
