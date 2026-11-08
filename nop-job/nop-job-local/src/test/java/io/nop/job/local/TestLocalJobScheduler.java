package io.nop.job.local;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.test.time.ManualClock;
import io.nop.commons.test.time.ManualScheduler;
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
    /**
     * check2 [P0] timer 路径契约：once 型 job（无 cron、repeatInterval<=0）只触发一次。
     * 覆盖两种形态：指定 minScheduleTime（到点触发一次）与未指定（立即触发一次）。
     * 触发后 job 必须 COMPLETED（onceTask 时从注册表移除），且等待期内不再产生第二次执行。
     *
     * <p>时间确定化（plan 2514-WI8）：注入 {@link ManualScheduler} + 注册 {@link ManualClock}，
     * 触发经显式推进虚拟时间发生；「不再触发」以结构性证据断言（定时器条目已清空 + 注册表已移除），
     * 再推进多个调度周期复核执行计数不变，替代原 sleep(300) 墙钟观察窗。</p>
     */
    @Test
    void testOnceJobFiresExactlyOnce() throws Exception {
        ManualClock clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        try {
            MockInvoker scheduled = new MockInvoker();
            ManualScheduler timer1 = new ManualScheduler(clock);
            LocalJobScheduler sched1 = new LocalJobScheduler(new ManualScheduledExecutor(timer1), name -> scheduled);
            sched1.activate();
            try {
                JobSpec spec = new JobSpec();
                spec.setJobName("test-once-scheduled");
                spec.setJobInvoker("mock");
                spec.setOnceTask(true);
                TriggerSpec ts = new TriggerSpec();
                ts.setMinScheduleTime(clock.peekMillis() + 100);
                spec.setTriggerSpec(ts);
                sched1.addJob(spec, false);

                // 推进虚拟时间越过 minScheduleTime：到点触发一次
                timer1.advance(200);
                assertEquals(1, scheduled.invokeCount.get(),
                        "scheduled once job must fire exactly once at its minScheduleTime");
                // onceTask 完成后即从注册表移除（fireCount 随之不可观测），以注册表移除 + 定时器清空作结构性证据
                assertFalse(sched1.getJobNames().contains("test-once-scheduled"),
                        "onceTask job must be removed from the registry after its single fire");
                assertEquals(0, timer1.pendingTaskCount(),
                        "no timer entry may remain after the single fire");
                // 再推进 3 个调度周期以上（虚拟时间），确认不再触发
                timer1.advance(300);
                assertEquals(1, scheduled.invokeCount.get(),
                        "scheduled once job must fire exactly once");
            } finally {
                sched1.deactivate();
            }

            MockInvoker immediate = new MockInvoker();
            ManualScheduler timer2 = new ManualScheduler(clock);
            LocalJobScheduler sched2 = new LocalJobScheduler(new ManualScheduledExecutor(timer2), name -> immediate);
            sched2.activate();
            try {
                JobSpec spec = new JobSpec();
                spec.setJobName("test-once-immediate");
                spec.setJobInvoker("mock");
                TriggerSpec ts = new TriggerSpec();
                // no cron, no repeatInterval, no minScheduleTime → fire immediately, once
                spec.setTriggerSpec(ts);
                sched2.addJob(spec, false);

                // 立即型触发经一次推进落地
                timer2.advance(1);
                assertEquals(1, immediate.invokeCount.get(),
                        "immediate once job must fire exactly once");
                assertEquals(JobState.COMPLETED, sched2.getJobState("test-once-immediate"),
                        "non-onceTask once job must end in COMPLETED (not WAITING for another fire)");
                assertEquals(0, timer2.pendingTaskCount(),
                        "no timer entry may remain after the single fire");
                // 再推进 3 个调度周期以上（虚拟时间），确认不再触发
                timer2.advance(300);
                assertEquals(1, immediate.invokeCount.get(),
                        "immediate once job must fire exactly once");
            } finally {
                sched2.deactivate();
            }
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }

    /**
     * check2 [P3-13]: SUSPENDED 状态的 job 执行 addJob(allowUpdate=true)（配置热更新）时
     * 必须保持 SUSPENDED——不得经 scheduleNext 被静默改回 WAITING 重新排程（撤销 suspendJob
     * 的效果）。resumeJob 后以新 trigger 恢复调度。
     */
    @Test
    void testUpdateWhileSuspendedStaysSuspended() throws Exception {
        ManualClock clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        try {
            MockInvoker invoker = new MockInvoker();
            ManualScheduler timer = new ManualScheduler(clock);
            LocalJobScheduler sched = new LocalJobScheduler(new ManualScheduledExecutor(timer), name -> invoker);
            sched.activate();
            try {
                JobSpec spec = newSpec("test-update-suspended", 10, true);
                sched.addJob(spec, false);
                // 推进虚拟时间让自动首触发落地并取基线（挂起后 fireCount 不再增长的对照）
                timer.advance(50);
                int baseline = invoker.invokeCount.get();
                assertTrue(baseline >= 1, "initial auto-fire must have landed before sampling the baseline");

                sched.suspendJob("test-update-suspended");
                assertEquals(JobState.SUSPENDED, sched.getJobState("test-update-suspended"));

                // hot update while suspended: spec/trigger replaced, state must stay SUSPENDED
                sched.addJob(newSpec("test-update-suspended", 10, true), true);
                assertEquals(JobState.SUSPENDED, sched.getJobState("test-update-suspended"),
                        "update must not silently resume a suspended job");
                // 结构性证据：挂起 job 无任何待触发定时器条目；再推进多个调度周期复核计数不变
                assertEquals(0, timer.pendingTaskCount(),
                        "suspended job must have no pending scheduled fire");
                timer.advance(200);
                assertEquals(baseline, invoker.invokeCount.get(),
                        "no new fires while suspended (update must not reschedule)");

                // resume restores scheduling with the replaced trigger
                assertTrue(sched.resumeJob("test-update-suspended"));
                timer.advance(1000);
                assertTrue(invoker.invokeCount.get() >= baseline + 1,
                        "resume after update must resume scheduling");
            } finally {
                sched.deactivate();
            }
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
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
            // awaitIdle 只看 running==null：首次触发刚被调度、还没进入执行时它就直接返回，
            // 基线会在自动触发落地前被采样，满载时那次迟到的触发让断言多出一次调用。
            // 先按触发次数等自动触发真正结束，再取基线。
            assertTrue(sched.awaitFireCount("test-fireNow", 1, 2, TimeUnit.SECONDS));
            assertTrue(sched.awaitIdle("test-fireNow", 2, TimeUnit.SECONDS));
            int baseline = count.get();
            assertEquals(1, baseline, "addJob 的自动触发是 fireNow 之前唯一的一次调用");

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

    @Test
    void testRemovedRunningJobDoesNotResurrectWhenNameReadded() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);
        AtomicInteger oldInvokeCount = new AtomicInteger();
        AtomicInteger newInvokeCount = new AtomicInteger();

        IJobInvoker oldInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                oldInvokeCount.incrementAndGet();
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

        IJobInvoker newInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                newInvokeCount.incrementAndGet();
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE);
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        ManualClock clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        try {
            ManualScheduler timer = new ManualScheduler(clock);
            LocalJobScheduler sched = new LocalJobScheduler(
                    new ManualScheduledExecutor(timer),
                    name -> "old".equals(name) ? oldInvoker : newInvoker
            );
            sched.activate();
            try {
                JobSpec oldSpec = newSpec("test-resurrect", 10, true);
                oldSpec.setJobInvoker("old");
                sched.addJob(oldSpec, false);
                // 推进虚拟时间触发旧实例首火（进入阻塞执行的异步段）
                timer.advance(1);
                assertTrue(executionStarted.await(2, TimeUnit.SECONDS));

                assertTrue(sched.removeJob("test-resurrect"));

                JobSpec reAddedSpec = newSpec("test-resurrect", 100000, true);
                reAddedSpec.setJobInvoker("new");
                sched.addJob(reAddedSpec, false);
                // 新实例自动首触发经显式推进落地（100000ms 周期，推进窗口内不再重排）
                timer.advance(50);
                assertEquals(1, newInvokeCount.get());

                allowComplete.countDown();

                // 旧实例完成后若因同名新实例存在而自我重排程，会以 oldInvoker 反复并行执行：
                // 在虚拟时间线上推进远超旧实例 10ms 周期的窗口——若复活，oldInvokeCount 必然增长。
                timer.advance(60_000);
                assertEquals(1, oldInvokeCount.get(),
                        "removed instance must not be rescheduled after completion");
                assertEquals(1, newInvokeCount.get());
            } finally {
                sched.deactivate();
            }
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
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
}
