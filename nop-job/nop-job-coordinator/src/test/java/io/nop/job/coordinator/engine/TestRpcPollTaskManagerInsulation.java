package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.util.ICancelToken;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2496 1.4-MA-05 站点①（C4-004）回归：RpcPollTaskManager.pollOnce 的任何 Throwable（含 Error）
 * 都在站点守卫内捕获留痕，不逃逸到周期设施边界——entry 不再僵尸化零日志，后续轮次仍正常触发。
 */
public class TestRpcPollTaskManagerInsulation {

    private static final String TASK_ID = "task-ma05-1";
    private static final String FIRE_ID = "fire-ma05-1";
    private static final String SCHEDULE_ID = "sched-ma05-1";

    private NopJobTask newTask() {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(TASK_ID);
        task.setJobFireId(FIRE_ID);
        task.setTaskStatus(0);
        return task;
    }

    private NopJobFire newFire() {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(FIRE_ID);
        fire.setJobScheduleId(SCHEDULE_ID);
        return fire;
    }

    private NopJobSchedule newSchedule() {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(SCHEDULE_ID);
        schedule.setJobName("ma05Job");
        return schedule;
    }

    @Test
    void testPollOnceErrorDoesNotEscape() {
        RpcPollTaskManager manager = new RpcPollTaskManager();
        manager.setRpcPollTaskClient(new IRpcPollTaskClient() {
            @Override
            public CompletionStage<String> startJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                                                    ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(TASK_ID);
            }

            @Override
            public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule schedule, NopJobFire fire,
                                                                NopJobTask task, ICancelToken cancelToken) {
                throw new LinkageError("poll-boom");
            }

            @Override
            public CompletionStage<Boolean> cancelJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                                                      ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });

        CompletableFuture<io.nop.job.api.execution.JobFireResult> future = new CompletableFuture<>();
        RpcPollTaskManager.PollEntry entry = new RpcPollTaskManager.PollEntry(
                TASK_ID, newSchedule(), newFire(), newTask(), null, future, 0L);

        // MA-05 站点①：修复前 Error 从 pollOnce 逃逸（JDK 周期抑制 + entry 僵尸化零日志）；修复后站点守卫捕获
        assertDoesNotThrow(() -> manager.pollOnce(entry));
        assertFalse(entry.getFuture().isDone(),
                "a failed poll round must not settle the entry future (next round still polls)");
    }

    /**
     * 站点①链级验收：注册在真实周期设施上的 entry，getJobStatus 每轮抛 Error 时轮询链仍存活
     * （多轮触发），entry 不从注册表消失（deadline 兜底通道保持可用）。
     */
    @Test
    void testPollChainSurvivesErrorRounds() throws Exception {
        RpcPollTaskManager manager = new RpcPollTaskManager();
        AtomicInteger pollCalls = new AtomicInteger();
        manager.setRpcPollTaskClient(new IRpcPollTaskClient() {
            @Override
            public CompletionStage<String> startJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                                                    ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(TASK_ID);
            }

            @Override
            public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule schedule, NopJobFire fire,
                                                                NopJobTask task, ICancelToken cancelToken) {
                pollCalls.incrementAndGet();
                throw new LinkageError("poll-boom-round-" + pollCalls.get());
            }

            @Override
            public CompletionStage<Boolean> cancelJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task,
                                                      ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        // 平台 IScheduledExecutor 载体（生产注入形态）+ 最小轮询周期
        manager.setScheduledExecutor(io.nop.commons.concurrent.executor.GlobalExecutors.globalTimer());
        manager.setPollIntervalMs(1000);

        CompletableFuture<io.nop.job.api.execution.JobFireResult> future = new CompletableFuture<>();
        manager.register(newSchedule(), newFire(), newTask(), null, future);
        try {
            long deadline = System.currentTimeMillis() + 10_000;
            while (pollCalls.get() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(pollCalls.get() >= 2,
                    "MA-05 station-1: poll chain must survive Error rounds (multiple rounds trigger)");
            assertEquals(1, manager.activeCount(),
                    "entry stays registered (deadline fallback channel intact after failed rounds)");
            assertFalse(future.isDone());
        } finally {
            // 清理：终结 future 触发 whenComplete → cancelEntry 摘除句柄
            future.complete(io.nop.job.api.execution.JobFireResult.CONTINUE);
        }
    }
}
