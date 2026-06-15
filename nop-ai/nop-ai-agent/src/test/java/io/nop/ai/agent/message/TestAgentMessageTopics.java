package io.nop.ai.agent.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.nop.ai.agent.engine.NopAiAgentException;

public class TestAgentMessageTopics {

    @Test
    void inboxTopicProducesExpectedFormat() {
        assertEquals("agent.sess-1.inbox", AgentMessageTopics.inboxTopic("sess-1"));
    }

    @Test
    void replyTopicProducesExpectedFormat() {
        assertEquals("agent.sess-1.reply", AgentMessageTopics.replyTopic("sess-1"));
    }

    @Test
    void replyTopicDoesNotEmbedCorrelationId() {
        String reply = AgentMessageTopics.replyTopic("sess-1");
        assertFalse(reply.contains("corr"), "reply topic must not embed any correlation marker: " + reply);
        assertFalse(reply.matches(".*\\.\\d+\\..*"), "reply topic must be session-scoped, not correlation-scoped: " + reply);
    }

    @Test
    void broadcastTopicProducesExpectedFormat() {
        assertEquals("agent.broadcast.team-1", AgentMessageTopics.broadcastTopic("team-1"));
    }

    @Test
    void topicNamespaceIsAgentPrefixed() {
        assertTrue(AgentMessageTopics.inboxTopic("s").startsWith(AgentMessageTopics.NAMESPACE_PREFIX));
        assertTrue(AgentMessageTopics.replyTopic("s").startsWith(AgentMessageTopics.NAMESPACE_PREFIX));
        assertTrue(AgentMessageTopics.broadcastTopic("s").startsWith(AgentMessageTopics.NAMESPACE_PREFIX));
    }

    @Test
    void topicDoesNotUsePlatformPrefixes() {
        String inbox = AgentMessageTopics.inboxTopic("s");
        String reply = AgentMessageTopics.replyTopic("s");
        String broadcast = AgentMessageTopics.broadcastTopic("s");
        assertFalse(inbox.startsWith("bro-"));
        assertFalse(reply.startsWith("reply-"));
        assertFalse(broadcast.startsWith("bro-"));
        assertFalse(broadcast.startsWith("bat-"));
    }

    @Test
    void inboxTopicRejectsNullOrEmptySessionId() {
        assertThrows(NopAiAgentException.class, () -> AgentMessageTopics.inboxTopic(null));
        assertThrows(NopAiAgentException.class, () -> AgentMessageTopics.inboxTopic(""));
    }

    @Test
    void replyTopicRejectsNullOrEmptySessionId() {
        assertThrows(NopAiAgentException.class, () -> AgentMessageTopics.replyTopic(null));
        assertThrows(NopAiAgentException.class, () -> AgentMessageTopics.replyTopic(""));
    }

    @Test
    void broadcastTopicRejectsNullOrEmptyScope() {
        assertThrows(NopAiAgentException.class, () -> AgentMessageTopics.broadcastTopic(null));
        assertThrows(NopAiAgentException.class, () -> AgentMessageTopics.broadcastTopic(""));
    }
}
