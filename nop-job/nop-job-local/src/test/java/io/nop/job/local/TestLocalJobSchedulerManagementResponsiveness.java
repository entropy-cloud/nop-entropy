package io.nop.job.local;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.job.api.JobState;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2513 1.4-C2-001 / C10-003 收口门：监视器内外部 invoker 外移（锁范围收窄）回归。
 * <p>
 * 裁定②（ai-dev/design/concurrency/job-rpcpoll-termination-and-invoker-lock.md §3.7）：
 * {@code executeJob} 拆两阶段 {@code prepareJobLocked}（锁内）+ {@code startJobUnlocked}（锁外，
 * 出锁 identity/状态复检 → invokeAsync → whenComplete 重取锁终态迁移）——两个调用点（timer 回调 /
 * fireNow）的 synchronized 块在两阶段之间闭合。latch 阻塞型同步 invoker（BeanMethodJobInvoker
 * 同步形态模拟）下管理 API（getJobState）与停机（deactivate）必须 ≤500ms 响应
 * （缝5红②：缺陷态恒超时红）；出锁放弃分支显式终态 + ctx 真快照反射防回归缝。
 */
public class TestLocalJobSchedulerManagementResponsiveness {

    private LocalJobScheduler scheduler;

    @AfterEach
    void tearDown() {
        // 兜底清理（重复 deactivate 安全：active=false + 空表遍历）
        if (scheduler != null) {
            scheduler.deactivate();
        }
    }

    // ========== 缝5（红②）：阻塞型同步 invoker × 管理 API 响应性 ==========

    /**
     * 红②→绿：latch 阻塞型同步 invoker（方法体在调用线程跑完的 BeanMethodJobInvoker 形态模拟）
     * 执行期间，getJobState 必须 ≤500ms 返回——缺陷态 invokeAsync 全程持 per-job 监视器恒超时红。
     * 修复后 job 体最终自然完成不崩（fireCount 落地、状态离开 RUNNING）。
     */
    @Test
    void testC2_001_getJobStateResponsiveWhileBlockingInvokerRuns() throws Exception {
        CountDownLatch bodyEntered = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        AtomicInteger invokeCount = new AtomicInteger();
        scheduler = newScheduler(name -> syncBlockingInvoker(bodyEntered, releaseBody, invokeCount));

        JobSpec spec = newSpec("c20001-resp", 10);
        scheduler.addJob(spec, false);

        assertTrue(bodyEntered.await(5, TimeUnit.SECONDS), "blocking job body must have started");

        // 红②断言：缺陷态监视器被 job 体持有，getJobState 无限期阻塞（>500ms 红）
        long begin = System.nanoTime();
        JobState state = (JobState) assertResponsive(() -> scheduler.getJobState("c20001-resp"));
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;
        assertEquals(JobState.RUNNING, state, "job must report RUNNING while its body executes");
        assertTrue(elapsedMs <= 500, "getJobState must respond within 500ms, took " + elapsedMs + "ms");

        releaseBody.countDown();
        assertTrue(scheduler.awaitIdle("c20001-resp", 5, TimeUnit.SECONDS),
                "job body must complete naturally after release (no stuck RUNNING)");
        assertEquals(1, invokeCount.get(), "exactly one fire while blocked (no double-fire)");
    }

    /**
     * 停机响应性（@PreDestroy → deactivate 入口）：job 体阻塞执行期间 deactivate 必须 ≤500ms
     * 完成，停机不再被最长 job 体时长卡死；释放后 body 落地不崩（whenComplete 重取锁走
     * handleResult 的 !containsKey 守卫）。
     */
    @Test
    void testC2_001_deactivateResponsiveWhileBlockingInvokerRuns() throws Exception {
        CountDownLatch bodyEntered = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        AtomicInteger invokeCount = new AtomicInteger();
        scheduler = newScheduler(name -> syncBlockingInvoker(bodyEntered, releaseBody, invokeCount));

        JobSpec spec = newSpec("c20001-stop", 10);
        scheduler.addJob(spec, false);
        assertTrue(bodyEntered.await(5, TimeUnit.SECONDS));

        long begin = System.nanoTime();
        assertResponsive(() -> {
            scheduler.deactivate();
            return null;
        });
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;
        assertTrue(elapsedMs <= 500, "deactivate must not block on the running job body, took "
                + elapsedMs + "ms");
        assertEquals(0, scheduler.getJobNames().size(), "deactivate must clear the registry");

        releaseBody.countDown();
        // body 落地（回调重取锁，registry 已清 → COMPLETED 终态守卫）；等待其完成避免线程泄漏
        long deadline = System.currentTimeMillis() + 5000;
        while (invokeCount.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, invokeCount.get());
    }

    /**
     * awaitIdle 执行期上界变体：job 体阻塞期间监视器获取段瞬时化——释放后 awaitIdle 在
     * timeout 上界内返回 true（缺陷态获取等待被持锁 job 体无限拉长）。
     */
    @Test
    void testC2_001_awaitIdleBoundedDuringExecution() throws Exception {
        CountDownLatch bodyEntered = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        AtomicInteger invokeCount = new AtomicInteger();
        scheduler = newScheduler(name -> syncBlockingInvoker(bodyEntered, releaseBody, invokeCount));

        JobSpec spec = newSpec("c20001-idle", 10);
        scheduler.addJob(spec, false);
        assertTrue(bodyEntered.await(5, TimeUnit.SECONDS));

        // 300ms 后释放 body；awaitIdle(5s) 必须在释放后迅速返回 true
        Thread releaser = new Thread(releaseBody::countDown);
        releaser.start();
        long begin = System.nanoTime();
        assertTrue(scheduler.awaitIdle("c20001-idle", 5, TimeUnit.SECONDS),
                "awaitIdle must return once the body completes");
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;
        assertTrue(elapsedMs <= 4500, "awaitIdle wait must be bounded by body duration + margin, took "
                + elapsedMs + "ms");
        releaser.join(5000);
    }

    // ========== 出锁段放弃分支（rule 24 显式分支） ==========

    /**
     * 出锁段 identity/状态复检放弃分支：prepare 之后、startJobUnlocked 之前 job 被交错方
     * cancelJob 终态化 → 放弃分支重取锁清 running + 完结占位 done，禁走 handleResult
     * （fireCount 不变证明未走 handleResult，交错方终态不被改写回 WAITING）。
     */
    @Test
    void testC2_001_abandonAfterInterleave_explicitTerminalNotHandleResult() throws Exception {
        AtomicInteger invokeCount = new AtomicInteger();
        scheduler = newScheduler(name -> new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE);
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });

        JobSpec spec = newSpec("c20001-abandon", 100000);
        scheduler.addJob(spec, false);
        assertTrue(scheduler.awaitIdle("c20001-abandon", 5, TimeUnit.SECONDS));

        Map<String, ?> jobs = readJobsMap(scheduler);
        Object job = jobs.get("c20001-abandon");
        assertNotNull(job);

        // 锁内阶段1（模拟 timer/fireNow 块内尾调）：suspend 保证存在非 RUNNING 交错态可判
        scheduler.suspendJob("c20001-abandon");
        Object prepared = invokePrepareJobLocked(scheduler, job);
        assertNotNull(prepared, "prepareJobLocked must produce the phase-1 result");

        // 交错方 cancelJob：状态 COMPLETED + 移除调度
        assertTrue(scheduler.cancelJob("c20001-abandon"));
        assertEquals(JobState.COMPLETED, scheduler.getJobState("c20001-abandon"));

        // 出锁阶段2：复检失败 → 放弃分支（非 invoke、非 handleResult）
        CompletableFuture<?> done = (CompletableFuture<?>) prepared.getClass()
                .getDeclaredField("done").get(prepared);
        invokeStartJobUnlocked(scheduler, job, prepared, done);

        assertEquals(0, invokeCount.get(), "abandoned execution must not invoke the invoker");
        assertTrue(done.isDone(), "placeholder done must complete (awaitIdle must not hang)");
        assertEquals(JobState.COMPLETED, scheduler.getJobState("c20001-abandon"),
                "interleaver's terminal state must not be rewritten (handleResult forbidden)");
        assertTrue(scheduler.awaitIdle("c20001-abandon", 2, TimeUnit.SECONDS),
                "running placeholder must be cleared in the abandon branch");
    }

    // ========== ctx 真快照反射防回归缝（裁定③ 缝5 增补） ==========

    /**
     * SimpleExecutionContext 不得持有 ScheduledJob/SimpleJobState/JobSpec 活引用——
     * 构造时必须复制 ctx 消费字段为真快照（§3.7 快照字段清单；新增字段须同步清单）。
     */
    @Test
    void testC2_001_ctxSnapshot_noLiveReferences() throws Exception {
        Class<?> ctxClass = Class.forName("io.nop.job.local.LocalJobScheduler$SimpleExecutionContext");
        for (Field f : ctxClass.getDeclaredFields()) {
            Class<?> t = f.getType();
            boolean forbidden = t == LocalJobScheduler.ScheduledJob.class
                    || t == LocalJobScheduler.SimpleJobState.class
                    || t == JobSpec.class;
            assertTrue(!forbidden,
                    "SimpleExecutionContext must not hold live references, field: " + f.getName()
                            + " : " + t.getName());
        }
    }

    // ========== helpers ==========

    private LocalJobScheduler newScheduler(java.util.function.Function<String, IJobInvoker> resolver) {
        LocalJobScheduler sched = new LocalJobScheduler(GlobalExecutors.globalTimer(), resolver::apply);
        sched.activate();
        return sched;
    }

    /** 阻塞型同步 invoker：方法体在调用线程（即调度线程）跑完再包 completedFuture——BeanMethodJobInvoker 同步形态。 */
    private static IJobInvoker syncBlockingInvoker(CountDownLatch entered, CountDownLatch release,
                                                   AtomicInteger invokeCount) {
        return new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                entered.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE);
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        };
    }

    private interface ResponsiveCall {
        Object call();
    }

    /** 缺陷态下该调用无限期阻塞——preemptively 500ms 上界把红显式化为断言失败。 */
    private static Object assertResponsive(ResponsiveCall call) {
        return org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofMillis(500),
                call::call, "management API must not block on the running job body (monitor held)");
    }

    private JobSpec newSpec(String name, long interval) {
        JobSpec spec = new JobSpec();
        spec.setJobName(name);
        spec.setJobInvoker("mock");
        TriggerSpec ts = new TriggerSpec();
        ts.setRepeatInterval(interval);
        ts.setRepeatFixedDelay(true);
        spec.setTriggerSpec(ts);
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> readJobsMap(LocalJobScheduler sched) throws Exception {
        Field f = LocalJobScheduler.class.getDeclaredField("jobs");
        f.setAccessible(true);
        return (Map<String, ?>) f.get(sched);
    }

    private static Object invokePrepareJobLocked(LocalJobScheduler sched, Object job) throws Exception {
        Method m = LocalJobScheduler.class.getDeclaredMethod("prepareJobLocked",
                Class.forName("io.nop.job.local.LocalJobScheduler$ScheduledJob"));
        m.setAccessible(true);
        return m.invoke(sched, job);
    }

    private static void invokeStartJobUnlocked(LocalJobScheduler sched, Object job, Object prepared,
                                               CompletableFuture<?> done) throws Exception {
        Method m = LocalJobScheduler.class.getDeclaredMethod("startJobUnlocked",
                Class.forName("io.nop.job.local.LocalJobScheduler$ScheduledJob"),
                io.nop.job.api.execution.IJobExecutionContext.class, CompletableFuture.class);
        m.setAccessible(true);
        Object ctx = prepared.getClass().getDeclaredField("ctx").get(prepared);
        m.invoke(sched, job, ctx, done);
    }
}
