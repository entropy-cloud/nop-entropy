package io.nop.ai.gateway.failover;

import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.ai.core.routing.ModelClassRouter;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.exceptions.NopException;

import java.util.ArrayList;
import java.util.List;

/**
 * 选择 + 熔断探活恢复辅助（W7 网关形态，plan 2026-08-15-1116-3 Phase 4）。
 *
 * <p>与 W6 本地适配器 {@code ChatServiceFailoverAdapter.selectNextWithProbe/probeBrokenCandidates}
 * 同款语义（D5b/D5c/D5d）：全池饱和（{@code ERR_AI_MODEL_CLASS_SATURATED}）时对健康视图 OPEN
 * 的候选显式 {@code allowCall} 探活（冷却期满 → HALF_OPEN 放行，探活成功经 recordSuccess →
 * CLOSED 恢复）；探活候选池 = 类内候选 + provider 链扩展候选；只探未尝试、非并发饱和、熔断
 * OPEN 的候选；探活未放行任何候选 → 原样抛饱和（fail-loud）。主动路径（active=true）不跨
 * provider 链（router 语义），被动路径含链扩展。
 */
final class FailoverProbeSupport {

    private FailoverProbeSupport() {
    }

    static ModelClassCandidate selectNextWithProbe(ThresholdBreaker breaker, ConcurrencyRegistry registry,
                                                   ModelClassRouter router, boolean active, String primaryProvider) {
        try {
            return active ? router.selectNext() : router.selectNextAfterFailure();
        } catch (NopException e) {
            if (!NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode().equals(e.getErrorCode())) {
                throw e;
            }
            if (probeBrokenCandidates(breaker, registry, router, primaryProvider)) {
                return active ? router.selectNext() : router.selectNextAfterFailure();
            }
            throw e;
        }
    }

    static boolean isConcurrencySaturated(ConcurrencyRegistry registry, ModelClassCandidate candidate) {
        Integer limit = candidate.getConcurrencyLimit();
        return limit != null && limit > 0
                && registry.currentCount(candidate.getProvider(), candidate.getAccountKey()) > limit;
    }

    /**
     * 探活遍历（W6 D5b/D5c/D5d 语义）：候选池 = 类内候选 + provider 链扩展候选。
     *
     * @return true 当至少一个候选被放行探活（HALF_OPEN）
     */
    private static boolean probeBrokenCandidates(ThresholdBreaker breaker, ConcurrencyRegistry registry,
                                                 ModelClassRouter router, String primaryProvider) {
        boolean probed = false;
        List<ModelClassCandidate> pool = new ArrayList<>(router.getInClassCandidates());
        pool.addAll(LlmConfigHelper.resolveProviderChainCandidates(primaryProvider));
        for (ModelClassCandidate candidate : pool) {
            if (router.getAttempted().contains(candidate)) {
                continue;
            }
            if (breaker.getState(candidate.getModelKey()) != CircuitState.OPEN) {
                continue;
            }
            if (isConcurrencySaturated(registry, candidate)) {
                continue;
            }
            if (breaker.allowCall(candidate.getModelKey())) {
                probed = true;
            }
        }
        return probed;
    }
}
