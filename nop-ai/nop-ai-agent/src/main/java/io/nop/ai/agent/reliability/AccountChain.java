package io.nop.ai.agent.reliability;

import io.nop.ai.core.model.LlmAccountModel;

import java.util.List;

/**
 * 有序备用账号链游走器（plan 2026-08-01-1505-1，设计 §3.6）。
 *
 * <p>持有一个 provider 声明的有序备用账号清单（{@code <accounts>}，不含主账号），按声明顺序逐个推进。
 * 由 {@code LlmCallCoordinator} 重试循环在 {@code QUOTA_EXCEEDED}/{@code AUTH_INVALID} 触发 FALLBACK 时
 * 使用：取下一个账号 → 经 {@code ChatOptions.accountKey}/{@code accountBaseUrl} 下沉到
 * {@code ChatServiceImpl}。链耗尽（{@link #next()} 返回 null）时重试循环 fail-loud（设计 §6.9，
 * Minimum Rules #24——不静默跳过/降级）。
 *
 * <p><b>链语义</b>：{@code <accounts>} 是备用账号链。主账号 = {@code resolveApiKey(provider)}（首次调用用，
 * 与未配置链时完全一致，零回归）。首次 QUOTA/AUTH FALLBACK 从链首（{@code [0]}）开始切换。
 *
 * <p>本类是<b>有状态的 per-execution 游走器</b>（cursor 推进），非线程安全——由重试循环（单线程 per-call）
 * 独占持有。跨并发调用应各创建独立实例（经 {@link IAccountChainResolver}）。
 */
public final class AccountChain {
    private final List<LlmAccountModel> accounts;
    private int cursor = 0;

    /**
     * @param accounts 有序备用账号清单（来自 {@code LlmConfigHelper.resolveAccountChain}）；null/empty 视为无链
     */
    public AccountChain(List<LlmAccountModel> accounts) {
        this.accounts = accounts != null ? accounts : List.of();
    }

    /**
     * @return true 当链中没有备用账号（provider 未配置 {@code <accounts>}）
     */
    public boolean isEmpty() {
        return accounts.isEmpty();
    }

    /**
     * @return 链中账号总数
     */
    public int size() {
        return accounts.size();
    }

    /**
     * 取下一个备用账号，推进游标。
     *
     * @return 下一个账号，或 null 当链已耗尽（调用方 fail-loud）
     */
    public LlmAccountModel next() {
        if (cursor >= accounts.size()) {
            return null;
        }
        return accounts.get(cursor++);
    }

    /**
     * @return 已消费的账号数（诊断用）
     */
    public int consumed() {
        return cursor;
    }
}
