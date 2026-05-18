package io.nop.job.coordinator.engine;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultJobCancelHandler {
    private IBeanContainer originalContainer;
    private DefaultJobCancelHandler handler;

    @BeforeEach
    void setUp() {
        originalContainer = BeanContainer.isInitialized() ? BeanContainer.instance() : null;
        handler = new DefaultJobCancelHandler();
    }

    @AfterEach
    void tearDown() {
        if (originalContainer != null) {
            BeanContainer.registerInstance(originalContainer);
        }
    }

    private void setupContainer(String beanName, Object bean) {
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean(beanName, bean);
        BeanContainer.registerInstance(container);
    }

    private void setupEmptyContainer() {
        BeanContainer.registerInstance(new StaticBeanContainer());
    }

    private NopJobSchedule createSchedule(String scheduleId, String executorKind) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(scheduleId);
        schedule.setJobName("testJob");
        schedule.setExecutorKind(executorKind);
        return schedule;
    }

    private NopJobFire createFire(String fireId, String scheduleId, String executorKind) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(fireId);
        fire.setJobScheduleId(scheduleId);
        fire.setExecutorKind(executorKind);
        return fire;
    }

    private NopJobTask createTask(String taskId, String fireId) {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(taskId);
        task.setJobFireId(fireId);
        return task;
    }

    /**
     * Branch 1: executorKind resolves, invoker found → cancelAsync called successfully
     */
    @Test
    void testCancelRunningTask_normalCancel() {
        AtomicBoolean cancelCalled = new AtomicBoolean(false);
        AtomicReference<IJobExecutionContext> capturedCtx = new AtomicReference<>();

        IJobInvoker invoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return null;
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                cancelCalled.set(true);
                capturedCtx.set(jobCtx);
                return CompletableFuture.completedFuture(true);
            }
        };

        setupContainer("nopJobInvoker_test", invoker);

        NopJobSchedule schedule = createSchedule("s1", "test");
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        handler.cancelRunningTask(schedule, fire, task);

        assertTrue(cancelCalled.get(), "cancelAsync should have been called");

        IJobExecutionContext ctx = capturedCtx.get();
        assertNotNull(ctx, "CancelJobExecutionContext should be passed to invoker");
        assertEquals("s1", ctx.getJobDefId());
        assertEquals("t1", ctx.getInstanceId());
    }

    /**
     * Branch 1 variant: executorKind comes from fire, not schedule
     */
    @Test
    void testCancelRunningTask_executorKindFromFire() {
        AtomicBoolean cancelCalled = new AtomicBoolean(false);

        IJobInvoker invoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return null;
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                cancelCalled.set(true);
                return CompletableFuture.completedFuture(true);
            }
        };

        setupContainer("nopJobInvoker_rpc", invoker);

        NopJobSchedule schedule = createSchedule("s1", "local");
        NopJobFire fire = createFire("f1", "s1", "rpc");
        NopJobTask task = createTask("t1", "f1");

        handler.cancelRunningTask(schedule, fire, task);

        assertTrue(cancelCalled.get(), "cancelAsync should have been called with fire's executorKind");
    }

    /**
     * Branch 2: executorKind resolves, invoker NOT found → early return
     */
    @Test
    void testCancelRunningTask_invokerNotFound() {
        setupEmptyContainer();

        NopJobSchedule schedule = createSchedule("s1", "unknown");
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        handler.cancelRunningTask(schedule, fire, task);
    }

    /**
     * Branch 2 variant: bean exists but is not an IJobInvoker
     */
    @Test
    void testCancelRunningTask_beanIsNotInvoker() {
        setupContainer("nopJobInvoker_test", "not-an-invoker");

        NopJobSchedule schedule = createSchedule("s1", "test");
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        handler.cancelRunningTask(schedule, fire, task);
    }

    /**
     * Branch 3: executorKind is null (both fire and schedule have null) → invoker null → early return
     */
    @Test
    void testCancelRunningTask_nullExecutorKind() {
        NopJobSchedule schedule = createSchedule("s1", null);
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        handler.cancelRunningTask(schedule, fire, task);
    }

    /**
     * Branch 3 variant: executorKind is blank/whitespace → invoker null → early return
     */
    @Test
    void testCancelRunningTask_blankExecutorKind() {
        NopJobSchedule schedule = createSchedule("s1", "");
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        handler.cancelRunningTask(schedule, fire, task);
    }

    /**
     * Branch 3 variant: executorKind is whitespace-only → invoker null
     */
    @Test
    void testCancelRunningTask_whitespaceExecutorKind() {
        NopJobSchedule schedule = createSchedule("s1", "   ");
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        handler.cancelRunningTask(schedule, fire, task);
    }

    /**
     * Branch 3 variant: executorKind is set but BeanContainer is not initialized
     */
    @Test
    void testCancelRunningTask_beanContainerNotInitialized() {
        NopJobSchedule schedule = createSchedule("s1", "test");
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        handler.cancelRunningTask(schedule, fire, task);
    }

    /**
     * Branch 4: invoker.cancelAsync() throws exception → caught and logged
     */
    @Test
    void testCancelRunningTask_invokerThrowsException() {
        IJobInvoker throwingInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return null;
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                throw new RuntimeException("cancel failed");
            }
        };

        setupContainer("nopJobInvoker_test", throwingInvoker);

        NopJobSchedule schedule = createSchedule("s1", "test");
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        // Exception should be caught internally, not propagated
        handler.cancelRunningTask(schedule, fire, task);
    }

    /**
     * Branch 4 variant: cancelAsync returns CompletionStage that completes exceptionally
     */
    @Test
    void testCancelRunningTask_cancelAsyncCompletesExceptionally() {
        AtomicBoolean cancelCalled = new AtomicBoolean(false);

        IJobInvoker invoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return null;
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                cancelCalled.set(true);
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                future.completeExceptionally(new RuntimeException("async cancel failed"));
                return future;
            }
        };

        setupContainer("nopJobInvoker_test", invoker);

        NopJobSchedule schedule = createSchedule("s1", "test");
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        // The whenComplete handler logs the error but does not propagate
        handler.cancelRunningTask(schedule, fire, task);

        assertTrue(cancelCalled.get(), "cancelAsync should have been called");
    }

    /**
     * Verify cancelAsync returning null CompletionStage does not cause NPE
     */
    @Test
    void testCancelRunningTask_cancelAsyncReturnsNull() {
        IJobInvoker invoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return null;
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return null;
            }
        };

        setupContainer("nopJobInvoker_test", invoker);

        NopJobSchedule schedule = createSchedule("s1", "test");
        NopJobFire fire = createFire("f1", "s1", null);
        NopJobTask task = createTask("t1", "f1");

        handler.cancelRunningTask(schedule, fire, task);
    }
}
