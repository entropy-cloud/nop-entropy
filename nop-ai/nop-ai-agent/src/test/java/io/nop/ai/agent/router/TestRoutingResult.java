package io.nop.ai.agent.router;

import io.nop.ai.api.chat.ChatOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestRoutingResult {

    @Test
    void constructionWithAllFields() {
        ChatOptions options = new ChatOptions();
        options.setModel("gpt-4");

        RoutingResult result = new RoutingResult(options, "complex", "smart-router");

        assertSame(options, result.getOptions());
        assertEquals("complex", result.getComplexity());
        assertEquals("smart-router", result.getRoutingReason());
    }

    @Test
    void constructionWithNullableFields() {
        ChatOptions options = new ChatOptions();

        RoutingResult result = new RoutingResult(options, null, null);

        assertSame(options, result.getOptions());
        assertNull(result.getComplexity());
        assertNull(result.getRoutingReason());
    }

    @Test
    void nullOptionsThrows() {
        assertThrows(NullPointerException.class, () -> new RoutingResult(null, null, null));
    }

    @Test
    void optionsReferencePreserved() {
        ChatOptions options = new ChatOptions();
        options.setProvider("openai");
        options.setModel("gpt-4o");
        options.setTemperature(0.7f);

        RoutingResult result = new RoutingResult(options, null, "pass-through");

        assertNotNull(result.getOptions());
        assertEquals("openai", result.getOptions().getProvider());
        assertEquals("gpt-4o", result.getOptions().getModel());
        assertEquals(Float.valueOf(0.7f), result.getOptions().getTemperature());
    }

    @Test
    void toStringContainsFields() {
        ChatOptions options = new ChatOptions();
        options.setModel("test-model");

        RoutingResult result = new RoutingResult(options, "simple", "test-reason");

        String str = result.toString();
        assertNotNull(str);
    }

    @Test
    void equalityWithSameOptions() {
        ChatOptions opts = new ChatOptions();
        opts.setModel("gpt-4");

        RoutingResult a = new RoutingResult(opts, "complex", "reason-a");
        RoutingResult b = new RoutingResult(opts, "complex", "reason-a");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
