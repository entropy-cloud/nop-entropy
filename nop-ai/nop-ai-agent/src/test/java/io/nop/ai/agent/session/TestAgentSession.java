package io.nop.ai.agent.session;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAgentSession {

    @Test
    void testCreateFactoryMethod() {
        AgentSession session = AgentSession.create("sess-1", "my-agent");

        assertEquals("sess-1", session.getSessionId());
        assertEquals("my-agent", session.getAgentName());
        assertEquals(AgentExecStatus.pending, session.getStatus());
        assertEquals(0, session.getMessageCount());
        assertEquals(0, session.getTotalTokensUsed());
        assertEquals(0, session.getTotalIterations());
        assertNotNull(session.getMetadata());
        assertTrue(session.getCreatedAt() > 0);
        assertTrue(session.getUpdatedAt() > 0);
    }

    @Test
    void testAppendMessages() {
        AgentSession session = AgentSession.create("sess-1", "agent");

        ChatUserMessage userMsg = new ChatUserMessage("hello");
        ChatAssistantMessage assistantMsg = new ChatAssistantMessage();
        assistantMsg.setContent("hi");

        session.appendMessages(List.of(userMsg, assistantMsg));

        assertEquals(2, session.getMessageCount());
        assertEquals("hello", session.getMessages().get(0).getContent());
        assertEquals("hi", session.getMessages().get(1).getContent());
    }

    @Test
    void testGetMessagesReturnsImmutableCopy() {
        AgentSession session = AgentSession.create("sess-1", "agent");
        session.appendMessages(List.of(new ChatUserMessage("hello")));

        List<ChatMessage> messages = session.getMessages();
        assertEquals(1, messages.size());

        assertThrows(UnsupportedOperationException.class, () ->
                messages.add(new ChatUserMessage("should fail")));
    }

    @Test
    void testTokensAndIterationsAccumulate() {
        AgentSession session = AgentSession.create("sess-1", "agent");

        session.addTokensUsed(100);
        assertEquals(100, session.getTotalTokensUsed());

        session.addTokensUsed(50);
        assertEquals(150, session.getTotalTokensUsed());

        session.addIterations(3);
        assertEquals(3, session.getTotalIterations());

        session.addIterations(2);
        assertEquals(5, session.getTotalIterations());
    }

    @Test
    void testMetadataOperations() {
        AgentSession session = AgentSession.create("sess-1", "agent");

        session.getMetadata().put("key1", "value1");
        assertEquals("value1", session.getMetadata().get("key1"));

        session.setMetadata(Map.of("key2", "value2"));
        assertEquals("value2", session.getMetadata().get("key2"));
        assertEquals(1, session.getMetadata().size());
    }

    @Test
    void testStatusTransition() {
        AgentSession session = AgentSession.create("sess-1", "agent");
        assertEquals(AgentExecStatus.pending, session.getStatus());

        session.setStatus(AgentExecStatus.running);
        assertEquals(AgentExecStatus.running, session.getStatus());

        session.setStatus(AgentExecStatus.completed);
        assertEquals(AgentExecStatus.completed, session.getStatus());
    }

    @Test
    void testTouchUpdatesTimestamp() throws InterruptedException {
        AgentSession session = AgentSession.create("sess-1", "agent");
        long before = session.getUpdatedAt();

        Thread.sleep(10);
        session.touch();

        assertTrue(session.getUpdatedAt() >= before);
    }

    @Test
    void testNewFieldsDefaultToNull() {
        AgentSession session = AgentSession.create("sess-1", "agent");

        assertNull(session.getParentSessionId());
        assertNull(session.getPlanId());
        assertNull(session.getCompactedAt());
    }

    @Test
    void testParentSessionIdRoundTrip() {
        AgentSession session = AgentSession.create("sess-1", "agent");

        session.setParentSessionId("parent-1");
        assertEquals("parent-1", session.getParentSessionId());
    }

    @Test
    void testPlanIdRoundTrip() {
        AgentSession session = AgentSession.create("sess-1", "agent");

        session.setPlanId("plan-42");
        assertEquals("plan-42", session.getPlanId());
    }

    @Test
    void testCompactedAtRoundTrip() {
        AgentSession session = AgentSession.create("sess-1", "agent");

        Long ts = System.currentTimeMillis();
        session.setCompactedAt(ts);
        assertEquals(ts, session.getCompactedAt());
    }

    @Test
    void testMarkCompacted() throws InterruptedException {
        AgentSession session = AgentSession.create("sess-1", "agent");
        long before = session.getUpdatedAt();

        Thread.sleep(10);
        session.markCompacted();

        assertNotNull(session.getCompactedAt());
        assertTrue(session.getCompactedAt() >= before);
        assertTrue(session.getUpdatedAt() >= before);
    }
}
