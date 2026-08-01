package io.nop.ai.core.service;

import io.nop.ai.core.model.LlmAccountModel;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-01-1505-1 Phase 2: {@code LlmConfigHelper.resolveAccountChain} 有序账号链解析
 * （Minimum Rules #25 — 新功能必有测试）。
 *
 * <p>验证：N 账号链按声明顺序解析出 N 个不同 apiKey + baseUrl 覆盖 + 额度元数据；
 * 无 {@code <accounts>} 配置的 provider 退回空列表（零回归，非 null）。
 */
public class TestLlmConfigHelperAccountChain extends JunitBaseTestCase {

    @Test
    void resolveAccountChainReturnsOrderedAccounts() {
        List<LlmAccountModel> chain = LlmConfigHelper.resolveAccountChain("test-accounts");

        assertNotNull(chain);
        assertEquals(3, chain.size(), "3 <account> entries → 3-element chain");

        // 声明顺序保留（first-match-wins / 有序链的基础不变量）。
        LlmAccountModel a0 = chain.get(0);
        LlmAccountModel a1 = chain.get(1);
        LlmAccountModel a2 = chain.get(2);

        assertEquals("backup-1", a0.getId());
        assertEquals("key-backup-1", a0.getApiKey());
        assertEquals("https://backup1.example.com", a0.getBaseUrl(),
                "per-account baseUrl override must be parsed");

        assertEquals("backup-2", a1.getId());
        assertEquals("key-backup-2", a1.getApiKey());
        assertEquals(null, a1.getBaseUrl(), "absent baseUrl override stays null");

        assertEquals("backup-3", a2.getId());
        assertEquals("key-backup-3", a2.getApiKey());
        assertEquals("https://backup3.example.com", a2.getBaseUrl());
        assertEquals(Long.valueOf(1_000_000L), a2.getQuotaLimit(),
                "diagnostic quota metadata must be parsed");
        assertEquals("2026-09-01", a2.getRenewAt());

        // 三个 apiKey 互不相同（链游走切换的前提）。
        assertTrue(!a0.getApiKey().equals(a1.getApiKey())
                        && !a1.getApiKey().equals(a2.getApiKey()),
                "chain accounts must have distinct apiKey values");
    }

    @Test
    void resolveAccountChainEmptyWhenNoAccountsConfigured() {
        // deepseek 是真实的 provider 配置但无 <accounts> → 空列表（零回归，非 null）。
        List<LlmAccountModel> chain = LlmConfigHelper.resolveAccountChain("deepseek");

        assertNotNull(chain, "no <accounts> config must return non-null empty list (not null)");
        assertTrue(chain.isEmpty(), "deepseek has no <accounts> → empty chain");
    }

    @Test
    void resolveAccountChainReturnsUnmodifiableView() {
        List<LlmAccountModel> chain = LlmConfigHelper.resolveAccountChain("test-accounts");
        // 防御性拷贝：调用方拿到的快照不可变，后续 config 变更不影响已解析链。
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> chain.clear(),
                "resolved chain must be an unmodifiable snapshot");
    }
}
