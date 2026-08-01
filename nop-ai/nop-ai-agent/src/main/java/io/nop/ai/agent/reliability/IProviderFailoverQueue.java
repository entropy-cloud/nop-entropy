package io.nop.ai.agent.reliability;

/**
 * 跨调用共享的 provider 维度健康/熔断状态（plan 2026-08-01-1905-3，设计 §13.4 裁定 B/D）。
 *
 * <p>承载跨 provider 有序故障转移的<b>去重/防震荡</b>状态（per-provider 冷却）。与
 * {@link AccountChain}（per-execution 游走器，单次调用内线性向前）正交：本接口是
 * <b>跨调用共享单例</b>（类比注入式 {@link ICircuitBreaker} 单例），记录 provider 级失败
 * 并在冷却期内拒绝把该 provider 作为 failover 目标，防 P1↔P2 跨调用反复弹跳。
 *
 * <p><b>与 {@link ICircuitBreaker} 的关系（裁定 B reconcile）</b>：两层并存不 roll-up。
 * model 级熔断（{@code ThresholdBreaker}，per {@code provider:model} 复合键）门控单模型调用是否放行；
 * provider 级熔断（本接口，per provider）门控跨 provider failover 的去重。不同抽象层、不同决策、不耦合。
 *
 * <p><b>状态 in-memory only（per queue instance）</b>，持久化/跨进程共享是 Non-Goal successor
 * （同 {@code ThresholdBreaker}）。
 *
 * <p><b>shipped 默认（{@link NoOpProviderFailoverQueue}）</b>：{@link #isProviderAvailable} 恒 true、
 * record 为显式 no-op，零行为回归（无去重——failover 目标恒可用，单次调用内由游标保证向前）。
 * 功能实现（{@link ProviderFailoverQueue}）经显式注入启用。
 */
public interface IProviderFailoverQueue {

    /**
     * 记录 provider 级失败（其账号链耗尽 = provider 级不可用）。由 {@code LlmCallCoordinator}
     * 在同 provider 账号链耗尽、升级到跨 provider failover 之前调用。
     *
     * <p>语义：更新该 provider 的 {@code lastFailureAt = now}，递增 consecutiveFailures。
     * 后续冷却期内（{@code now - lastFailureAt < cooldownMs}）{@link #isProviderAvailable} 返回 false。
     *
     * @param provider LLM provider 名称（非 null）
     */
    void recordProviderFailure(String provider);

    /**
     * 记录 provider 级成功（failover 到该 provider 后调用成功）。重置该 provider 的失败计数。
     *
     * @param provider LLM provider 名称（非 null）
     */
    void recordProviderSuccess(String provider);

    /**
     * 查询 provider 当前是否可作为 failover 目标（不在冷却期内）。
     *
     * <p>shipped 默认恒 true（不去重）。功能实现在冷却期内返回 false，使 {@link ProviderFailoverChain}
     * 游走器跳过该 provider 继续向后找可用目标。
     *
     * @param provider LLM provider 名称（非 null）
     * @return true 当 provider 可用（未失败 / 已过冷却期）；false 当在冷却期内
     */
    boolean isProviderAvailable(String provider);
}
