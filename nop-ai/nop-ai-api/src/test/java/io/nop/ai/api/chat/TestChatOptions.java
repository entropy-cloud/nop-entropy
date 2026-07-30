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
}
