package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.impl.TaskFlowManagerImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 258 Phase 2 DB-backed resume E2E：端到端验证终态 saveStepState wiring 使 DB-backed resume（snapshot 语义，
 * 非 in-memory 引用）可观测终态。
 *
 * <p>使用 {@link SnapshotResumeTaskStateStore}（snapshot 语义，模拟 DB round-trip）—— 每次 save 创建深拷贝 snapshot，
 * 使 driver 之后对 live bean 的 mutate 不影响已保存 snapshot。这闭合 plan 257 显式 carry-over 的 production gap：
 * plan 257 的 {@code ResumeCapableTaskStateStore}（引用语义）掩盖了此 gap，本测试用 snapshot 语义暴露并验证修复。
 *
 * <p>端到端路径（#22 Anti-Hollow）：task execute 入口 → step 完成 → 终态 driver → 终态 saveStepState 持久化 snapshot →
 * fresh load（snapshot，非引用）→ reader 检查 isDone → 跳过/重抛。
 *
 * <p>覆盖：
 * <ul>
 *   <li>COMPLETED resume：execute → 终态持久化 → fresh load → reader 命中 → step body 不重调用 → 返回缓存 result</li>
 *   <li>FAILED resume：execute → 终态 FAILED 持久化 → fresh load → reader 重抛 exception（非静默跳过）</li>
 *   <li>pre-fix gap 证据：第 0 次 save = ACTIVE（若无终态 save，resume load 取回 ACTIVE → reader 不跳过 → step 重执行）</li>
 *   <li>接线验证（#23）：save count = ACTIVE 1 + 终态 1 = 2</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/task/test/beans/test-reliability.beans.xml")
public class TestDbBackedTerminalStateResume extends JunitBaseTestCase {

    @Inject
    ITaskFlowManager injectedTaskFlowManager;

    private SnapshotResumeTaskStateStore snapshotStore;
    private TaskFlowManagerImpl taskFlowManager;
    private ExecutionCounterBean counter;

    @BeforeEach
    public void setUpSnapshot() {
        counter = (ExecutionCounterBean) io.nop.api.core.ioc.BeanContainer.instance()
                .getBean("testExecutionCounter");
        snapshotStore = new SnapshotResumeTaskStateStore();
        counter.reset();
        snapshotStore.reset();

        taskFlowManager = new TaskFlowManagerImpl();
        taskFlowManager.setNonPersistStateStore(snapshotStore);
    }

    private Map<String, Object> runTask(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        return task.execute(taskRt).syncGetOutputs();
    }

    // ==================== DB-backed resume COMPLETED → skip body, return cached result ====================

    @Test
    public void resume_completedStep_snapshotLoadSkipsBody_returnsCachedResult_plan258() {
        String stepPath = "@main/plainStep";

        // ---- 第一次执行：step 完成 → 终态 driver → 终态 saveStepState 持久化 COMPLETED snapshot ----
        Map<String, Object> ret1 = runTask("test/no-decorator-baseline");
        assertEquals("OK", ret1.get(TaskConstants.VAR_RESULT),
                "first execution: step body runs and returns 'OK'");
        assertEquals(1, counter.get(),
                "first execution: step body must execute exactly once");

        // 接线验证（#23）：save count = ACTIVE 1 + 终态 1 = 2（终态 save 被 driver 出口调用）
        assertEquals(2, snapshotStore.getSaveCount(stepPath),
                "wiring: terminal saveStepState called after succeed-driver. "
                        + "ACTIVE-time save (1) + terminal save (1) = 2. Pre-fix: only 1 (ACTIVE).");

        // pre-fix gap 证据：第 0 次 save = ACTIVE（若无终态 save，此 ACTIVE snapshot 会是 resume load 的结果）
        ITaskStepState activeSnapshot = snapshotStore.getSnapshotAt(stepPath, 0);
        assertNotNull(activeSnapshot, "ACTIVE-time save must produce a snapshot");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STEP_STATUS_ACTIVE), activeSnapshot.getStepStatus(),
                "first save (ACTIVE-time) snapshots ACTIVE. "
                        + "Pre-fix (no terminal save): resume load would return this ACTIVE snapshot "
                        + "→ reader isDone=false → step re-executed (plan 257 reader 空转).");

        // 最新 snapshot = COMPLETED（终态 save 覆盖 ACTIVE）
        ITaskStepState latestSnapshot = snapshotStore.getLatestSnapshot(stepPath);
        assertNotNull(latestSnapshot);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED),
                latestSnapshot.getStepStatus(),
                "terminal save must overwrite ACTIVE snapshot with COMPLETED");

        // ---- Reset counter ONLY（snapshotStore 保留——终态 snapshot 用于 resume）----
        counter.reset();

        // ---- Resume：新 runtime、fresh DB load（snapshot，非引用）→ reader 命中 isDone → 跳过 step body ----
        Map<String, Object> ret2 = runTask("test/no-decorator-baseline");
        assertEquals("OK", ret2.get(TaskConstants.VAR_RESULT),
                "resume: must return cached result 'OK' (from persisted COMPLETED snapshot, not re-executed body)");

        // 端到端验证（#22）/ 接线验证（#23）：step body **不被重新调用**（counter==0）
        // 关键：snapshot 语义下，load 取回的是 COMPLETED snapshot（非 driver mutate 后的引用）——
        // 若无终态 save，load 取回 ACTIVE snapshot → isDone false → step 重执行（counter==1，plan 257 gap）
        assertEquals(0, counter.get(),
                "resume: step body must NOT be re-executed (counter=0). "
                        + "Snapshot semantics: fresh load returns COMPLETED snapshot (not live reference). "
                        + "Pre-fix (no terminal save): snapshot stays ACTIVE → reader isDone=false → counter=1.");
    }

    // ==================== DB-backed resume FAILED → skip body, rethrow exception ====================

    @Test
    public void resume_failedStep_snapshotLoadRethrowsException_plan258() {
        String stepPath = "@main/resumeFailStep";

        // ---- 第一次执行：step 失败 → 终态 FAILED driver → 终态 saveStepState 持久化 FAILED snapshot ----
        try {
            runTask("test/resume-failed-step");
            fail("first execution: step that always throws should propagate exception");
        } catch (Exception e) {
            assertNotNull(e, "first execution must throw");
        }
        assertEquals(1, counter.get(),
                "first execution: step body must execute once before failing");

        // 接线验证（#23）：save count = ACTIVE 1 + 终态 1 = 2
        assertEquals(2, snapshotStore.getSaveCount(stepPath),
                "wiring: terminal saveStepState called after FAILED-driver. "
                        + "ACTIVE-time save (1) + terminal save (1) = 2.");

        // pre-fix gap 证据：第 0 次 save = ACTIVE
        ITaskStepState activeSnapshot = snapshotStore.getSnapshotAt(stepPath, 0);
        assertNotNull(activeSnapshot);
        assertEquals(Integer.valueOf(TaskConstants.TASK_STEP_STATUS_ACTIVE), activeSnapshot.getStepStatus(),
                "first save (ACTIVE-time) snapshots ACTIVE. Pre-fix: resume load would return ACTIVE.");

        // 最新 snapshot = FAILED + exception
        ITaskStepState latestSnapshot = snapshotStore.getLatestSnapshot(stepPath);
        assertNotNull(latestSnapshot);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED),
                latestSnapshot.getStepStatus(),
                "terminal save must overwrite ACTIVE snapshot with FAILED");
        assertNotNull(latestSnapshot.exception(),
                "terminal snapshot must persist exception (reader can rethrow on resume)");

        // ---- Reset counter ONLY ----
        counter.reset();

        // ---- Resume：fresh DB load（snapshot）→ reader 命中 FAILED → 重抛 exception（非静默跳过，#24）----
        try {
            runTask("test/resume-failed-step");
            fail("resume: FAILED terminal snapshot must rethrow exception, not silently return");
        } catch (Exception e) {
            // 无静默跳过（#24）：FAILED 终态 snapshot 经 fresh load → reader 重抛 exception
            assertNotNull(e, "resume must rethrow exception for FAILED terminal snapshot (not silent skip). "
                    + "Snapshot semantics: fresh load returns FAILED snapshot (not live reference). "
                    + "Pre-fix (no terminal save): snapshot stays ACTIVE → reader isDone=false → step re-executed.");
        }

        // 接线验证（#23）：step body 不被重新调用（counter==0——reader 短路在 step body 之前）
        assertEquals(0, counter.get(),
                "resume: step body must NOT be re-executed for FAILED snapshot (counter=0, reader short-circuited). "
                        + "If counter is 1, the reader failed to short-circuit (snapshot was ACTIVE, not FAILED).");
    }

    // ==================== pre-fix gap 独立证明：ACTIVE snapshot 在终态 save 之前 ====================

    @Test
    public void preFixGap_activeSnapshotBeforeTerminal_provesTerminalSaveNecessity_plan258() {
        // 执行 → ACTIVE snapshot 先于终态 snapshot 被保存
        runTask("test/no-decorator-baseline");

        String stepPath = "@main/plainStep";
        ITaskStepState first = snapshotStore.getSnapshotAt(stepPath, 0);
        ITaskStepState second = snapshotStore.getSnapshotAt(stepPath, 1);

        assertNotNull(first, "ACTIVE-time save snapshot must exist");
        assertNotNull(second, "terminal save snapshot must exist (plan 258 wiring)");

        // 第 0 次 = ACTIVE，第 1 次 = COMPLETED
        // 这证明：若无终态 save（pre-fix），resume load（取 latest snapshot）取回的会是 ACTIVE
        // （因为只有第 0 次 save 存在）→ reader isDone false → step 重执行
        assertEquals(Integer.valueOf(TaskConstants.TASK_STEP_STATUS_ACTIVE), first.getStepStatus(),
                "snapshot[0] = ACTIVE-time save (proves ACTIVE row written first)");
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED), second.getStepStatus(),
                "snapshot[1] = terminal save (proves terminal persistence overwrites ACTIVE). "
                        + "Without this save, latest snapshot would be ACTIVE → plan 257 reader 空转 in production.");

        assertTrue(first.isDone() == false,
                "ACTIVE snapshot isDone=false (reader would NOT skip on this snapshot)");
        assertTrue(second.isDone(),
                "COMPLETED snapshot isDone=true (reader DOES skip on this snapshot)");
    }
}
