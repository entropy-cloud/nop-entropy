package io.nop.ai.core.routing;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleErrors;
import io.nop.rule.core.execute.RuleManager;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W5b (plan 2026-08-15-1116-1, Phase 3): RuleBasedSelectionStrategy 语义测试 +
 * 规则文件加载测试 + 接线验证（router 运行时调用规则策略）+ 端到端全链
 * （规则文件 → IRuleManager → 策略 → router → 选中候选下沉 ChatOptions）。
 *
 * <p>测试规则文件位于 {@code src/test/resources/_vfs/nop/rule/rule-selection/}，
 * 经 {@code resolve-rule:{ruleName}/v{version}} 加载（{@code RuleServiceHelper}
 * 实证路径 = {@code /nop/rule/{ruleName}/v{version}.rule.xml}）。
 *
 * <p>覆盖语义分支：① 未命中 → null；② 命中无输出 → null；③ 越界 / 命中已尝试
 * → fail-loud（{@code ERR_AI_AGENT_INVALID_ARG}）；输入契约（candidates 不含
 * accountKey、health 键为 Integer、请求属性正确传入）；平台硬约束（未声明输入 →
 * {@code ERR_RULE_UNKNOWN_INPUT_VAR}）；接线（router 运行时调用策略）与端到端全链。
 */
public class TestRuleBasedSelectionStrategy extends JunitBaseTestCase {

    private final ThresholdBreaker breaker = new ThresholdBreaker(3, 60_000L);
    private final ConcurrencyRegistry registry = new ConcurrencyRegistry();
    private final IModelClassHealth health = new CandidateHealthProvider(breaker, registry);

    private static ModelClassCandidate candidate(String provider, String model, String accountKey) {
        return new ModelClassCandidate(provider, model, accountKey, null, null);
    }

    private static List<ModelClassCandidate> twoCandidates() {
        return List.of(candidate("test-accounts", "deepseek-v4", null),
                candidate("test-accounts", "deepseek-v4", "key-backup-1"));
    }

    private RuleBasedSelectionStrategy strategy(String ruleName) {
        RuleBasedSelectionStrategy strategy = new RuleBasedSelectionStrategy();
        strategy.setRuleManager(new RuleManager());
        strategy.setRuleName(ruleName);
        strategy.setRuleVersion(1L);
        return strategy;
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
    void computedInputRuleSelectsIndexedCandidate() {
        // computed 输入派生辅助变量（契约 D2 模式②）：preferredProvider =
        // candidates[1].provider → 命中 → selectedIndex 1。
        ModelClassCandidate selected = strategy("rule-selection/preferred")
                .select(request("test-accounts", "deepseek-v4"), twoCandidates(), health, Set.of());
        assertNotNull(selected, "computed-input rule must select candidate index 1");
        assertEquals("key-backup-1", selected.getAccountKey(),
                "selected candidate must be the one at index 1 of the candidate list");
    }

    @Test
    void healthBasedComputedRuleSelectsIndexedCandidate() {
        // computed 输入消费健康视图（契约 D2 模式②）：secondAvailable =
        // health[1].available → 命中 → selectedIndex 1（证明 health 输入被规则消费）。
        ModelClassCandidate selected = strategy("rule-selection/computed")
                .select(request("test-accounts", "deepseek-v4"), twoCandidates(), health, Set.of());
        assertNotNull(selected, "computed-input rule must select candidate index 1");
        assertEquals("key-backup-1", selected.getAccountKey());
    }

    @Test
    void ruleNotMatchedReturnsNull() {
        // 语义分支①：规则未命中（isRuleMatch()==false）→ null（调用方 fail-loud）。
        ModelClassCandidate selected = strategy("rule-selection/nomatch")
                .select(request("test-accounts", "deepseek-v4"), twoCandidates(), health, Set.of());
        assertNull(selected, "no-match rule must return null (caller fail-loud)");
    }

    @Test
    void matchedWithoutOutputReturnsNull() {
        // 语义分支②：命中但输出 map 无 selectedIndex → null（与未命中同语义）。
        ModelClassCandidate selected = strategy("rule-selection/noselect")
                .select(request("test-accounts", "deepseek-v4"), twoCandidates(), health, Set.of());
        assertNull(selected, "matched-without-output rule must return null");
    }

    @Test
    void outOfBoundsFailsLoud() {
        // 语义分支③：越界 → fail-loud（规则配置错误，不静默回退默认策略）。
        NopException e = assertThrows(NopException.class,
                () -> strategy("rule-selection/outofbounds")
                        .select(request("test-accounts", "deepseek-v4"), twoCandidates(), health, Set.of()),
                "out-of-bounds selectedIndex must fail loud");
        assertEquals(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    @Test
    void attemptedHitFailsLoud() {
        // 已尝试集语义：selectedIndex 命中已尝试候选 → fail-loud（接口契约"已尝试不得重复返回"）。
        Set<ModelClassCandidate> attempted = new LinkedHashSet<>();
        attempted.add(twoCandidates().get(0));
        NopException e = assertThrows(NopException.class,
                () -> strategy("rule-selection/attempted")
                        .select(request("test-accounts", "deepseek-v4"), twoCandidates(), health, attempted),
                "re-selecting an attempted candidate must fail loud");
        assertEquals(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    @Test
    void inputsFollowContractAndExcludeAccountKey() {
        // 输入契约断言：candidates 不含 accountKey/apiKey、health 键为 Integer、
        // attempted = 已尝试候选 index 集、请求属性正确传入。
        CapturingRuleManager capturing = new CapturingRuleManager();
        RuleBasedSelectionStrategy strategy = new RuleBasedSelectionStrategy();
        strategy.setRuleManager(capturing);
        strategy.setRuleName("rule-selection/preferred");
        strategy.setRuleVersion(1L);

        List<ModelClassCandidate> candidates = twoCandidates();
        Set<ModelClassCandidate> attempted = new LinkedHashSet<>();
        attempted.add(candidates.get(0));
        strategy.select(request("test-accounts", "deepseek-v4"), candidates, health, attempted);

        Map<String, Object> inputs = capturing.lastInputs;
        assertNotNull(inputs, "strategy must set rule inputs");
        assertEquals("test-accounts", inputs.get(RuleBasedSelectionStrategy.INPUT_PROVIDER));
        assertEquals("deepseek-v4", inputs.get(RuleBasedSelectionStrategy.INPUT_MODEL));

        List<?> candidateList = (List<?>) inputs.get(RuleBasedSelectionStrategy.INPUT_CANDIDATES);
        assertEquals(2, candidateList.size());
        for (Object item : candidateList) {
            Map<?, ?> itemMap = (Map<?, ?>) item;
            assertFalse(itemMap.containsKey("accountKey"), "candidate input must NOT contain accountKey (apiKey secret)");
            assertFalse(itemMap.containsKey("apiKey"), "candidate input must NOT contain apiKey");
            assertTrue(itemMap.containsKey("index"));
            assertTrue(itemMap.containsKey("provider"));
            assertTrue(itemMap.containsKey("model"));
            assertTrue(itemMap.containsKey("accountBaseUrl"));
            assertTrue(itemMap.containsKey("concurrencyLimit"));
        }
        Map<?, ?> candidate0 = (Map<?, ?>) candidateList.get(0);
        assertEquals(0, ((Integer) candidate0.get("index")).intValue());

        Map<?, ?> healthView = (Map<?, ?>) inputs.get(RuleBasedSelectionStrategy.INPUT_HEALTH);
        assertEquals(2, healthView.size());
        for (Object key : healthView.keySet()) {
            assertTrue(key instanceof Integer, "health map keys must be Integer candidate indexes");
        }
        Map<?, ?> health0 = (Map<?, ?>) healthView.get(0);
        assertEquals(CircuitState.CLOSED.name(), health0.get("circuitState"));
        assertEquals(0, ((Integer) health0.get("currentConcurrency")).intValue());
        assertTrue((Boolean) health0.get("available"));

        List<?> attemptedIndexes = (List<?>) inputs.get(RuleBasedSelectionStrategy.INPUT_ATTEMPTED);
        assertEquals(List.of(0), attemptedIndexes, "attempted input must be the indexes of attempted candidates");
    }

    @Test
    void requestWithoutOptionsPassesNullModelAndProvider() {
        // 请求 options == null（接口契约允许）→ model/provider 输入为 null（不得 mandatory）。
        CapturingRuleManager capturing = new CapturingRuleManager();
        RuleBasedSelectionStrategy strategy = new RuleBasedSelectionStrategy();
        strategy.setRuleManager(capturing);
        strategy.setRuleName("rule-selection/preferred");
        strategy.setRuleVersion(1L);

        strategy.select(new ChatRequest(), twoCandidates(), health, Set.of());

        assertNull(capturing.lastInputs.get(RuleBasedSelectionStrategy.INPUT_MODEL),
                "model input must be null when request.options is null");
        assertNull(capturing.lastInputs.get(RuleBasedSelectionStrategy.INPUT_PROVIDER),
                "provider input must be null when request.options is null");
    }

    @Test
    void undeclaredInputFailsWithPlatformError() {
        // 平台硬约束：规则文件漏声明契约输入（本规则只声明 model）→ 运行期
        // ERR_RULE_UNKNOWN_INPUT_VAR（策略契约输入集与规则声明一致由平台强制）。
        NopException e = assertThrows(NopException.class,
                () -> strategy("rule-selection/missing-input")
                        .select(request("test-accounts", "deepseek-v4"), twoCandidates(), health, Set.of()),
                "undeclared contract input must fail at runtime with the platform error code");
        assertEquals(RuleErrors.ERR_RULE_UNKNOWN_INPUT_VAR.getErrorCode(), e.getErrorCode());
    }

    @Test
    void nullRuleManagerFailsFast() {
        // ruleManager 未注入（ioc:optional 解析为 null）：首用显式 fail-fast，不静默回退。
        RuleBasedSelectionStrategy strategy = new RuleBasedSelectionStrategy();
        strategy.setRuleName("rule-selection/preferred");
        NopException e = assertThrows(NopException.class,
                () -> strategy.select(request("test-accounts", "deepseek-v4"), twoCandidates(), health, Set.of()),
                "unwired ruleManager must fail fast on first use");
        assertEquals(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    @Test
    void nullHealthViewFailsFast() {
        // 健康视图缺失 = 调用方编程错误：显式 fail-fast（与默认策略契约一致）。
        assertThrows(NopAiCoreException.class,
                () -> strategy("rule-selection/preferred")
                        .select(request("test-accounts", "deepseek-v4"), twoCandidates(), null, Set.of()));
    }

    @Test
    void routerInvokesRuleStrategyAtRuntime() {
        // 接线验证（Minimum Rules #23）：router 运行时调用规则策略——默认策略按声明序
        // 会选中候选 0（主账号，accountKey null），规则策略选中候选 1（backup-1）——
        // 断言选中结果 = 规则决策即证明策略在调用链上被真实消费。
        ChatOptions options = new ChatOptions();
        options.setModel("deepseek-v4");
        options.setProvider("test-accounts");
        ModelClassRouter router = ModelClassRouter.forRequest(new ChatRequest(), options,
                strategy("rule-selection/preferred"), breaker, registry);

        ModelClassCandidate selected = router.selectNext();
        assertNotNull(selected);
        assertEquals("key-backup-1", selected.getAccountKey(),
                "router must surface the rule strategy's decision (backup-1, not the default first candidate)");
        assertEquals(1, router.getAttempted().size(),
                "selected candidate must be recorded in the router's attempted set");
    }

    @Test
    void endToEndRuleToRouterToChatOptions() {
        // 端到端全链（Minimum Rules #22）：规则文件（_vfs/nop/rule/）→ IRuleManager 加载
        // → 策略 select → ModelClassRouter（构造注入规则策略 + 候选集）→ 选中候选下沉
        // ChatOptions 四字段（provider/model/accountKey/accountBaseUrl）→ 与规则选择一致。
        ChatOptions options = new ChatOptions();
        options.setModel("deepseek-v4");
        options.setProvider("test-accounts");
        ModelClassRouter router = ModelClassRouter.forRequest(new ChatRequest(), options,
                strategy("rule-selection/preferred"), breaker, registry);

        assertTrue(router.hasRoutingGroup());
        assertEquals("tier-v4", router.getModelClassId());

        ModelClassCandidate selected = router.selectNext();
        assertNotNull(selected);
        assertEquals("test-accounts", selected.getProvider());
        assertEquals("deepseek-v4", selected.getModel());

        ChatOptions sunk = router.toChatOptions(options, selected);
        assertEquals("test-accounts", sunk.getProvider(), "sunk provider must match the rule-selected candidate");
        assertEquals("deepseek-v4", sunk.getModel(), "sunk model must match the rule-selected candidate");
        assertEquals("key-backup-1", sunk.getAccountKey(),
                "sunk accountKey must be the rule-selected backup account apiKey");
        assertEquals("https://backup1.example.com", sunk.getAccountBaseUrl(),
                "sunk accountBaseUrl must be the rule-selected backup account baseUrl");
        assertEquals("key-backup-1", selected.getAccountKey());
        assertSame(selected, router.getAttempted().stream().findFirst().orElse(null),
                "router attempted set must contain the selected candidate");
    }

    /**
     * 记录型 IRuleManager：委托真实 {@link RuleManager} 并在 getRule 时捕获最近一次
     * select 调用设置的规则输入（setInputs 先于 getRule，捕获时点正确）。
     */
    static class CapturingRuleManager implements IRuleManager {
        final IRuleManager delegate = new RuleManager();
        Map<String, Object> lastInputs;

        @Override
        public IRuleRuntime newRuleRuntime(IServiceContext svcCtx, IEvalScope scope) {
            return delegate.newRuleRuntime(svcCtx, scope);
        }

        @Override
        public IRuleRuntime newRuleRuntime() {
            return delegate.newRuleRuntime();
        }

        @Override
        public IExecutableRule getRule(String ruleName, Long ruleVersion) {
            IExecutableRule real = delegate.getRule(ruleName, ruleVersion);
            return ruleRt -> {
                lastInputs = ruleRt.getInputs();
                return real.execute(ruleRt);
            };
        }

        @Override
        public IExecutableRule loadRuleFromPath(String path) {
            return delegate.loadRuleFromPath(path);
        }

        @Override
        public io.nop.rule.core.model.RuleModel getRuleModel(String ruleName, Long ruleVersion) {
            return delegate.getRuleModel(ruleName, ruleVersion);
        }

        @Override
        public io.nop.rule.core.model.RuleModel loadRuleModelFromPath(String path) {
            return delegate.loadRuleModelFromPath(path);
        }
    }
}
