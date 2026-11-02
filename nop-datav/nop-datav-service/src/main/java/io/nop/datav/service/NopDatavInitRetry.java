package io.nop.datav.service;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 裁定⑬（plan 2526，CL-020）：init 注册失败启动延迟重试（模块内最小自愈面）。
 *
 * <p>{@code @PostConstruct} 注册失败仅记日志时，周期扫描/恢复 bean 在整个进程生命周期内
 * 静默停摆。本工具在注册失败的 catch 分支调度<b>一次性</b>延迟重试（30s 固定延迟、上限一次）：
 * 重试成功 INFO、仍失败结构化 WARN 放弃——禁无限重试、禁抛崩启动（多租户宿主不抛崩的既有
 * 选择保持）。平台级统一绝缘层（R1-01，2.1-C3-001）零触碰；scanRegistered 健康位形态被拒
 * （只可观测不自愈，归 R1-01 统一观测方案）——五站点接入点清单随批登记供 R1-01 消费。</p>
 *
 * <p><b>测试缝</b>：{@link #setSchedulerOverrideForTest(Consumer)} 非 null 时替代
 * {@code GlobalExecutors.globalTimer()}——重试动作被捕获供同步触发（不武装真实定时器，
 * 红⑬不依赖真实 30s 等待、无跨测试定时器残留）。沿 executionStartHook
 * package-private seam 惯例（测试类须位于 {@code io.nop.datav.service} 包）。</p>
 */
public final class NopDatavInitRetry {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavInitRetry.class);

    /** 延迟重试间隔（固定 30s——启动期瞬态故障窗的单次闭合，非常驻轮询） */
    public static final long DELAY_MS = 30_000;

    /** 测试缝：非 null 时替代 globalTimer 捕获重试动作（生产恒 null） */
    private static volatile Consumer<Runnable> schedulerOverride;

    private NopDatavInitRetry() {
    }

    /**
     * 调度一次性延迟重试。重试执行体带「成功 INFO / 仍失败 WARN 放弃」守卫——
     * 重试失败不再排下一次（上限一次，禁无限重试）。
     *
     * @param site   结构化日志站点标识（五站点清单逐名可 grep）
     * @param action 重试执行体（通常为注册动作本体；可抛异常，由守卫捕获）
     */
    public static void scheduleOnce(String site, Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
                LOG.info("nop.datav.init-retry-succeeded: site={}", site);
            } catch (Exception e) {
                LOG.warn("nop.datav.init-retry-failed-giving-up: site={}", site, e);
            }
        };
        Consumer<Runnable> override = schedulerOverride;
        if (override != null) {
            override.accept(guarded);
            return;
        }
        try {
            GlobalExecutors.globalTimer().schedule(() -> {
                guarded.run();
                return null;
            }, DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // 定时器本身不可用（极端启动期故障）：显式 WARN 可见，不抛崩启动
            LOG.warn("nop.datav.init-retry-schedule-failed: site={}", site, e);
        }
    }

    // ==================== 测试缝（package-private） ====================

    static void setSchedulerOverrideForTest(Consumer<Runnable> override) {
        schedulerOverride = override;
    }

    static void clearSchedulerOverrideForTest() {
        schedulerOverride = null;
    }
}
