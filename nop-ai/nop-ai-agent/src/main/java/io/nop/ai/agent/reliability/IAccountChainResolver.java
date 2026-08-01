package io.nop.ai.agent.reliability;

/**
 * 账号链解析策略（plan 2026-08-01-1505-1，设计 §3.6）。
 *
 * <p>按 provider 名称解析一个<b>新鲜的</b> {@link AccountChain}（每次调用返回独立的有状态游走器，
 * 供单次 {@code doLlmCallWithRetry} 独占使用）。无链配置时返回空 chain（{@link AccountChain#isEmpty()} == true），
 * 重试循环据此判"无链"→ fail-loud（非静默返回 null）。
 *
 * <p>生产默认实现经 {@code LlmConfigHelper.resolveAccountChain(provider)} 从 {@code {provider}.llm.xml}
 * 的 {@code <accounts>} 解析（纯配置文件，裁定 A）。测试可注入假实现以隔离 config 加载。
 *
 * <p>这是 {@code @FunctionalInterface}：实现只需提供一个 {@code apply(provider)} 方法。
 */
@FunctionalInterface
public interface IAccountChainResolver {

    /**
     * @param provider LLM provider 名称（{@code ChatOptions.getProvider()}）
     * @return 一个新鲜的有状态 {@link AccountChain}；无链时返回空 chain（非 null）
     */
    AccountChain apply(String provider);
}
