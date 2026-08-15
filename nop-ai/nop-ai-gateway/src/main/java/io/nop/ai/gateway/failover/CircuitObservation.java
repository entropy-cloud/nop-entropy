package io.nop.ai.gateway.failover;

import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ThresholdBreaker;

/**
 * 熔断状态迁移 / 冷却期观察辅助（W8 OBS-01 Phase 1 裁定）：编排层调用点派生——
 * 在 {@code recordFailure}/{@code recordSuccess}/{@code allowCall} 调用前后经
 * {@code getState} 对比，状态变化时上报 {@link IFailoverMetrics#onCircuitTransition}，
 * 冷却期事件按观察前状态归类（OPEN → {@code rejected}；HALF_OPEN → {@code probe-rejected}；
 * 迁移至 OPEN = 冷却启动 {@code started}）。
 *
 * <p><b>并发近似语义（契约）</b>：{@code ThresholdBreaker.getState} 为无锁读（类
 * javadoc 明确），并发交错下本观察可能对同一次迁移重复计数或错误归因——契约语义列
 * 已显式写明"近似观测"。core 原语（{@code ThresholdBreaker}）内部状态机与公共方法
 * 语义零改动。
 *
 * <p>{@code metrics} 可空（null = 观测 no-op，Phase 1 null-object / fail-fast 裁定：
 * 仅测试/手工构造形态；标准部署经 beans.xml 非 optional ref fail-fast）。
 */
final class CircuitObservation {

    private CircuitObservation() {
    }

    static void recordFailure(IFailoverMetrics metrics, ThresholdBreaker breaker,
                              String provider, String model, String modelKey) {
        if (metrics == null) {
            breaker.recordFailure(modelKey);
            return;
        }
        CircuitState before = breaker.getState(modelKey);
        breaker.recordFailure(modelKey);
        observe(metrics, provider, model, before, breaker.getState(modelKey));
    }

    static void recordSuccess(IFailoverMetrics metrics, ThresholdBreaker breaker,
                              String provider, String model, String modelKey) {
        if (metrics == null) {
            breaker.recordSuccess(modelKey);
            return;
        }
        CircuitState before = breaker.getState(modelKey);
        breaker.recordSuccess(modelKey);
        observe(metrics, provider, model, before, breaker.getState(modelKey));
    }

    /**
     * {@code allowCall} + 观测：OPEN→HALF_OPEN 探活放行（迁移上报）或冷却期拒绝事件
     * （未期满 / 探活占用）。返回与 {@code breaker.allowCall} 一致。
     */
    static boolean allowCall(IFailoverMetrics metrics, ThresholdBreaker breaker,
                             String provider, String model, String modelKey) {
        if (metrics == null) {
            return breaker.allowCall(modelKey);
        }
        CircuitState before = breaker.getState(modelKey);
        boolean allowed = breaker.allowCall(modelKey);
        observe(metrics, provider, model, before, breaker.getState(modelKey));
        if (!allowed) {
            // CLOSED 恒放行；拒绝只可能发生在 OPEN（未期满）或 HALF_OPEN（探活占用）。
            metrics.onCooldown(provider, model, before == CircuitState.HALF_OPEN
                    ? CooldownEventType.PROBE_REJECTED : CooldownEventType.REJECTED);
        }
        return allowed;
    }

    private static void observe(IFailoverMetrics metrics, String provider, String model,
                                CircuitState before, CircuitState after) {
        if (before != after) {
            metrics.onCircuitTransition(provider, model, after);
            if (after == CircuitState.OPEN) {
                // CLOSED→OPEN 与 HALF_OPEN→OPEN 均重启冷却计时（ThresholdBreaker 语义）。
                metrics.onCooldown(provider, model, CooldownEventType.STARTED);
            }
        }
    }
}
