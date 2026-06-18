package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConstants;
import io.nop.task.dao.entity.NopTaskInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.task.state.TaskStateBean;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan 262 Phase 1 focused 测试：验证 task 级 {@code afterLoad}/{@code beforeSave} lifecycle hook 在真实
 * {@link DaoTaskStateStore} 的 load/save runtime 被真实调用（非仅接口存在，Anti-Hollow #23 接线验证）。
 *
 * <p>save 路径：runtime 外部持有 custom {@link RecordingTaskStateBean} 子类，{@code saveTaskState} 后断言
 * {@code beforeSave} 被调用一次，且调用发生在 entity 拷贝之前（经 beforeSave 内 status 突变可被持久化到 entity 观测）。
 *
 * <p>load 路径：{@code loadTaskState} 经 {@code toTaskStateBean}（{@code protected} 工厂 {@code newTaskStateBean}）
 * 内部构造 bean 后调 {@code afterLoad}，经 spy store override 工厂返回 {@link RecordingTaskStateBean} 可观测实例，
 * 断言 {@code afterLoad} 被调用一次，且调用发生在 reconstruction 之后（经 afterLoad 捕获到 toTaskStateBean 设置的 status 观测）。
 *
 * <p>对称 step 级 {@code DaoTaskStateStore.loadStepState:188} / {@code saveStepState:202} 既有 hook 接线。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestTaskStateLifecycleHooks extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    /**
     * 可观测的 {@link TaskStateBean} 子类：记录 {@code afterLoad}/{@code beforeSave} 调用计数 + 入参 + 关键观测点。
     */
    static class RecordingTaskStateBean extends TaskStateBean {
        int afterLoadCount;
        int beforeSaveCount;
        ITaskRuntime afterLoadTaskRt;
        ITaskRuntime beforeSaveTaskRt;
        // afterLoad 时捕获的 taskStatus：若非 null 则证明 afterLoad 在 toTaskStateBean 设置 status 之后运行
        Integer capturedStatusOnAfterLoad;
        // beforeSave 时若非 null 则把 status 突变为此值：用于证明 beforeSave 在 entity 拷贝之前运行
        Integer beforeSaveStatusOverride;

        @Override
        public void afterLoad(ITaskRuntime taskRt) {
            afterLoadCount++;
            afterLoadTaskRt = taskRt;
            capturedStatusOnAfterLoad = getTaskStatus();
        }

        @Override
        public void beforeSave(ITaskRuntime taskRt) {
            beforeSaveCount++;
            beforeSaveTaskRt = taskRt;
            if (beforeSaveStatusOverride != null)
                setTaskStatus(beforeSaveStatusOverride);
        }
    }

    /**
     * spy store：仅 override {@code newTaskStateBean} 工厂返回可观测 {@link RecordingTaskStateBean}，
     * 其余 load/save/entity 拷贝/JSON 序列化逻辑全部沿用真实 {@link DaoTaskStateStore}（非 in-memory 引用）。
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

    private DaoTaskStateStore newStore() {
        DaoTaskStateStore store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);
        return store;
    }

    private TaskRuntimeImpl newRuntime(DaoTaskStateStore store) {
        return new TaskRuntimeImpl(null, store, null, XLang.newEvalScope(), false);
    }

    // ==================== save path: beforeSave ====================

    /**
     * saveTaskState 调用 beforeSave 一次，入参为 runtime，且 beforeSave 在 entity 拷贝之前运行
     * （beforeSave 内 status 突变被 entity.setStatus 持久化观测）。afterLoad 在 save 路径不被调用（anti-over-match）。
     */
    @Test
    public void saveTaskState_invokesBeforeSaveOnce_beforeEntityCopy() {
        DaoTaskStateStore store = newStore();
        TaskRuntimeImpl runtime = newRuntime(store);

        // newTaskState 持久化 ACTIVE 行并返回 base state
        ITaskState base = store.newTaskState("saveHookTask", 0, runtime);

        // 换入可观测子类（同一 taskInstanceId），初始 ACTIVE，beforeSave 内突变 status 为 COMPLETED
        RecordingTaskStateBean rec = new RecordingTaskStateBean();
        rec.setTaskInstanceId(base.getTaskInstanceId());
        rec.setTaskName("saveHookTask");
        rec.setTaskStatus(TaskConstants.TASK_STATUS_ACTIVE);
        rec.beforeSaveStatusOverride = TaskConstants.TASK_STATUS_COMPLETED;
        runtime.setTaskState(rec);

        store.saveTaskState(runtime);

        assertEquals(1, rec.beforeSaveCount, "beforeSave must be called exactly once on saveTaskState");
        assertSame(runtime, rec.beforeSaveTaskRt, "beforeSave must receive the runtime");
        assertEquals(0, rec.afterLoadCount, "afterLoad must NOT be called on save path (anti-over-match)");

        // 顺序证明：entity status 反映 beforeSave 的突变（COMPLETED），而非初始 ACTIVE
        NopTaskInstance entity = daoProvider.daoFor(NopTaskInstance.class)
                .getEntityById(rec.getTaskInstanceId());
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED), entity.getStatus(),
                "entity status must reflect beforeSave mutation => beforeSave ran before entity copy");
    }

    // ==================== load path: afterLoad ====================

    /**
     * loadTaskState 调用 afterLoad 一次，入参为 runtime，且 afterLoad 在 toTaskStateBean reconstruction 之后运行
     * （afterLoad 捕获到 toTaskStateBean 从 entity 设置的 status）。beforeSave 在 load 路径不被调用（anti-over-match）。
     */
    @Test
    public void loadTaskState_invokesAfterLoadOnce_afterToTaskStateBean() {
        // setup：经真实 store 持久化一个 COMPLETED task（含 result）
        DaoTaskStateStore setupStore = newStore();
        TaskRuntimeImpl setupRuntime = newRuntime(setupStore);
        ITaskState base = setupStore.newTaskState("loadHookTask", 0, setupRuntime);
        setupRuntime.setTaskState(base);
        base.setTaskStatus(TaskConstants.TASK_STATUS_COMPLETED);
        base.setResultValue("load-result");
        setupStore.saveTaskState(setupRuntime);

        // load：经 spy store，toTaskStateBean 构造 RecordingTaskStateBean 后调 afterLoad
        HookSpyDaoTaskStateStore spyStore = new HookSpyDaoTaskStateStore();
        spyStore.setDaoProvider(daoProvider);
        TaskRuntimeImpl loadRuntime = newRuntime(spyStore);

        ITaskState loaded = spyStore.loadTaskState(base.getTaskInstanceId(), loadRuntime);

        assertNotNull(spyStore.lastBean, "spy store must have constructed a recording bean via newTaskStateBean");
        assertSame(spyStore.lastBean, loaded, "loadTaskState must return the bean it called afterLoad on");
        assertEquals(1, spyStore.lastBean.afterLoadCount, "afterLoad must be called exactly once on loadTaskState");
        assertSame(loadRuntime, spyStore.lastBean.afterLoadTaskRt, "afterLoad must receive the runtime");
        assertEquals(0, spyStore.lastBean.beforeSaveCount, "beforeSave must NOT be called on load path (anti-over-match)");

        // 顺序证明：afterLoad 捕获的 status == COMPLETED（toTaskStateBean 从 entity 设置），而非 null
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED),
                spyStore.lastBean.capturedStatusOnAfterLoad,
                "afterLoad must observe status set by toTaskStateBean => afterLoad ran after reconstruction");
        assertEquals("load-result", loaded.getResultValue(),
                "reconstructed result must be available (toTaskStateBean ran before afterLoad)");
    }

    // ==================== zero regression: default no-op hooks ====================

    /**
     * 默认 {@link TaskStateBean}（不 override hook）load/save 行为不变：no-op hook 无副作用，状态/result 正常 round-trip。
     */
    @Test
    public void defaultTaskStateBean_hooksAreNoOp_noSideEffect() {
        DaoTaskStateStore store = newStore();
        TaskRuntimeImpl runtime = newRuntime(store);

        ITaskState state = store.newTaskState("noopHookTask", 0, runtime);
        runtime.setTaskState(state);
        state.setTaskStatus(TaskConstants.TASK_STATUS_COMPLETED);
        state.setResultValue("noop-result");

        // 默认 no-op hook：load/save 不抛异常，status/result 正常 round-trip
        store.saveTaskState(runtime);
        ITaskState loaded = store.loadTaskState(state.getTaskInstanceId(), runtime);

        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED), loaded.getTaskStatus(),
                "default no-op hook must not alter persisted status");
        assertEquals("noop-result", loaded.getResultValue(),
                "default no-op hook must not alter persisted result");
    }

    /**
     * loadTaskState 对不存在的 taskInstanceId 返回 null，不触发 afterLoad（无 bean 构造）。
     */
    @Test
    public void loadTaskState_unknownInstance_returnsNull_withoutAfterLoad() {
        DaoTaskStateStore store = newStore();
        TaskRuntimeImpl runtime = newRuntime(store);
        ITaskState loaded = store.loadTaskState("non-existent-instance-id", runtime);
        assertNull(loaded, "loadTaskState must return null for unknown taskInstanceId");
    }
}
