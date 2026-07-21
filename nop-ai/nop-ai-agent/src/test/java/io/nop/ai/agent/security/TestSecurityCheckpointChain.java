package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestSecurityCheckpointChain {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void testChainAllowsWhenAllCheckpointsPass() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read-file");
        toolCall.setArguments(Map.of("path", "/tmp/test.txt"));

        SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                "test-session", "test-agent", toolCall, ctx, "/tmp", model);

        SecurityCheckpointChain chain = SecurityCheckpointChain.builder()
                .add(c -> SecurityCheckpoint.Decision.ALLOW)
                .add(c -> SecurityCheckpoint.Decision.ALLOW)
                .add(c -> SecurityCheckpoint.Decision.ALLOW)
                .build();

        SecurityCheckpoint.Decision result = chain.evaluate(checkCtx);
        assertEquals(SecurityCheckpoint.Decision.ALLOW, result);
    }

    @Test
    void testChainStopsOnFirstDeny() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("bash");
        toolCall.setArguments(Collections.emptyMap());

        SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                "test-session", "test-agent", toolCall, ctx, "/tmp", model);

        SecurityCheckpointChain chain = SecurityCheckpointChain.builder()
                .add(c -> SecurityCheckpoint.Decision.ALLOW)
                .add(c -> SecurityCheckpoint.Decision.DENY)
                .add(c -> {
                    throw new IllegalStateException("Should not reach this checkpoint after DENY");
                })
                .build();

        SecurityCheckpoint.Decision result = chain.evaluate(checkCtx);
        assertEquals(SecurityCheckpoint.Decision.DENY, result);
    }

    @Test
    void testChainStopsOnFirstDenyAndBreak() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("bash");
        toolCall.setArguments(Collections.emptyMap());

        SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                "test-session", "test-agent", toolCall, ctx, "/tmp", model);

        SecurityCheckpointChain chain = SecurityCheckpointChain.builder()
                .add(c -> SecurityCheckpoint.Decision.ALLOW)
                .add(c -> SecurityCheckpoint.Decision.DENY_AND_BREAK)
                .add(c -> {
                    throw new IllegalStateException("Should not reach after DENY_AND_BREAK");
                })
                .build();

        SecurityCheckpoint.Decision result = chain.evaluate(checkCtx);
        assertEquals(SecurityCheckpoint.Decision.DENY_AND_BREAK, result);
    }

    @Test
    void testEmptyChainThrows() {
        assertThrows(IllegalStateException.class,
                () -> SecurityCheckpointChain.builder().build());
    }

    @Test
    void testCheckContextDefaults() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read-file");

        SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                "session-1", "agent-1", toolCall, ctx, "/work", model);

        assertEquals("session-1", checkCtx.sessionId());
        assertEquals("agent-1", checkCtx.agentName());
        assertEquals("read-file", checkCtx.toolName());
        assertEquals("/work", checkCtx.fingerprintWorkDir());
        assertEquals(model, checkCtx.agentModel());
        assertEquals(toolCall, checkCtx.chatToolCall());
        assertEquals(ctx, checkCtx.executionContext());
        assertEquals(null, checkCtx.layer2ResolvedLevel());
    }

    @Test
    void testCheckContextWithResolvedLevel() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("bash");

        SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                "session-1", "agent-1", toolCall, ctx, "/work", model);

        SecurityCheckpoint.CheckContext withLevel = checkCtx.withResolvedLevel(SecurityLevel.RESTRICTED);
        assertEquals(SecurityLevel.RESTRICTED, withLevel.layer2ResolvedLevel());
        assertEquals(null, checkCtx.layer2ResolvedLevel());
    }
}
