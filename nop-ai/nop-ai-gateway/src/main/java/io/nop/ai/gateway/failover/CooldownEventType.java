package io.nop.ai.gateway.failover;

/**
 * 冷却期事件类型（W8 OBS-01 契约 §3.6：冷却期计数三事件定义，均可达，非"永不触发"）。
 *
 * <ul>
 *   <li>{@link #STARTED}：熔断迁移至 OPEN（CLOSED→OPEN 或 HALF_OPEN→OPEN），冷却计时启动。</li>
 *   <li>{@link #REJECTED}：OPEN 下 {@code allowCall} 未期满拒绝。</li>
 *   <li>{@link #PROBE_REJECTED}：HALF_OPEN 探活占用拒绝（并发调用者按 OPEN 拒绝）。</li>
 * </ul>
 */
public enum CooldownEventType {

    /** 熔断迁移至 OPEN（冷却计时启动）。 */
    STARTED,

    /** OPEN 下 allowCall 未期满拒绝。 */
    REJECTED,

    /** HALF_OPEN 探活占用拒绝。 */
    PROBE_REJECTED
}
