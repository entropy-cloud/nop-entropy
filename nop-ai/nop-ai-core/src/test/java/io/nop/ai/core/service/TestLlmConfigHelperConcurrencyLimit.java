package io.nop.ai.core.service;

import io.nop.ai.core.model.LlmAccountModel;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 2026-08-15-0604-3 Phase 2: {@code LlmConfigHelper.resolveConcurrencyLimit} 并发上限读取
 * 五态矩阵 + 主账号路径（Minimum Rules #23 接线 + #24 无静默跳过 + #25 新功能必有测试）。
 *
 * <p>层级语义（helper javadoc 为行为契约权威）：账号级显式配置（含 0/负数）→ 账号值不回退；
 * 账号未配置 → 回退 provider 级缺省（根元素 concurrencyLimit）；provider 级也未配置 → null = 不限制；
 * 主账号（无 {@link LlmAccountModel} 实例，account=null）→ 取 provider 级缺省。
 */
public class TestLlmConfigHelperConcurrencyLimit extends JunitBaseTestCase {

    private LlmAccountModel account(String provider, String id) {
        List<LlmAccountModel> chain = LlmConfigHelper.resolveAccountChain(provider);
        for (LlmAccountModel a : chain) {
            if (id.equals(a.getId())) {
                return a;
            }
        }
        throw new AssertionError("no account with id=" + id + " in provider " + provider);
    }

    @Test
    void providerDefaultReadsRootElement() {
        // provider 级缺省 = 根元素 concurrencyLimit="10"（从配置模型取值，非 stub）。
        assertEquals(Integer.valueOf(10), LlmConfigHelper.resolveConcurrencyLimit("test-accounts"));
    }

    @Test
    void accountLevelOverrideWins() {
        // 账号级覆盖生效：配置了账号级值 → 返回账号值（不回退 provider 缺省）。
        assertEquals(Integer.valueOf(3),
                LlmConfigHelper.resolveConcurrencyLimit("test-accounts", account("test-accounts", "backup-2")));
    }

    @Test
    void accountWithoutLimitFallsBackToProviderDefault() {
        // 账号未配置 → 回退 provider 级缺省（10）。
        assertEquals(Integer.valueOf(10),
                LlmConfigHelper.resolveConcurrencyLimit("test-accounts", account("test-accounts", "backup-1")));
    }

    @Test
    void explicitZeroIsExplicitlyUnlimitedNoFallback() {
        // 显式配置 0 = 显式不限制（不回退 provider 缺省 10）——"不限制"是显式契约，非静默忽略。
        assertEquals(Integer.valueOf(0),
                LlmConfigHelper.resolveConcurrencyLimit("test-accounts", account("test-accounts", "backup-3")));
    }

    @Test
    void explicitNegativeIsExplicitlyUnlimitedNoFallback() {
        // 显式配置负数 = 显式不限制（不回退 null 缺省）。
        assertEquals(Integer.valueOf(-1),
                LlmConfigHelper.resolveConcurrencyLimit("test-concurrency-misc",
                        account("test-concurrency-misc", "misc-2")));
    }

    @Test
    void unconfiguredEverywhereIsUnlimited() {
        // 均未配置 = 不限制：provider 缺省 null + 账号解析 null。
        assertNull(LlmConfigHelper.resolveConcurrencyLimit("test-concurrency-misc"),
                "provider without concurrencyLimit → null = unlimited");
        assertNull(LlmConfigHelper.resolveConcurrencyLimit("test-concurrency-misc",
                        account("test-concurrency-misc", "misc-1")),
                "account without concurrencyLimit + provider without default → null = unlimited");
    }

    @Test
    void mainAccountPathUsesProviderDefault() {
        // 主账号（无 LlmAccountModel 实例，account=null）→ 取 provider 级缺省。
        assertEquals(Integer.valueOf(10), LlmConfigHelper.resolveConcurrencyLimit("test-accounts", null),
                "main account falls back to provider-level default");
        assertNull(LlmConfigHelper.resolveConcurrencyLimit("test-concurrency-misc", null),
                "main account with provider default absent → null = unlimited");
    }

    @Test
    void accountModelCarriesParsedConcurrencyLimit() {
        // 接线验证：账号级值确实来自配置模型（LlmAccountModel.getConcurrencyLimit），非 helper 内硬编码。
        assertEquals(Integer.valueOf(3), account("test-accounts", "backup-2").getConcurrencyLimit());
        assertEquals(Integer.valueOf(0), account("test-accounts", "backup-3").getConcurrencyLimit());
        assertNull(account("test-accounts", "backup-1").getConcurrencyLimit());
    }
}
