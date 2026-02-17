package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.messages.ChatUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatUsage 测试 - 包含 Prompt Caching 字段
 */
public class TestChatUsage {

    @Test
    public void testBasicUsage() {
        ChatUsage usage = new ChatUsage(100, 50);
        
        assertEquals(100, usage.getPromptTokens().intValue());
        assertEquals(50, usage.getCompletionTokens().intValue());
        assertEquals(150, usage.getTotalTokens().intValue());
    }

    @Test
    public void testPromptCachingFields() {
        ChatUsage usage = new ChatUsage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setTotalTokens(150);
        usage.setCacheHitTokens(80);
        usage.setCacheCreationTokens(20);
        
        assertEquals(100, usage.getPromptTokens().intValue());
        assertEquals(50, usage.getCompletionTokens().intValue());
        assertEquals(150, usage.getTotalTokens().intValue());
        assertEquals(80, usage.getCacheHitTokens().intValue());
        assertEquals(20, usage.getCacheCreationTokens().intValue());
    }

    @Test
    public void testCopy() {
        ChatUsage original = new ChatUsage(100, 50);
        original.setCacheHitTokens(80);
        original.setCacheCreationTokens(20);
        
        ChatUsage copy = original.copy();
        
        assertNotSame(original, copy);
        assertEquals(100, copy.getPromptTokens().intValue());
        assertEquals(50, copy.getCompletionTokens().intValue());
        assertEquals(150, copy.getTotalTokens().intValue());
        assertEquals(80, copy.getCacheHitTokens().intValue());
        assertEquals(20, copy.getCacheCreationTokens().intValue());
        
        original.setCacheHitTokens(0);
        assertEquals(80, copy.getCacheHitTokens().intValue());
    }

    @Test
    public void testNullPromptCachingFields() {
        ChatUsage usage = new ChatUsage(100, 50);
        
        assertNull(usage.getCacheHitTokens());
        assertNull(usage.getCacheCreationTokens());
    }
}
