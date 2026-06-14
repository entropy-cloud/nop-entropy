package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Phase 1 tests: channel/principal field propagation.
 *
 * <p>Verifies three layers:
 * <ol>
 *   <li>{@link AgentMessageRequest} carries channelKind/principal via the new
 *       constructor overload; existing constructors delegate to null.</li>
 *   <li>{@link AgentExecutionContext} exposes channelKind/principal as mutable
 *       fields defaulting to null.</li>
 *   <li>{@code DefaultAgentEngine.doExecute} actually propagates the request's
 *       channel/principal into the context handed to the executor (wiring
 *       verification, not just field existence).</li>
 * </ol>
 */
public class TestChannelPrincipalPropagation {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // AgentMessageRequest: channel/principal fields + constructor delegation
    // ========================================================================

    @Test
    void newConstructorCarriesChannelAndPrincipal() {
        Principal principal = Principal.user();
        AgentMessageRequest req = new AgentMessageRequest(
                "agent", "hi", "sess", Map.of("k", "v"), ChannelKind.GROUP, principal);

        assertEquals(ChannelKind.GROUP, req.getChannelKind());
        assertSame(principal, req.getPrincipal());
    }

    @Test
    void legacyFourArgConstructorDefaultsChannelAndPrincipalToNull() {
        AgentMessageRequest req = new AgentMessageRequest("agent", "hi", "sess", new HashMap<>());

        assertNull(req.getChannelKind(), "legacy 4-arg constructor must default channelKind to null");
        assertNull(req.getPrincipal(), "legacy 4-arg constructor must default principal to null");
    }

    @Test
    void legacyTwoArgConstructorDefaultsChannelAndPrincipalToNull() {
        AgentMessageRequest req = new AgentMessageRequest("agent", "hi");

        assertNull(req.getChannelKind());
        assertNull(req.getPrincipal());
    }

    @Test
    void newConstructorAcceptsNullChannelAndPrincipal() {
        AgentMessageRequest req = new AgentMessageRequest("agent", "hi", "sess", null, null, null);

        assertNull(req.getChannelKind());
        assertNull(req.getPrincipal());
    }

    // ========================================================================
    // AgentExecutionContext: mutable fields, default null
    // ========================================================================

    @Test
    void contextChannelAndPrincipalDefaultNull() {
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "sess");

        assertNull(ctx.getChannelKind(), "channelKind must default to null (unknown channel)");
        assertNull(ctx.getPrincipal(), "principal must default to null (anonymous identity)");
    }

    @Test
    void contextChannelAndPrincipalAreSettable() {
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "sess");
        Principal principal = Principal.operator();

        ctx.setChannelKind(ChannelKind.API);
        ctx.setPrincipal(principal);

        assertEquals(ChannelKind.API, ctx.getChannelKind());
        assertSame(principal, ctx.getPrincipal());
    }

    @Test
    void contextChannelAndPrincipalSetterAcceptsNull() {
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "sess");
        ctx.setChannelKind(ChannelKind.WEBUI);
        ctx.setPrincipal(Principal.user());

        ctx.setChannelKind(null);
        ctx.setPrincipal(null);

        assertNull(ctx.getChannelKind());
        assertNull(ctx.getPrincipal());
    }

    // ========================================================================
    // doExecute propagation wiring (request -> context)
    // ========================================================================

    /**
     * A DefaultAgentEngine subclass that wraps the real executor in a
     * capturing decorator so the {@link AgentExecutionContext} handed to
     * {@code execute()} can be inspected after the run. This verifies the
     * actual doExecute propagation path, not just field existence.
     */
    static final class CapturingEngine extends DefaultAgentEngine {
        final AtomicReference<AgentExecutionContext> captured = new AtomicReference<>();

        CapturingEngine(IChatService chatService, IToolManager toolManager) {
            super(chatService, toolManager);
        }

        @Override
        IAgentExecutor resolveExecutor(AgentModel model, IToolAccessChecker toolAccessChecker,
                                       IPathAccessChecker pathAccessChecker) {
            IAgentExecutor real = super.resolveExecutor(model, toolAccessChecker, pathAccessChecker);
            return ctx -> {
                captured.set(ctx);
                return real.execute(ctx);
            };
        }
    }

    private IChatService echoChatService() {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("done");
        ChatResponse response = ChatResponse.success(msg);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return response;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    /**
     * A tool manager whose {@code loadTool} returns {@code null} so that no tool
     * definitions are built and the ReAct loop completes in a single LLM call
     * (the mock chat service returns a final assistant message with no tool
     * calls). Sufficient for the channel/principal propagation wiring test.
     */
    private IToolManager emptyToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
            }

            @Override
            public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                return null;
            }
        };
    }

    @Test
    void doExecutePropagatesChannelAndPrincipalIntoContext() {
        CapturingEngine engine = new CapturingEngine(echoChatService(), emptyToolManager());
        Principal principal = Principal.user();

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "hello", null, null, ChannelKind.GROUP, principal);

        engine.execute(req).toCompletableFuture().join();

        AgentExecutionContext ctx = engine.captured.get();
        assertEquals(ChannelKind.GROUP, ctx.getChannelKind(),
                "doExecute must propagate the request's channelKind into the execution context");
        assertSame(principal, ctx.getPrincipal(),
                "doExecute must propagate the request's principal into the execution context");
    }

    @Test
    void doExecuteLeavesChannelAndPrincipalNullWhenAbsent() {
        CapturingEngine engine = new CapturingEngine(echoChatService(), emptyToolManager());

        // Legacy 4-arg constructor — no channel/principal
        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "hello", null, null);

        engine.execute(req).toCompletableFuture().join();

        AgentExecutionContext ctx = engine.captured.get();
        assertNull(ctx.getChannelKind(),
                "doExecute must leave channelKind null when the request carries none (backward compat)");
        assertNull(ctx.getPrincipal(),
                "doExecute must leave principal null when the request carries none (backward compat)");
    }
}
