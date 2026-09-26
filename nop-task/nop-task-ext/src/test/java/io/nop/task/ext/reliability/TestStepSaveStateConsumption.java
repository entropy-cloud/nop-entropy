package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.dao.entity.NopTaskStepInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskFlowManagerImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 2 [维度03-05]：per-step saveState 契约消费的 DB 级证明。
 *
 * <p>xdef 头注释承诺"如果在步骤上配置了saveState，则可以从任意步骤中断并恢复执行"，
 * 但 getSaveState() 此前全仓零消费者——任务级 defaultSaveState=true 时所有步骤无条件落盘。
 * 修复后：显式 saveState="false" 的步骤跳过自身状态行的全部生命周期落盘（ACTIVE/挂起/终态），
 * 未配置的步骤行为不变。
 *
 * <p>Anti-Hollow：经真实 DB 持久化（DaoTaskStateStore）驱动完整任务执行后直接查库断言行级结果，
 * 排除"门控存在但落盘路径未接通"的空壳风险。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestStepSaveStateConsumption extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void saveStateFalse_stepProducesNoDbRow_persistedSiblingHasRow() {
        DaoTaskStateStore store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);
        TaskFlowManagerImpl mgr = new TaskFlowManagerImpl();
        mgr.setTaskStateStore(store);
        mgr.setNonPersistStateStore(store);

        ITask task = mgr.getTask("test/step-save-state-off", 0);
        ITaskRuntime taskRt = mgr.newTaskRuntime(task, true, null);
        Map<String, Object> ret = task.execute(taskRt).syncGetOutputs();

        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT), "task must run to completion");

        List<NopTaskStepInstance> rows = daoProvider.daoFor(NopTaskStepInstance.class).findAll();
        boolean hasSkipped = rows.stream()
                .anyMatch(r -> r.getStepPath() != null && r.getStepPath().endsWith("/skipped"));
        boolean hasPersisted = rows.stream()
                .anyMatch(r -> r.getStepPath() != null && r.getStepPath().endsWith("/persisted"));

        // 核心断言：显式 saveState="false" 的步骤不产生状态行
        assertFalse(hasSkipped,
                "step with explicit saveState=false must NOT produce a state row (03-05 contract consumption)");
        // 对照组：未配置 saveState 的兄弟步骤照常落盘（证明持久化链路是活的）
        assertTrue(hasPersisted,
                "sibling step without saveState=false must persist its state row (control group), rows="
                        + rows.stream().map(NopTaskStepInstance::getStepPath).toList());
    }
}
