package io.nop.ai.gateway.routing;

import io.nop.ai.core.model.ModelClassCandidate;

import io.nop.ai.core.NopAiCoreException;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT;
import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ICircuitBreaker;
import io.nop.ai.core.reliability.ThresholdBreaker;

/**
 * 候选健康状态构建器（plan 2026-08-15-0849-2，设计 §3.3）——复用既有机制，不新建判定：
 * <ul>
 *   <li>熔断状态：{@link ICircuitBreaker#getState(String)}（{@code ThresholdBreaker}，
 *       {@code provider:model} 键，含 OPEN 冷却期语义）；</li>
 *   <li>并发计数：{@link ConcurrencyRegistry#currentCount(String, String)}；
 *       并发上限取候选解析期携带值（W3 层级语义已解析）。</li>
 * </ul>
 *
 * <p>复用优先不变式：本类不新建第二套熔断/并发判定——只做既有机制的读取投影。
 */
public final class CandidateHealthProvider implements IModelClassHealth {
    private final ICircuitBreaker breaker;
    private final ConcurrencyRegistry registry;

    public CandidateHealthProvider(ICircuitBreaker breaker, ConcurrencyRegistry registry) {
        this.breaker = breaker != null ? breaker : new ThresholdBreaker();
        this.registry = registry != null ? registry : new ConcurrencyRegistry();
    }

    @Override
    public CandidateHealth healthOf(ModelClassCandidate candidate) {
        if (candidate == null) {
            throw new NopAiCoreException(ERR_AI_CORE_INVALID_ARGUMENT).param(ARG_DETAIL, "candidate must not be null");
        }
        CircuitState state = breaker.getState(candidate.getModelKey());
        // 第 5 标量（plan 2509 裁定⑦(b)）：allowCall 纯读投影——非 OPEN、或 OPEN 且冷却已过
        // 即 true；不占 probe 槽不迁移（权威状态迁移见 {@link #allowCall(ModelClassCandidate)}）。
        boolean allowCallProjection = state != CircuitState.OPEN
                || (breaker instanceof ThresholdBreaker
                        && ((ThresholdBreaker) breaker).cooldownElapsed(candidate.getModelKey()));
        return new CandidateHealth(state,
                registry.currentCount(candidate.getProvider(), candidate.getAccountKey()),
                candidate.getConcurrencyLimit(), allowCallProjection);
    }

    /**
     * 权威状态迁移 allowCall（plan 2509 裁定⑦(b)）：选择策略「选中后、返回前」唯一调用点——
     * OPEN→HALF_OPEN 惰性迁移 + 占 probe 槽在此收口（真实探针由选中收口，幻影结构性消除）；
     * HALF_OPEN 单探针语义由原语内建互斥 + probeTimeout 逃逸维持。非
     * {@link ThresholdBreaker} 实现退化为健康投影（无状态迁移面）。
     */
    @Override
    public boolean allowCall(ModelClassCandidate candidate) {
        if (candidate == null) {
            throw new NopAiCoreException(ERR_AI_CORE_INVALID_ARGUMENT).param(ARG_DETAIL, "candidate must not be null");
        }
        if (breaker instanceof ThresholdBreaker) {
            return ((ThresholdBreaker) breaker).allowCall(candidate.getModelKey());
        }
        return healthOf(candidate).isAvailable();
    }
}
