package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentConstraintsModel;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.ChatOptionsModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAgentExecutionContext {

    @Test
    public void testDefaultConstructor() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = new AgentExecutionContext(model);

        assertNotNull(ctx.getMessages());
        assertTrue(ctx.getMessages().isEmpty());
        assertEquals(AgentExecStatus.pending, ctx.getStatus());
        assertEquals(10, ctx.getMaxIterations());
        assertEquals(0, ctx.getCurrentIteration());
        assertEquals(0, ctx.getTokensUsed());
        assertNotNull(ctx.getMetadata());
        assertTrue(ctx.getMetadata().isEmpty());
        assertNull(ctx.getLastError());
        assertNull(ctx.getPlan());
        assertNull(ctx.getSessionId());
        assertNull(ctx.getChatOptionsModel());
    }

    @Test
    public void testFieldReadWrite() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = new AgentExecutionContext(model);

        ctx.setStatus(AgentExecStatus.running);
        assertEquals(AgentExecStatus.running, ctx.getStatus());

        ctx.setCurrentIteration(5);
        assertEquals(5, ctx.getCurrentIteration());

        ctx.setTokensUsed(1000L);
        assertEquals(1000L, ctx.getTokensUsed());

        ctx.setLastError("something went wrong");
        assertEquals("something went wrong", ctx.getLastError());

        ctx.setSessionId("session-123");
        assertEquals("session-123", ctx.getSessionId());

        ctx.setMaxIterations(20);
        assertEquals(20, ctx.getMaxIterations());
    }

    @Test
    public void testMessagesMutability() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = new AgentExecutionContext(model);

        ChatMessage msg1 = new ChatUserMessage("hello");
        ChatMessage msg2 = new ChatUserMessage("world");

        ctx.addMessage(msg1);
        ctx.addMessage(msg2);

        assertEquals(2, ctx.getMessages().size());
        assertEquals("hello", ctx.getMessages().get(0).getContent());
        assertEquals("world", ctx.getMessages().get(1).getContent());
    }

    @Test
    public void testFactoryMethodWithNullConstraints() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, null);

        assertEquals(10, ctx.getMaxIterations());
        assertNull(ctx.getSessionId());
        assertNull(ctx.getChatOptionsModel());
    }

    @Test
    public void testFactoryMethodWithSessionId() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        assertEquals("test-session", ctx.getSessionId());
        assertEquals(10, ctx.getMaxIterations());
    }

    @Test
    public void testFactoryMethodExtractsMaxIterations() {
        AgentModel model = new AgentModel();
        AgentConstraintsModel constraints = new AgentConstraintsModel();
        constraints.setMaxIterations(25);
        model.setConstraints(constraints);

        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test");

        assertEquals(25, ctx.getMaxIterations());
    }

    @Test
    public void testFactoryMethodExtractsChatOptions() {
        AgentModel model = new AgentModel();
        ChatOptionsModel chatOptions = new ChatOptionsModel();
        model.setChatOptions(chatOptions);

        AgentConstraintsModel constraints = new AgentConstraintsModel();
        constraints.setMaxIterations(15);
        model.setConstraints(constraints);

        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");

        assertNotNull(ctx.getChatOptionsModel());
        assertEquals(15, ctx.getMaxIterations());
        assertEquals("session-1", ctx.getSessionId());
    }

    @Test
    public void testFactoryMethodWithNullMaxIterations() {
        AgentModel model = new AgentModel();
        AgentConstraintsModel constraints = new AgentConstraintsModel();
        constraints.setMaxIterations(null);
        model.setConstraints(constraints);

        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test");

        assertEquals(10, ctx.getMaxIterations());
    }
}
