package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConstants;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.task.state.TaskStateBean;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 262 Phase 2 cross-restart E2E：经真实 {@link DaoTaskStateStore}（localDb）DB round-trip（实体↔DB 列 JSON 序列化，
 * 非 in-memory 引用）证明 task 级 {@code afterLoad}/{@code beforeSave} hook wiring 经持久化边界在 runtime 真实生效
 * （Anti-Hollow #22 端到端 + #23 接线验证）。
 *
 * <p>save 路径：spy store（仅 override {@code newTaskStateBean} 工厂返回 {@link RecordingTaskStateBean}，其余 DB 持久化
 * 逻辑全部沿用真实 store）的 {@code saveTaskState} 调 {@code beforeSave}（count=1）→ 状态经实体拷贝 + remark/errorBeanData
 * JSON 序列化持久化到 DB。
 *
 * <p>load 路径：fresh spy store 实例（模拟进程重启）{@code loadTaskState} 经 {@code taskDao().getEntityById} 取回 fresh entity
 * → {@code toTaskStateBean} 反序列化 → {@code afterLoad}（count=1），hook 调用经 DB 序列化边界存活。
 *
 * <p>各终态（in-progress/COMPLETED/FAILED/KILLED/TIMEOUT）经 store save→load round-trip 后 {@code beforeSave}/
 * {@code afterLoad} 各被精确调用一次（regression guard：精确调用计数断言，防止未来 per-status early-return 静默跳过 hook）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestTaskStateLifecycleHookCrossRestart extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    static final ErrorCode ERR_PLAN_262_CROSS =
            ErrorCode.define("nop.err.test.plan-262.cross-restart", "plan 262 cross-restart exception");

    static class RecordingTaskStateBean extends TaskStateBean {
        int afterLoadCount;
        int beforeSaveCount;
        Integer capturedStatusOnAfterLoad;

        @Override
        public void afterLoad(ITaskRuntime taskRt) {
            afterLoadCount++;
            capturedStatusOnAfterLoad = getTaskStatus();
        }

        @Override
        public void beforeSave(ITaskRuntime taskRt) {
            beforeSaveCount++;
        }
    }

    /**
     * spy store：仅 override {@code newTaskStateBean} 工厂返回可观测 {@link RecordingTaskStateBean}，
     * 其余 load/save/entity 拷贝/JSON 序列化逻辑全部沿用真实 {@link DaoTaskStateStore}（DB round-trip，非 in-memory 引用）。
     */
    static class HookSpyDaoTaskStateStore extends DaoTaskStateStore {
        RecordingTaskStateBean lastBean;

        @Override
        protected TaskStateBean newTaskStateBean() {
            RecordingTaskStateBean bean = new RecordingTaskStateBean();
            lastBean = bean;
            return bean;
        }
    }

    private HookSpyDaoTaskStateStore newSpyStore() {
        HookSpyDaoTaskStateStore store = new HookSpyDaoTaskStateStore();
        store.setDaoProvider(daoProvider);
        return store;
    }

    private TaskRuntimeImpl newRuntime(DaoTaskStateStore store) {
        return new TaskRuntimeImpl(null, store, null, XLang.newEvalScope(), false);
    }

    // ==================== cross-restart E2E: COMPLETED (result via remark JSON) ====================

    /**
     * 端到端验证（#22）：COMPLETED task 经 save（beforeSave 触发 + result 经 remark JSON 序列化持久化到 DB）
     * → fresh store load（反序列化 + afterLoad 触发）完整路径连通，hook 调用经 DB 序列化边界可观测。
     */
    @Test
    public void crossRestart_completedTask_hooksSurviveDbSerializationBoundary() {
        // === Phase A: fresh execute（save 路径，beforeSave 触发 + remark JSON 持久化）===
        HookSpyDaoTaskStateStore saveStore = newSpyStore();
        TaskRuntimeImpl saveRuntime = newRuntime(saveStore);
        RecordingTaskStateBean saveBean = (RecordingTaskStateBean) saveStore.newTaskState("crossCompletedTask", 0, saveRuntime);
        saveRuntime.setTaskState(saveBean);
        saveBean.setTaskStatus(TaskConstants.TASK_STATUS_COMPLETED);
        saveBean.setResultValue("completed-result");

        saveStore.saveTaskState(saveRuntime);

        assertEquals(1, saveBean.beforeSaveCount, "beforeSave must fire once on save (save side of round-trip)");
        assertEquals(0, saveBean.afterLoadCount, "afterLoad must NOT fire on save path");

        // === Phase B: cross-restart（fresh store 实例从 DB 反序列化，afterLoad 触发）===
        HookSpyDaoTaskStateStore loadStore = newSpyStore();
        TaskRuntimeImpl loadRuntime = newRuntime(loadStore);

        ITaskState loaded = loadStore.loadTaskState(saveBean.getTaskInstanceId(), loadRuntime);

        assertNotNull(loadStore.lastBean, "fresh store must construct a recording bean on load");
        assertEquals(1, loadStore.lastBean.afterLoadCount,
                "afterLoad must fire once on load across DB serialization boundary (load side of round-trip)");
        assertEquals(0, loadStore.lastBean.beforeSaveCount, "beforeSave must NOT fire on load path");
        // afterLoad 在 toTaskStateBean reconstruction 之后运行：捕获到从 DB 反序列化的 COMPLETED status + result
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED),
                loadStore.lastBean.capturedStatusOnAfterLoad,
                "afterLoad must observe status reconstructed from DB => ran after toTaskStateBean across DB boundary");
        assertEquals("completed-result", loaded.getResultValue(),
                "result must round-trip through DB (remark JSON serialization boundary)");
    }

    // ==================== cross-restart E2E: FAILED (exception via errorBeanData JSON) ====================

    /**
     * 端到端验证（#22）：FAILED task 经 save（beforeSave 触发 + exception 经 errorBeanData JSON 序列化持久化到 DB）
     * → fresh store load（反序列化 + afterLoad 触发），hook 调用经 DB 序列化边界（errorBeanData 列）存活。
     */
    @Test
    public void crossRestart_failedTask_hooksSurviveDbSerializationBoundary() {
        // === Phase A: fresh execute（save 路径，beforeSave 触发 + errorBeanData JSON 持久化）===
        HookSpyDaoTaskStateStore saveStore = newSpyStore();
        TaskRuntimeImpl saveRuntime = newRuntime(saveStore);
        RecordingTaskStateBean saveBean = (RecordingTaskStateBean) saveStore.newTaskState("crossFailedTask", 0, saveRuntime);
        saveRuntime.setTaskState(saveBean);
        saveBean.setTaskStatus(TaskConstants.TASK_STATUS_FAILED);
        NopException exp = new NopException(ERR_PLAN_262_CROSS).param("crossParam", "crossValue");
        saveBean.exception(exp);

        saveStore.saveTaskState(saveRuntime);

        assertEquals(1, saveBean.beforeSaveCount, "beforeSave must fire once on save (FAILED path)");
        assertEquals(0, saveBean.afterLoadCount, "afterLoad must NOT fire on save path");

        // === Phase B: cross-restart（fresh store 从 DB errorBeanData 列反序列化 exception，afterLoad 触发）===
        HookSpyDaoTaskStateStore loadStore = newSpyStore();
        TaskRuntimeImpl loadRuntime = newRuntime(loadStore);

        ITaskState loaded = loadStore.loadTaskState(saveBean.getTaskInstanceId(), loadRuntime);

        assertEquals(1, loadStore.lastBean.afterLoadCount,
                "afterLoad must fire once on load across errorBeanData JSON serialization boundary");
        assertEquals(0, loadStore.lastBean.beforeSaveCount, "beforeSave must NOT fire on load path");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_FAILED),
                loadStore.lastBean.capturedStatusOnAfterLoad,
                "afterLoad must observe FAILED status reconstructed from DB");

        // exception 经 errorBeanData 列 JSON 序列化边界存活（plan 261 既有能力，本计划证明 hook 与其共存）
        Throwable loadedExp = loaded.exception();
        assertNotNull(loadedExp, "exception must round-trip through DB (errorBeanData JSON boundary)");
        assertTrue(loadedExp instanceof NopException);
        assertEquals(ERR_PLAN_262_CROSS.getErrorCode(), ((NopException) loadedExp).getErrorCode());
        assertEquals("crossValue", ((NopException) loadedExp).getParam("crossParam"),
                "exception param must round-trip through errorBeanData, proving load ran full reconstruction before afterLoad");
    }

    // ==================== regression guard: exact call count per task status ====================

    /**
     * 各终态（in-progress ACTIVE / COMPLETED / FAILED / KILLED / TIMEOUT）task 经 store save→load round-trip 后，
     * {@code beforeSave} 与 {@code afterLoad} 各被精确调用一次（regression guard：精确计数断言，
     * 防止未来 per-status early-return 逻辑静默跳过 hook）。
     */
    @Test
    public void crossRestart_eachTaskStatus_beforeSaveAndAfterLoadCalledExactlyOnce() {
        int[][] statusCases = {
                {TaskConstants.TASK_STATUS_ACTIVE, 0},      // in-progress（非终态）
                {TaskConstants.TASK_STATUS_COMPLETED, 1},   // result 提供标记
                {TaskConstants.TASK_STATUS_FAILED, 0},
                {TaskConstants.TASK_STATUS_KILLED, 0},
                {TaskConstants.TASK_STATUS_TIMEOUT, 0},
        };
        for (int[] statusCase : statusCases) {
            int status = statusCase[0];
            int provideResult = statusCase[1];

            // save 路径（每个 status 独立 store/runtime/instance）
            HookSpyDaoTaskStateStore saveStore = newSpyStore();
            TaskRuntimeImpl saveRuntime = newRuntime(saveStore);
            RecordingTaskStateBean saveBean =
                    (RecordingTaskStateBean) saveStore.newTaskState("statusCase-" + status, 0, saveRuntime);
            saveRuntime.setTaskState(saveBean);
            saveBean.setTaskStatus(status);
            if (provideResult == 1)
                saveBean.setResultValue("result-for-" + status);

            saveStore.saveTaskState(saveRuntime);
            assertEquals(1, saveBean.beforeSaveCount,
                    "beforeSave must be called exactly once on saveTaskState for status " + status);
            assertEquals(0, saveBean.afterLoadCount,
                    "afterLoad must NOT be called on save path for status " + status);

            // fresh store load 路径（模拟 cross-restart）
            HookSpyDaoTaskStateStore loadStore = newSpyStore();
            TaskRuntimeImpl loadRuntime = newRuntime(loadStore);
            loadStore.loadTaskState(saveBean.getTaskInstanceId(), loadRuntime);

            assertNotNull(loadStore.lastBean, "load store must construct a bean for status " + status);
            assertEquals(1, loadStore.lastBean.afterLoadCount,
                    "afterLoad must be called exactly once on loadTaskState for status " + status
                            + " (regression guard: no per-status early-return skips hook)");
            assertEquals(0, loadStore.lastBean.beforeSaveCount,
                    "beforeSave must NOT be called on load path for status " + status);
            assertEquals(Integer.valueOf(status), loadStore.lastBean.capturedStatusOnAfterLoad,
                    "afterLoad must observe reconstructed status " + status + " from DB");
        }
    }
}
