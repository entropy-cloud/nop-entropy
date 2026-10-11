package io.nop.ai.api.chat;

import io.nop.ai.api.chat.messages.ChatToolDefinition;
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
    // P1-6.7-006：maxContextTokens（独立上下文窗口声明）必须随 copy()/merge()/Builder
    // 全链携带，否则 routedOptions 复制/合并时静默丢窗口声明，退回缺省 128000。
    // ========================================================================

    @Test
    public void testMaxContextTokensIsIndependentOfMaxTokens() {
        ChatOptions options = new ChatOptions();
        options.setMaxTokens(4096);
        assertNull(options.getMaxContextTokens(),
                "maxContextTokens must default to null (resolved to the engine default 128000), independent of maxTokens");
        options.setMaxContextTokens(1000000);
        assertEquals(4096, options.getMaxTokens().intValue());
        assertEquals(1000000, options.getMaxContextTokens().intValue());
    }

    @Test
    public void testCopyPreservesMaxContextTokens() {
        ChatOptions original = new ChatOptions();
        original.setMaxContextTokens(200000);
        ChatOptions copy = original.copy();
        assertEquals(200000, copy.getMaxContextTokens().intValue(),
                "copy() must carry maxContextTokens");
    }

    @Test
    public void testMergeOverridesMaxContextTokens() {
        ChatOptions base = new ChatOptions();
        base.setMaxContextTokens(128000);
        ChatOptions override = new ChatOptions();
        override.setMaxContextTokens(8192);
        ChatOptions merged = base.merge(override);
        assertEquals(8192, merged.getMaxContextTokens().intValue(),
                "merge() must let other's maxContextTokens override");

        ChatOptions nullOverride = new ChatOptions();
        nullOverride.setModel("gpt-4");
        assertEquals(128000, base.merge(nullOverride).getMaxContextTokens().intValue(),
                "null maxContextTokens in other must not overwrite base");
    }

    @Test
    public void testBuilderSetsMaxContextTokens() {
        ChatOptions options = ChatOptions.builder()
                .model("gpt-4")
                .maxTokens(4096)
                .maxContextTokens(64000)
                .build();
        assertEquals(64000, options.getMaxContextTokens().intValue());
        assertEquals(4096, options.getMaxTokens().intValue());
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

    // ========================================================================
    // P2-ROUND4-CALL-PATH Phase 4：merge() 列表字段覆盖语义（裁定 A）。
    // javadoc 承诺"非null值会覆盖"，router 逐跳 copy().merge(tierOptions) 时 stop/tools
    // 应为后者覆盖而非重复累加。
    // ========================================================================

    @Test
    public void testMergeStopListOverridesInsteadOfAppending() {
        ChatOptions base = new ChatOptions();
        base.setStop(java.util.Arrays.asList("A", "B"));

        ChatOptions override = new ChatOptions();
        override.setStop(java.util.Arrays.asList("X"));

        ChatOptions merged = base.merge(override);

        assertEquals(java.util.Arrays.asList("X"), merged.getStop(),
                "merge must OVERRIDE the stop list (javadoc: non-null overrides), not append");
    }

    @Test
    public void testMergeToolsListOverridesInsteadOfAppending() {
        ChatOptions base = new ChatOptions();
        base.setTools(java.util.Arrays.asList(ChatToolDefinition.of("tool_a", "a")));

        ChatOptions override = new ChatOptions();
        override.setTools(java.util.Arrays.asList(ChatToolDefinition.of("tool_b", "b")));

        ChatOptions merged = base.merge(override);

        assertNotNull(merged.getTools());
        assertEquals(1, merged.getTools().size(), "tools must be overridden, not concatenated");
        assertEquals("tool_b", merged.getTools().get(0).getName());
    }

    @Test
    public void testMergeNullListFieldDoesNotOverride() {
        ChatOptions base = new ChatOptions();
        base.setStop(java.util.Arrays.asList("A"));

        ChatOptions override = new ChatOptions();
        override.setModel("gpt-4");
        override.setStop(null); // 未设置 = 不覆盖

        ChatOptions merged = base.merge(override);

        assertEquals(java.util.Arrays.asList("A"), merged.getStop(),
                "null stop in other must NOT overwrite base's stop list");
        assertEquals("gpt-4", merged.getModel());
    }

    @Test
    public void testMergeScalarOverrideSemanticsUnchanged() {
        ChatOptions base = new ChatOptions();
        base.setTemperature(0.7f);
        base.setStop(java.util.Arrays.asList("A"));

        ChatOptions override = new ChatOptions();
        override.setTemperature(1.0f);

        ChatOptions merged = base.merge(override);

        assertEquals(1.0f, merged.getTemperature(), 0.001f, "scalar fields keep overwrite semantics");
        assertEquals(java.util.Arrays.asList("A"), merged.getStop(),
                "null list field in other leaves base list untouched");
    }
}
