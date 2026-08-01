package io.nop.ai.api.chat;

/**
 * LLM 错误规范化分类（纯词汇枚举，不 import 任何上层类型）。
 *
 * <p>对应 {@code ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md} §3.2：
 * 把 provider 的异构错误响应规范化为少数固定分类。分类是事实判断（这条响应是什么），
 * 恢复是策略判断（拿这个事实怎么办），两者解耦——本枚举只承载事实。</p>
 *
 * <p><b>类型归属（plan 2026-08-01-1440-1 裁定）</b>：本枚举从 {@code nop-ai-core}
 * 迁移到 {@code nop-ai-api}（最低层，仅依赖 {@code nop-api-core}）。理由：
 * {@link ChatResponse} 在 {@code nop-ai-api}，而 core 生产者、agent 消费者三方共同可见
 * 的唯一层就是 {@code nop-ai-api}（依赖图：nop-ai-api → nop-api-core；nop-ai-core → nop-ai-api；
 * nop-ai-agent → nop-ai-core）。把枚举留在 nop-ai-core 会让 ChatResponse 无法引用它
 * （形成 nop-ai-core→nop-ai-api→nop-ai-core 循环依赖）。迁移后信号通路全程同一类型，
 * 无任何按名/按类型转换。</p>
 *
 * <p>与恢复动作的对应（由上层 {@code IRetryPolicy} 消费）：</p>
 * <ul>
 *   <li>{@link #TRANSIENT} — 5xx/超时/连接重置；同账号可重试（退避）→ RETRY</li>
 *   <li>{@link #RATE_LIMITED} — 429 限流（"太快了"）；等待后重试 → RETRY（按 Retry-After）</li>
 *   <li>{@link #QUOTA_EXCEEDED} — 额度耗尽/计费上限（"没钱了"）；不可重试 → 切换账号</li>
 *   <li>{@link #AUTH_INVALID} — 401/403 key 无效/过期/无权限；不可重试 → 切换账号</li>
 *   <li>{@link #NON_TRANSIENT} — 400 请求错误/prompt 过长/内容过滤；不可重试 → STOP</li>
 *   <li>{@link #CACHE_STATE_LOST} — 409 本地推理缓存丢失（TRANSIENT 族）；原样回放 → RETRY</li>
 * </ul>
 *
 * <p>注意（设计 §6.1 零回归红线不变量）：{@link #QUOTA_EXCEEDED} / {@link #AUTH_INVALID} /
 * {@link #CACHE_STATE_LOST} 今日不可达（无人生产），只能经配置后的 {@code <errorMappings>}
 * 到达；默认启发式把 401/403 映射为 {@link #NON_TRANSIENT}（不是 {@link #AUTH_INVALID}）。</p>
 */
public enum ErrorClassification {
    TRANSIENT,
    NON_TRANSIENT,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    AUTH_INVALID,
    CACHE_STATE_LOST
}
