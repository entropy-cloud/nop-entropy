package io.nop.ai.core.response;

import io.nop.ai.core.api.messages.AiChatExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAiChatResponse {
    @Test
    public void testParseBlock() {
        AiChatExchange exchange = new AiChatExchange();
        exchange.setContent("<A>test</A>");
        String data = exchange.getBlock("<A>", "</A>", false, false);
        assertEquals("test", data);
    }
}
