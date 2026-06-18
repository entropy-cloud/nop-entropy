package io.nop.task.ext.reliability;

import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepState;
import io.nop.task.state.DefaultTaskStateStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plan 252 Phase 3 E2E 测试基础设施：捕获 {@link ITaskStepState} 引用供测试断言 step 级状态转移。
 *
 * <p>继承 {@link DefaultTaskStateStore} 仅在 {@link #newStepState} 中捕获 state 引用（行为零变更）。
 * 因 {@link io.nop.task.state.TaskStepStateBean#succeed} / {@code fail} 原地 mutate 同一对象，
 * 故捕获的引用在执行后反映终态（stepStatus / resultValue / exception 等）。
 *
 * <p>{@link io.nop.task.ITaskRuntime} / {@link io.nop.task.ITaskState} 无 step-state 查询接口，
 * {@link DefaultTaskStateStore#saveStepState} 为 no-op，故需本捕获机制观察 state.getStepStatus() 等。
 */
public class StateCapturingTaskStateStore extends DefaultTaskStateStore {
    public static final String BEAN_NAME = "testStateCapturingStore";

    private final Map<String, ITaskStepState> capturedStates = new ConcurrentHashMap<>();

    @Override
    public ITaskStepState newStepState(ITaskStepState parentState, String stepName, String stepType, ITaskRuntime taskRt) {
        ITaskStepState state = super.newStepState(parentState, stepName, stepType, taskRt);
        capturedStates.put(stepName, state);
        return state;
    }

    /**
     * 获取指定 stepName 的捕获 state 引用（执行后反映终态）。
     */
    public ITaskStepState getCapturedState(String stepName) {
        return capturedStates.get(stepName);
    }

    /**
     * 清空捕获（测试间隔离）。
     */
    public void reset() {
        capturedStates.clear();
    }
}
