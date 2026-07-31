package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiMessage;
import io.nop.ai.core.api.messages.AiUserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestAiChatSession {

    @Test
    public void testSessionLifecycle() throws Exception {
        DefaultAiChatSession session = new DefaultAiChatSession();
        session.setSessionId("sid-1");
        assertEquals("sid-1", session.getSessionId());

        AiChatOptions options = new AiChatOptions();
        options.setProvider("deepseek");
        session.setChatOptions(options);
        assertSame(options, session.getChatOptions());

        AiUserMessage msg1 = new AiUserMessage("hello");
        msg1.setMessageId("m1");
        AiUserMessage msg2 = new AiUserMessage("world");
        msg2.setMessageId("m2");

        session.addMessage(msg1);
        session.addMessages(List.of(msg2));

        List<AiMessage> history = session.getActiveHistoryMessages();
        assertEquals(2, history.size());
        assertEquals("m1", history.get(0).getMessageId());
        assertEquals("m2", history.get(1).getMessageId());

        session.disableMessages(List.of("m1"));
        assertEquals(1, session.getActiveHistoryMessages().size());
        assertEquals("m2", session.getActiveHistoryMessages().get(0).getMessageId());

        session.close();
    }

    @Test
    public void testEmptySessionReturnsEmptyHistory() {
        DefaultAiChatSession session = new DefaultAiChatSession();
        assertNull(session.getSessionId());
        assertNotNull(session.getActiveHistoryMessages());
        assertEquals(0, session.getActiveHistoryMessages().size());
    }

    @Test
    public void testDisableUnknownMessageKeepsHistory() {
        DefaultAiChatSession session = new DefaultAiChatSession();
        AiUserMessage msg = new AiUserMessage("hello");
        msg.setMessageId("m1");
        session.addMessage(msg);

        session.disableMessages(List.of("not-exist"));
        assertEquals(1, session.getActiveHistoryMessages().size());
    }
}
