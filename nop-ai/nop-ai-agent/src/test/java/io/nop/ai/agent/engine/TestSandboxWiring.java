package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.ISandboxBackend;
import io.nop.ai.agent.security.NoOpSandboxBackend;
import io.nop.ai.agent.security.SandboxConfig;
import io.nop.ai.agent.security.SandboxRequest;
import io.nop.ai.agent.security.SandboxResult;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.toolkit.api.IToolManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 219 Phase 1 wiring + smoke test. Verifies:
 *
 * <ol>
 *   <li><b>Wiring Verification (Minimum Rules #23)</b>: the engine →
 *       resolveExecutor → Builder → executor field reference chain is
 *       connected for the sandbox backend, both for the shipped default
 *       and for an explicitly-injected custom backend.</li>
 *   <li><b>Anti-Hollow Smoke</b>: the wired backend is not a hollow
 *       reference — calling {@code execute(SandboxRequest)} directly
 *       returns a populated {@link SandboxResult}, proving the contract
 *       surface has runtime behaviour.</li>
 * </ol>
 *
 * <p>The engine construction itself is minimal (the two mandatory ctor
 * args). We never invoke {@code engine.execute(...)} — the wiring test
 * uses {@link DefaultAgentEngine#resolveExecutor} directly, which builds
 * the {@link ReActAgentExecutor} via the same Builder path used on the
 * real dispatch path.
 */
public class TestSandboxWiring {

    // Minimal stubs — the wiring test never invokes them.
    private IChatService unusedChatService() {
        return new IChatService() {
            @Override
            public java.util.concurrent.CompletionStage<io.nop.ai.api.chat.ChatResponse> callAsync(
                    io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                throw new UnsupportedOperationException();
            }

            @Override
            public io.nop.ai.api.chat.ChatResponse call(
                    io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                    io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private IToolManager unusedToolManager() {
        return new IToolManager() {
            @Override
            public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallResult> callTool(
                    String toolName, io.nop.ai.toolkit.model.AiToolCall call,
                    io.nop.ai.toolkit.api.IToolExecuteContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls,
                    io.nop.ai.toolkit.api.IToolExecuteContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.List<io.nop.ai.toolkit.model.AiToolModel> listTools() {
                return java.util.Collections.emptyList();
            }

            @Override
            public io.nop.ai.toolkit.model.AiToolModel loadTool(String toolName) {
                return null;
            }
        };
    }

    private io.nop.ai.agent.model.AgentModel reactModel() {
        io.nop.ai.agent.model.AgentModel m = new io.nop.ai.agent.model.AgentModel();
        m.setName("sandbox-wiring-test");
        m.setMode("react");
        return m;
    }

    // ========================================================================
    // Wiring Verification (Minimum Rules #23): engine → executor chain
    // ========================================================================

    @Test
    void shippedDefaultExecutorHoldsNoOpSandboxBackend() {
        DefaultAgentEngine engine = new DefaultAgentEngine(unusedChatService(), unusedToolManager());

        // Engine-level getter: confirms the field default is in place.
        ISandboxBackend engineLevel = engine.getSandboxBackend();
        assertNotNull(engineLevel, "engine must always hold a non-null sandboxBackend");
        assertTrue(engineLevel instanceof NoOpSandboxBackend,
                "shipped default must be NoOpSandboxBackend; got " + engineLevel.getClass().getName());

        // resolveExecutor builds the ReActAgentExecutor via the same
        // Builder path used on the real dispatch path. The executor must
        // end up holding a non-null sandboxBackend reference (the chain
        // engine → resolveExecutor → Builder.sandboxBackend → field is
        // connected).
        IAgentExecutor executor = engine.resolveExecutor(reactModel());
        assertTrue(executor instanceof ReActAgentExecutor,
                "react-mode model must resolve to a ReActAgentExecutor");
        ReActAgentExecutor react = (ReActAgentExecutor) executor;
        ISandboxBackend executorLevel = react.getSandboxBackend();
        assertNotNull(executorLevel,
                "executor must hold a non-null sandboxBackend — engine → resolveExecutor → "
                        + "Builder.sandboxBackend → field chain must be connected");
        assertTrue(executorLevel instanceof NoOpSandboxBackend,
                "shipped default executor must hold a NoOpSandboxBackend; got "
                        + executorLevel.getClass().getName());
    }

    @Test
    void customBackendInjectedViaEngineReachesExecutor() {
        // A sentinel backend instance — identity comparison proves the
        // exact instance propagates from engine → executor.
        ISandboxBackend sentinel = new ISandboxBackend() {
            @Override
            public SandboxResult execute(SandboxRequest request) {
                throw new UnsupportedOperationException("sentinel");
            }

            @Override
            public String toString() {
                return "SentinelSandboxBackend";
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(unusedChatService(), unusedToolManager());
        engine.setSandboxBackend(sentinel);

        // Engine-level getter returns the exact injected instance.
        assertSame(sentinel, engine.getSandboxBackend(),
                "engine getter must return the exact instance injected via setSandboxBackend");

        // The executor built by resolveExecutor must receive the same
        // instance — proving the wiring through the Builder is connected.
        ReActAgentExecutor react = (ReActAgentExecutor) engine.resolveExecutor(reactModel());
        assertSame(sentinel, react.getSandboxBackend(),
                "executor must hold the exact instance injected via engine.setSandboxBackend — "
                        + "engine → resolveExecutor → Builder.sandboxBackend → field chain must preserve identity");
    }

    @Test
    void setSandboxBackendNullFallsBackToNoOp() {
        DefaultAgentEngine engine = new DefaultAgentEngine(unusedChatService(), unusedToolManager());
        engine.setSandboxBackend(null);
        assertTrue(engine.getSandboxBackend() instanceof NoOpSandboxBackend,
                "setSandboxBackend(null) must fall back to NoOpSandboxBackend (plan 219)");
    }

    // ========================================================================
    // Anti-Hollow Smoke: execute() is end-to-end callable
    // ========================================================================

    @Test
    void smokeExecuteReturnsPopulatedResult() {
        // The Anti-Hollow check (Minimum Rules #22 / #24): calling
        // execute() directly on the wired backend must return a populated
        // SandboxResult, proving the contract surface is not a hollow
        // reference. We use the shipped NoOpSandboxBackend because the
        // smoke test must run on every platform without Docker.
        DefaultAgentEngine engine = new DefaultAgentEngine(unusedChatService(), unusedToolManager());
        ISandboxBackend backend = engine.getSandboxBackend();

        SandboxRequest req = SandboxRequest.of(
                List.of("sh", "-c", "echo smoke-test-payload"),
                SandboxConfig.defaults());

        SandboxResult result = backend.execute(req);

        assertNotNull(result, "execute() must return a non-null SandboxResult");
        assertTrue(result.getStdout().contains("smoke-test-payload"),
                "smoke execute() must capture the command output; got: " + result.getStdout());
    }
}
