package io.nop.ai.agent.engine;

import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 223 Phase 2 engine-wiring tests: verifies that
 * {@code DefaultAgentEngine} ships with {@link NoOpTeamManager} as the
 * default (zero behaviour regression) and that {@code setTeamManager}
 * accepts a functional manager with null-safe fallback.
 *
 * <p>Coverage map (Wiring Verification — Minimum Rules #23):
 * <ul>
 *   <li>{@link #noOpIsShippedDefault} — new engine defaults to NoOp instance</li>
 *   <li>{@link #setTeamManagerInjectsFunctionalManager} — setter assigns</li>
 *   <li>{@link #setTeamManagerNullFallsBackToNoOp} — null-safe fallback</li>
 * </ul>
 */
public class TestTeamManagerEngineWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void noOpIsShippedDefault() {
        // A freshly constructed engine (no setter call) must hold a
        // NoOpTeamManager instance — the engine does not manage teams by
        // default (zero behaviour regression).
        DefaultAgentEngine engine = newEngine();
        ITeamManager mgr = engine.getTeamManager();
        assertTrue(mgr instanceof NoOpTeamManager,
                "shipped default teamManager must be a NoOpTeamManager instance");
        assertSame(NoOpTeamManager.noOp(), mgr,
                "default is the NoOp singleton");
    }

    @Test
    void setTeamManagerInjectsFunctionalManager() {
        DefaultAgentEngine engine = newEngine();
        InMemoryTeamManager functional = new InMemoryTeamManager();

        engine.setTeamManager(functional);

        assertSame(functional, engine.getTeamManager(),
                "setTeamManager must assign the functional manager");
    }

    @Test
    void setTeamManagerNullFallsBackToNoOp() {
        DefaultAgentEngine engine = newEngine();
        // null must fall back to the NoOp default (consistent with the
        // setActorRuntime / setMessenger null-safe pattern).
        engine.setTeamManager(null);

        ITeamManager mgr = engine.getTeamManager();
        assertTrue(mgr instanceof NoOpTeamManager,
                "setTeamManager(null) must fall back to NoOpTeamManager");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private DefaultAgentEngine newEngine() {
        // Minimal engine construction — only the teamManager wiring is
        // under test, so the chat service / tool manager are trivial stubs
        // that are never invoked (no execute() call).
        IChatService chat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        IToolManager tools = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
            }

            @Override
            public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                return model;
            }
        };
        // 2-arg constructor uses an InMemorySessionStore by default.
        return new DefaultAgentEngine(chat, tools);
    }
}
