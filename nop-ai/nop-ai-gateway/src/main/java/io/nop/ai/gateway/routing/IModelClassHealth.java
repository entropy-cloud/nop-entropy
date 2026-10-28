package io.nop.ai.gateway.routing;

import io.nop.ai.core.model.ModelClassCandidate;

/**
 * 候选健康状态视图（plan 2026-08-15-0849-2，设计 §3.3）——选择策略的输入契约。
 *
 * <p>按候选查询其健康快照（熔断状态 + 并发计数 + 并发上限）。契约：{@link #healthOf}
 * 返回<b>非 null</b> 快照（健康判断是候选健康度量的完整投影，永不缺失）。
 */
public interface IModelClassHealth {

    /**
     * @param candidate 候选（不可为 null）
     * @return 该候选的健康快照；非 null
     */
    CandidateHealth healthOf(ModelClassCandidate candidate);

    /**
     * 权威 allowCall 复查（plan 2509 R2-06 裁定⑦(b)）：选择策略「选中后、返回前」唯一调用点。
     * 默认实现 = 健康投影（无状态迁移面）——非 {@link CandidateHealthProvider} 的实现保持
     * 既有「只读视图」语义零回归；熔断原语的 OPEN→HALF_OPEN 迁移与 probe 槽占用由
     * {@code CandidateHealthProvider} 覆写承载。
     */
    default boolean allowCall(ModelClassCandidate candidate) {
        CandidateHealth health = healthOf(candidate);
        return health.isAvailable() || health.isAllowCall();
    }
}
