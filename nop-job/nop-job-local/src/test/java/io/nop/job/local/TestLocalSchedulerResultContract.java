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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check 审计 nop-job 两条 LocalJobScheduler 条目的回归：
 * <ul>
 *   <li>[P2] 成功路径忽略 JobFireResult.nextScheduleTime（契约：CONTINUE(nextScheduleTime)&gt;0
 *   时以指定时间为准）——本地与分布式worker链路行为不一致；</li>
 *   <li>[P2] 触发器计算异常从 whenComplete 回调逃逸被吞，job 永久卡在 RUNNING 无日志。</li>
 * </ul>
 */
public class TestLocalSchedulerResultContract {

    private LocalJobScheduler scheduler;

    static class NoopInvoker implements IJobInvoker {
        @Override
        public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
            return CompletableFuture.completedFuture(JobFireResult.CONTINUE(0));
        }

        @Override
        public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
            return CompletableFuture.completedFuture(true);
        }
    }

    @BeforeEach
    void setUp() {
        scheduler = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> null);
        scheduler.activate();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.deactivate();
        }
    }

    static class FixedResultInvoker implements IJobInvoker {
        final AtomicInteger invokeCount = new AtomicInteger();
        final JobFireResult result;

        FixedResultInvoker(JobFireResult result) {
            this.result = result;
        }

        @Override
        public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
            invokeCount.incrementAndGet();
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
            return CompletableFuture.completedFuture(true);
        }
    }

    /**
     * invoker返回CONTINUE(nextScheduleTime=now+300ms)：下次触发必须按指定时间，
     * 而非trigger的repeatInterval（10s）。修复前本地链路忽略该值。
     */
    @Test
    void testNextScheduleTimeFromResultOverridesTrigger() throws Exception {
        FixedResultInvoker[] holder = new FixedResultInvoker[1];
        CountDownLatch firstFire = new CountDownLatch(1);
        // 时间确定化（plan 2514-WI8）：触发判定走 CoreMetrics 虚拟时钟 + ManualScheduler 显式推进，
        // 第二次触发是否遵循 nextScheduleTime 在虚拟时间线上确定性发生（若按 trigger 10s，
        // 2s 推进窗口内不会有第二次触发）。
        ManualClock clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        try {
            // resolver按名字返回同一个invoker实例
            ManualScheduler timer = new ManualScheduler(clock);
            LocalJobScheduler sched = new LocalJobScheduler(new ManualScheduledExecutor(timer), name -> {
                if (holder[0] == null) {
                    holder[0] = new FixedResultInvoker(null) {
                        @Override
                        public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                            invokeCount.incrementAndGet();
                            long override = clock.peekMillis() + 300;
                            firstFire.countDown();
                            return CompletableFuture.completedFuture(JobFireResult.CONTINUE(override));
                        }
                    };
                }
                return holder[0];
            });
            sched.activate();
            try {
                JobSpec spec = new JobSpec();
                spec.setJobName("override-next");
                spec.setJobInvoker("mock");
                TriggerSpec ts = new TriggerSpec();
                ts.setRepeatInterval(10_000); // trigger节奏10s
                ts.setRepeatFixedDelay(true);
                spec.setTriggerSpec(ts);
                sched.addJob(spec, false);

                // 首次触发经显式推进落地
                timer.advance(1);
                assertTrue(firstFire.await(5, TimeUnit.SECONDS), "first fire should happen immediately");
                assertEquals(1, holder[0].invokeCount.get(), "first fire should happen immediately");
                // 首次触发后，若尊重nextScheduleTime（now+300ms 虚拟），2s 推进窗口内必发生第二次；
                // 若忽略（按trigger 10s），2s 内不会有第二次
                timer.advance(2000);
                assertTrue(holder[0].invokeCount.get() >= 2,
                        "second fire must follow result.nextScheduleTime (~300ms), not trigger interval (10s). fires="
                                + holder[0].invokeCount.get());
            } finally {
                sched.deactivate();
            }
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }

    /**
     * 病态cron（2月30日不存在，nextScheduleTime计算触发runaway守卫抛异常）：
     * 修复前异常从addJob/scheduleNext逃逸且job卡死无状态；修复后不抛出、job置FAILED。
     */
    @Test
    void testPathologicalTriggerFailsJobInsteadOfThrowing() {
        LocalJobScheduler sched = new LocalJobScheduler(GlobalExecutors.globalTimer(), name -> new NoopInvoker());
        sched.activate();
        try {
            JobSpec spec = new JobSpec();
            spec.setJobName("bad-cron");
            spec.setJobInvoker("mock");
            TriggerSpec ts = new TriggerSpec();
            ts.setCronExpr("0 0 0 30 2 ?"); // 2月30日永不匹配
            spec.setTriggerSpec(ts);

            assertDoesNotThrow(() -> sched.addJob(spec, false),
                    "trigger calculation error must not escape addJob");

            JobState state = sched.getJobState("bad-cron");
            assertNotNull(state);
            assertEquals(JobState.FAILED, state,
                    "job with pathological trigger must be FAILED, not silently stuck. state=" + state);
        } finally {
            sched.deactivate();
        }
    }
}
