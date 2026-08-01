package io.nop.ai.agent.reliability;

import io.nop.ai.core.model.LlmFailoverProviderModel;

import java.util.List;

/**
 * 跨 provider 有序故障转移的 per-execution 游走器（plan 2026-08-01-1905-3，设计 §13.4 裁定 A/D）。
 *
 * <p>持有 {@code primaryProvider} 之后的有序 failover 目标子表（来自 {@code _default.llm-failover.xml}），
 * 按声明顺序逐个推进。由 {@code LlmCallCoordinator} 重试循环在同 provider 账号链耗尽、升级到跨 provider
 * failover 时使用：取下一个<b>可用</b> provider（跳过 {@link IProviderFailoverQueue} 冷却中的）→ 经
 * {@code ChatOptions.provider}/{@code model} 下沉到 {@code ChatServiceImpl}。链耗尽
 * （{@link #nextAvailable(IProviderFailoverQueue)} 返回 null）时重试循环 fail-loud（设计 §6.9，
 * Minimum Rules #24——不静默跳过/降级）。
 *
 * <p><b>向前语义（裁定 D）</b>：cursor 只增不减——单次调用内 P1→P2→P3 线性向前，不回退，故单次调用内
 * <b>无震荡可能</b>。跨调用震荡（P1↔P2 反复弹跳）由 {@link IProviderFailoverQueue} 的 per-provider
 * 冷却防（去重维度），与本游走器（单次向前维度）正交。
 *
 * <p>本类是<b>有状态的 per-execution 游走器</b>（cursor 推进），非线程安全——由重试循环（单线程 per-call）
 * 独占持有。跨并发调用应各创建独立实例（经 {@link IProviderFailoverChainResolver}）。
 *
 * <p>对称于 {@link AccountChain}（同 provider 账号链游走器）：结构、向前语义、fail-loud-on-exhaust 一致。
 */
public final class ProviderFailoverChain {
    private final List<LlmFailoverProviderModel> failoverProviders;
    private int cursor = 0;

    /**
     * @param failoverProviders 有序 failover 目标子表（来自 {@code LlmConfigHelper.resolveFailoverChain}，
     *                          primary 之后的 providers）；null/empty 视为无链
     */
    public ProviderFailoverChain(List<LlmFailoverProviderModel> failoverProviders) {
        this.failoverProviders = failoverProviders != null ? failoverProviders : List.of();
    }

    /**
     * @return true 当链中没有 failover 目标（primary 不在表/是表尾/配置文件缺省）
     */
    public boolean isEmpty() {
        return failoverProviders.isEmpty();
    }

    /**
     * @return 链中 failover 目标总数
     */
    public int size() {
        return failoverProviders.size();
    }

    /**
     * 取下一个<b>可用</b> failover 目标 provider，推进游标。
     *
     * <p>向前游走，跳过 {@code healthQueue} 判定仍在冷却期（不可用）的 provider（裁定 D 去重——
     * 防跨调用切回刚失败的 provider）。第一个可用的即为返回值，游标停在其后。全部冷却中或链尾 → null。
     *
     * @param healthQueue 跨调用共享 provider 健康状态（去重）；非 null
     * @return 下一个可用 failover provider，或 null 当链已耗尽（调用方 fail-loud）
     */
    public LlmFailoverProviderModel nextAvailable(IProviderFailoverQueue healthQueue) {
        while (cursor < failoverProviders.size()) {
            LlmFailoverProviderModel candidate = failoverProviders.get(cursor);
            cursor++;
            if (healthQueue.isProviderAvailable(candidate.getProvider())) {
                return candidate;
            }
            // 冷却中（跨调用去重）→ 跳过，继续向后。
        }
        return null;
    }

    /**
     * @return 已消费（游标已越过）的 provider 数（诊断用）
     */
    public int consumed() {
        return cursor;
    }
}
