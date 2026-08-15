package io.nop.ai.core.routing;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;

import java.util.List;
import java.util.Set;

/**
 * 默认选择策略（plan 2026-08-15-0849-2，设计 §3.3）：健康度 + 并发感知 + 声明序的有序回退。
 *
 * <p>与既有 {@code AccountChain} 声明序语义一致：跳过熔断 OPEN / 并发饱和 / 本轮已尝试的候选，
 * 其余按<b>声明顺序</b>返回首个；全部不可用返回 null（调用方 fail-loud）。
 *
 * <p><b>无失败记账副作用</b>：跳过（含并发饱和跳过）不调用 {@code ThresholdBreaker.recordFailure}
 * ——并发超限/熔断跳过是非失败事件，失败记账归编排层（见 {@link ModelClassRouter} 契约）。
 */
public final class DefaultSelectionStrategy implements ISelectionStrategy {

    @Override
    public ModelClassCandidate select(ChatRequest request, List<ModelClassCandidate> candidates,
                                      IModelClassHealth health, Set<ModelClassCandidate> attempted) {
        if (candidates == null) {
            return null;
        }
        // 健康视图缺失 = 调用方编程错误：显式 fail-fast，不隐式假设（Phase 3 裁定落档）。
        if (health == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "DefaultSelectionStrategy: health view must not be null");
        }
        for (ModelClassCandidate candidate : candidates) {
            if (attempted != null && attempted.contains(candidate)) {
                continue;
            }
            if (!health.healthOf(candidate).isAvailable()) {
                // 熔断 OPEN / 并发饱和：跳过（不记失败）。
                continue;
            }
            return candidate;
        }
        // 全部跳过/已尝试 = 无可用候选（调用方 fail-loud）。
        return null;
    }
}
