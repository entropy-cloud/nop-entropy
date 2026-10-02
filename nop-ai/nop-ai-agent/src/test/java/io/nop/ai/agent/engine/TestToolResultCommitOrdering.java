package io.nop.ai.agent.engine;

import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.hook.IHookRegistry;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2284 (WI3 invariant 2): tool results are committed IN-PLACE — inserted
 * at the end of the batch's contiguous tool_call/tool_output segment, before
 * any message a wait-phase wakeup appended after the tool calls. With no
 * wait-phase messages the ordering is byte-identical to the previous
 * tail-append behaviour (zero-regression assertion).
 */
public class TestToolResultCommitOrdering {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    static class StubToolManager implements IToolManager {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            io.nop.ai.toolkit.api.IToolExecuteContext context) {
            calls.incrementAndGet();
            AiToolCallResult r = new AiToolCallResult();
            r.setStatus("success");
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
            return CompletableFuture.completedFuture(new io.nop.ai.toolkit.model.AiToolCallsResponse());
        }

        @Override
        public List<io.nop.ai.toolkit.model.AiToolModel> listTools() {
            return List.of();
        }

        @Override
        public io.nop.ai.toolkit.model.AiToolModel loadTool(String toolName) {
            return null;
        }
    }

    private static AgentExecutionContext ctx() {
        return new AgentExecutionContext(new AgentModel());
    }

    private static ChatToolCall callOf(String id, String name) {
        ChatToolCall call = new ChatToolCall();
        call.setId(id);
        call.setName(name);
        call.setArguments(Map.of());
        return call;
    }

    private static ChatToolCallMessage callMessage(String id, String name) {
        return new ChatToolCallMessage(id, name, Map.of());
    }

    private static AgentToolDispatcher dispatcher(IHookRegistry registry) {
        AgentHookInvoker hookInvoker = new AgentHookInvoker(registry, null);
        StubToolManager tm = new StubToolManager();
        return new AgentToolDispatcher(tm, null, null, null, null, null, null,
                0, null, NoOpCheckpoint.noOp(), hookInvoker,
                new AgentToolPlanResolver(tm),
                new AgentSecurityConsultation(
                        new DefaultPostDenialGuard(),
                        new Slf4jAuditLogger(),
                        new DefaultToolAccessChecker(),
                        new AllowAllPermissionProvider(),
                        new DefaultPathAccessChecker(),
                        new DefaultSecurityLevelResolver(),
                        new DefaultPermissionMatrix(),
                        new DefaultApprovalGate(),
                        new DefaultDenialLedger(),
                        FailFastStrategy.failFast(),
                        new InMemoryWriteIntentRegistry(),
                        new AgentToolPlanResolver(tm),
                        hookInvoker));
    }

    private static List<String> roles(List<ChatMessage> messages) {
        List<String> tokens = new ArrayList<>();
        for (ChatMessage m : messages) {
            if (m instanceof ChatToolCallMessage) {
                tokens.add("call:" + ((ChatToolCallMessage) m).getCallId());
            } else if (m instanceof ChatToolResponseMessage) {
                tokens.add("result:" + ((ChatToolResponseMessage) m).getCallId());
            } else if (m instanceof ChatUserMessage) {
                tokens.add("user");
            } else if (m instanceof ChatAssistantMessage) {
                tokens.add("assistant");
            } else {
                tokens.add(m.getRole());
            }
        }
        return tokens;
    }

    private static void dispatch(AgentToolDispatcher dispatcher, AgentExecutionContext c,
                                 ChatToolCall... calls) {
        AgentToolExecuteContext execCtx = new AgentToolExecuteContext(null,
                java.util.Collections.emptyMap(), 0L, null, null, null, null, null,
                "s1", "agent", java.util.Set.of(), java.util.Set.of(), null, null,
                null, null, null);
        dispatcher.executeAllowedCalls(c, "agent", "s1", List.of(calls), execCtx, 0L, new int[]{0});
    }

    @Test
    void noWaitPhaseMessageKeepsLegacyTailAppendOrdering() {
        AgentExecutionContext c = ctx();
        c.addMessage(new ChatUserMessage("run tools"));
        c.addMessage(new ChatAssistantMessage(""));
        c.addMessage(callMessage("1", "read-file"));
        c.addMessage(callMessage("2", "read-file"));

        dispatch(dispatcher(new DefaultHookRegistry()), c, callOf("1", "read-file"),
                callOf("2", "read-file"));

        assertEquals(List.of("user", "assistant", "call:1", "call:2", "result:1", "result:2"),
                roles(c.getMessages()));
    }

    @Test
    void resultsInsertBeforeWaitPhaseMessage() {
        AgentExecutionContext c = ctx();
        c.addMessage(new ChatUserMessage("run tools"));
        c.addMessage(new ChatAssistantMessage(""));
        c.addMessage(callMessage("1", "read-file"));
        c.addMessage(callMessage("2", "read-file"));
        // message a wait-phase wakeup (WI4/WI5) appended after the tool calls
        c.addMessage(new ChatUserMessage("[steering] switch to plan B"));

        dispatch(dispatcher(new DefaultHookRegistry()), c, callOf("1", "read-file"),
                callOf("2", "read-file"));

        assertEquals(List.of("user", "assistant", "call:1", "call:2", "result:1", "result:2",
                "user"), roles(c.getMessages()));
    }

    @Test
    void laterResultJoinsContiguousSegmentAfterEarlierResult() {
        AgentExecutionContext c = ctx();
        c.addMessage(new ChatUserMessage("run tools"));
        c.addMessage(new ChatAssistantMessage(""));
        c.addMessage(callMessage("1", "read-file"));
        c.addMessage(callMessage("2", "read-file"));
        // call 2 already committed (as if it had completed first); the steering
        // wakeup message follows the committed result
        c.addMessage(new ChatToolResponseMessage("2", "read-file", "earlier result"));
        c.addMessage(new ChatUserMessage("[steering] switch to plan B"));

        dispatch(dispatcher(new DefaultHookRegistry()), c, callOf("1", "read-file"));

        assertEquals(List.of("user", "assistant", "call:1", "call:2", "result:2", "result:1",
                "user"), roles(c.getMessages()));
    }

    @Test
    void denyErrorResultInsideSegmentKeepsInsertionAfterBatchCalls() {
        AgentExecutionContext c = ctx();
        c.addMessage(new ChatUserMessage("run tools"));
        c.addMessage(new ChatAssistantMessage(""));
        c.addMessage(callMessage("1", "read-file"));
        // security-chain deny error result committed in-segment during dispatch
        c.addMessage(ChatToolResponseMessage.error("1", "read-file", "denied"));
        c.addMessage(callMessage("2", "read-file"));
        c.addMessage(new ChatUserMessage("[steering] switch to plan B"));

        dispatch(dispatcher(new DefaultHookRegistry()), c, callOf("2", "read-file"));

        assertEquals(List.of("user", "assistant", "call:1", "result:1", "call:2", "result:2",
                "user"), roles(c.getMessages()));
    }

    @Test
    void reenterResultFollowsSameInPlaceDiscipline() {
        IHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, new IAgentLifecycleHook() {
            @Override
            public HookResult onEvent(HookContext hookCtx) {
                return new HookResult.ReenterResult("re-enter requested");
            }
        });

        AgentExecutionContext c = ctx();
        c.addMessage(new ChatUserMessage("run tools"));
        c.addMessage(new ChatAssistantMessage(""));
        c.addMessage(callMessage("1", "read-file"));
        c.addMessage(new ChatUserMessage("[steering] switch to plan B"));

        dispatch(dispatcher(registry), c, callOf("1", "read-file"));

        List<String> tokens = roles(c.getMessages());
        // the re-enter synthetic result carries the real callId (AR-03) and is
        // inserted in-place, before the wait-phase message; the AR-03
        // iteration-level re-enter MARKER (a user message, not a tool result)
        // still lands at the tail — it belongs to the post-batch semantics,
        // not to the tool_result pairing
        assertEquals(List.of("user", "assistant", "call:1", "result:1", "user", "user"), tokens);
        ChatToolResponseMessage reenter = (ChatToolResponseMessage) c.getMessages().get(3);
        assertEquals("re-enter requested", reenter.getContent());
        ChatUserMessage marker = (ChatUserMessage) c.getMessages().get(5);
        assertEquals("[re-enter requested by lifecycle hook]", marker.getContent());
    }

    @Test
    void openCallSnapshotExcludesClosedPreviousTurnCalls() {
        AgentExecutionContext c = ctx();
        c.addMessage(new ChatUserMessage("turn 1"));
        c.addMessage(new ChatAssistantMessage(""));
        c.addMessage(callMessage("1", "read-file"));
        c.addMessage(new ChatToolResponseMessage("1", "read-file", "done"));
        // current turn's open batch
        c.addMessage(new ChatUserMessage("turn 2"));
        c.addMessage(new ChatAssistantMessage(""));
        c.addMessage(callMessage("2", "read-file"));

        dispatch(dispatcher(new DefaultHookRegistry()), c, callOf("2", "read-file"));

        assertEquals(List.of("user", "assistant", "call:1", "result:1", "user", "assistant",
                "call:2", "result:2"), roles(c.getMessages()));
    }
}
