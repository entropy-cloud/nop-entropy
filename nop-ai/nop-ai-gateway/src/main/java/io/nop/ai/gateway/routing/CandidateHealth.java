package io.nop.ai.gateway.routing;

import io.nop.ai.core.reliability.CircuitState;

/**
 * 单个候选的健康状态视图（plan 2026-08-15-0849-2，设计 §3.3）。
 *
 * <p>由 {@code CandidateHealthProvider} 在策略选择时构建（每候选一个快照）：
 * <ul>
 *   <li>熔断状态——复用 {@code ThresholdBreaker.getState(modelKey)}（{@code provider:model} 键），
 *       不新建判定；OPEN = 冷却期（复用 {@link CircuitState}，冷却判定不另起炉灶）。</li>
 *   <li>并发计数——并发注册表当前值（in-flight 计数）。</li>
 *   <li>并发上限——候选解析期携带的 {@code concurrencyLimit}（W3 层级语义已解析：
 *       null/≤0 = 不限制）。</li>
 * </ul>
 *
 * <p><b>饱和判定</b>：{@code limit != null && limit > 0 && current >= limit} = 并发饱和；
 * 并发超限<b>不记入熔断失败计数</b>（非失败事件，只影响本轮选择——设计 §3.3）。
 */
public final class CandidateHealth {
    private final CircuitState circuitState;
    private final int currentConcurrency;
    private final Integer concurrencyLimit;
    private final boolean allowCall;

    public CandidateHealth(CircuitState circuitState, int currentConcurrency, Integer concurrencyLimit) {
        this(circuitState, currentConcurrency, concurrencyLimit, circuitState != CircuitState.OPEN);
    }

    /**
     * @param allowCall 第 5 标量（plan 2509 R2-06 裁定⑦(b)）：allowCall 冷却感知<b>纯读投影</b>——
     *                  非 OPEN、或 OPEN 且冷却已过即 true（不占 probe 槽不迁移，供脚本偏好排序；
     *                  权威状态迁移 allowCall 归选择策略「选中后唯一调用点」）
     */
    public CandidateHealth(CircuitState circuitState, int currentConcurrency, Integer concurrencyLimit,
                           boolean allowCall) {
        this.circuitState = circuitState != null ? circuitState : CircuitState.CLOSED;
        this.currentConcurrency = currentConcurrency;
        this.concurrencyLimit = concurrencyLimit;
        this.allowCall = allowCall;
    }

    /**
     * @return allowCall 冷却感知纯读投影（非 OPEN、或 OPEN 且冷却已过）；权威状态迁移
     * 经 {@code IModelClassHealth#allowCall(ModelClassCandidate)}（选中后唯一调用点）
     */
    public boolean isAllowCall() {
        return allowCall;
    }

    public CircuitState getCircuitState() {
        return circuitState;
    }

    public int getCurrentConcurrency() {
        return currentConcurrency;
    }

    /**
     * @return 并发上限（候选解析期已解析）；null/≤0 = 不限制
     */
    public Integer getConcurrencyLimit() {
        return concurrencyLimit;
    }

    /**
     * @return true 当熔断 OPEN（冷却期内，拒绝调用）
     */
    public boolean isCircuitOpen() {
        return circuitState == CircuitState.OPEN;
    }

    /**
     * @return true 当并发饱和（in-flight 计数 ≥ 上限；null/≤0 上限恒不饱和）
     */
    public boolean isConcurrencySaturated() {
        return concurrencyLimit != null && concurrencyLimit > 0 && currentConcurrency >= concurrencyLimit;
    }

    /**
     * @return true 当候选可用（非熔断 OPEN 且非并发饱和）
     */
    public boolean isAvailable() {
        return !isCircuitOpen() && !isConcurrencySaturated();
    }
}
