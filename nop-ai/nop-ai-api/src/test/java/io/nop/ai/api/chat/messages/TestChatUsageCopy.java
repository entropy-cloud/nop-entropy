package io.nop.ai.api.chat.messages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P2-ROUND4-CALL-PATH Phase 2 回归：{@link ChatUsage#copy()} 对 null token 字段 null-safe，
 * 且显式保留 provider 上报的 {@code totalTokens}（可能含缓存 token，prompt+completion ≠ total），
 * 不再经构造器重算。下游 {@code ChatResponse.copy()} 对含 null token 的合法 usage 不再 NPE。
 */
public class TestChatUsageCopy {

    @Test
    public void copy_nullTokenFieldsIsNullSafeAndPreservesNulls() {
        // 生产形态：parseUsage 的 getIntByPath 对缺失路径返回 null。
        ChatUsage usage = new ChatUsage();
        usage.setPromptTokens(null);
        usage.setCompletionTokens(null);
        usage.setTotalTokens(42);

        ChatUsage copy = usage.copy();

        assertNotSame(usage, copy);
        assertNull(copy.getPromptTokens(), "null promptTokens must stay null (no NPE, no zero-fill)");
        assertNull(copy.getCompletionTokens(), "null completionTokens must stay null");
        assertEquals(42, copy.getTotalTokens(), "provider-reported totalTokens must be preserved");
    }

    @Test
    public void copy_constructorWithNullTokensDoesNotNpe() {
        ChatUsage usage = new ChatUsage(null, null);
        assertNull(usage.getPromptTokens());
        assertNull(usage.getCompletionTokens());
        assertNull(usage.getTotalTokens(), "two null fields → totalTokens stays null (not recomputed)");
    }

    @Test
    public void copy_preservesProviderReportedTotalTokensWithCache() {
        // 缓存 token 场景：promptTokens + completionTokens != totalTokens（totalTokens 含缓存统计）。
        ChatUsage usage = new ChatUsage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setTotalTokens(500); // 含 350 缓存 token，不得被重算覆盖
        usage.setCacheHitTokens(300);
        usage.setCacheCreationTokens(50);
        usage.setCacheMissTokens(100);

        ChatUsage copy = usage.copy();

        assertEquals(100, copy.getPromptTokens().intValue());
        assertEquals(50, copy.getCompletionTokens().intValue());
        assertEquals(500, copy.getTotalTokens().intValue(),
                "totalTokens must be copied verbatim, NOT recomputed as prompt+completion");
        assertEquals(300, copy.getCacheHitTokens().intValue());
        assertEquals(50, copy.getCacheCreationTokens().intValue());
        assertEquals(100, copy.getCacheMissTokens().intValue());
    }

    @Test
    public void copy_fullFieldEqualityForNonNullValues() {
        ChatUsage usage = new ChatUsage(10, 20);
        usage.setCacheHitTokens(5);

        ChatUsage copy = usage.copy();

        assertEquals(10, copy.getPromptTokens().intValue());
        assertEquals(20, copy.getCompletionTokens().intValue());
        assertEquals(30, copy.getTotalTokens().intValue());
        assertEquals(5, copy.getCacheHitTokens().intValue());
    }
}