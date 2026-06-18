package io.nop.task.ext.reliability;

import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.state.DefaultTaskStateStore;
import io.nop.task.utils.TaskStepHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plan 257 Phase 3 resume E2E 测试基础设施：支持真实 save→load round-trip 的 in-memory state store。
 *
 * <p>继承 {@link DefaultTaskStateStore}（non-persist 基线），覆盖 {@link #saveStepState} 和 {@link #loadStepState}：
 * <ul>
 *   <li>{@code saveStepState}：将 step state **引用**存入 map（key = stepPath）。因 {@link io.nop.task.state.TaskStepStateBean}
 *       的 succeed/fail 原地 mutate 同一对象，保存的引用在执行后反映终态（COMPLETED / FAILED）。</li>
 *   <li>{@code loadStepState}：按 stepPath 从 map 取回 state。resume 时返回终态 state → reader 命中 isDone → 跳过 step body。</li>
 * </ul>
 *
 * <p>这是 plan 257 设计裁定 6 允许的「真实 ITaskStateStore save+load」路径——
 * state 经 {@code saveStepState → loadStepState} 完整 round-trip（非注入预填 state 的单测）。
 * 与 {@link DaoTaskStateStore}（DB-backed snapshot 语义）的区别：本类使用引用语义（in-memory），
 * 因 TaskStepStateBean 原地 mutate 特性而天然支持 resume round-trip。
 */
public class ResumeCapableTaskStateStore extends DefaultTaskStateStore {
    public static final String BEAN_NAME = "testResumeCapableStore";

    private final Map<String, ITaskStepState> savedStates = new ConcurrentHashMap<>();

    @Override
    public boolean isSupportPersist() {
        return true;
    }

    @Override
    public ITaskStepState loadStepState(ITaskStepState parentState, String stepName, String stepType,
                                        ITaskRuntime taskRt) {
        String parentPath = parentState == null ? null : parentState.getStepPath();
        String stepPath = TaskStepHelper.buildStepPath(parentPath, stepName);
        return savedStates.get(stepPath);
    }

    @Override
    public void saveStepState(ITaskStepRuntime stepRt) {
        ITaskStepState state = stepRt.getState();
        if (state != null && state.getStepPath() != null) {
            savedStates.put(state.getStepPath(), state);
        }
    }

    /**
     * 清空保存的 state（测试间隔离）。
     */
    public void reset() {
        savedStates.clear();
    }

    /**
     * 获取指定 stepPath 的保存 state（供测试断言终态）。
     */
    public ITaskStepState getSavedState(String stepPath) {
        return savedStates.get(stepPath);
    }
}
