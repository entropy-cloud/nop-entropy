package io.nop.job.local;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.job.api.JobState;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestLocalJobScheduler {

    private LocalJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> mockInvoker()
        );
        scheduler.activate();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.deactivate();
        }
    }

    static class MockInvoker implements IJobInvoker {
        final AtomicInteger invokeCount = new AtomicInteger();

        @Override
        public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
            invokeCount.incrementAndGet();
            return null;
        }

        @Override
        public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
            return CompletableFuture.completedFuture(true);
        }
    }

    private static IJobInvoker mockInvoker() {
        return new MockInvoker();
    }

    @Test
    void testAddAndRemove() {
        JobSpec spec = newSpec("test-add", 10, true);
        scheduler.addJob(spec, false);

        assertEquals(1, scheduler.getJobNames().size());
        assertTrue(scheduler.getJobNames().contains("test-add"));
        assertNotNull(scheduler.getJobDetail("test-add"));

        assertTrue(scheduler.removeJob("test-add"));
        assertEquals(0, scheduler.getJobNames().size());
        assertNull(scheduler.getJobDetail("test-add"));
    }

    @Test
    void testSuspendResume() {
        JobSpec spec = newSpec("test-suspend", 100, true);
        scheduler.addJob(spec, false);

        assertTrue(scheduler.suspendJob("test-suspend"));
        assertEquals(JobState.SUSPENDED, scheduler.getJobState("test-suspend"));

        assertTrue(scheduler.resumeJob("test-suspend"));
        assertNotNull(scheduler.getJobState("test-suspend"));
        assertNotEquals(JobState.SUSPENDED, scheduler.getJobState("test-suspend"));
    }

    @Test
    void testCancel() {
        JobSpec spec = newSpec("test-cancel", 100, true);
        scheduler.addJob(spec, false);

        assertTrue(scheduler.cancelJob("test-cancel"));
        assertEquals(JobState.COMPLETED, scheduler.getJobState("test-cancel"));
    }

    @Test
    void testPeriodicExecution() throws Exception {
        MockInvoker invoker = new MockInvoker();
        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> invoker
        );
        sched.activate();

        try {
            JobSpec spec = newSpec("test-periodic", 10, true);
            sched.addJob(spec, false);

            assertTrue(sched.awaitFireCount("test-periodic", 6, 5, TimeUnit.SECONDS),
                    "expected > 5 invocations but got " + invoker.invokeCount.get());
        } finally {
            sched.deactivate();
        }
    }

    /**
     * check2 [P0] timer 路径契约：once 型 job（无 cron、repeatInterval<=0）只触发一次。
     * 覆盖两种形态：指定 minScheduleTime（到点触发一次）与未指定（立即触发一次）。
     * 触发后 job 必须 COMPLETED（onceTask 时从注册表移除），且等待期内不再产生第二次执行。
     */
    @Test
    void testOnceJobFiresExactlyOnce() throws Exception {
        MockInvoker scheduled = new MockInvoker();
        LocalJobScheduler sched1 = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> scheduled);
        sched1.activate();
        try {
            JobSpec spec = new JobSpec();
            spec.setJobName("test-once-scheduled");
            spec.setJobInvoker("mock");
            spec.setOnceTask(true);
            TriggerSpec ts = new TriggerSpec();
            ts.setMinScheduleTime(System.currentTimeMillis() + 100);
            spec.setTriggerSpec(ts);
            sched1.addJob(spec, false);

            // onceTask 完成后即从注册表移除（fireCount 随之不可观测），以注册表移除 + 实际执行次数断言
            assertTrue(sched1.awaitState("test-once-scheduled", null, 5, TimeUnit.SECONDS),
                    "onceTask job must be removed from the registry after its single fire");
            // 再等 3 个调度周期以上，确认不再触发
            Thread.sleep(300);
            assertEquals(1, scheduled.invokeCount.get(), "scheduled once job must fire exactly once");
        } finally {
            sched1.deactivate();
        }

        MockInvoker immediate = new MockInvoker();
        LocalJobScheduler sched2 = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> immediate);
        sched2.activate();
        try {
            JobSpec spec = new JobSpec();
            spec.setJobName("test-once-immediate");
            spec.setJobInvoker("mock");
            TriggerSpec ts = new TriggerSpec();
            // no cron, no repeatInterval, no minScheduleTime → fire immediately, once
            spec.setTriggerSpec(ts);
            sched2.addJob(spec, false);

            assertTrue(sched2.awaitFireCount("test-once-immediate", 1, 5, TimeUnit.SECONDS));
            assertTrue(sched2.awaitState("test-once-immediate", JobState.COMPLETED, 5, TimeUnit.SECONDS),
                    "non-onceTask once job must end in COMPLETED (not WAITING for another fire)");
            Thread.sleep(300);
            assertEquals(1, immediate.invokeCount.get(), "immediate once job must fire exactly once");
        } finally {
            sched2.deactivate();
        }
    }

    /**
     * check2 [P3-13]: SUSPENDED 状态的 job 执行 addJob(allowUpdate=true)（配置热更新）时
     * 必须保持 SUSPENDED——不得经 scheduleNext 被静默改回 WAITING 重新排程（撤销 suspendJob
     * 的效果）。resumeJob 后以新 trigger 恢复调度。
     */
    @Test
    void testUpdateWhileSuspendedStaysSuspended() throws Exception {
        MockInvoker invoker = new MockInvoker();
        LocalJobScheduler sched = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> invoker);
        sched.activate();
        try {
            JobSpec spec = newSpec("test-update-suspended", 10, true);
            sched.addJob(spec, false);
            assertTrue(sched.awaitIdle("test-update-suspended", 2, TimeUnit.SECONDS));
            int baseline = invoker.invokeCount.get();

            sched.suspendJob("test-update-suspended");
            assertEquals(JobState.SUSPENDED, sched.getJobState("test-update-suspended"));

            // hot update while suspended: spec/trigger replaced, state must stay SUSPENDED
            sched.addJob(newSpec("test-update-suspended", 10, true), true);
            assertEquals(JobState.SUSPENDED, sched.getJobState("test-update-suspended"),
                    "update must not silently resume a suspended job");
            Thread.sleep(200);
            assertEquals(baseline, invoker.invokeCount.get(),
                    "no new fires while suspended (update must not reschedule)");

            // resume restores scheduling with the replaced trigger
            assertTrue(sched.resumeJob("test-update-suspended"));
            assertTrue(sched.awaitFireCount("test-update-suspended", baseline + 1, 5, TimeUnit.SECONDS),
                    "resume after update must resume scheduling");
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testFireNow() throws Exception {
        AtomicInteger count = new AtomicInteger();

        IJobInvoker fireOnceInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                count.incrementAndGet();
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE);
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> fireOnceInvoker
        );
        sched.activate();

        try {
            JobSpec spec = newSpec("test-fireNow", 100000, true);
            sched.addJob(spec, false);

            // fixedDelay 的 PeriodicTrigger 首次触发在 now+1ms，addJob 会立刻自动触发一次。
            // 先等它落地到稳定状态，再以当前 count 作为基线，避免竞态导致的断言不稳定。
            assertTrue(sched.awaitIdle("test-fireNow", 2, TimeUnit.SECONDS));
            int baseline = count.get();

            sched.suspendJob("test-fireNow");
            assertEquals(JobState.SUSPENDED, sched.getJobState("test-fireNow"));

            assertTrue(sched.fireNow("test-fireNow"));
            assertTrue(sched.awaitIdle("test-fireNow", 2, TimeUnit.SECONDS));
            assertEquals(baseline + 1, count.get());
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testMaxExecutionCount() throws Exception {
        MockInvoker invoker = new MockInvoker();
        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> invoker
        );
        sched.activate();

        try {
            JobSpec spec = new JobSpec();
            spec.setJobName("test-max-count");
            spec.setJobInvoker("mock");
            TriggerSpec ts = new TriggerSpec();
            ts.setRepeatInterval(10);
            ts.setRepeatFixedDelay(true);
            ts.setMaxExecutionCount(3);
            spec.setTriggerSpec(ts);

            sched.addJob(spec, false);
            assertTrue(sched.awaitFireCount("test-max-count", 3, 5, TimeUnit.SECONDS));
            assertTrue(sched.awaitState("test-max-count", JobState.COMPLETED, 5, TimeUnit.SECONDS));

            assertEquals(3, invoker.invokeCount.get());
            assertEquals(JobState.COMPLETED, sched.getJobState("test-max-count"));
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testDeactivate() {
        JobSpec spec = newSpec("test-deactivate", 10, true);
        scheduler.addJob(spec, false);
        scheduler.deactivate();
        assertEquals(0, scheduler.getJobNames().size());
    }

    @Test
    void testCancelWhileRunning() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);
        AtomicInteger invokeCount = new AtomicInteger();

        IJobInvoker blockingInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                executionStarted.countDown();
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        allowComplete.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return JobFireResult.CONTINUE;
                });
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> blockingInvoker
        );
        sched.activate();
        try {
            JobSpec spec = newSpec("test-cancel-running", 10, true);
            sched.addJob(spec, false);

            assertTrue(executionStarted.await(2, TimeUnit.SECONDS));
            assertTrue(sched.cancelJob("test-cancel-running"));
            assertEquals(JobState.COMPLETED, sched.getJobState("test-cancel-running"));

            allowComplete.countDown();
            assertTrue(sched.awaitIdle("test-cancel-running", 2, TimeUnit.SECONDS));

            assertEquals(JobState.COMPLETED, sched.getJobState("test-cancel-running"));
            assertEquals(1, invokeCount.get());
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testRemoveWhileRunning() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);
        AtomicInteger invokeCount = new AtomicInteger();

        IJobInvoker blockingInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                executionStarted.countDown();
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        allowComplete.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return JobFireResult.CONTINUE;
                });
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> blockingInvoker
        );
        sched.activate();
        try {
            JobSpec spec = newSpec("test-remove-running", 10, true);
            sched.addJob(spec, false);

            assertTrue(executionStarted.await(2, TimeUnit.SECONDS));
            assertTrue(sched.removeJob("test-remove-running"));
            assertNull(sched.getJobDetail("test-remove-running"));

            allowComplete.countDown();
            assertTrue(sched.awaitIdle("test-remove-running", 2, TimeUnit.SECONDS));

            assertNull(sched.getJobDetail("test-remove-running"));
            assertEquals(1, invokeCount.get());
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testUpdateWhileRunningPreservesOldSpec() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);
        AtomicInteger invokeCount = new AtomicInteger();

        IJobInvoker blockingInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                executionStarted.countDown();
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        allowComplete.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return JobFireResult.CONTINUE;
                });
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        IJobInvoker secondInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                return null;
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        AtomicInteger invokerIndex = new AtomicInteger(0);
        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> {
                    if (invokerIndex.getAndIncrement() == 0)
                        return blockingInvoker;
                    return secondInvoker;
                }
        );
        sched.activate();
        try {
            JobSpec oldSpec = newSpec("test-update-running", 10, true);
            oldSpec.setOnceTask(false);
            sched.addJob(oldSpec, false);

            assertTrue(executionStarted.await(2, TimeUnit.SECONDS));

            JobSpec newSpec = newSpec("test-update-running", 100, true);
            newSpec.setOnceTask(true);
            sched.addJob(newSpec, true);

            allowComplete.countDown();
            assertTrue(sched.awaitFireCount("test-update-running", 2, 5, TimeUnit.SECONDS));

            assertNotNull(sched.getJobDetail("test-update-running"));
            assertTrue(invokeCount.get() >= 2);
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testCheckActiveThrowsCorrectError() {
        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> mockInvoker()
        );

        NopException ex = assertThrows(NopException.class,
                () -> sched.addJob(newSpec("test", 10, true), false));
        assertEquals("nop.err.job.scheduler-not-active", ex.getErrorCode());
    }

    @Test
    void testAddDuplicateJobThrowsAlreadyExists() {
        scheduler.addJob(newSpec("dup-job", 10, true), false);
        NopException ex = assertThrows(NopException.class,
                () -> scheduler.addJob(newSpec("dup-job", 10, true), false));
        assertEquals("nop.err.job.already-exists", ex.getErrorCode());
    }

    private JobSpec newSpec(String name, long interval, boolean fixedDelay) {
        JobSpec spec = new JobSpec();
        spec.setJobName(name);
        spec.setJobInvoker("mock");
        TriggerSpec ts = new TriggerSpec();
        ts.setRepeatInterval(interval);
        ts.setRepeatFixedDelay(fixedDelay);
        spec.setTriggerSpec(ts);
        return spec;
    }

    // ========== plan 2496 1.4-MA-05 站点③（C4-012 LocalJobScheduler whenComplete 区域）==========

    /**
     * 站点③：whenComplete 回调动作内的 Error（此处经 handleResult → scheduleNext → 触发器计算抛
     * LinkageError 注入）不得被丢弃的衍生 future 吞掉导致 job 永久滞留 RUNNING；修复后 job 以
     * FAILED 终态收口（终态 fail-safe），done future 异常完结（等待者可见）。
     */
    @Test
    void testWhenCompleteErrorTerminalizesJobNotStuckRunning() throws Exception {
        CompletableFuture<JobFireResult> invokeFuture = CompletableFuture.completedFuture(
                // CONTINUE（nextScheduleTime=-1）：handleResult 落到 scheduleNext(job,0) → 触发器计算（注入点）
                JobFireResult.CONTINUE);
        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> new IJobInvoker() {
                    @Override
                    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                        return invokeFuture;
                    }

                    @Override
                    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                        return CompletableFuture.completedFuture(Boolean.TRUE);
                    }
                }
        );
        sched.activate();
        try {
            String jobName = "test-ma05-trigger-error";
            sched.addJob(newSpec(jobName, 100, true), false);

            // 把 trigger 换成「第二次调用抛 LinkageError」的触发器：
            // 第 1 次调用发生在 addJob→scheduleNext（正常返回 next=+100ms）；
            // 第 2 次调用发生在首轮执行完成后的 whenComplete 回调→handleResult→scheduleNext（注入点）
            java.util.Map<String, ?> jobs = readJobsMap(sched);
            Object scheduledJob = jobs.get(jobName);
            assertNotNull(scheduledJob);
            java.lang.reflect.Field triggerField = scheduledJob.getClass().getDeclaredField("trigger");
            triggerField.setAccessible(true);
            triggerField.set(scheduledJob, new io.nop.job.core.ITrigger() {
                int calls = 0;

                @Override
                public long nextScheduleTime(long afterTime, io.nop.job.core.ITriggerEvalContext ctx) {
                    if (++calls >= 2)
                        throw new LinkageError("trigger-boom");
                    return afterTime + 100;
                }
            });

            // 修复前：Error 逃出回调被丢弃 future 吞掉，job 永久滞留 RUNNING（awaitState 超时 → 红）
            // 修复后：终态 fail-safe 生效，job 进入 FAILED
            assertTrue(sched.awaitState(jobName, JobState.FAILED, 5, TimeUnit.SECONDS),
                    "MA-05 station-3: Error inside whenComplete callback must fail-safe the job to FAILED, "
                            + "not leave it stuck RUNNING");
        } finally {
            sched.deactivate();
        }
    }

    private static java.util.Map<String, ?> readJobsMap(LocalJobScheduler sched) throws Exception {
        java.lang.reflect.Field f = LocalJobScheduler.class.getDeclaredField("jobs");
        f.setAccessible(true);
        return (java.util.Map<String, ?>) f.get(sched);
    }
}
