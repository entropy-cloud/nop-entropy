package io.nop.ai.core.routing;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-15-0849-2 Phase 4 (ROUTE-04): ModelClassRouter 候选集游走 + 全池饱和 fail-loud
 * + provider 链扩展 + 接线验证 + 端到端全链（Minimum Rules #22/#23/#24/#25）。
 *
 * <p>饱和语义：并发饱和与健康度饱和共用 {@code ERR_AI_MODEL_CLASS_SATURATED}
 * （语义 = "当前无可用候选"）；主动路径不跨 provider 链；被动路径类内耗尽后经
 * {@code resolveFailoverChain} 扩展（primary = 请求目标 provider）。
 */
public class TestModelClassRouter extends JunitBaseTestCase {

    private final ThresholdBreaker breaker = new ThresholdBreaker(3, 60_000L);
    private final ConcurrencyRegistry registry = new ConcurrencyRegistry();

    private ModelClassRouter router(String model, String provider) {
        ChatOptions options = new ChatOptions();
        options.setModel(model);
        options.setProvider(provider);
        return ModelClassRouter.forRequest(new ChatRequest(), options, new DefaultSelectionStrategy(),
                breaker, registry);
    }

    private void tripOpen(String modelKey) {
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure(modelKey);
        }
    }

    private void saturateTierSat() {
        // 全候选并发饱和：主账号 limit 2 ×2、backup-s1 limit 1 ×1、backup-s2 limit 1 ×1。
        registry.acquire("test-sat", null);
        registry.acquire("test-sat", null);
        registry.acquire("test-sat", "key-s1");
        registry.acquire("test-sat", "key-s2");
    }

    @Test
    void inClassWalkSelectsByDeclarationOrder() {
        ModelClassRouter router = router("deepseek-v4", "test-accounts");
        assertTrue(router.hasRoutingGroup());
        assertEquals("tier-v4", router.getModelClassId());

        // 类内游走全链：首个可用 = test-accounts 主账号（声明序 + 主账号在前）。
        ModelClassCandidate c0 = router.selectNext();
        assertNotNull(c0);
        assertEquals("test-accounts", c0.getProvider());
        assertEquals("deepseek-v4", c0.getModel());
        assertNull(c0.getAccountKey(), "main account = null accountKey");
        assertEquals(1, router.getAttempted().size());

        // 候选信息可下沉为 ChatOptions 四字段（provider/model/accountKey/accountBaseUrl）。
        ChatOptions options = new ChatOptions();
        options.setModel("deepseek-v4");
        options.setProvider("test-accounts");
        ChatOptions sunk = router.toChatOptions(options, c0);
        assertEquals("test-accounts", sunk.getProvider());
        assertEquals("deepseek-v4", sunk.getModel());
        assertNull(sunk.getAccountKey(), "main account sinks null accountKey (credential-chain semantics)");
        assertNull(sunk.getAccountBaseUrl(), "main account sinks null accountBaseUrl (provider root baseUrl)");
    }

    @Test
    void noRoutingGroupIsZeroRegression() {
        // 请求 model 未归属任何类 = 无路由组（零回归：调用方走既有单 provider 行为）。
        ModelClassRouter router = router("no-such-model", "test-accounts");
        assertFalse(router.hasRoutingGroup());
        assertNull(router.getModelClassId());
        assertTrue(router.getInClassCandidates().isEmpty());
    }

    @Test
    void selectNextWithoutRoutingGroupFailsFast() {
        ModelClassRouter router = router("no-such-model", "test-accounts");
        NopException e = assertThrows(NopException.class, router::selectNext,
                "router misuse (no routing group) must fail fast, not silently return");
        assertEquals(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    @Test
    void activePathConcurrencySaturationFailsLoud() {
        // 主动路径：类内并发饱和 → fail-loud（不跨 provider 链）。
        ModelClassRouter router = router("sat-model", "test-sat");
        saturateTierSat();
        NopException e = assertThrows(NopException.class, router::selectNext);
        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(), e.getErrorCode());
        assertEquals("tier-sat", e.getParam(NopAiCoreErrors.ARG_MODEL_CLASS),
                "saturation error carries the triggering model class");
    }

    @Test
    void activePathHealthSaturationFailsLoud() {
        // 主动路径：类内健康度饱和（全候选熔断 OPEN）→ fail-loud。
        ModelClassRouter router = router("deepseek-v4", "test-accounts");
        tripOpen("test-accounts:deepseek-v4");
        tripOpen("test-accounts:deepseek-v4-max");
        tripOpen("test-modelclass:test-model-1");

        NopException e = assertThrows(NopException.class, router::selectNext);
        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(), e.getErrorCode());
    }

    @Test
    void passivePathExtendsViaProviderChain() {
        // 被动路径：类内全饱和 → 经 provider 链（test-sat → test-failover）扩展 → 选中扩展候选。
        ModelClassRouter router = router("sat-model", "test-sat");
        saturateTierSat();

        ModelClassCandidate extended = router.selectNextAfterFailure();
        assertNotNull(extended);
        assertEquals("test-failover", extended.getProvider(),
                "passive path must extend via resolveFailoverChain(primary=test-sat)");
        assertEquals("failover-model", extended.getModel(),
                "provider-chain candidate model = failover provider defaultModel");
        assertNull(extended.getAccountKey(), "provider-chain candidate = target provider main account");
    }

    @Test
    void passivePathChainExhaustedFailsLoud() {
        // 被动路径：类内 + provider 链扩展全饱和 → fail-loud（链耗尽 = 全池饱和）。
        ModelClassRouter router = router("sat-model", "test-sat");
        saturateTierSat();
        registry.acquire("test-failover", null);
        registry.acquire("test-failover", null);
        // 扩展候选（test-failover 主账号，limit 2）也已饱和 → 全池饱和 fail-loud。
        NopException e = assertThrows(NopException.class, router::selectNextAfterFailure);
        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(), e.getErrorCode());
    }

    @Test
    void attemptedCandidatesAreNotReselectedOnPassivePath() {
        // 被动路径失败重选：本轮已尝试候选被策略跳过（"失败标记"入参语义）。
        ModelClassRouter router = router("deepseek-v4", "test-accounts");
        ModelClassCandidate c0 = router.selectNext();
        ModelClassCandidate c1 = router.selectNextAfterFailure();
        assertNotNull(c0);
        assertNotNull(c1);
        assertFalse(c0.equals(c1), "re-selection must not return the already-attempted candidate");
        assertEquals(2, router.getAttempted().size());
    }

    @Test
    void routerInvokesStrategyAndReadsRegistryAtRuntime() {
        // 接线验证（Minimum Rules #23）：router 在运行时调用策略（记录调用）+ 经健康视图读注册表
        // （预置计数使主账号候选饱和 → 策略跳过它返回 backup-s1，证明注册表状态被读取）。
        final int[] strategyCalls = {0};
        ISelectionStrategy recording = (request, candidates, health, attempted) -> {
            strategyCalls[0]++;
            assertNotNull(health, "router must pass a non-null health view");
            for (ModelClassCandidate c : candidates) {
                if (attempted.contains(c) || !health.healthOf(c).isAvailable()) {
                    continue;
                }
                return c;
            }
            return null;
        };

        ChatOptions options = new ChatOptions();
        options.setModel("sat-model");
        options.setProvider("test-sat");
        ModelClassRouter router = ModelClassRouter.forRequest(new ChatRequest(), options, recording, breaker, registry);

        registry.acquire("test-sat", null);
        registry.acquire("test-sat", null);
        ModelClassCandidate selected = router.selectNext();

        assertTrue(strategyCalls[0] >= 1, "router must invoke the selection strategy at runtime");
        assertNotNull(selected);
        assertEquals("key-s1", selected.getAccountKey(),
                "main account saturated in registry → strategy skipped it → backup-s1 selected "
                        + "(proves the router's health pipeline reads the registry)");
    }

    @Test
    void endToEndFullChainFromConfigToSaturatedFailLoud() {
        // 端到端（Minimum Rules #22）：配置（model-class + accounts + provider 链）→ 解析 →
        // model 归属 → 游走 → 策略选中 → 候选下沉四字段 → 全池饱和 fail-loud，从配置入口到最终决策出口完整走通。
        ChatOptions options = new ChatOptions();
        options.setModel("deepseek-v4");
        options.setProvider("test-accounts");
        ModelClassRouter router = ModelClassRouter.forRequest(new ChatRequest(), options,
                new DefaultSelectionStrategy(), breaker, registry);

        // 解析 + 归属：模型类就位。
        assertTrue(router.hasRoutingGroup());
        assertEquals("tier-v4", router.getModelClassId());

        // 游走 + 策略选中：首个候选 = 类内声明序首位（test-accounts 主账号）。
        ModelClassCandidate c0 = router.selectNext();
        assertNotNull(c0);
        assertEquals("test-accounts", c0.getProvider());
        assertEquals("deepseek-v4", c0.getModel());

        // 候选下沉：四字段语义（accountKey/accountBaseUrl null = 主账号 + provider 根 baseUrl）。
        ChatOptions sunk = router.toChatOptions(options, c0);
        assertEquals("test-accounts", sunk.getProvider());
        assertEquals("deepseek-v4", sunk.getModel());
        assertNull(sunk.getAccountKey());
        assertNull(sunk.getAccountBaseUrl());

        // 全池饱和：类内（test-accounts 两 modelKey + test-modelclass）+ provider 链扩展
        // （test-sat 全候选 + test-failover 主账号）全部不可用 → fail-loud。
        tripOpen("test-accounts:deepseek-v4");
        tripOpen("test-accounts:deepseek-v4-max");
        tripOpen("test-modelclass:test-model-1");
        saturateTierSat();
        registry.acquire("test-failover", null);
        registry.acquire("test-failover", null);

        NopException e = assertThrows(NopException.class, router::selectNextAfterFailure);
        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(), e.getErrorCode());
        assertEquals("tier-v4", e.getParam(NopAiCoreErrors.ARG_MODEL_CLASS));
    }

    @Test
    void sinkDownPreservesOriginalOptions() {
        // 下沉不丢失原 options 其他字段（复制语义）。
        ModelClassRouter router = router("sat-model", "test-sat");
        ChatOptions original = new ChatOptions();
        original.setModel("sat-model");
        original.setProvider("test-sat");
        original.setTemperature(0.7f);
        original.setMaxTokens(123);

        ModelClassCandidate c = router.selectNext();
        ChatOptions sunk = router.toChatOptions(original, c);
        assertEquals(Float.valueOf(0.7f), sunk.getTemperature());
        assertEquals(Integer.valueOf(123), sunk.getMaxTokens());
        assertEquals(c.getProvider(), sunk.getProvider());
        assertEquals(c.getModel(), sunk.getModel());
    }

    @Test
    void failoverChainExpansionIsReusable() {
        // resolveProviderChainCandidates 独立可复用（接线层：router 扩展候选来源）。
        List<ModelClassCandidate> chain = LlmConfigHelper.resolveProviderChainCandidates("test-sat");
        assertEquals(1, chain.size(), "test-sat → [test-failover]，主账号 + 空账号链 = 1 候选");
        ModelClassCandidate c = chain.get(0);
        assertEquals("test-failover", c.getProvider());
        assertEquals("failover-model", c.getModel());
        assertNull(c.getAccountKey());
    }
}
