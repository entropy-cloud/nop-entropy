package io.nop.ai.core.routing;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plan 2026-08-15-0849-2 Phase 3 (ROUTE-02/03): 默认选择策略六项语义
 * （Minimum Rules #25——新功能必有测试 + #24 无静默跳过）。
 *
 * <p>语义：熔断 OPEN 跳过 / 并发饱和跳过 / 声明序 / 已尝试集跳过 / 全部跳过返回 null /
 * 并发饱和跳过不触发熔断失败记账（策略无 recordFailure 副作用）。
 */
public class TestDefaultSelectionStrategy extends JunitBaseTestCase {

    private final DefaultSelectionStrategy strategy = new DefaultSelectionStrategy();

    private final ThresholdBreaker breaker = new ThresholdBreaker(3, 60_000L);
    private final ConcurrencyRegistry registry = new ConcurrencyRegistry();
    private final IModelClassHealth health = new CandidateHealthProvider(breaker, registry);

    private static ModelClassCandidate candidate(String provider, String model, Integer limit) {
        return new ModelClassCandidate(provider, model, null, null, limit);
    }

    @Test
    void skipsCircuitOpenCandidates() {
        // 候选 0 熔断 OPEN（provider:model 键）→ 跳过，返回候选 1。
        ModelClassCandidate c0 = candidate("p1", "m1", null);
        ModelClassCandidate c1 = candidate("p1", "m2", null);
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure(c0.getModelKey());
        }
        assertEquals(CircuitState.OPEN, breaker.getState(c0.getModelKey()));

        assertSame(c1, strategy.select(request(), List.of(c0, c1), health, Set.of()),
                "OPEN candidate skipped, next healthy by declaration order");
    }

    @Test
    void skipsConcurrencySaturatedCandidates() {
        // 候选 0 并发饱和（limit=1，计数已达 1）→ 跳过，返回候选 1。
        ModelClassCandidate c0 = candidate("p1", "m1", 1);
        ModelClassCandidate c1 = candidate("p1", "m2", null);
        registry.acquire("p1", null);
        assertEquals(1, registry.currentCount("p1", null));

        assertSame(c1, strategy.select(request(), List.of(c0, c1), health, Set.of()),
                "concurrency-saturated candidate skipped");
    }

    @Test
    void respectsDeclarationOrder() {
        ModelClassCandidate c0 = candidate("p1", "m1", null);
        ModelClassCandidate c1 = candidate("p1", "m2", null);
        ModelClassCandidate c2 = candidate("p1", "m3", null);

        assertSame(c0, strategy.select(request(), List.of(c0, c1, c2), health, Set.of()),
                "all healthy → first declared");
    }

    @Test
    void skipsAttemptedCandidates() {
        ModelClassCandidate c0 = candidate("p1", "m1", null);
        ModelClassCandidate c1 = candidate("p1", "m2", null);
        Set<ModelClassCandidate> attempted = new LinkedHashSet<>();
        attempted.add(c0);

        assertSame(c1, strategy.select(request(), List.of(c0, c1), health, attempted),
                "attempted candidate must not be re-selected");
    }

    @Test
    void allUnavailableReturnsNull() {
        ModelClassCandidate c0 = candidate("p1", "m1", 1);
        ModelClassCandidate c1 = candidate("p1", "m2", 1);
        registry.acquire("p1", null);
        registry.acquire("p1", null); // 两个候选同一 provider 主账号键，均饱和
        assertEquals(2, registry.currentCount("p1", null));

        assertNull(strategy.select(request(), List.of(c0, c1), health, Set.of()),
                "all candidates saturated → null (caller fail-loud)");
    }

    @Test
    void saturationSkipHasNoCircuitFailureSideEffect() {
        // 并发饱和跳过不触发熔断失败记账：策略无 recordFailure 副作用（语义测试）。
        ModelClassCandidate c0 = candidate("p1", "m1", 1);
        ModelClassCandidate c1 = candidate("p1", "m2", null);
        registry.acquire("p1", null);

        assertSame(c1, strategy.select(request(), List.of(c0, c1), health, Set.of()));

        assertEquals(CircuitState.CLOSED, breaker.getState(c0.getModelKey()),
                "saturation skip must not record a circuit failure");
        assertEquals(CircuitState.CLOSED, breaker.getState(c1.getModelKey()));
    }

    @Test
    void nullHealthViewFailsFast() {
        // 健康视图缺失 = 调用方编程错误：显式 fail-fast，不隐式假设（Phase 3 裁定）。
        assertThrows(io.nop.ai.core.NopAiCoreException.class,
                () -> strategy.select(request(), List.of(candidate("p1", "m1", null)), null, Set.of()));
    }

    private static ChatRequest request() {
        return new ChatRequest();
    }
}
