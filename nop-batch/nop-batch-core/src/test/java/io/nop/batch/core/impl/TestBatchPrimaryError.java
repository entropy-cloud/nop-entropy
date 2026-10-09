package io.nop.batch.core.impl;

import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.exceptions.BatchCancelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.nop.batch.core.BatchErrors.ERR_BATCH_CANCEL_PROCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-3.3-003（plan 2381 Phase 2）：allOf 异常交付受完成时序竞态影响（audit 探针 5/5 实证：
 * 树左优先使 cancel 异常掩蔽 fail-fast 根因）——修复机制 = AtomicReference CAS 收集首个非取消
 * 异常作 primaryError，onTaskComplete 优先交付。
 * <p>
 * 判别形式裁定：真实竞态依赖线程时序（probe 5 轮可复现但非单测确定性），单测以机制直测替代——
 * onTaskComplete 直调 + primaryError 预置真根因 + allOf err=BatchCancelException，断言
 * saveTaskState 与 future 均以真根因收尾（修复前该路径交付 BCE）。CAS 收集点与 allOf 优先级
 * 接线由代码审查锚定（BatchTask executeChunkLoop catch / allOf 回调）。纯取消对照（无根因→
 * BCE/KILLED 保持）由本文件第二用例与既有 defect5 覆盖。
 */
@Timeout(30)
public class TestBatchPrimaryError {

    private BatchTask newTask(IBatchStateStore store) {
        return new BatchTask("test", 0, null, Runnable::run, 1,
                null, null, null, store, null, 0);
    }

    @Test
    public void testPrimaryErrorBeatsAllOfDeliveredCancelException() throws Exception {
        List<Throwable> savedErrors = new CopyOnWriteArrayList<>();
        IBatchStateStore capturingStore = new IBatchStateStore() {
            @Override
            public void loadTaskState(IBatchTaskContext context) {
            }

            @Override
            public void saveTaskState(boolean complete, Throwable ex, IBatchTaskContext context) {
                if (complete)
                    savedErrors.add(ex);
            }
        };

        BatchTask task = newTask(capturingStore);

        // 模拟竞态结果：真根因已 CAS 入 primaryError，而 allOf 交付的是兄弟线程的取消异常
        Throwable rootCause = new RuntimeException("root-cause");
        task.primaryErrorRef.set(rootCause);

        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        ctx.setTaskName("primary-error");

        CompletableFuture<Void> future = new CompletableFuture<>();
        Throwable allOfDelivered = new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);
        task.onTaskComplete(future, null, allOfDelivered, ctx);

        assertTrue(future.isCompletedExceptionally(), "task must fail");
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof RuntimeException
                        && "root-cause".equals(ex.getCause().getMessage()),
                "future must be completed with the primary root cause, got: " + ex.getCause());

        assertEquals(1, savedErrors.size(), "saveTaskState must capture exactly one final error");
        assertTrue(savedErrors.get(0) == rootCause,
                "primaryError (root cause) must drive saveTaskState terminal state, got: "
                        + savedErrors.get(0));
    }

    @Test
    public void testNoPrimaryErrorFallsBackToAllOfError() {
        List<Throwable> savedErrors = new CopyOnWriteArrayList<>();
        IBatchStateStore capturingStore = new IBatchStateStore() {
            @Override
            public void loadTaskState(IBatchTaskContext context) {
            }

            @Override
            public void saveTaskState(boolean complete, Throwable ex, IBatchTaskContext context) {
                if (complete)
                    savedErrors.add(ex);
            }
        };

        BatchTask task = newTask(capturingStore);
        assertTrue(task.primaryErrorRef.get() == null, "fresh task must have empty primaryError");

        BatchTaskContextImpl ctx = new BatchTaskContextImpl();
        ctx.setTaskName("pure-cancel");
        CompletableFuture<Void> future = new CompletableFuture<>();
        Throwable cancelEx = new BatchCancelException(ERR_BATCH_CANCEL_PROCESS);
        task.onTaskComplete(future, null, cancelEx, ctx);

        assertEquals(1, savedErrors.size());
        assertTrue(savedErrors.get(0) == cancelEx,
                "without primaryError the allOf-delivered cancel exception must drive the terminal state");
    }
}
