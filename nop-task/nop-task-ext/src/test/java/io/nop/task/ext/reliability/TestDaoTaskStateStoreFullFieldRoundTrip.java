package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.dao.entity.NopTaskStepInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.task.impl.TaskStepRuntimeImpl;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_stepPath;
import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_taskInstanceId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 264 Phase 1 focused 测试：验证 {@link DaoTaskStateStore} 的 save→load round-trip 补齐既有 entity 列的
 * Group A（{@code internal} + {@code tagSet}↔{@code tagText}）双向 round-trip，以及 Group B（{@code createTime}/
 * {@code updateTime}）时间历史读回，闭合 7× carry-over「全量字段持久化」optimization candidate。
 *
 * <p>所有用例均使用真实 DB-backed {@link DaoTaskStateStore}（{@code @NopTestConfig(localDb=true)}），
 * 新增字段经真实 entity↔bean + DB 序列化边界 round-trip，非 in-memory 引用直接拷贝。
 *
 * <p>本计划前：{@code copyStepStateToEntity} 与 {@code toStepStateBean} 均不触及 {@code internal}/{@code tagText}，
 * {@code toStepStateBean} 不读回 {@code createTime}/{@code updateTime}（save 侧已写但 load 丢弃），使持久化行
 * 「schema 在场但语义丢失」。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoTaskStateStoreFullFieldRoundTrip extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private DaoTaskStateStore store;
    private ITaskRuntime taskRt;
    private ITaskState taskState;

    @BeforeEach
    public void setUpStore() {
        store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);

        TaskRuntimeImpl runtime = new TaskRuntimeImpl(null, store, null, XLang.newEvalScope(), false);
        taskState = store.newTaskState("testFullFieldTask", 0, runtime);
        runtime.setTaskState(taskState);
        taskRt = runtime;
    }

    private ITaskStepRuntime newStepRt(String stepName) {
        ITaskStepState state = store.newStepState(null, stepName, "xpl", taskRt);
        TaskStepRuntimeImpl stepRt = new TaskStepRuntimeImpl(taskRt, store, XLang.newEvalScope());
        stepRt.setState(state);
        return stepRt;
    }

    private NopTaskStepInstance loadStepEntity(String taskInstanceId, String stepPath) {
        IEntityDao<NopTaskStepInstance> dao = daoProvider.daoFor(NopTaskStepInstance.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(PROP_NAME_taskInstanceId, taskInstanceId));
        query.addFilter(FilterBeans.eq(PROP_NAME_stepPath, stepPath));
        return dao.findFirstByQuery(query);
    }

    // ==================== Group A: internal + tagSet round-trip ====================

    /**
     * Group A 双向 round-trip（#23 接线验证）：含非空 internal + tagSet 的 step → save（写 entity 列 + DB）
     * → fresh load → 逐字段断言 round-trip 等值（非仅方法存在）。
     */
    @Test
    public void groupA_internalAndTagSet_saveLoadRoundTrip_preserved() {
        // 1. 构造含非空 internal + tagSet 的 step（终态 COMPLETED）
        ITaskStepRuntime stepRt = newStepRt("groupAStep");
        ITaskStepState state = stepRt.getState();
        state.setInternal(Boolean.TRUE);
        Set<String> tags = new LinkedHashSet<>();
        tags.add("tag-a");
        tags.add("tag-b");
        state.setTagSet(tags);
        state.succeed("RESULT", null, taskRt);

        // 2. save（持久化 internal + tagText 到 DB entity）
        store.saveStepState(stepRt);

        // 3. entity 字段断言：internal + tagText 列已写入（非 null，非空壳）
        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity, "persisted step entity must exist");
        assertEquals(Boolean.TRUE, entity.getInternal(),
                "entity.internal column must be written (plan 264 Group A core delta, was always null pre-264)");
        assertNotNull(entity.getTagText(), "entity.tagText column must be written");
        assertTrue(entity.getTagText().contains("tag-a") && entity.getTagText().contains("tag-b"),
                "tagText CSV must contain both tags");

        // 4. fresh load（从 DB entity 读回 internal + tagSet）
        ITaskStepState loaded = store.loadStepState(null, "groupAStep", "xpl", taskRt);
        assertNotNull(loaded, "loadStepState must return persisted state");
        assertEquals(Boolean.TRUE, loaded.getInternal(),
                "round-trip must preserve internal=true (plan 264 Group A core delta)");
        assertNotNull(loaded.getTagSet(), "round-trip must restore non-null tagSet");
        assertEquals(tags, loaded.getTagSet(),
                "round-trip must preserve tagSet contents (plan 264 Group A core delta, was always null pre-264)");
    }

    /**
     * Group A null 安全：fresh step（未设置 internal/tagSet）→ save → load → internal null、tagSet null（不因新增读回而行为变化）。
     * 这是零回归 guard：新增字段读回不影响 fresh execute 路径。
     */
    @Test
    public void groupA_freshStepWithoutInternalAndTags_loadReturnsNullFields() {
        ITaskStepRuntime stepRt = newStepRt("freshStepNoTags");
        ITaskStepState state = stepRt.getState();
        state.succeed("R", null, taskRt);
        store.saveStepState(stepRt);

        ITaskStepState loaded = store.loadStepState(null, "freshStepNoTags", "xpl", taskRt);
        assertNotNull(loaded);
        assertNull(loaded.getInternal(),
                "fresh step without internal set: load returns null internal (zero-regression, no behavior change)");
        // tagSet may be null or empty; assert it carries no tags
        assertTrue(loaded.getTagSet() == null || loaded.getTagSet().isEmpty(),
                "fresh step without tags: load returns no tags (zero-regression)");
    }

    // ==================== Group B: time history read-back ====================

    /**
     * Group B 时间历史读回（#23 接线验证）：save 写入 createTime/updateTime → fresh load 读回非 null
     * （消除「写而不读」pre-264 loaded bean 时间历史恒 null 的隐患）。
     */
    @Test
    public void groupB_createTimeAndUpdateTime_readBackNonNullable() {
        ITaskStepRuntime stepRt = newStepRt("groupBTimeStep");
        ITaskStepState state = stepRt.getState();
        state.succeed("R", null, taskRt);

        // save：copyStepStateToEntity 写 createTime（if null）+ updateTime
        store.saveStepState(stepRt);

        // entity 侧时间列非 null（save 侧已成立，本计划不动）
        NopTaskStepInstance entity = loadStepEntity(taskState.getTaskInstanceId(), state.getStepPath());
        assertNotNull(entity.getCreateTime(), "entity.createTime must be written by save");
        assertNotNull(entity.getUpdateTime(), "entity.updateTime must be written by save");

        // fresh load：Group B 读回（plan 264 核心增量，pre-264 loaded bean 时间恒 null）
        ITaskStepState loaded = store.loadStepState(null, "groupBTimeStep", "xpl", taskRt);
        assertNotNull(loaded);
        assertNotNull(loaded.getCreateTime(),
                "loaded bean createTime must be non-null (plan 264 Group B read-back, was null pre-264)");
        assertNotNull(loaded.getUpdateTime(),
                "loaded bean updateTime must be non-null (plan 264 Group B read-back, was null pre-264)");

        // 读回值与 entity 记录一致（round-trip 等值，非占位值）
        assertEquals(entity.getCreateTime().toLocalDateTime(), loaded.getCreateTime(),
                "loaded createTime must equal entity-recorded value (round-trip equality)");
        assertEquals(entity.getUpdateTime().toLocalDateTime(), loaded.getUpdateTime(),
                "loaded updateTime must equal entity-recorded value (round-trip equality)");
    }

    // ==================== zero regression: existing fields unaffected ====================

    /**
     * 零回归 guard（设计裁定 4）：既有 round-trip 字段集（stepStatus/resultValue/retryCount）行为不变。
     */
    @Test
    public void existingFields_roundTripUnaffected_byNewGroupAGroupBReadBack() {
        ITaskStepRuntime stepRt = newStepRt("regressionGuardStep");
        ITaskStepState state = stepRt.getState();
        state.setRetryAttempt(3);
        state.succeed("CACHED_RESULT", null, taskRt);

        store.saveStepState(stepRt);

        ITaskStepState loaded = store.loadStepState(null, "regressionGuardStep", "xpl", taskRt);
        assertNotNull(loaded);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED), loaded.getStepStatus(),
                "existing stepStatus round-trip unchanged");
        assertTrue(loaded.isDone(), "existing isDone() behavior unchanged");
        assertEquals("CACHED_RESULT", loaded.result().getResult(),
                "existing resultValue round-trip unchanged");
        assertEquals(Integer.valueOf(3), loaded.getRetryAttempt(),
                "existing retryAttempt round-trip unchanged");
        assertNull(loaded.getInternal(),
                "fresh step without internal: load returns null internal (zero-regression)");
    }

    // ==================== loadMainStepState also round-trips new fields ====================

    /**
     * loadMainStepState（plan 263 envelope resume 路径）复用 toStepStateBean，故 Group A/B 同样生效。
     * 验证 mainStep envelope 也补齐字段（#23 接线验证：toStepStateBean 改动同时覆盖 loadStepState 与 loadMainStepState）。
     */
    @Test
    public void mainStepState_loadAlsoRoundTripsGroupAAndB() {
        // 构造 mainStep state 并持久化（mainStep path = @main）
        ITaskStepState mainState = store.newMainStepState(taskState);
        mainState.setInternal(Boolean.TRUE);
        Set<String> tags = new LinkedHashSet<>();
        tags.add("main-tag");
        mainState.setTagSet(tags);

        TaskStepRuntimeImpl mainStepRt = new TaskStepRuntimeImpl(taskRt, store, XLang.newEvalScope());
        mainStepRt.setState(mainState);
        store.saveStepState(mainStepRt);

        // fresh load via loadMainStepState（plan 263 envelope resume 路径）
        ITaskStepState loaded = store.loadMainStepState(taskState, taskRt);
        assertNotNull(loaded, "loadMainStepState must return persisted mainStep envelope");
        assertEquals(TaskConstants.MAIN_STEP_NAME, loaded.getStepPath());
        assertEquals(Boolean.TRUE, loaded.getInternal(),
                "loadMainStepState must round-trip internal (shares toStepStateBean with loadStepState)");
        assertEquals(tags, loaded.getTagSet(),
                "loadMainStepState must round-trip tagSet");
        assertNotNull(loaded.getCreateTime(),
                "loadMainStepState must read-back createTime (Group B)");
        assertNotNull(loaded.getUpdateTime(),
                "loadMainStepState must read-back updateTime (Group B)");
    }

    // ==================== cross-restart E2E (Phase 2, Anti-Hollow #22) ====================

    /**
     * 端到端验证（Anti-Hollow #22）：fresh execute（含非空 internal + tagSet 的 step → 终态 driver → saveStepState
     * 写 entity 列 + DB）→ 模拟中断 → fresh DaoTaskStateStore 实例（模拟进程重启后全新 store 从 DB 读取历史持久化数据）
     * loadTaskState + loadStepState → 断言新增 round-trip 字段经 DB 序列化边界存活（save 值 == fresh load 值）
     * + loaded bean 时间历史非 null。
     *
     * <p>使用独立的 fresh store 实例（非 in-memory 引用），证明 Group A/B 字段经真实 DB 序列化边界 round-trip，
     * 非「写而丢失 / 写而不读」的伪装。本计划前此 round-trip 在 fresh store 侧不成立（toStepStateBean 不读回
     * internal/tagSet/createTime/updateTime）。
     */
    @Test
    public void crossRestartResume_fullFieldRoundTripSurvivesDbBoundary() {
        // === Phase A: fresh execute（首次执行：含非空 internal + tagSet，终态 COMPLETED，持久化到 DB）===
        ITaskStepRuntime stepRt = newStepRt("crossRestartFullFieldStep");
        ITaskStepState state = stepRt.getState();
        state.setInternal(Boolean.TRUE);
        Set<String> tags = new LinkedHashSet<>();
        tags.add("alpha");
        tags.add("beta");
        tags.add("gamma");
        state.setTagSet(tags);
        state.succeed("CROSS_RESTART_RESULT", null, taskRt);
        // 终态 driver → 持久化 internal + tagText + createTime + updateTime 到 DB entity
        store.saveStepState(stepRt);

        // === Phase B: cross-restart（fresh DaoTaskStateStore + fresh runtime 从 DB 反序列化）===
        DaoTaskStateStore restartedStore = new DaoTaskStateStore();
        restartedStore.setDaoProvider(daoProvider);
        TaskRuntimeImpl restartedRuntime = new TaskRuntimeImpl(null, restartedStore, null, XLang.newEvalScope(), false);
        restartedRuntime.setTaskState(restartedStore.loadTaskState(taskState.getTaskInstanceId(), restartedRuntime));

        ITaskStepState resumed = restartedStore.loadStepState(null, "crossRestartFullFieldStep", "xpl", restartedRuntime);
        assertNotNull(resumed, "cross-restart loadStepState must return persisted state");

        // === Phase C: Anti-Hollow 断言——新增字段经 DB 边界存活（非空壳、非占位）===
        // Group A: internal 经 DB 序列化边界 round-trip（save 写入值 == fresh load 读回值）
        assertEquals(Boolean.TRUE, resumed.getInternal(),
                "cross-restart: internal must survive DB round-trip (plan 264 Group A core delta, was null pre-264)");
        // Group A: tagSet 经 tagText CSV ↔ DB ↔ parse round-trip（顺序保留 + 内容等值）
        assertNotNull(resumed.getTagSet(),
                "cross-restart: tagSet must be non-null after DB round-trip");
        assertEquals(tags, resumed.getTagSet(),
                "cross-restart: tagSet contents must survive DB round-trip (plan 264 Group A core delta, was null pre-264)");
        // 既有字段仍成立（零回归：cross-restart resume 正确性不被新增读回影响）
        assertTrue(resumed.isDone(), "cross-restart: resumed step must be terminal (zero-regression on existing fields)");
        assertEquals("CROSS_RESTART_RESULT", resumed.result().getResult(),
                "cross-restart: existing resultValue round-trip unchanged");

        // Group B: 时间历史经 DB 边界读回非 null（pre-264 loaded bean 时间恒 null——「写而不读」伪装）
        assertNotNull(resumed.getCreateTime(),
                "cross-restart: loaded createTime must be non-null (plan 264 Group B read-back, was null pre-264)");
        assertNotNull(resumed.getUpdateTime(),
                "cross-restart: loaded updateTime must be non-null (plan 264 Group B read-back, was null pre-264)");
    }

    /**
     * Anti-Hollow 反向证明：cross-restart 后若 prior 行未写 internal/tagText（模拟历史行 / pre-264 数据），
     * fresh load 读回 internal=null、tagSet 无标签——不因新增读回逻辑而抛错或产生伪数据（向后兼容，零回归）。
     */
    @Test
    public void crossRestartResume_legacyRowWithoutGroupAFields_loadsCleanlyWithoutFakeData() {
        // 构造一个未设置 internal/tagSet 的 step（模拟 pre-264 历史 / fresh execute 未填字段）
        ITaskStepRuntime stepRt = newStepRt("legacyFullFieldStep");
        ITaskStepState state = stepRt.getState();
        state.succeed("R", null, taskRt);
        store.saveStepState(stepRt);

        // cross-restart fresh store
        DaoTaskStateStore restartedStore = new DaoTaskStateStore();
        restartedStore.setDaoProvider(daoProvider);
        TaskRuntimeImpl restartedRuntime = new TaskRuntimeImpl(null, restartedStore, null, XLang.newEvalScope(), false);
        restartedRuntime.setTaskState(restartedStore.loadTaskState(taskState.getTaskInstanceId(), restartedRuntime));

        ITaskStepState resumed = restartedStore.loadStepState(null, "legacyFullFieldStep", "xpl", restartedRuntime);
        assertNotNull(resumed);
        assertNull(resumed.getInternal(),
                "legacy row without internal: cross-restart load returns null (no fake data, backward compat)");
        assertTrue(resumed.getTagSet() == null || resumed.getTagSet().isEmpty(),
                "legacy row without tags: cross-restart load returns no tags (no fake data, backward compat)");
        // Group B 时间仍读回（save 侧已写 createTime/updateTime，与 Group A 是否填充无关）
        assertNotNull(resumed.getCreateTime(),
                "Group B time read-back is independent of Group A fields: createTime still non-null");
    }
}
