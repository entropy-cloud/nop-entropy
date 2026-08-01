package io.nop.ai.api.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestChatOptions {

    @Test
    public void testDefaultValues() {
        ChatOptions options = new ChatOptions();
        assertNull(options.getProvider());
        assertNull(options.getModel());
        assertNull(options.getTemperature());
        assertNull(options.getMaxTokens());
    }

    @Test
    public void testSetterGetter() {
        ChatOptions options = new ChatOptions();
        options.setProvider("openai");
        options.setModel("gpt-4");
        options.setTemperature(0.7f);
        options.setMaxTokens(4096);
        assertEquals("openai", options.getProvider());
        assertEquals("gpt-4", options.getModel());
        assertEquals(0.7f, options.getTemperature());
        assertEquals(4096, options.getMaxTokens().intValue());
    }

    @Test
    public void testCopyConstructor() {
        ChatOptions original = new ChatOptions();
        original.setProvider("claude");
        original.setMaxTokens(8192);
        ChatOptions copy = original.copy();
        assertEquals("claude", copy.getProvider());
        assertEquals(8192, copy.getMaxTokens().intValue());
    }

    // ========================================================================
    // 账号回退链字段（plan 2026-08-01-1505-1，设计 §3.6）：
    // copy()/merge()/Builder 必须携带 accountKey/accountBaseUrl，否则重试循环切换账号时
    // 静默丢账号（Rule #11 陷阱）。
    // ========================================================================

    @Test
    public void testCopyPreservesAccountFields() {
        ChatOptions original = new ChatOptions();
        original.setProvider("openai");
        original.setAccountKey("sk-backup-key");
        original.setAccountBaseUrl("https://backup.example.com");

        ChatOptions copy = original.copy();

        assertEquals("sk-backup-key", copy.getAccountKey(),
                "copy() must carry accountKey (otherwise retry-loop routedOptions reassign loses it)");
        assertEquals("https://backup.example.com", copy.getAccountBaseUrl(),
                "copy() must carry accountBaseUrl");
    }

    @Test
    public void testMergeOverridesAccountFields() {
        ChatOptions base = new ChatOptions();
        base.setProvider("openai");
        base.setAccountKey("sk-primary");

        ChatOptions override = new ChatOptions();
        override.setAccountKey("sk-backup");
        override.setAccountBaseUrl("https://backup.example.com");

        ChatOptions merged = base.merge(override);

        assertEquals("sk-backup", merged.getAccountKey(),
                "merge() must let other's accountKey override (account switch on FALLBACK)");
        assertEquals("https://backup.example.com", merged.getAccountBaseUrl());
    }

    @Test
    public void testMergePreservesBaseAccountKeyWhenOtherNull() {
        ChatOptions base = new ChatOptions();
        base.setAccountKey("sk-primary");

        ChatOptions other = new ChatOptions();
        other.setModel("gpt-4");

        ChatOptions merged = base.merge(other);

        assertEquals("sk-primary", merged.getAccountKey(),
                "merge() must keep base accountKey when other does not specify one");
    }

    @Test
    public void testBuilderSetsAccountFields() {
        ChatOptions options = ChatOptions.builder()
                .provider("openai")
                .model("gpt-4")
                .accountKey("sk-builder-key")
                .accountBaseUrl("https://builder.example.com")
                .build();

        assertEquals("sk-builder-key", options.getAccountKey());
        assertEquals("https://builder.example.com", options.getAccountBaseUrl());
    }

    @Test
    public void testAccountFieldsDefaultNull() {
        ChatOptions options = new ChatOptions();
        assertNull(options.getAccountKey(), "default accountKey is null (zero-regression: ChatServiceImpl falls back to resolveApiKey)");
        assertNull(options.getAccountBaseUrl());
    }
}
