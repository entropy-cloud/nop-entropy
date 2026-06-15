package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.IPermissionMatrix;
import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.security.LevelHints;
import io.nop.ai.agent.security.MatrixDecision;
import io.nop.ai.agent.security.NoOpSecurityLevelResolver;
import io.nop.ai.agent.security.PassThroughPermissionMatrix;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.SecurityLevel;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 end-to-end tests: verifies the Layer 2 dispatch-path consultation is
 * actually connected — from {@link AgentMessageRequest} channel/principal, through
 * {@code DefaultAgentEngine.doExecute}, the {@link ReActAgentExecutor} Builder,
 * into the dispatch loop, where a functional resolver + restrictive matrix
 * allow/deny tool calls based on action kind + hints + channel.
 *
 * <p>Includes wiring verification (resolver.resolve / matrix.check are actually
 * invoked in the dispatch loop) and backward-compat (NoOp/PassThrough defaults
 * produce zero spurious denials).
 */
public class TestDispatchPathSecurityConsultation {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Functional resolver + restrictive matrix (design §5.1 / §5.3 rule tables)
    // ========================================================================

    /**
     * Test-internal functional resolver encoding a slice of the design §5.1
     * rule table: {@code shell.exec} with {@code highImpact} → RESTRICTED,
     * everything else → STANDARD. Counts resolve() calls for wiring verification.
     */
    static final class CountingResolver implements ISecurityLevelResolver {
        final AtomicInteger resolveCount = new AtomicInteger();

        @Override
        public SecurityLevel resolve(String actionKind, LevelHints hints) {
            resolveCount.incrementAndGet();
            String kind = actionKind == null ? "" : actionKind.replace('_', '.').toLowerCase(Locale.ROOT);
            if ("shell.exec".equals(kind) && hints.isHighImpact()) {
                return SecurityLevel.RESTRICTED;
            }
            return SecurityLevel.STANDARD;
        }
    }

    /**
     * Test-internal restrictive matrix encoding a slice of the design §5.3
     * channel table: GROUP only allows STANDARD; WEBUI allows all. Counts
     * check() calls for wiring verification.
     */
    static final class CountingMatrix implements IPermissionMatrix {
        final AtomicInteger checkCount = new AtomicInteger();

        @Override
        public MatrixDecision check(ChannelKind channel, Principal principal, SecurityLevel level) {
            checkCount.incrementAndGet();
            if (channel == ChannelKind.GROUP && level != SecurityLevel.STANDARD) {
                return MatrixDecision.deny(channel, level,
                        "GROUP channel only allows STANDARD, got " + level);
            }
            return MatrixDecision.allow();
        }
    }

    // ========================================================================
    // Mocks
    // ========================================================================

    private IChatService chatServiceReturningShellExecThenFinal() {
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_shell_1");
        toolCall.setName("shell.exec");
        toolCall.setArguments(Map.of("command", "ls"));
        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("done");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);

        AtomicInteger count = new AtomicInteger();
        List<ChatResponse> responses = List.of(toolResponse, finalResponse);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(responses.get(Math.min(count.getAndIncrement(), responses.size() - 1)));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return responses.get(Math.min(count.getAndIncrement(), responses.size() - 1));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager stubToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
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

    // ========================================================================
    // End-to-end: GROUP channel + ELEVATED-action (RESTRICTED) → deny
    // ========================================================================

    @Test
    void groupChannelShellExecIsDeniedByDispatchPathConsultation() {
        CountingResolver resolver = new CountingResolver();
        CountingMatrix matrix = new CountingMatrix();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningShellExecThenFinal(), stubToolManager());
        engine.setSecurityLevelResolver(resolver);
        engine.setPermissionMatrix(matrix);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run ls", null, null, ChannelKind.GROUP, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // Wiring verification: resolver + matrix were actually invoked in the loop.
        assertTrue(resolver.resolveCount.get() >= 1,
                "securityLevelResolver.resolve(...) must be called in the dispatch loop (wiring), got: "
                        + resolver.resolveCount.get());
        assertTrue(matrix.checkCount.get() >= 1,
                "permissionMatrix.check(...) must be called in the dispatch loop (wiring), got: "
                        + matrix.checkCount.get());

        // The tool call was denied: a ChatToolResponseMessage.error carries the
        // security-policy denial reason.
        boolean foundSecurityDenial = false;
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage tr = (ChatToolResponseMessage) m;
                String body = tr.getContent() != null ? tr.getContent() : "";
                if (body.contains("Security policy denied")) {
                    foundSecurityDenial = true;
                    break;
                }
            }
        }
        assertTrue(foundSecurityDenial,
                "GROUP channel + shell.exec (RESTRICTED) must be denied by the dispatch-path "
                        + "consultation — expected a 'Security policy denied' tool response");
    }

    // ========================================================================
    // End-to-end: WEBUI channel + RESTRICTED-action → allow
    // ========================================================================

    @Test
    void webuiChannelShellExecIsAllowedByDispatchPathConsultation() {
        CountingResolver resolver = new CountingResolver();
        CountingMatrix matrix = new CountingMatrix();

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningShellExecThenFinal(), stubToolManager());
        engine.setSecurityLevelResolver(resolver);
        engine.setPermissionMatrix(matrix);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run ls", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // Wiring verification: resolver + matrix were actually invoked.
        assertTrue(resolver.resolveCount.get() >= 1,
                "resolver.resolve(...) must be called even when the matrix allows");
        assertTrue(matrix.checkCount.get() >= 1,
                "matrix.check(...) must be called even when it allows");

        // WEBUI allows RESTRICTED → the tool call proceeds (success result, no denial).
        boolean foundSecurityDenial = false;
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage tr = (ChatToolResponseMessage) m;
                String body = tr.getContent() != null ? tr.getContent() : "";
                if (body.contains("Security policy denied")) {
                    foundSecurityDenial = true;
                    break;
                }
            }
        }
        assertTrue(!foundSecurityDenial,
                "WEBUI channel + RESTRICTED must be allowed — no 'Security policy denied' expected");
    }

    // ========================================================================
    // Backward-compat: NoOp/PassThrough defaults → no spurious denials
    // ========================================================================

    @Test
    void defaultNoOpAndPassThroughProduceNoSpuriousDenials() {
        // Test needs insecure default: opt into NoOp/PassThrough to verify
        // the opt-in backward-compat path (no classification, no channel
        // restrictions). Default is now Default* which classifies shell.exec
        // as ELEVATED and would deny it on GROUP channel.
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatServiceReturningShellExecThenFinal(), stubToolManager());
        engine.setSecurityLevelResolver(NoOpSecurityLevelResolver.noOp());
        engine.setPermissionMatrix(PassThroughPermissionMatrix.passThrough());

        assertTrue(engine.getSecurityLevelResolver() instanceof NoOpSecurityLevelResolver);
        assertTrue(engine.getPermissionMatrix() instanceof PassThroughPermissionMatrix);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run ls", null, null, ChannelKind.GROUP, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        boolean foundSecurityDenial = false;
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage tr = (ChatToolResponseMessage) m;
                String body = tr.getContent() != null ? tr.getContent() : "";
                if (body.contains("Security policy denied")) {
                    foundSecurityDenial = true;
                    break;
                }
            }
        }
        assertTrue(!foundSecurityDenial,
                "NoOp/PassThrough defaults must allow the tool call — no spurious Layer 2 denial");
    }

    // ========================================================================
    // Builder wiring: executor receives the three components
    // ========================================================================

    @Test
    void builderDefaultsWhenNotSet() {
        // A Builder-built executor without the Layer 2 components must still
        // build (defaults applied) and never throw during a dispatch loop.
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceReturningShellExecThenFinal())
                .toolManager(stubToolManager())
                .build();

        AgentModel model = new AgentModel();
        model.setName("btest");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "btest-session");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("hi"));

        // Default resolver = NoOp, default matrix = PassThrough → no denial,
        // completes without throwing.
        executor.execute(ctx).toCompletableFuture().join();
        // No exception thrown = wiring defaults are sound.
    }

    @Test
    void groupChannelStandardActionIsAllowed() {
        // A STANDARD-level action (fs.read) must be allowed even on GROUP channel.
        CountingResolver resolver = new CountingResolver();
        CountingMatrix matrix = new CountingMatrix();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_read_1");
        toolCall.setName("fs.read");
        toolCall.setArguments(Map.of("path", "in.txt"));
        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);
        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("done");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);
        AtomicInteger count = new AtomicInteger();
        List<ChatResponse> responses = List.of(toolResponse, finalResponse);
        IChatService chat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest r, ICancelToken t) {
                return CompletableFuture.completedFuture(responses.get(Math.min(count.getAndIncrement(), 1)));
            }

            @Override
            public ChatResponse call(ChatRequest r, ICancelToken t) {
                return responses.get(Math.min(count.getAndIncrement(), 1));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest r, ICancelToken t) {
                return s -> {};
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager());
        engine.setSecurityLevelResolver(resolver);
        engine.setPermissionMatrix(matrix);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "read", null, null, ChannelKind.GROUP, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // GROUP + STANDARD → allowed (matrix allows STANDARD on GROUP).
        boolean foundSecurityDenial = false;
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage tr = (ChatToolResponseMessage) m;
                String body = tr.getContent() != null ? tr.getContent() : "";
                if (body.contains("Security policy denied")) {
                    foundSecurityDenial = true;
                    break;
                }
            }
        }
        assertTrue(!foundSecurityDenial,
                "GROUP channel + STANDARD action (fs.read) must be allowed");
        assertTrue(resolver.resolveCount.get() >= 1, "resolver invoked for fs.read");
        assertTrue(matrix.checkCount.get() >= 1, "matrix invoked for fs.read on GROUP");
    }
}
