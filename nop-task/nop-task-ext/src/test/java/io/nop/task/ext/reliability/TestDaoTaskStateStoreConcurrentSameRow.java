package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.dao.entity.NopTaskStepInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.task.impl.TaskStepRuntimeImpl;
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

import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_stepPath;
import static io.nop.task.dao.entity._gen._NopTaskStepInstance.PROP_NAME_taskInstanceId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 3 [维度05-02]：同一 step 行并发 saveStepState 不再以乐观锁异常冒泡。
 *
 * <p>缺陷（修复前）：持久化模式下 fork/fork-n 分支共享同一 stepPath 行、kill/完成双 driver
 * 等对同一行的并发"读→改→写"，后写者因 ORM version 条件不满足抛乐观锁异常
 * （ERR_ORM_UPDATE_ENTITY_NOT_FOUND），沿分支 promise 冒泡为与任务语义无关的分支失败。
 * owner 已知边界只覆盖"跨进程 fork+DB-resume"，不覆盖运行期并发写。
 *
 * <p>修复：DaoTaskStateStore.saveStepState 按 (taskInstanceId, stepPath) 进程内条带锁串行化
 * read-copy-write。本测试以双线程齐射同一行断言：全程无异常 + 全部更新可见（version 单调推进，
 * 无丢更新）。内存 store（默认）无此问题，测试必须走真实 DB（{@code @NopTestConfig(localDb=true)}）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoTaskStateStoreConcurrentSameRow extends JunitBaseTestCase {

    private static final int THREADS = 2;
    private static final int SAVES_PER_THREAD = 50;

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
        taskState = store.newTaskState("testConcurrentSameRowTask", 0, runtime);
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

    @Test
    public void concurrentSaveSameRow_noOptimisticLockException_noLostUpdate() throws Exception {
        ITaskStepRuntime stepRt = newStepRt("contendedStep");
        ITaskStepState state = stepRt.getState();

        // 首存建行（version 从 0 起）
        store.saveStepState(stepRt);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < SAVES_PER_THREAD; i++)
                        store.saveStepState(stepRt);
                } catch (Throwable e) {
                    errors.add(e);
                }
            }));
        }
        start.countDown();
        for (Future<?> f : futures)
            f.get();
        pool.shutdown();

        assertTrue(errors.isEmpty(),
                "concurrent same-row saves must not surface optimistic-lock exceptions, got: " + errors);

        NopTaskStepInstance row = loadStepEntity(state.getTaskInstanceId(), state.getStepPath());
        assertNotNull(row, "contended row must exist");
        // 全部更新串行化生效：初始 0 + THREADS × SAVES_PER_THREAD 次 update，version 单调推进无丢失
        assertEquals(THREADS * SAVES_PER_THREAD, row.getVersion().intValue(),
                "all concurrent updates must land (no lost update under striped row serialization)");
    }
}
