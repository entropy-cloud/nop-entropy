package io.nop.ai.core.routing;

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
}
