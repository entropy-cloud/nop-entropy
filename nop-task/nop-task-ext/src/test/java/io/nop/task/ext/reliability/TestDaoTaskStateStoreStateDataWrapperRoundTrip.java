package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.dao.entity.NopTaskStepInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.task.impl.TaskStepRuntimeImpl;
import io.nop.task.state.TaskStepStateBean;
import io.nop.task.step.LoopTaskStep;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 349 Phase 6 focused 测试：验证 {@link DaoTaskStateStore} 的版本化 stateBeanData wrapper
 * （resultValue/stateBean/outputs/nextStepName/persistVars 复用同列）的 save→DB→load round-trip，
 * 以及对旧格式（裸 resultValue JSON）的向后兼容与残留列清理。
 *
 * <p>闭合 check/check2 两轮审计反复发现却两度暂缓的恢复完整性缺口的最小闭环：
 * loop/fork/if/choose/suspend 的 stateBean 与非 RESULT 导出变量在 DB 恢复后不再丢失。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoTaskStateStoreStateDataWrapperRoundTrip extends JunitBaseTestCase {

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
        taskState = store.newTaskState("testStateDataWrapperTask", 0, runtime);
        runtime.setTaskState(taskState);
        taskRt = runtime;
    }

    private ITaskStepRuntime newStepRt(String stepName) {
        ITaskStepState state = store.newStepState(null, stepName, "xpl", taskRt);
        TaskStepRuntimeImpl stepRt = new TaskStepRuntimeImpl(taskRt, store, XLang.newEvalScope());
        stepRt.setState(state);
        return stepRt;
    }

    /**
     * stateBean（LoopStateBean：items+index）/outputs/nextStepName/persistVars 经版本化 wrapper
     * round-trip 后恢复；typed getStateBean(LoopStateBean.class) 从 Map 形态还原为精确类型，
     * 使 loop 恢复能从持久化 index 续跑而非从 0 重跑。
     */
    @Test
    public void stateBeanOutputsNextStepAndPersistVarsRoundTrip() {
        ITaskStepRuntime stepRt = newStepRt("loopStep");

        TaskStepStateBean state = (TaskStepStateBean) stepRt.getState();
        LoopTaskStep.LoopStateBean loopState = new LoopTaskStep.LoopStateBean();
        loopState.setItems(Arrays.<Object>asList("a", "b", "c"));
        loopState.setIndex(2);
        state.setStateBean(loopState);
        state.setResultValue("result-v1");

        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("RESULT", 42);
        outputs.put("sum", 7);
        state.setOutputs(outputs);
        state.setSavedNextStepName("nextStep");
        Map<String, Object> persistVars = new LinkedHashMap<>();
        persistVars.put("counter", 3);
        state.setPersistVarsSnapshot(persistVars);
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);

        store.saveStepState(stepRt);

        ITaskStepState loaded = store.loadStepState(null, "loopStep", "xpl", taskRt);
        assertNotNull(loaded, "loadStepState must return persisted state");
        assertEquals("result-v1", loaded.getResultValue());
        assertEquals("nextStep", loaded.getSavedNextStepName(), "dynamic next step name must round-trip");
        assertEquals(7, loaded.getOutputs().get("sum"), "non-RESULT output must round-trip for continuation-skip replay");
        assertEquals(3, loaded.getPersistVarsSnapshot().get("counter"), "persistVars snapshot must round-trip");

        // typed restore：Map 形态按请求类型转换为精确 bean（loop 恢复依赖此路径）
        LoopTaskStep.LoopStateBean restored = loaded.getStateBean(LoopTaskStep.LoopStateBean.class);
        assertNotNull(restored, "typed stateBean must be restored from wrapper map");
        assertEquals(2, restored.getIndex(), "loop index must survive round-trip (pre-fix: never persisted, resume restarted from 0)");
        assertEquals(3, restored.getItems().size());
    }

    /**
     * 旧格式向后兼容：裸 resultValue JSON（历史行）读取为 resultValue，不因 wrapper 解析失败而丢数据。
     */
    @Test
    public void legacyBareResultValueStillLoads() {
        ITaskStepRuntime stepRt = newStepRt("legacyStep");
        TaskStepStateBean state = (TaskStepStateBean) stepRt.getState();
        state.setResultValue("legacy-value");
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);
        store.saveStepState(stepRt);

        // 模拟历史行：把 stateBeanData 覆写为旧格式（裸 JSON，无版本号）
        NopTaskStepInstance entity = findEntity("legacyStep");
        assertNotNull(entity);
        entity.setStateBeanData("\"legacy-value\"");
        daoProvider.daoFor(NopTaskStepInstance.class).updateEntityDirectly(entity);

        ITaskStepState loaded = store.loadStepState(null, "legacyStep", "xpl", taskRt);
        assertNotNull(loaded);
        assertEquals("legacy-value", loaded.getResultValue(),
                "legacy bare-JSON stateBeanData must load as resultValue (backward compat)");
    }

    /**
     * 残留列清理：第二次 save 时 resultValue/stateBean 等为 null → stateBeanData 显式清空，
     * 不残留第一次的旧 JSON（修复重试失败后展示层读到旧结果）。
     */
    @Test
    public void secondSaveWithNullValuesClearsStaleColumn() {
        ITaskStepRuntime stepRt = newStepRt("clearingStep");
        TaskStepStateBean state = (TaskStepStateBean) stepRt.getState();
        state.setResultValue("stale");
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED);
        store.saveStepState(stepRt);
        NopTaskStepInstance entity = findEntity("clearingStep");
        assertNotNull(entity.getStateBeanData(), "first save must write stateBeanData");

        state.setResultValue(null);
        state.setOutputs(null);
        state.setSavedNextStepName(null);
        state.setPersistVarsSnapshot(null);
        store.saveStepState(stepRt);

        entity = findEntity("clearingStep");
        assertNull(entity.getStateBeanData(),
                "second save with null values must clear stateBeanData (pre-fix: stale JSON persisted)");
    }

    /**
     * suspend 场景：Boolean stateBean（first 标记）round-trip 后 getStateBean(Boolean.class) 直接命中，
     * resume 不再重复挂起。
     */
    @Test
    public void suspendBooleanStateBeanRoundTrip() {
        ITaskStepRuntime stepRt = newStepRt("suspendStep");
        TaskStepStateBean state = (TaskStepStateBean) stepRt.getState();
        state.setStateBean(Boolean.TRUE);
        store.saveStepState(stepRt);

        ITaskStepState loaded = store.loadStepState(null, "suspendStep", "xpl", taskRt);
        assertEquals(Boolean.TRUE, loaded.getStateBean(Boolean.class),
                "suspend first-flag must round-trip so resume continues instead of re-suspending");
    }

    private NopTaskStepInstance findEntity(String stepName) {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq(
                io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_taskInstanceId,
                taskState.getTaskInstanceId()));
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq(
                io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_stepPath,
                stepName));
        return daoProvider.daoFor(NopTaskStepInstance.class).findFirstByQuery(query);
    }
}
