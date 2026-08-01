package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.NopAiAgentErrors;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * 功能性 {@link IProviderFailoverQueue}：per-provider 冷却状态机（plan 2026-08-01-1905-3，
 * 设计 §13.4 裁定 B/D）。
 *
 * <p>每个 provider 维护独立 {@link ProviderHealth}（{@code lastFailureAt} +
 * {@code consecutiveFailures}）。{@link #recordProviderFailure} 记录失败时刻；
 * {@link #isProviderAvailable} 在冷却期内（{@code now - lastFailureAt < cooldownMs}）返回 false。
 * 这使 {@link ProviderFailoverChain} 游走器跳过冷却中的 provider，防 P1↔P2 跨调用反复弹跳（防震荡）。
 *
 * <p><b>可测试时间源（裁定 D，不复制 ThresholdBreaker 反模式）</b>：时间经可注入
 * {@link LongSupplier nowMs}（构造器参数，默认 {@code System::currentTimeMillis}）。
 * 测试注入可控时间源（如 {@code AtomicLong}）精确推进时间，零窗口确定性测试去重边界。
 * 对比 {@code ThresholdBreaker.java:122} 直接调 {@code System.currentTimeMillis()}——已知反模式，
 * 本类不复制。
 *
 * <p><b>线程安全</b>：状态 tracked per provider 在 {@link ConcurrentHashMap}。每个 entry 的
 * 读写经 {@code synchronized(entry)} 保证一致性（同 {@code ThresholdBreaker} 模式）。
 *
 * <p>状态 in-memory only（per queue instance）。持久化/跨进程共享是 Non-Goal successor。
 */
public final class ProviderFailoverQueue implements IProviderFailoverQueue {

    /** 默认冷却时间（ms），同 {@link ThresholdBreaker#DEFAULT_COOLDOWN_MS}。 */
    public static final long DEFAULT_COOLDOWN_MS = ThresholdBreaker.DEFAULT_COOLDOWN_MS;

    private final long cooldownMs;
    private final LongSupplier nowMs;
    private final ConcurrentMap<String, ProviderHealth> health = new ConcurrentHashMap<>();

    /**
     * 默认冷却（60s）+ 系统时间源。
     */
    public ProviderFailoverQueue() {
        this(DEFAULT_COOLDOWN_MS, System::currentTimeMillis);
    }

    /**
     * @param cooldownMs provider 失败后冷却时长（ms，须 &gt;= 0；0 = 不冷却，去重禁用）
     * @param nowMs      时间源（ms since epoch）；测试注入可控时间源。非 null
     */
    public ProviderFailoverQueue(long cooldownMs, LongSupplier nowMs) {
        if (cooldownMs < 0) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiAgentErrors.ARG_MSG,
                    "ProviderFailoverQueue cooldownMs must be >= 0: " + cooldownMs);
        }
        if (nowMs == null) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiAgentErrors.ARG_MSG,
                    "ProviderFailoverQueue nowMs must not be null");
        }
        this.cooldownMs = cooldownMs;
        this.nowMs = nowMs;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    @Override
    public void recordProviderFailure(String provider) {
        requireProvider(provider);
        ProviderHealth entry = health.computeIfAbsent(provider, k -> new ProviderHealth());
        synchronized (entry) {
            entry.lastFailureAt = nowMs.getAsLong();
            entry.consecutiveFailures++;
            entry.recovered = false;
        }
    }

    @Override
    public void recordProviderSuccess(String provider) {
        requireProvider(provider);
        ProviderHealth entry = health.get(provider);
        if (entry == null) {
            // 未失败的 provider 成功：无需重置（本就可用）。
            return;
        }
        synchronized (entry) {
            entry.consecutiveFailures = 0;
            // 成功 = provider 恢复健康：标记 recovered 使其立即可用作 failover 目标
            // （不受 lastFailureAt 冷却约束——成功证明已恢复）。
            entry.recovered = true;
        }
    }

    @Override
    public boolean isProviderAvailable(String provider) {
        requireProvider(provider);
        ProviderHealth entry = health.get(provider);
        if (entry == null) {
            // 未记录失败的 provider 恒可用（健康默认）。
            return true;
        }
        synchronized (entry) {
            if (entry.recovered) {
                // 成功后恢复——立即可用（不受冷却约束）。
                return true;
            }
            if (cooldownMs <= 0) {
                return true;
            }
            return (nowMs.getAsLong() - entry.lastFailureAt) >= cooldownMs;
        }
    }

    /**
     * 诊断用：查询 provider 当前连续失败计数（未经冷却判断）。未记录过的 provider 返回 0。
     */
    public int getConsecutiveFailures(String provider) {
        ProviderHealth entry = health.get(provider);
        return entry != null ? entry.consecutiveFailures : 0;
    }

    private static void requireProvider(String provider) {
        if (provider == null) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiAgentErrors.ARG_MSG,
                    "provider must not be null");
        }
    }

    /**
     * Per-provider 可变状态，所有字段经 entry monitor（synchronized on entry instance）变更。
     */
    private static final class ProviderHealth {
        volatile long lastFailureAt = 0L;
        volatile int consecutiveFailures = 0;
        // true 当 provider 健康（初始/成功后）；false 当在失败冷却中。isProviderAvailable:
        // recovered || cooldownElapsed。success 重置为 true（成功证明恢复，立即可用作 failover 目标）。
        volatile boolean recovered = true;
    }
}
