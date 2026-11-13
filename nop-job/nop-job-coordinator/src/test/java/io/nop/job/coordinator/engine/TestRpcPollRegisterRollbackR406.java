package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.util.ICancelToken;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.46 R4-06 (plan 2539) W-06 / 1.4-C1-004 — driver 红⑥ (REE-injected
 * deterministic red): {@code register} runs inside the RemoteJobInvoker whenComplete
 * ACTION whose exception the future framework swallows. Pre-fix, a
 * {@code scheduleWithFixedDelay} RejectedExecutionException (shutdown window) left
 * the entry + cancel listener leaked, the promise never completed and the task row
 * stayed RUNNING with zero log. Post-fix the registration rolls back via the
 * idempotent cancelEntry chain and completes the caller's future with the existing
 * remote-invoke-failure error — the task terminates through the normal failure path.
 */
public class TestRpcPollRegisterRollbackR406 {

    private static final String TASK_ID = "task-r406-rollback";

    private RpcPollTaskManager manager;

    @BeforeEach
    void setUp() {
        manager = new RpcPollTaskManager();
        manager.setPollIntervalMs(1000);
    }

    @AfterEach
    void tearDown() {
        // registry must always drain (rollback or termination)
        if (manager != null) {
            long deadline = System.currentTimeMillis() + 5000;
            while (manager.activeCount() > 0 && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * rejecting wrapper: delegates everything to the global timer EXCEPT
     * scheduleWithFixedDelay, which throws REE (shutdown-window shape).
     */
    static final class RejectingScheduledExecutor
            implements io.nop.commons.concurrent.executor.IScheduledExecutor {
        private final io.nop.commons.concurrent.executor.IScheduledExecutor delegate =
                io.nop.commons.concurrent.executor.GlobalExecutors.globalTimer();

        @Override
        public <V> CompletableFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay,
                                                 TimeUnit unit) {
            return delegate.schedule(callable, delay, unit);
        }

        @Override
        public java.util.concurrent.Future<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                                                                  long period, TimeUnit unit) {
            return delegate.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        @Override
        public java.util.concurrent.Future<?> scheduleWithFixedDelay(Runnable command,
                                                                     long initialDelay, long delay,
                                                                     TimeUnit unit) {
            throw new java.util.concurrent.RejectedExecutionException("r406: executor shutting down");
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }

        @Override
        public <V> CompletableFuture<V> submit(java.util.concurrent.Callable<V> callable) {
            return delegate.submit(callable);
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return delegate.submit(task, result);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolConfig getConfig() {
            return delegate.getConfig();
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolStats stats() {
            return delegate.stats();
        }

        @Override
        public void destroy() {
            // NOTE: delegate lifecycle untouched — this stub never destroys the global timer
        }

        @Override
        public void refreshConfig() {
            delegate.refreshConfig();
        }
    }

    @Test
    public void rejectedRegistrationRollsBackAndCompletesError() throws Exception {
        manager.setScheduledExecutor(new RejectingScheduledExecutor());

        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        long t0 = System.currentTimeMillis();
        manager.register(newSchedule(null), newFire(), newTask(), null, future);

        // post-fix: the future is completed with the existing remote-invoke-failure
        // error — the caller (and the task row) terminate through the failure path
        JobFireResult result = future.get(5, TimeUnit.SECONDS);
        long settleMillis = System.currentTimeMillis() - t0;
        assertTrue(settleMillis < 4500, "the rollback must settle the future promptly");

        assertNotNull(result.getError(), "the terminal result must carry an explicit error");
        assertEquals(JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED.getErrorCode(),
                result.getError().getErrorCode(),
                "rollback must resolve ERR_JOB_REMOTE_INVOKE_FAILED (existing code, no silent clear)");
        assertEquals(0, manager.activeCount(),
                "the registry must be empty after rollback (entry + listener cleaned via cancelEntry)");
    }

    /**
     * control leg: the steady-state path (working executor) still registers normally
     * — the rollback wrapper must not disturb the happy path (2513 four-channel
     * zero-touch face).
     */
    @Test
    public void steadyStateRegistrationStillWorks() throws Exception {
        manager.setRpcPollTaskClient(new IRpcPollTaskClient() {
            @Override
            public CompletionStage<String> startJob(NopJobSchedule s, NopJobFire f, NopJobTask t,
                                                    ICancelToken token) {
                return CompletableFuture.completedFuture(t.getJobTaskId());
            }

            @Override
            public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule s, NopJobFire f,
                                                                NopJobTask t, ICancelToken token) {
                TaskStatusBean bean = new TaskStatusBean();
                bean.setTaskStatus(3); // SUCCESS — terminal on the first poll
                return CompletableFuture.completedFuture(bean);
            }

            @Override
            public CompletionStage<Boolean> cancelJob(NopJobSchedule s, NopJobFire f, NopJobTask t,
                                                      ICancelToken token) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        manager.setPollFailThreshold(3);
        manager.setScheduledExecutor(io.nop.commons.concurrent.executor.GlobalExecutors.globalTimer());

        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        manager.register(newSchedule(null), newFire(), newTask(), null, future);

        JobFireResult result = future.get(15, TimeUnit.SECONDS);
        assertTrue(!result.isErrorResult() || result.isCompleted(),
                "the steady-state path must complete normally (not via rollback)");
        // bounded drain: the whenComplete→cancelEntry removal runs on the completing
        // poller thread — the main thread's get() can unblock a step early (same
        // race the EntryTermination suite handles with a deadline drain loop)
        long deadline = System.currentTimeMillis() + 5000;
        while (manager.activeCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(0, manager.activeCount(), "terminated entry leaves the registry");
    }

    private NopJobTask newTask() {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(TASK_ID);
        task.setJobFireId("fire-r406-rollback");
        task.setTaskStatus(0);
        task.setTargetHost("worker-host-1");
        return task;
    }

    private NopJobFire newFire() {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("fire-r406-rollback");
        fire.setJobScheduleId("sched-r406-rollback");
        return fire;
    }

    private NopJobSchedule newSchedule(Integer timeoutSeconds) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId("sched-r406-rollback");
        schedule.setJobName("r406RollbackJob");
        schedule.setTimeoutSeconds(timeoutSeconds);
        return schedule;
    }
}
