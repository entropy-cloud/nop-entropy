package io.nop.ai.gateway.routing;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.model.ModelClassCandidate;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.execute.RuleManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2509 R2-06 成员 3.3-C7-005（含 C3-005 半边，C10-014 面）回归钉：allowCall 门控
 * 双面收敛（裁定⑦(b)）。
 *
 * <p>红形态（修复前）：选择面从不调用 {@code ThresholdBreaker.allowCall}——OPEN 冷却期满
 * 候选被永久跳过（容量单调流失不可自愈）；规则脚本无 allowCall 输入可用。
 * 绿形态：healthView 第 5 标量 = 纯读投影（不占 probe 槽不迁移）+ 权威状态迁移 allowCall
 * 移至两策略「选中后、返回前」唯一调用点；HALF_OPEN 单探针维持（探针在飞时门控拒绝）；
 * C3-005 半边：attempted 维持请求级生命周期，跨请求可达性经冷却复查恢复（零新簿记）。
 */
class TestSelectionAllowCallRecheck_C7_005 extends JunitBaseTestCase {

    private final ThresholdBreaker breaker = new ThresholdBreaker(1, 60);
    private final ConcurrencyRegistry registry = new ConcurrencyRegistry();
    private final IModelClassHealth health = new CandidateHealthProvider(breaker, registry);

    private static ModelClassCandidate candidate(String provider, String model) {
        return new ModelClassCandidate(provider, model, null, null, null);
    }

    private static ChatRequest request(String provider, String model) {
        ChatOptions options = new ChatOptions();
        options.setProvider(provider);
        options.setModel(model);
        ChatRequest request = new ChatRequest();
        request.setOptions(options);
        return request;
    }

    @Test
    @Timeout(30)
    void openCooldownElapsedCandidateSelectableViaAuthoritativeAllowCall() throws Exception {
        ModelClassCandidate a = candidate("p", "m");
        breaker.recordFailure(a.getModelKey()); // 阈值 1 → OPEN
        assertEquals(CircuitState.OPEN, breaker.getState(a.getModelKey()));

        Thread.sleep(80); // 冷却（60ms）期满

        // 红形态：OPEN 候选被永久跳过 → null（容量流失不可自愈）。
        ModelClassCandidate selected = new DefaultSelectionStrategy()
                .select(request("p", "m"), List.of(a), health, Set.of());
        assertNotNull(selected, "OPEN candidate whose cooldown elapsed must be reachable via the "
                + "authoritative allowCall recheck (no permanent capacity loss)");
        assertEquals(a, selected);
        // 权威状态迁移真实发生：HALF_OPEN 单探针放行（选中后唯一调用点）。
        assertEquals(CircuitState.HALF_OPEN, breaker.getState(a.getModelKey()),
                "the authoritative allowCall must be the sole state-transition call site");
    }

    @Test
    @Timeout(30)
    void openCooldownNotElapsedStillSkippedNoPhantomProbeSlot() {
        ModelClassCandidate a = candidate("p", "m");
        breaker.recordFailure(a.getModelKey()); // OPEN，冷却 60ms 未期满

        ModelClassCandidate selected = new DefaultSelectionStrategy()
                .select(request("p", "m"), List.of(a), health, Set.of());
        assertNull(selected, "OPEN candidate inside its cooldown must stay gated");
        // 纯读投影不占 probe 槽：状态不迁移（无幻影 HALF_OPEN）。
        assertEquals(CircuitState.OPEN, breaker.getState(a.getModelKey()),
                "health-view projection must not mutate the breaker (no phantom probe slot)");
    }

    @Test
    @Timeout(30)
    void halfOpenProbeInFlightGateRejectsSecondCaller() throws Exception {
        ModelClassCandidate a = candidate("p", "m");
        breaker.recordFailure(a.getModelKey());
        Thread.sleep(80);
        // 单探针：第一次权威 allowCall 占用 probe 槽（HALF_OPEN）。
        assertTrue(new DefaultSelectionStrategy()
                .select(request("p", "m"), List.of(a), health, Set.of()) != null);
        assertEquals(CircuitState.HALF_OPEN, breaker.getState(a.getModelKey()));

        // 探针在飞：第二个调用方必须被门控拒绝（单探针语义维持）。
        assertNull(new DefaultSelectionStrategy().select(request("p", "m"), List.of(a), health, Set.of()),
                "HALF_OPEN with a probe in flight must gate additional candidates (single-probe contract)");
    }

    @Test
    @Timeout(30)
    void attemptedCandidateReachableNextRequestAfterCooldown() throws Exception {
        // C3-005 半边：attempted 维持请求级生命周期（零新簿记）；跨请求可达性由
        // allowCall 冷却复查恢复——下一请求（新 attempted 集）经冷却复查可达。
        ModelClassCandidate a = candidate("p", "m");
        // 请求 1：attempted 含 a 且 OPEN 冷却未满 → 不可达。
        breaker.recordFailure(a.getModelKey());
        assertNull(new DefaultSelectionStrategy().select(request("p", "m"), List.of(a), health, Set.of(a)),
                "request 1: OPEN inside cooldown must be unreachable");

        Thread.sleep(80); // 冷却期满

        // 请求 2：新请求新 attempted 集 → 经冷却复查恢复可达（无 remove/revisit 簿记）。
        ModelClassCandidate selected = new DefaultSelectionStrategy()
                .select(request("p", "m"), List.of(a), health, Set.of());
        assertEquals(a, selected, "request 2: cooldown-elapsed candidate must be reachable again "
                + "(C3-005 half: requested-scoped attempted + allowCall recheck, zero bookkeeping)");
    }

    @Test
    @Timeout(30)
    void ruleScriptConsumesAllowCallScalarAndGatesFalse() throws Exception {
        RuleBasedSelectionStrategy strategy = new RuleBasedSelectionStrategy();
        IRuleManager ruleManager = new RuleManager();
        strategy.setRuleManager(ruleManager);
        strategy.setRuleName("rule-selection/allowcall");
        strategy.setRuleVersion(1L);

        ModelClassCandidate a = candidate("p", "m");

        // 腿 1（健康候选）：health[0].allowCall 投影 true → 规则命中 → 选中候选 0。
        // 红形态：health 视图无 allowCall 标量 → 表达式取值 false → 未命中 → null。
        ModelClassCandidate selected = strategy.select(request("p", "m"), List.of(a), health, Set.of());
        assertNotNull(selected, "rule script must be able to consume the health allowCall scalar "
        + "(5th projection scalar missing in the health view)");
        assertEquals(a, selected);

        // 腿 2（OPEN 冷却期满）：投影 true → 规则命中；权威 allowCall（选中后唯一调用点）
        // 放行 → HALF_OPEN 探针。
        breaker.recordFailure(a.getModelKey());
        Thread.sleep(80);
        selected = strategy.select(request("p", "m"), List.of(a), health, Set.of());
        assertNotNull(selected, "rule-selected candidate with elapsed cooldown must pass the "
                + "authoritative allowCall recheck");
        assertEquals(CircuitState.HALF_OPEN, breaker.getState(a.getModelKey()));

        // 腿 3（OPEN 冷却未满）：allowCall 投影 false → 规则未命中 → null（allowCall==false 被门控）。
        breaker.recordFailure(a.getModelKey()); // HALF_OPEN 探针失败 → 重新 OPEN（冷却重启）
        assertNull(strategy.select(request("p", "m"), List.of(a), health, Set.of()),
                "allowCall == false candidate must be gated in the rule script branch");
    }
}
