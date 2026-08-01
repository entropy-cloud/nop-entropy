package io.nop.ai.agent.reliability;

/**
 * Pass-through {@link IProviderFailoverQueue} used as the shipped default when no
 * functional queue is registered (plan 2026-08-01-1905-3，设计 §13.4 裁定 B/D / L3-W2-4).
 *
 * <p>{@link #isProviderAvailable} 恒返回 {@code true}，{@link #recordProviderFailure} /
 * {@link #recordProviderSuccess} 为显式 no-op。这使跨 provider failover 的去重维度"未启用"——
 * failover 目标恒可用，单次调用内的向前游标（{@link ProviderFailoverChain}）单独保证不回退。
 * 零行为回归：无功能 queue 注入时，去重逻辑完全 no-op，与未引入本接口前一致。
 *
 * <p>这是<b>显式 pass-through 默认</b>，非静默跳过必需行为（Minimum Rules #24）。与
 * {@link AlwaysClosed} / {@link NoOpGoalTracker} / {@link NoOpSustainer} 同级 pass-through 模式。
 *
 * <p>stateless，因此线程安全。
 */
public final class NoOpProviderFailoverQueue implements IProviderFailoverQueue {

    private static final NoOpProviderFailoverQueue INSTANCE = new NoOpProviderFailoverQueue();

    private NoOpProviderFailoverQueue() {
    }

    /**
     * @return singleton pass-through {@link IProviderFailoverQueue} 实例
     */
    public static IProviderFailoverQueue noOp() {
        return INSTANCE;
    }

    @Override
    public void recordProviderFailure(String provider) {
        // Explicit no-op: pass-through 默认不维护 per-provider 状态，失败记录按设计丢弃。
        // 非空方法体占位作 placeholder（Minimum Rules #24）。
    }

    @Override
    public void recordProviderSuccess(String provider) {
        // Explicit no-op（同上）。
    }

    @Override
    public boolean isProviderAvailable(String provider) {
        return true;
    }
}
