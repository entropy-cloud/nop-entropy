package io.nop.task.impl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.state.SnapshotTaskStateStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归测试：同一 task 执行内 stepPath 被重复实例化（loop 迭代 2+、fork 分支 2+）时，
 * 后续实例化不得被 continuation-skip 静默跳过并复用首轮的持久化终态结果。
 *
 * <p>缺陷（修复前）：{@link TaskStepRuntimeImpl#newStepRuntime} 无条件调用
 * {@code stateStore.loadStepState}。loop/fork 的循环体子步骤在每次迭代/每个分支中经
 * {@code executeWithParentRt → newStepRuntime(同名)} 创建运行时，stepPath 恒定；
 * 第 1 轮完成时终态行（COMPLETED）已写入 store，第 2 轮 load 命中该行 → recoverMode=true
 * + isDone=true → continuation-skip 直接返回第 1 轮缓存结果，step body 不再执行。
 *
 * <p>使用 {@link SnapshotTaskStateStore}（snapshot 语义，模拟 DaoTaskStateStore 的
 * 按 stepPath load 最新快照行为）暴露该缺陷：
 * <ul>
 *   <li>{@code loopIteration2_executesInsteadOfReusingIteration1Result}：loop 2 次迭代，
 *       修复前 sum 停留在第 1 轮（'FAIL'），修复后两轮都执行（'OK'）。</li>
 *   <li>{@code forkBranch2_executesInsteadOfReusingBranch1Result}：fork 2 个分支，
 *       修复前第 2 分支复用第 1 分支结果（'FAIL'），修复后两分支都执行（'OK'）。</li>
 *   <li>{@code uniquePathResume_firstInstantiationStillLoads}}：守卫——唯一路径步骤的
 *       resume（第 2 次执行加载持久化终态跳过）语义不受本修复影响。</li>
 * </ul>
 */
public class TestRepeatedStepPathExecution {

    private TaskFlowManagerImpl taskFlowManager;
    private SnapshotTaskStateStore snapshotStore;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        snapshotStore = new SnapshotTaskStateStore();
        taskFlowManager = new TaskFlowManagerImpl();
        taskFlowManager.setNonPersistStateStore(snapshotStore);
    }

    private Map<String, Object> runTask(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        return task.execute(taskRt).syncGetOutputs();
    }

    @Test
    public void loopIteration2_executesInsteadOfReusingIteration1Result() {
        Map<String, Object> ret = runTask("test/loop-01");

        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "loop iteration 2 must execute its step body (sum 0+1+2=3). "
                        + "Pre-fix: iteration 2 continuation-skipped on iteration 1's persisted "
                        + "COMPLETED state and reused the first-round result -> 'FAIL'.");

        // 每轮迭代 step1 应有 ACTIVE + 终态共 2 次 save，2 轮 = 4 次。
        // 修复前第 2 轮被 skip（无 ACTIVE save、无终态 save），只有 2 次。
        assertEquals(4, snapshotStore.getSaveCount("@main/test/step1"),
                "both loop iterations must create step state and persist terminal state. "
                        + "Pre-fix: only iteration 1 executed (2 saves), iteration 2 was silently skipped.");
    }

    @Test
    public void forkBranch2_executesInsteadOfReusingBranch1Result() {
        Map<String, Object> ret = runTask("test/fork-01");

        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "fork branch 2 must execute its step body (agg sum 2+3=5). "
                        + "Pre-fix: branch 2 continuation-skipped on branch 1's persisted "
                        + "COMPLETED state and reused branch 1's result -> sum 2+2=4 -> 'FAIL'.");

        // 每个分支 step1 应有 ACTIVE + 终态共 2 次 save，2 个分支 = 4 次。
        // 修复前第 2 分支被 skip（无 ACTIVE save、无终态 save），只有 2 次。
        assertEquals(4, snapshotStore.getSaveCount("@main/test/test/step1"),
                "both fork branches must create step state and persist terminal state. "
                        + "Pre-fix: only branch 1 executed (2 saves), branch 2 was silently skipped.");
    }

    @Test
    public void uniquePathResume_firstInstantiationStillLoads() {
        // 第 1 次执行：step 完成，ACTIVE + 终态共 2 次 save
        Map<String, Object> ret1 = runTask("test/terminal-state-persist-success");
        assertEquals("TERMINAL_OK", ret1.get(TaskConstants.VAR_RESULT));
        assertEquals(2, snapshotStore.getSaveCount("@main/terminalPersistStep"));

        // 第 2 次执行（模拟 resume）：唯一路径的首次实例化仍从 store 加载终态 → continuation-skip
        Map<String, Object> ret2 = runTask("test/terminal-state-persist-success");
        assertEquals("TERMINAL_OK", ret2.get(TaskConstants.VAR_RESULT),
                "unique-path step must still return cached result on re-execution (resume semantics preserved)");
        assertEquals(2, snapshotStore.getSaveCount("@main/terminalPersistStep"),
                "re-execution of a unique-path step must skip the body (no additional ACTIVE/terminal saves). "
                        + "This guards that the repeated-step-path fix does not break resume-by-load.");
    }
}
