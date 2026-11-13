package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskErrors;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskFlowManagerImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.49 R4-09（plan 2542）W-13（1.5-C7-014；C10-014 宿主）红⑬：并发 resume 执行级 fencing。
 *
 * <p>50 轮竞速：挂起任务恢复后双线程并发 resume，断言 mainStep 业务体执行恒 == 1/轮，
 * 输家路以 {@code ERR_TASK_INSTANCE_ALREADY_RUNNING} 确定性失败；顺序 resume 对照腿
 * 证明 fence 不误伤合法恢复（防「fencing 拒绝合法恢复」反向伪绿）。
 *
 * <p>概率腿（竞速窗未命中轮）诚实登记：以 CyclicBarrier 对齐 claim 点前构造确定性并发，
 * 若某轮输家未命中并发窗（先路已释放 fence），该轮按「顺序 resume」语义计为合法成功，
 * 计数断言仍成立（执行恒==1），并在统计中记录未命中轮数。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/task/test/beans/test-reliability.beans.xml")
public class TestConcurrentResumeSingleExecutionR409 extends JunitBaseTestCase {

    private static final int ROUNDS = 50;

    @Inject
    ITaskFlowManager injectedTaskFlowManager;

    @Inject
    io.nop.dao.api.IDaoProvider daoProvider;

    private ExecutionCounterBean counter;

    // resume 需持久化 task state store（getTaskRuntime 要求；生产为 DaoTaskStateStore）
    private DaoTaskStateStore stateStore;

    @BeforeEach
    public void setUpCounter() {
        counter = (ExecutionCounterBean) io.nop.api.core.ioc.BeanContainer.instance()
                .getBean(ExecutionCounterBean.BEAN_NAME);
        counter.reset();
        stateStore = new DaoTaskStateStore();
        stateStore.setDaoProvider(daoProvider);
    }

    private TaskFlowManagerImpl newManager() {
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        manager.setTaskStateStore(stateStore);
        return manager;
    }

    /**
     * 红⑬/C10-014：50 轮并发 resume，执行恒 == 1。
     */
    @Test
    public void concurrentResume_mainStepExecutesExactlyOncePerRound() throws Exception {
        TaskFlowManagerImpl manager = newManager();
        ITask task = manager.getTask("test/r409-concurrent-resume", 0);

        int winnerFailures = 0;
        int unmappedRounds = 0;

        for (int round = 0; round < ROUNDS; round++) {
            counter.reset();

            // 第一次执行：进入 SUSPENDED
            ITaskRuntime first = manager.newTaskRuntime(task, true, null);
            task.execute(first);
            String taskInstanceId = first.getTaskInstanceId();
            counter.reset();

            CountDownLatch ready = new CountDownLatch(2);
            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Throwable> errors = new CopyOnWriteArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?>[] futures = new Future<?>[2];
                for (int i = 0; i < 2; i++) {
                    futures[i] = pool.submit(() -> {
                        ready.countDown();
                        try {
                            barrier.await(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        ITaskRuntime rt = manager.getTaskRuntime(taskInstanceId, null);
                        try {
                            task.execute(rt).syncGetOutputs();
                        } catch (NopException e) {
                            errors.add(e);
                        }
                        return null;
                    });
                }
                ready.await(30, TimeUnit.SECONDS);
                for (Future<?> f : futures)
                    f.get(60, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            assertEquals(1, counter.get(),
                    "round " + round + ": mainStep body must execute exactly once under concurrent resume "
                            + "(W-13 fencing); pre-fix: executes twice (side effects doubled)");

            for (Throwable t : errors) {
                if (t instanceof NopException
                        && TaskErrors.ERR_TASK_INSTANCE_ALREADY_RUNNING.getErrorCode()
                        .equals(((NopException) t).getErrorCode()))
                    winnerFailures++;
            }
            if (errors.isEmpty())
                unmappedRounds++;
            else
                assertEquals(1, errors.size(),
                        "round " + round + ": at most one loser is expected, got " + errors);
        }

        // 概率腿诚实登记：绝大多数轮次输家确定性命中 fence（latch 对齐构造）；
        // 未命中轮（先路已释放）按顺序 resume 语义合法，计数断言不受影响
        assertTrue(winnerFailures + unmappedRounds == ROUNDS,
                "every round must be explained by either a fenced loser or a legal sequential resume");
        System.out.println("R409 C10-014: rounds=" + ROUNDS + ", fencedLoserRounds=" + winnerFailures
                + ", unmappedRounds=" + unmappedRounds);
        assertTrue(winnerFailures > 0,
                "latch-aligned concurrent resume must deterministically hit the fence at least once "
                        + "(if this ever fails, the race window is not being exercised — honest registration applies)");
    }

    /**
     * 红⑬ 对照腿：顺序 resume（先路完全退出后第二次 resume）必须成功——防 fencing 误伤合法恢复。
     */
    @Test
    public void sequentialResume_afterFenceReleased_isAllowed() {
        TaskFlowManagerImpl manager = newManager();
        ITask task = manager.getTask("test/r409-concurrent-resume", 0);

        ITaskRuntime first = manager.newTaskRuntime(task, true, null);
        task.execute(first);
        String taskInstanceId = first.getTaskInstanceId();
        counter.reset();

        // 顺序 resume #1：正常完成（不触发 fence）
        ITaskRuntime second = manager.getTaskRuntime(taskInstanceId, null);
        Map<String, Object> out = task.execute(second).syncGetOutputs();
        assertEquals("42", String.valueOf(out.get(TaskConstants.VAR_RESULT)).trim(),
                "sequential resume must succeed and run mainStep once");
        assertEquals(1, counter.get(), "sequential resume must execute mainStep exactly once");

        // 顺序 resume #2：fence 已在 #1 出口释放，故仍可再次 claim（不误拒）
        counter.reset();
        // 顺序 resume #2：任务此时已 COMPLETED → 短路返回缓存 result（不再执行 body），
        // 证 fence 已在 #1 出口释放（否则此处会抛 ERR_TASK_INSTANCE_ALREADY_RUNNING）
        ITaskRuntime third = manager.getTaskRuntime(taskInstanceId, null);
        assertDoesNotThrow(() -> task.execute(third).syncGetOutputs(),
                "fence must be released on every exit path (Throwable-safe) — a later resume must not be rejected");
        assertEquals(0, counter.get(),
                "already-COMPLETED resume short-circuits (no body re-execution)");
    }
}
