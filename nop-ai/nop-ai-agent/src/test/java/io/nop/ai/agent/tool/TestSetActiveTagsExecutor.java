package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 296 (WS2): unit tests for {@link SetActiveTagsExecutor}.
 */
public class TestSetActiveTagsExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AiToolCall buildCall(String input) {
        AiToolCall call = new AiToolCall();
        call.setId(1);
        call.setToolName(SetActiveTagsExecutor.TOOL_NAME);
        call.setInput(input);
        return call;
    }

    private AgentToolExecuteContext buildContextWithSession(AgentSession session) {
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                null, null, 0L, null, null, null, null, null, "s1", "agent");
        ctx.setSession(session);
        return ctx;
    }

    @Test
    void setsTagsOnSession() {
        AgentSession session = AgentSession.create("s1", "agent");
        AgentToolExecuteContext ctx = buildContextWithSession(session);
        AiToolCall call = buildCall("{\"tags\":[\"admin\",\"channel:webui\"]}");

        // Verify the input was set correctly
        assertEquals("{\"tags\":[\"admin\",\"channel:webui\"]}", call.getInput());

        SetActiveTagsExecutor executor = new SetActiveTagsExecutor();
        CompletionStage<AiToolCallResult> stage = executor.executeAsync(call, ctx);
        AiToolCallResult result = stage.toCompletableFuture().join();

        String body = result.getOutput() != null ? result.getOutput().getBody() : "null";
        assertEquals("success", result.getStatus(), "result body=" + body);
        assertNotNull(session.getActiveTags(), "activeTags null; result body=" + body);
        assertTrue(session.getActiveTags().contains("admin"),
                "activeTags=" + session.getActiveTags() + "; result body=" + body);
        assertTrue(session.getActiveTags().contains("channel:webui"));
        assertEquals(2, session.getActiveTags().size());
    }

    @Test
    void emptyTagsClearsFilter() {
        AgentSession session = AgentSession.create("s1", "agent");
        session.setActiveTags(java.util.Set.of("readonly"));
        AgentToolExecuteContext ctx = buildContextWithSession(session);
        AiToolCall call = buildCall("{\"tags\":[]}");

        SetActiveTagsExecutor executor = new SetActiveTagsExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertNotNull(session.getActiveTags());
        assertTrue(session.getActiveTags().isEmpty());
    }

    @Test
    void failsWhenSessionIsNull() {
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                null, null, 0L, null, null, null, null, null, "s1", "agent");
        // session not set → null
        AiToolCall call = buildCall("{\"tags\":[\"admin\"]}");

        SetActiveTagsExecutor executor = new SetActiveTagsExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().join();

        // Honest error, not silent success
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("no AgentSession"));
    }

    @Test
    void getToolName() {
        assertEquals("set-active-tags", new SetActiveTagsExecutor().getToolName());
    }
}
