package io.nop.ai.core.routing;

import io.nop.ai.core.NopAiCoreException;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT;
import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
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
        return new CandidateHealth(breaker.getState(candidate.getModelKey()),
                registry.currentCount(candidate.getProvider(), candidate.getAccountKey()),
                candidate.getConcurrencyLimit());
    }
}
