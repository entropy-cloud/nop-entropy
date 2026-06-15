package io.nop.ai.agent.engine;

import io.nop.ai.agent.session.InMemorySessionStore;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link AgentNames} — the P2 path-injection guard
 * (finding [13-16]) at the {@code loadAgentModel} chokepoint. Mirrors the
 * plan-190 {@link TestSessionIdValidation} pattern: proves valid identifiers
 * pass and every documented rejection vector throws
 * {@link NopAiAgentException} (fail-closed).
 *
 * <p>Also includes an end-to-end test at the {@link DefaultAgentEngine#execute}
 * public API boundary proving a traversal-shaped {@code agentName} on
 * {@link AgentMessageRequest} is rejected <b>before</b> any VFS resource load
 * (Minimum Rules #22 End-to-End + #23 Wiring Verification) — the sibling of
 * plan-190's {@code executeRejectsTraversalSessionIdBeforeStoreInteraction}.
 */
public class TestAgentNameValidation {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // requireValidIdentifier — valid inputs pass
    // ========================================================================

    @Test
    void validHyphenatedNamePassesIdentifierCheck() {
        String name = "test-agent";
        String result = assertDoesNotThrow(() -> AgentNames.requireValidIdentifier(name));
        assertEquals(name, result, "A hyphenated allow-list name must pass unchanged");
    }

    @Test
    void validUnderscoreNamePassesIdentifierCheck() {
        String name = "my_agent";
        String result = assertDoesNotThrow(() -> AgentNames.requireValidIdentifier(name));
        assertEquals(name, result);
    }

    @Test
    void validAlphanumericNamePassesIdentifierCheck() {
        String name = "Agent123";
        String result = assertDoesNotThrow(() -> AgentNames.requireValidIdentifier(name));
        assertEquals(name, result);
    }

    @Test
    void validFullAllowListCharsetPassesIdentifierCheck() {
        // Every char in [A-Za-z0-9_-] is accepted
        String name = "AbcXYZ09-_";
        String result = assertDoesNotThrow(() -> AgentNames.requireValidIdentifier(name));
        assertEquals(name, result);
    }

    // ========================================================================
    // requireValidIdentifier — rejection vectors (all fail-closed)
    // ========================================================================

    @Test
    void nullRejectedByIdentifierCheck() {
        assertThrows(NopAiAgentException.class, () -> AgentNames.requireValidIdentifier(null),
                "null agentName must be rejected (fail-closed)");
    }

    @Test
    void emptyRejectedByIdentifierCheck() {
        assertThrows(NopAiAgentException.class, () -> AgentNames.requireValidIdentifier(""),
                "empty agentName must be rejected (fail-closed)");
    }

    @Test
    void pathTraversalDotDotSlashRejected() {
        assertThrows(NopAiAgentException.class,
                () -> AgentNames.requireValidIdentifier("../../etc/passwd"),
                "'../' traversal must be rejected by the regex allow-list");
    }

    @Test
    void absolutePathRejected() {
        assertThrows(NopAiAgentException.class,
                () -> AgentNames.requireValidIdentifier("/etc/passwd"),
                "Absolute path '/etc/passwd' must be rejected ('/' outside allow-list)");
    }

    @Test
    void backslashPathRejected() {
        assertThrows(NopAiAgentException.class,
                () -> AgentNames.requireValidIdentifier("..\\x"),
                "Backslash traversal '..\\x' must be rejected");
    }

    @Test
    void nulByteRejected() {
        assertThrows(NopAiAgentException.class,
                () -> AgentNames.requireValidIdentifier("evil\0root"),
                "NUL byte must be rejected");
    }

    @Test
    void whitespaceRejected() {
        assertThrows(NopAiAgentException.class,
                () -> AgentNames.requireValidIdentifier("agent name"),
                "Whitespace must be rejected");
    }

    @Test
    void dotLiteralRejected() {
        assertThrows(NopAiAgentException.class,
                () -> AgentNames.requireValidIdentifier("."),
                "Literal '.' must be rejected ('.' outside allow-list)");
    }

    @Test
    void dotDotLiteralRejected() {
        assertThrows(NopAiAgentException.class,
                () -> AgentNames.requireValidIdentifier(".."),
                "Literal '..' must be rejected");
    }

    @Test
    void dotContainingNameRejected() {
        assertThrows(NopAiAgentException.class,
                () -> AgentNames.requireValidIdentifier("a.b"),
                "Dot-containing name 'a.b' must be rejected ('.' outside allow-list)");
    }

    @Test
    void unicodeCharRejected() {
        assertThrows(NopAiAgentException.class,
                () -> AgentNames.requireValidIdentifier("代理-1"),
                "Unicode chars must be rejected");
    }

    // ========================================================================
    // isValidIdentifier — predicate form (used by CallAgentExecutor)
    // ========================================================================

    @Test
    void isValidIdentifierReturnsTrueForValidNames() {
        assertTrue(AgentNames.isValidIdentifier("test-agent"));
        assertTrue(AgentNames.isValidIdentifier("my_agent"));
        assertTrue(AgentNames.isValidIdentifier("Agent123"));
    }

    @Test
    void isValidIdentifierReturnsFalseForTraversalNames() {
        assertFalse(AgentNames.isValidIdentifier(null));
        assertFalse(AgentNames.isValidIdentifier(""));
        assertFalse(AgentNames.isValidIdentifier("../../etc/passwd"));
        assertFalse(AgentNames.isValidIdentifier("/etc/passwd"));
        assertFalse(AgentNames.isValidIdentifier("..\\x"));
        assertFalse(AgentNames.isValidIdentifier("evil\0root"));
        assertFalse(AgentNames.isValidIdentifier("agent name"));
        assertFalse(AgentNames.isValidIdentifier("."));
        assertFalse(AgentNames.isValidIdentifier(".."));
        assertFalse(AgentNames.isValidIdentifier("a.b"));
        assertFalse(AgentNames.isValidIdentifier("代理-1"));
    }

    // ========================================================================
    // End-to-end at the public API boundary (Minimum Rules #22 + #23)
    // A traversal agentName on AgentMessageRequest is rejected by
    // DefaultAgentEngine.loadAgentModel BEFORE any VFS resource load
    // (loadComponentModel) or CompletableFuture construction.
    // ========================================================================

    /**
     * A chat service that throws if ever called — proving the executor path
     * (and therefore any VFS-driven agent model dispatch) is never reached
     * when the agentName is rejected at the chokepoint.
     */
    private static final class ThrowingChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger();

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            throw new AssertionError("IChatService must NOT be called when agentName is rejected "
                    + "at the loadAgentModel chokepoint (no VFS load, no executor dispatch)");
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            throw new AssertionError("IChatService must NOT be called when agentName is rejected "
                    + "at the loadAgentModel chokepoint (no VFS load, no executor dispatch)");
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            throw new AssertionError("IChatService must NOT be called when agentName is rejected "
                    + "at the loadAgentModel chokepoint (no VFS load, no executor dispatch)");
        }
    }

    private static IToolManager stubToolManager() {
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

    /**
     * End-to-end at the public API: {@code engine.execute(request)} with a
     * traversal-shaped {@code request.agentName} throws
     * {@link NopAiAgentException} synchronously (loadAgentModel is the first
     * statement of doExecute, before any session/store/chat interaction). The
     * chat service is never called, proving no VFS resource load occurred and
     * no executor dispatch started (Minimum Rules #22 + #23).
     *
     * <p>The synchronous throw is the key signal: loadAgentModel runs before
     * {@code CompletableFuture.supplyAsync(...)} is constructed in doExecute,
     * so a throw here surfaces directly from {@code execute()} rather than via
     * the returned future — proving the rejection happens before any VFS
     * resource load side effect.
     */
    @Test
    void executeRejectsTraversalAgentNameBeforeVfsResourceLoad() {
        ThrowingChatService chat = new ThrowingChatService();
        DefaultAgentEngine engine = new DefaultAgentEngine(
                chat, stubToolManager(), new InMemorySessionStore());

        AgentMessageRequest req = new AgentMessageRequest(
                "../../etc/passwd", "run", null, null);

        // loadAgentModel runs at the top of doExecute(), synchronously, before
        // any future is constructed. The throw surfaces directly from execute().
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.execute(req),
                "execute must reject a traversal agentName at the public API boundary "
                        + "(fail-closed, before any VFS resource load)");

        // The rejection message must mention the agent-name path-injection
        // guard so the wiring (AgentNames.requireValidIdentifier invoked by
        // loadAgentModel) is observable from the exception (Wiring Rule #23).
        assertTrue(ex.getMessage().contains("agentName")
                        && (ex.getMessage().contains("path-injection guard")
                        || ex.getMessage().contains("invalid characters")
                        || ex.getMessage().contains("[A-Za-z0-9_-]")),
                "Rejection message must identify the agent-name path-injection guard: "
                        + ex.getMessage());

        // No chat interaction occurred — the executor was never dispatched,
        // which means no VFS resource load happened for the traversal path.
        assertEquals(0, chat.callCount.get(),
                "IChatService must not be called when the agentName is rejected at "
                        + "the loadAgentModel chokepoint (no VFS load side effect)");
    }

    /**
     * Wiring Verification (Rule #23) + no over-blocking: a legitimate
     * allow-list agentName passes the guard and execute proceeds into the
     * executor / chat path normally — proving the guard is wired on the happy
     * path too and does not over-block valid agent names.
     *
     * <p>Uses {@code "test-agent"} (a real allow-list agent model under
     * {@code src/test/resources/_vfs/} used by the existing
     * {@code TestCallAgentExecutor} suite), so execute completes in one chat
     * call without tool dispatch.
     */
    @Test
    void executeAcceptsValidAgentNameAfterGuard() {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("ok");
        ChatResponse response = ChatResponse.success(msg);
        IChatService chat = new IChatService() {
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
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chat, stubToolManager(), new InMemorySessionStore());

        AgentMessageRequest req = new AgentMessageRequest(
                "test-agent", "hi", null, null);

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();
        assertEquals(io.nop.ai.agent.model.AgentExecStatus.completed, result.getStatus(),
                "A valid [A-Za-z0-9_-] agentName must pass the guard and execute normally "
                        + "(no over-blocking)");
    }
}
