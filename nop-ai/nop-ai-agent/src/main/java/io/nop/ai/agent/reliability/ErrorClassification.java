package io.nop.ai.agent.reliability;

/**
 * LLM 错误规范化分类。
 *
 * <p>已上移到 {@link io.nop.ai.core.model.ErrorClassification}（设计
 * {@code nop-ai-llm-error-normalization-design.md} §3.8：规范化配置 llm.xdef
 * 要引用它，故必须落在 nop-ai-core 的 bean-package 层）。本类保留为
 * 兼容桥接，新代码请直接引用 core 定义。</p>
 *
 * @deprecated 请使用 {@link io.nop.ai.core.model.ErrorClassification}
 */
@Deprecated
public enum ErrorClassification {
    TRANSIENT,
    NON_TRANSIENT,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    AUTH_INVALID,
    CACHE_STATE_LOST
}
