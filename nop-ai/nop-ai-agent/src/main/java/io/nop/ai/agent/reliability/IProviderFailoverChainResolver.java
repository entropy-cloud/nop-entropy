package io.nop.ai.agent.reliability;

/**
 * 跨 provider failover 链解析策略（plan 2026-08-01-1905-3，设计 §13.4 裁定 A）。
 *
 * <p>按 primary provider 名称解析一个<b>新鲜的</b> {@link ProviderFailoverChain}（每次调用返回独立的有状态
 * 游走器，供单次 {@code doLlmCallWithRetry} 独占使用）。无链配置（配置文件缺省 / primary 不在表 / primary 是表尾）
 * 时返回空 chain（{@link ProviderFailoverChain#isEmpty()} == true），重试循环据此判"无 failover"→ fail-loud
 * （非静默返回 null）。
 *
 * <p>对称于 {@link IAccountChainResolver}（同 provider 账号链解析）。生产默认实现经
 * {@code LlmConfigHelper.resolveFailoverChain(primaryProvider)} 从 {@code _default.llm-failover.xml}
 * 解析（纯配置文件，裁定 A）。测试可注入假实现以隔离 config 加载。
 *
 * <p>这是 {@code @FunctionalInterface}：实现只需提供一个 {@code apply(primaryProvider)} 方法。
 */
@FunctionalInterface
public interface IProviderFailoverChainResolver {

    /**
     * @param primaryProvider 主 provider 名称（failover 的起点，{@code ChatOptions.getProvider()}）
     * @return 一个新鲜的有状态 {@link ProviderFailoverChain}（primary 之后的有序 failover 目标）；
     *         无链时返回空 chain（非 null）
     */
    ProviderFailoverChain apply(String primaryProvider);
}
