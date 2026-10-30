package io.nop.wf.core.engine;

import io.nop.api.core.auth.IUserDelegateService;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.core.mock.InMemoryWfStore;
import io.nop.wf.core.mock.SimpleWfActorResolver;
import io.nop.wf.core.store.IWorkflowRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2514 MA-02（裁定①）红② 收口门测试（C10-003/004/005）：
 * 双线程并发驱动同一 wf 实例的五站点竞态——
 * C10-003: and 执行组双提交，组迁移恰好一次（下游步骤被创建，不会滞留中间态）；
 * C10-004: 末段并发完成，流程终结恰好一次（终态落盘恰好一次）；
 * C10-005: and-join 双上游并发到达，join 步骤实例恰好一个。
 * 缺陷态（无 wf 条带锁）下判定-迁移窗竞态导致组迁移丢失/终态双写/join 双实例（红），
 * 修复后由 IWorkflowStore.withWfLock 串行化判定-迁移段（绿）。
 */
public class TestWorkflowConcurrency extends BaseTestCase {
    private static final int ROUNDS = 100;

    private WorkflowManagerImpl workflowManager;
    private CountingWfStore store;

    /**
     * 终态落盘计数载体：统计 >= HISTORY_BOUND 的 wf 实例状态写次数（C10-004 恰好一次判据）
     */
    static class CountingWfStore extends InMemoryWfStore {
        final AtomicInteger terminalSaves = new AtomicInteger();

        @Override
        public void saveWfRecord(IWorkflowRecord record) {
            if (record.getStatus() != null
                    && record.getStatus() >= NopWfCoreConstants.WF_STATUS_HISTORY_BOUND)
                terminalSaves.incrementAndGet();
            super.saveWfRecord(record);
        }
    }

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
        workflowManager = new WorkflowManagerImpl();

        WorkflowEngineImpl engine = new WorkflowEngineImpl();
        engine.setWfActorResolver(new SimpleWfActorResolver());
        engine.setUserDelegateService(new IUserDelegateService() {
            @Override
            public boolean canDelegate(String userId, String ownerId, String scope) {
                return false;
            }

            @Override
            public java.util.Set<String> getDelegateOwnerIds(String userId, String scope) {
                return java.util.Collections.emptySet();
            }
        });
        workflowManager.setWorkflowEngine(engine);
        store = new CountingWfStore();
        workflowManager.setWorkflowStore(store);
        workflowManager.init();
    }

    private IServiceContext ctx(String userId) {
        IServiceContext context = new ServiceContextImpl();
        context.getContext().setUserId(userId);
        return context;
    }

    private IWorkflow newFlow(String wfName) {
        return workflowManager.newWorkflow(wfName, 1L);
    }

    private List<IWorkflowStep> stepsByName(IWorkflow wf, String stepName) {
        return wf.getSteps(true).stream()
                .filter(s -> s.getStepName().equals(stepName))
                .collect(Collectors.toList());
    }

    private void runConcurrently(Runnable taskA, Runnable taskB) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.function.Function<Runnable, Runnable> wrap = task -> () -> {
                try {
                    start.await();
                    task.run();
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            };
            Future<?> fa = pool.submit(wrap.apply(taskA));
            Future<?> fb = pool.submit(wrap.apply(taskB));
            start.countDown();
            fa.get(30, TimeUnit.SECONDS);
            fb.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        if (failure.get() != null) {
            throw new AssertionError("concurrent task failed", failure.get());
        }
    }

    /**
     * C10-003（MA-02 组迁移站点）：双线程并发 action 同 and 组——组迁移恰好一次。
     * 缺陷态红判据：双提交各自「成功」而组迁移永久丢失（下游 fin 步骤未创建）。
     */
    @Test
    public void testC10_003_andGroupConcurrentSubmit_transitionsExactlyOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            IWorkflow wf = newFlow("test/engineConcurrent");
            wf.start(null, ctx("0"));
            String wfId = wf.getWfId();

            // 发起：wf-start → g1（and 组双实例）
            IWorkflow started = workflowManager.getWorkflow(wfId);
            started.getStepById(stepsByName(started, "wf-start").get(0).getStepId()).invokeAction("go", null, ctx("0"));

            IWorkflow afterGo = workflowManager.getWorkflow(wfId);
            List<IWorkflowStep> members = stepsByName(afterGo, "g1");
            assertEquals(2, members.size());

            runConcurrently(
                    () -> workflowManager.getWorkflow(wfId).getStepById(members.get(0).getStepId())
                            .invokeAction("ok", null, ctx(members.get(0).getActor().getActorId())),
                    () -> workflowManager.getWorkflow(wfId).getStepById(members.get(1).getStepId())
                            .invokeAction("ok", null, ctx(members.get(1).getActor().getActorId())));

            IWorkflow after = workflowManager.getWorkflow(wfId);
            // 组迁移恰好一次：下游 fin 步骤被创建（缺陷态组迁移丢失 → fin 不存在 → 红）
            assertEquals(1, stepsByName(after, "fin").size(),
                    "round " + round + ": group transition lost or duplicated");
            // 组内成员全部退出
            for (IWorkflowStep member : stepsByName(after, "g1")) {
                assertTrue(member.isHistory(), "round " + round + ": member not exited");
            }
        }
    }

    /**
     * C10-004（MA-02 流程终结站点 + C3-011 末段并发完成）：双线程并发末步终结动作——
     * 流程终态落盘恰好一次（实例不滞留、不双写终态）。
     */
    @Test
    public void testC10_004_concurrentFinalCompletion_endsExactlyOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            store.terminalSaves.set(0);
            IWorkflow wf = newFlow("test/engineConcurrent");
            wf.start(null, ctx("0"));
            String wfId = wf.getWfId();

            // 发起 → and 组内两个成员按各自 owner 顺序完成 → 到达末步 fin
            IWorkflow started = workflowManager.getWorkflow(wfId);
            started.getStepById(stepsByName(started, "wf-start").get(0).getStepId()).invokeAction("go", null, ctx("0"));
            IWorkflow afterGo = workflowManager.getWorkflow(wfId);
            IWorkflowStep firstMember = stepsByName(afterGo, "g1").get(0);
            afterGo.getStepById(firstMember.getStepId())
                    .invokeAction("ok", null, ctx(firstMember.getActor().getActorId()));
            IWorkflow afterFirst = workflowManager.getWorkflow(wfId);
            IWorkflowStep remaining = stepsByName(afterFirst, "g1").stream()
                    .filter(s -> !s.isHistory()).findFirst().orElseThrow(AssertionError::new);
            afterFirst.getStepById(remaining.getStepId())
                    .invokeAction("ok", null, ctx(remaining.getActor().getActorId()));

            IWorkflow beforeFinal = workflowManager.getWorkflow(wfId);
            IWorkflowStep fin = stepsByName(beforeFinal, "fin").get(0);

            // 裁定① action 行：第二到达者的迁移被锁内终检拒绝（ERR_.._STATUS 兜底复用），
            // 输家收到显式错误属预期行为，不计入失败
            runConcurrently(
                    () -> invokeToleratingRace(wfId, fin.getStepId(), "3"),
                    () -> invokeToleratingRace(wfId, fin.getStepId(), "3"));

            IWorkflow after = workflowManager.getWorkflow(wfId);
            assertTrue(after.isEnded(), "round " + round + ": workflow not ended");
            assertEquals(NopWfCoreConstants.WF_STATUS_COMPLETED, after.getRecord().getStatus());
            // 终态落盘恰好一次（缺陷态双实例并发 checkEnd → 终态双写 → 红）
            assertEquals(1, store.terminalSaves.get(),
                    "round " + round + ": terminal state written more than once");
        }
    }

    /**
     * 裁定① action 行：并发双提交的输家以 ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS
     * 兜底拒绝（状态面恰好一次迁移），测试侧容忍该显式错误。
     */
    private void invokeToleratingRace(String wfId, String stepId, String userId) {
        try {
            workflowManager.getWorkflow(wfId).getStepById(stepId).invokeAction("sp", null, ctx(userId));
        } catch (io.nop.api.core.exceptions.NopException e) {
            if (!io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS.getErrorCode()
                    .equals(e.getErrorCode()))
                throw e;
        }
    }

    /**
     * C10-005（MA-02 join 双实例站点，C3-012 and 型）：and-split 双上游并发到达 and-join
     * 步骤——join 步骤实例恰好一个（缺陷态查建无互斥 → 双实例 → 红）。
     */
    @Test
    public void testC10_005_joinConcurrentArrival_singleJoinInstance() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            IWorkflow wf = newFlow("test/engineJoinConcurrent");
            wf.start(null, ctx("0"));
            String wfId = wf.getWfId();

            // 发起：wf-start → and-split 创建 u1/u2 双上游
            IWorkflow started = workflowManager.getWorkflow(wfId);
            started.getStepById(stepsByName(started, "wf-start").get(0).getStepId()).invokeAction("go", null, ctx("0"));

            IWorkflow afterStart = workflowManager.getWorkflow(wfId);
            IWorkflowStep u1 = stepsByName(afterStart, "u1").get(0);
            IWorkflowStep u2 = stepsByName(afterStart, "u2").get(0);
            assertNotNull(u1);
            assertNotNull(u2);

            runConcurrently(
                    () -> workflowManager.getWorkflow(wfId).getStepById(u1.getStepId())
                            .invokeAction("ok1", null, ctx("1")),
                    () -> workflowManager.getWorkflow(wfId).getStepById(u2.getStepId())
                            .invokeAction("ok2", null, ctx("2")));

            IWorkflow after = workflowManager.getWorkflow(wfId);
            // join 恰好单实例（缺陷态并发查建 → 双实例 → 红）
            assertEquals(1, stepsByName(after, "j").size(),
                    "round " + round + ": join step duplicated");
        }
    }
}
