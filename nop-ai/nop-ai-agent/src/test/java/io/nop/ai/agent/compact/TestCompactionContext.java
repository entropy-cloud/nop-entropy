package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCompactionContext {

    @Test
    void constructionWithAllFields() {
        List<ChatMessage> messages = Arrays.asList(
                new ChatUserMessage("hello"),
                new ChatUserMessage("world")
        );
        CompactConfig config = new CompactConfig(1000, "truncation", true);
        AgentModel model = new AgentModel();
        AgentExecutionContext execCtx = AgentExecutionContext.create(model, "session-1");

        CompactionContext ctx = new CompactionContext(
                messages, config, "session-1", "test-agent", execCtx
        );

        assertEquals(2, ctx.getMessages().size());
        assertEquals(config, ctx.getCompactConfig());
        assertEquals("session-1", ctx.getSessionId());
        assertEquals("test-agent", ctx.getAgentName());
        assertEquals(execCtx, ctx.getExecutionContext());
    }

    @Test
    void messagesAreImmutable() {
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(new ChatUserMessage("hello"));

        CompactionContext ctx = new CompactionContext(
                messages, null, "s1", "a1", null
        );

        assertThrows(UnsupportedOperationException.class, () ->
                ctx.getMessages().add(new ChatUserMessage("world"))
        );
    }

    @Test
    void messagesReflectSnapshotAtConstruction() {
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(new ChatUserMessage("hello"));

        CompactionContext ctx = new CompactionContext(
                messages, null, "s1", "a1", null
        );

        messages.add(new ChatUserMessage("world"));

        assertEquals(1, ctx.getMessages().size());
    }

    @Test
    void nullMessagesThrowsNPE() {
        assertThrows(NullPointerException.class, () ->
                new CompactionContext(null, null, "s1", "a1", null)
        );
    }

    @Test
    void emptyMessagesAllowed() {
        CompactionContext ctx = new CompactionContext(
                Collections.emptyList(), null, "s1", "a1", null
        );
        assertNotNull(ctx.getMessages());
        assertTrue(ctx.getMessages().isEmpty());
    }

    @Test
    void nullConfigAndOptionalFieldsAllowed() {
        CompactionContext ctx = new CompactionContext(
                Collections.emptyList(), null, null, null, null
        );
        assertEquals(0, ctx.getMessages().size());
    }
}
