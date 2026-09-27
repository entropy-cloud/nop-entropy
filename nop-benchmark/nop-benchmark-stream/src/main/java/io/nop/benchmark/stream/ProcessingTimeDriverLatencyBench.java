/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.api.core.time.CoreMetrics;
import io.nop.stream.core.execution.Mail;
import io.nop.stream.core.execution.ProcessingTimeServiceDriver;
import io.nop.stream.core.execution.TaskMailbox;
import io.nop.stream.core.execution.TaskProcessingTimeService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ProcessingTimeServiceDriver 处理时间定时器触发延迟基准（Plan 2279 Phase 1）。
 * 现实现为固定 tick 节奏（{@code Thread.sleep(tickMs)} + due 检查），处理时间
 * 定时器的触发延迟介于 0 与 tickMs 之间（均值 ≈ tickMs/2）；候选优化为
 * sleep-to-deadline（{@code nextTimerTimestamp} volatile 已存在）。
 *
 * <p>测量口径（计划钉死）：op 时间 = 注册 now+{@code ADVANCE_MS} 定时器后阻塞至
 * callback 执行的耗时（SampleTime 分布）；<b>fire 延迟 = op − ADVANCE_MS</b>
 * （报告与 evidence 中换算）。tickMs 越小、或 sleep-to-deadline 落地后，
 * fire 延迟分布越贴近 0。
 *
 * <p>装配：真实 TaskMailbox + TaskProcessingTimeService + 真实 driver 线程
 * （{@code start()}）+ 独立 task 线程排空 mailbox（生产中 fire mail 在 task
 * 线程执行——此处由专职线程扮演，JMH 线程仅注册与等待）。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class ProcessingTimeDriverLatencyBench {

    /** 注册提前量：fire 延迟 = op − 该值。 */
    static final long ADVANCE_MS = 50L;

    @Param({"100", "20"})
    long tickMs;

    TaskMailbox mailbox;
    TaskProcessingTimeService service;
    ProcessingTimeServiceDriver driver;
    Thread taskThread;
    volatile boolean running = true;

    @Setup(Level.Trial)
    public void setup() {
        mailbox = new TaskMailbox();
        service = new TaskProcessingTimeService();
        driver = new ProcessingTimeServiceDriver(mailbox, service, null, tickMs);
        taskThread = new Thread(() -> {
            while (running) {
                try {
                    Mail mail = mailbox.take();
                    mail.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "bench-task-thread");
        taskThread.setDaemon(true);
        taskThread.start();
        driver.start();
        if (!driver.isRunning()) {
            throw new IllegalStateException("driver failed to start");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        running = false;
        driver.shutdown();
        taskThread.interrupt();
    }

    /**
     * 注册 now+ADVANCE_MS 的处理时间定时器并阻塞至 fire；op 时间分布即
     * 注册→fire 全程，减去 ADVANCE_MS 为调度延迟（fire 延迟）。
     */
    @Benchmark
    public void registerAndAwaitFire(Blackhole bh) throws InterruptedException {
        long due = CoreMetrics.currentTimeMillis() + ADVANCE_MS;
        CountDownLatch fired = new CountDownLatch(1);
        service.registerTimer(due, timestamp -> fired.countDown());
        long start = System.nanoTime();
        if (!fired.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timer did not fire within 10s (tickMs=" + tickMs + ")");
        }
        bh.consume(System.nanoTime() - start);
    }
}
