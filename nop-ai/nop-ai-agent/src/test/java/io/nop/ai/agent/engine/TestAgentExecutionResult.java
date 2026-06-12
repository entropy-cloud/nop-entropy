package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentConstraintsModel;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAgentExecutionResult {

    @Test
    public void testFromContextBasicFields() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "s1");
        ctx.setStatus(AgentExecStatus.completed);
        ctx.setCurrentIteration(3);
        ctx.setTokensUsed(500L);
        ctx.addMessage(new ChatUserMessage("hello"));

        AgentExecutionResult result = AgentExecutionResult.fromContext(ctx);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(3, result.getTotalIterations());
        assertEquals(500L, result.getTotalTokensUsed());
        assertNull(result.getFinalMessage());
        assertNull(result.getError());
        assertTrue(result.getDurationMs() >= 0);
    }

    @Test
    public void testFromContextWithError() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "s2");
        ctx.setStatus(AgentExecStatus.failed);
        ctx.setLastError("timeout");

        AgentExecutionResult result = AgentExecutionResult.fromContext(ctx);

        assertEquals(AgentExecStatus.failed, result.getStatus());
        assertEquals("timeout", result.getError());
    }

    @Test
    public void testMessagesAreDefensiveCopy() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "s3");
        ctx.addMessage(new ChatUserMessage("msg1"));

        AgentExecutionResult result = AgentExecutionResult.fromContext(ctx);

        assertEquals(1, result.getMessages().size());
        assertEquals("msg1", result.getMessages().get(0).getContent());

        ctx.addMessage(new ChatUserMessage("msg2"));
        assertEquals(1, result.getMessages().size());
    }

    @Test
    public void testResultMessagesAreImmutable() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "s4");
        ctx.addMessage(new ChatUserMessage("msg1"));

        AgentExecutionResult result = AgentExecutionResult.fromContext(ctx);

        assertThrows(UnsupportedOperationException.class, () ->
                result.getMessages().add(new ChatUserMessage("msg2")));
    }

    @Test
    public void testFromContextWithEmptyMessages() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "s5");

        AgentExecutionResult result = AgentExecutionResult.fromContext(ctx);

        assertNotNull(result.getMessages());
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    public void testFromContextPreservesIterationAndTokens() {
        AgentModel model = new AgentModel();
        AgentConstraintsModel constraints = new AgentConstraintsModel();
        constraints.setMaxIterations(50);
        model.setConstraints(constraints);

        AgentExecutionContext ctx = AgentExecutionContext.create(model, "s6");
        ctx.setCurrentIteration(42);
        ctx.setTokensUsed(9999L);

        AgentExecutionResult result = AgentExecutionResult.fromContext(ctx);

        assertEquals(42, result.getTotalIterations());
        assertEquals(9999L, result.getTotalTokensUsed());
    }

    @Test
    public void testDirectConstructor() {
        List<ChatMessage> msgs = List.of(new ChatUserMessage("a"), new ChatUserMessage("b"));
        AgentExecutionResult result = new AgentExecutionResult(
                AgentExecStatus.completed, "done", msgs, 5, 1000L, 300L, null);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals("done", result.getFinalMessage());
        assertEquals(2, result.getMessages().size());
        assertEquals(5, result.getTotalIterations());
        assertEquals(1000L, result.getTotalTokensUsed());
        assertEquals(300L, result.getDurationMs());
        assertNull(result.getError());
    }
}
