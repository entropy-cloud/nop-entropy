package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConfigs;
import io.nop.task.TaskConstants;
import io.nop.task.dao.entity.NopTaskInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.49 R4-09（plan 2542）裁定 B：
 * <ul>
 *   <li>红① W-01（1.5-C1-006）：saveTaskState 双写者并发不裸冒泡 ORM 错误 + first-terminal-wins 收敛</li>
 *   <li>红⑪ W-11（1.5-C7-009）：newTaskState 写 workerId + 滞留 ACTIVE 读路径告警</li>
 * </ul>
 * C10-014/W-13（并发 resume 单执行）在 {@link TestConcurrentResumeSingleExecutionR409}。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestTaskStateFencingR409 extends JunitBaseTestCase {

    private static final int THREADS = 2;

    @Inject
    IDaoProvider daoProvider;

    private DaoTaskStateStore store;

    @BeforeEach
    public void setUpStore() {
        store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);
    }

    /**
     * 红①：同 taskInstanceId 双 runtime 并发终态保存——DB 已终态时输家向 DB 真值收敛且不裸冒泡。
     */
    @Test
    public void concurrentTerminalSaves_firstTerminalWinsWithoutRawOrmError() throws Exception {
        ITaskRuntime writer1 = newRuntime();
        ITaskState state1 = store.newTaskState("testW01", 0, writer1);
        ((TaskRuntimeImpl) writer1).setTaskState(state1);

        // T1: 先写 COMPLETED
        state1.setTaskStatus(TaskConstants.TASK_STATUS_COMPLETED);
        writer1.saveTaskState();

        // T2: 独立 runtime，凭陈旧快照写 KILLED（模拟另一路并发终态 driver）
        ITaskRuntime writer2 = newRuntime();
        ITaskState stale = store.loadTaskState(state1.getTaskInstanceId(), writer2);
        assertNotNull(stale);
        ((TaskRuntimeImpl) writer2).setTaskState(stale);

        List<Throwable> errors = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                final boolean isWinnerPath = i == 0;
                pool.submit(() -> {
                    try {
                        if (isWinnerPath) {
                            ITaskRuntime rt = newRuntime();
                            ITaskState s = store.loadTaskState(state1.getTaskInstanceId(), rt);
                            ((TaskRuntimeImpl) rt).setTaskState(s);
                            // 终态已存在：本次写须被 fresh-read 守卫吸收（不裸冒泡）
                            rt.saveTaskState();
                        } else {
                            stale.setTaskStatus(TaskConstants.TASK_STATUS_KILLED);
                            writer2.saveTaskState();
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(30, TimeUnit.SECONDS), "concurrent saves must complete within bound");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(errors.isEmpty(),
                "concurrent saveTaskState must not bubble raw ORM optimistic-lock errors (W-01); got=" + errors);

        IEntityDao<NopTaskInstance> dao = daoProvider.daoFor(NopTaskInstance.class);
        NopTaskInstance row = dao.getEntityById(state1.getTaskInstanceId());
        assertEquals(TaskConstants.TASK_STATUS_COMPLETED, row.getStatus(),
                "first-terminal-wins: COMPLETED (first) must not be overwritten by a later KILLED (W-01)");
        // 输家内存 bean 向 DB 真值收敛（修复前永久漂移 ACTIVE/KILLED）
        assertEquals(TaskConstants.TASK_STATUS_COMPLETED, stale.getTaskStatus(),
                "loser in-memory bean must converge to DB terminal status (W-01 drift closure)");
    }

    /**
     * 红⑪(i)：newTaskState 写入当前节点身份 workerId（修复前全仓无生产赋值点）。
     */
    @Test
    public void newTaskState_assignsWorkerIdNodeIdentity() {
        setTestConfig(TaskConfigs.CFG_TASK_NODE_ID, "node-r409");
        try {
            ITaskRuntime rt = newRuntime();
            ITaskState state = store.newTaskState("testW11", 0, rt);

            IEntityDao<NopTaskInstance> dao = daoProvider.daoFor(NopTaskInstance.class);
            NopTaskInstance row = dao.getEntityById(state.getTaskInstanceId());
            assertEquals("node-r409", row.getWorkerId(),
                    "newTaskState must stamp workerId with the node identity (W-11)");
        } finally {
            clearTestConfig(TaskConfigs.CFG_TASK_NODE_ID.getName());
        }
    }

    /**
     * 红⑪(ii)：滞留 ACTIVE 行（workerId=外来节点）被 loadTaskState 读取 → WARN + gauge 增量；
     * 同节点行不告警（防误报）；且守卫只读——status 仍 ACTIVE。
     */
    @Test
    public void staleActiveRow_warnsAndIncrementsGauge() {
        setTestConfig(TaskConfigs.CFG_TASK_NODE_ID, "node-self-r409");
        try {
            ITaskRuntime rt = newRuntime();
            ITaskState state = store.newTaskState("testW11Stale", 0, rt);

            IEntityDao<NopTaskInstance> dao = daoProvider.daoFor(NopTaskInstance.class);
            NopTaskInstance row = dao.getEntityById(state.getTaskInstanceId());
            // 模拟崩溃残留：ACTIVE 行属主为外部节点
            row.setStatus(TaskConstants.TASK_STATUS_ACTIVE);
            row.setWorkerId("node-foreign-r409");
            dao.updateEntityDirectly(row);

            io.micrometer.core.instrument.MeterRegistry registry = io.nop.commons.metrics.GlobalMeterRegistry.instance();
            double before = registry.find("nop.task.stale-active-loaded").gauge() == null
                    ? 0.0 : registry.find("nop.task.stale-active-loaded").gauge().value();

            store.loadTaskState(state.getTaskInstanceId(), newRuntime());

            assertNotNull(registry.find("nop.task.stale-active-loaded").gauge(),
                    "stale-active-loaded gauge must be registered on stale detection (W-11; pre-fix: absent)");
            assertEquals(before + 1.0, registry.find("nop.task.stale-active-loaded").gauge().value(),
                    "stale ACTIVE load must increment the gauge (W-11)");

            NopTaskInstance afterLoad = dao.getEntityById(state.getTaskInstanceId());
            assertEquals(TaskConstants.TASK_STATUS_ACTIVE, afterLoad.getStatus(),
                    "stale guard is read-only: status must remain ACTIVE (no auto-recovery, W-11)");

            // 同节点腿对照：workerId==self 不告警
            NopTaskInstance own = dao.getEntityById(state.getTaskInstanceId());
            own.setWorkerId("node-self-r409");
            dao.updateEntityDirectly(own);
            double mid = registry.find("nop.task.stale-active-loaded").gauge().value();
            store.loadTaskState(state.getTaskInstanceId(), newRuntime());
            assertEquals(mid, registry.find("nop.task.stale-active-loaded").gauge().value(),
                    "same-node ACTIVE row must not be flagged stale (false-positive guard)");
        } finally {
            clearTestConfig(TaskConfigs.CFG_TASK_NODE_ID.getName());
        }
    }

    private ITaskRuntime newRuntime() {
        return new TaskRuntimeImpl(new TaskFlowManagerImpl(), store, null, XLang.newEvalScope(), false);
    }
}
