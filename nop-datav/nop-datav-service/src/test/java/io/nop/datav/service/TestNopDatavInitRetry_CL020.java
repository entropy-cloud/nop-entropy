package io.nop.datav.service;

import io.nop.datav.service.entity.AbstractNopDatavTest;
import io.nop.api.core.exceptions.NopException;
import io.nop.datav.service.recovery.NopDatavStuckTaskScanner;
import io.nop.job.api.IJobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CL-020 周期任务注册失败启动延迟重试红⑬钉（plan 2526 裁定⑬，C10 缺保护）。
 *
 * <p>缺陷机制：{@code @PostConstruct} 注册失败仅记日志——无重试、无补注册，周期扫描/
 * 恢复 bean 整个进程生命周期静默停摆。裁定⑬ 最小面 = 失败后经 NopDatavInitRetry 调度
 * <b>一次性</b> 30s 延迟重试（上限一次，仍失败 WARN 放弃）。</p>
 *
 * <p><b>测试缝</b>：{@code NopDatavInitRetry.setSchedulerOverrideForTest} 捕获重试动作
 * （不武装真实定时器——红⑬不依赖 30s 等待、无跨测试定时器残留），同步触发断言：
 * 注册重试成功（addJob 第二次尝试）、且重试不再二次排程（上限一次，禁无限重试）。</p>
 *
 * <p>五站点接线（rule 23 源码结构钉，沿 2525 MA01 形态）：StuckTaskScanner / ReportScheduler /
 * AlertScheduler / ExportTaskRecovery / ReportDeliveryRecovery 的 init 失败分支均须消费
 * {@code NopDatavInitRetry.scheduleOnce}——变异（摘除任一站点接线）下本组断言红。</p>
 */
public class TestNopDatavInitRetry_CL020 extends AbstractNopDatavTest {

    private final AtomicReference<Runnable> capturedRetry = new AtomicReference<>();
    private final AtomicInteger scheduleCount = new AtomicInteger();

    @BeforeEach
    public void armSeam() {
        NopDatavInitRetry.setSchedulerOverrideForTest(action -> {
            capturedRetry.set(action);
            scheduleCount.incrementAndGet();
        });
    }

    @AfterEach
    public void disarmSeam() {
        NopDatavInitRetry.clearSchedulerOverrideForTest();
        capturedRetry.set(null);
        scheduleCount.set(0);
    }

    /**
     * 红⑬ 主钉：init 首次注册注入失败 → 恰排程一次延迟重试（接线）→ 同步触发重试 →
     * 第二次注册成功（自愈）且不二次排程（上限一次）。
     */
    @Test
    public void testInitFailureSchedulesOneShotRetryThatHealsRegistration() {
        AtomicInteger addJobCalls = new AtomicInteger();
        IJobScheduler failingThenHealing = proxyScheduler(addJobCalls, true);

        NopDatavStuckTaskScanner scanner = new NopDatavStuckTaskScanner();
        scanner.setScheduler(failingThenHealing);
        scanner.init();

        assertEquals(1, addJobCalls.get(), "initial registration attempt (injected failure)");
        assertEquals(1, scheduleCount.get(),
                "CL-020: init failure must schedule exactly ONE delayed retry (wiring)");

        Runnable retry = capturedRetry.get();
        assertNotNull(retry, "retry action captured by test seam (no real 30s timer armed)");
        retry.run();

        assertEquals(2, addJobCalls.get(), "retry re-attempts registration (healed)");
        assertEquals(1, scheduleCount.get(),
                "retry must NOT schedule another attempt (one-shot upper bound, 禁无限重试)");
    }

    /**
     * 重试仍失败 → 守卫捕获显式放弃（不抛出、不二次排程——禁无限重试/禁抛崩启动）。
     */
    @Test
    public void testRetryFailureGivesUpWithoutRescheduling() {
        AtomicInteger addJobCalls = new AtomicInteger();
        IJobScheduler alwaysFailing = proxyScheduler(addJobCalls, false);

        NopDatavStuckTaskScanner scanner = new NopDatavStuckTaskScanner();
        scanner.setScheduler(alwaysFailing);
        scanner.init();

        assertEquals(1, scheduleCount.get(), "initial failure schedules one retry");
        Runnable retry = capturedRetry.get();
        assertNotNull(retry, "retry captured");
        retry.run(); // 守卫吞掉失败 → 不抛（give-up WARN 路径）

        assertEquals(2, addJobCalls.get(), "retry attempted once more");
        assertEquals(1, scheduleCount.get(), "give-up: no further scheduling (上限一次)");
    }

    /**
     * 接线钉（rule 23，源码结构）：裁定⑬ 五站点 init 失败分支全部消费
     * {@code NopDatavInitRetry.scheduleOnce}（R1-01 接入点清单面）。
     */
    @Test
    public void testAllFiveInitSitesConsumeScheduleOnce() throws Exception {
        String[][] sites = {
                {"src/main/java/io/nop/datav/service/recovery/NopDatavStuckTaskScanner.java", "stuck-scanner"},
                {"src/main/java/io/nop/datav/service/report/NopDatavReportScheduler.java", "report-scheduler"},
                {"src/main/java/io/nop/datav/service/alert/NopDatavAlertScheduler.java", "alert-scheduler"},
                {"src/main/java/io/nop/datav/service/export/NopDatavExportTaskRecovery.java", "export-recovery"},
                {"src/main/java/io/nop/datav/service/report/NopDatavReportDeliveryRecovery.java", "report-delivery-recovery"},
        };
        for (String[] site : sites) {
            String code = Files.readString(Path.of(site[0]));
            assertTrue(code.contains("NopDatavInitRetry.scheduleOnce("),
                    "CL-020 site must consume scheduleOnce: " + site[0]);
            assertTrue(code.contains("\"" + site[1]),
                    "CL-020 site must carry its structured site id (" + site[1] + "): " + site[0]);
        }
    }

    // ==================== Helpers ====================

    /**
     * 注入失败 proxy：firstFailure=true 时仅第一次 addJob 抛错（其后自愈）；
     * false 时恒抛。其余方法按返回类型给默认值（activate 等 void/boolean 面）。
     */
    private static IJobScheduler proxyScheduler(AtomicInteger addJobCalls, boolean firstFailure) {
        return (IJobScheduler) Proxy.newProxyInstance(IJobScheduler.class.getClassLoader(),
                new Class<?>[]{IJobScheduler.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if ("addJob".equals(name)) {
                        int n = addJobCalls.incrementAndGet();
                        if (firstFailure ? n == 1 : true) {
                            throw NopException.adapt(new IllegalStateException("injected init registration failure (CL-020 red-13)"));
                        }
                    } else if ("toString".equals(name)) {
                        return "CL020-proxy-scheduler";
                    } else if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    } else if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == void.class) {
                        return null;
                    }
                    if (rt == boolean.class) {
                        return true;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }
}
