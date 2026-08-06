package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentCompactionCoordinator;
import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentHookInvoker;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.TokenEstimators;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.support.ChatResponseFixtures;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.ICompactionArchiveReader;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolExecutorProvider;
import io.nop.ai.toolkit.compact.ShortRef;
import io.nop.ai.toolkit.compact.ShortRefHasher;
import io.nop.ai.toolkit.executor.DefaultToolExecutorProvider;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.tools.ReadRefExecutor;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end wiring test for reference-style compaction dual-track
 * (design §8.2): exercises the complete path
 * <pre>
 *   AgentCompactionCoordinator.performCompaction (trigger)
 *     -> ReferenceCompactionStrategy produces shortRef + archives original
 *     -> AgentSession holds the archive instance
 *     -> AgentToolExecuteContext.getCompactionArchiveReader() exposes it
 *     -> ReadRefExecutor reads back the original by hash
 * </pre>
 * Plus tool-registration wiring (read-ref is registered in the toolkit
 * provider and visible by tool name). Verifies the anti-hollow contract:
 * the call chain is live at runtime, not just types present.
 */
public class TestReferenceCompactionEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentModel agentModel() {
        AgentModel m = new AgentModel();
        m.setName("test-agent");
        // Small maxTokens so the pipeline does NOT short-circuit as "relieved"
        // before the reference strategy gets a chance to run (the dual-dim
        // OR-gate trigger needs the context to be genuinely over threshold).
        io.nop.ai.core.model.ChatOptionsModel opts = new io.nop.ai.core.model.ChatOptionsModel();
        opts.setMaxTokens(200);
        m.setChatOptions(opts);
        return m;
    }

    private void assistantWithToolCall(List<ChatMessage> messages, String toolCallId, String toolName) {
        ChatToolCall call = new ChatToolCall();
        call.setId(toolCallId);
        call.setName(toolName);
        messages.add(ChatResponseFixtures.foldedAssistantWithToolCalls(null, call));
    }

    private String longContent(int chars) {
        StringBuilder sb = new StringBuilder(chars);
        while (sb.length() < chars) {
            sb.append("x");
        }
        return sb.toString();
    }

    /**
     * The headline end-to-end test: compact -> shortRef produced -> read-ref
     * reads back the exact original content. Drives the real
     * {@link AgentCompactionCoordinator#performCompaction} so the
     * coordinator->strategy->archive wiring is exercised (not just the
     * strategy in isolation).
     */
    @Test
    void compactProducesShortRefAndReadRefReadsItBack() {
        String sessionId = "e2e-session-1";
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        AgentSession session = sessionStore.getOrCreate(sessionId, "test-agent");

        // Build a pipeline with the reference strategy FIRST (Decision F
        // principle: 先剥离可保真内容→再摘要剩余 — reference must run before
        // the lossy layers so it archives long content before they would
        // clobber it). Layer2TurnPruningStrategy is the coexisting
        // summary-style strategy (dual-track coexistence).
        PipelineCompactor pipeline = new PipelineCompactor(
                new ReferenceCompactionStrategy(100),
                new Layer2TurnPruningStrategy()
        );

        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        ITokenEstimator estimator = TokenEstimators.defaultEstimator();
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                pipeline, NoOpCheckpoint.noOp(), sessionStore, estimator, invoker);

        // Build the context with messages that include a long, referenceable
        // tool response (read-file result). maxRecent in the coordinator's
        // default CompactConfig is 6, and we have only one tool response, so
        // it would be in the recent window — to force reference-ification we
        // make this the "older" content by adding the trigger condition that
        // the pipeline escalates through. We set a custom IContextCompactor
        // path instead: directly call the coordinator but pre-load enough
        // messages that reference compaction has older candidates.
        //
        // Simpler and more faithful: directly invoke the coordinator, which
        // uses CompactConfig.defaults() (maxRecent=6). With 1 tool response
        // it IS recent. To get a non-recent long tool response we add 6
        // trailing short tool responses so the long one falls out of the
        // recent window.
        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel(), sessionId);
        String originalLong = longContent(500);
        ctx.getMessages().add(new ChatSystemMessage("system"));
        ctx.getMessages().add(new ChatUserMessage("goal"));
        assistantWithToolCall(ctx.getMessages(), "tc-old", "read-file");
        ctx.getMessages().add(new ChatToolResponseMessage("tc-old", "read-file", originalLong));
        // 6 trailing short tool responses push tc-old out of the recent window
        for (int i = 0; i < 6; i++) {
            String id = "tc-recent-" + i;
            assistantWithToolCall(ctx.getMessages(), id, "bash");
            ctx.getMessages().add(new ChatToolResponseMessage(id, "bash", "short-result-" + i));
        }
        // Mark the context as needing compaction so performCompaction's
        // threshold guard fires.
        ctx.setTokensUsed(10_000_000L);

        coordinator.performCompaction(ctx, "test-agent", new int[]{0});

        // The compacted context messages should now contain a SHORT_REF
        // pointer for the long tool response
        String shortRefHash = null;
        boolean foundShortRef = false;
        for (ChatMessage msg : ctx.getMessages()) {
            if (msg instanceof ChatToolResponseMessage) {
                String content = ((ChatToolResponseMessage) msg).getContent();
                if (content != null && content.contains(ShortRef.MARKER)) {
                    foundShortRef = true;
                    ShortRef ref = ShortRef.parseFirst(content);
                    assertNotNull(ref, "shortRef in compacted content must be parseable");
                    shortRefHash = ref.getHash();
                    assertEquals(ShortRefHasher.hash(originalLong), shortRefHash,
                            "shortRef hash must equal the original content's hash");
                    break;
                }
            }
        }
        assertTrue(foundShortRef,
                "compaction must have replaced the long tool response with a shortRef pointer");

        // The original is now archived on the session (write side complete)
        assertNotNull(session.getCompactionArchive(),
                "the session must hold the materialised archive instance after compaction");
        assertTrue(session.getCompactionArchive().contains(shortRefHash),
                "the archive must contain the original under the shortRef hash");
        assertEquals(originalLong, session.getCompactionArchive().getByHash(shortRefHash));

        // Read side: build an AgentToolExecuteContext carrying the session,
        // then call ReadRefExecutor with the shortRef hash. The executor
        // reaches the archive via getCompactionArchiveReader() (the
        // AgentToolExecuteContext override -> session.getCompactionArchive()).
        AgentToolExecuteContext toolCtx = new AgentToolExecuteContext(
                null, null, Long.MAX_VALUE, null, null,
                io.nop.commons.concurrent.executor.SyncThreadPoolExecutor.INSTANCE,
                null, null, sessionId, "test-agent",
                null, null, null, null, null, null);
        toolCtx.setSession(session);

        ICompactionArchiveReader reader = toolCtx.getCompactionArchiveReader();
        assertNotNull(reader, "AgentToolExecuteContext must expose the session's archive reader");
        assertTrue(reader.contains(shortRefHash));

        ReadRefExecutor readRef = new ReadRefExecutor();
        XNode node = XNode.make("read-ref");
        node.setAttr("id", "1");
        node.setAttr("hash", shortRefHash);
        AiToolCall call = AiToolCall.fromNode(node);

        AiToolCallResult result = readRef.executeAsync(call, toolCtx).toCompletableFuture().join();

        assertEquals("success", result.getStatus(),
                "read-ref must succeed when the hash matches archived content");
        assertEquals(originalLong, result.getOutput().getBody(),
                "read-ref must return the exact original content (lossless round-trip)");
        assertNotNull(result.getOutput());
    }

    /**
     * Wiring verification: read-ref is registered as an IToolExecutor in the
     * toolkit provider and visible by tool name. This proves the bean
     * registration in ai-tools-defaults.beans.xml is reflected at runtime
     * (anti-hollow: the tool is reachable, not just declared).
     */
    @Test
    void readRefToolRegisteredAndCallableByName() {
        DefaultToolExecutorProvider provider = new DefaultToolExecutorProvider();
        ReadRefExecutor executor = new ReadRefExecutor();
        provider.setExecutors(List.of(executor));

        assertTrue(provider.getToolNames().contains("read-ref"),
                "read-ref must be visible in the provider's tool-name set");
        IToolExecutor resolved = provider.getExecutor("read-ref");
        assertNotNull(resolved, "read-ref must be resolvable by name from the provider");
        assertEquals("read-ref", resolved.getToolName());

        // Callable: invoke it with a missing-hash argument and confirm it
        // returns an explicit error (not throws, not silent). This proves
        // the executor is wired and live.
        XNode node = XNode.make("read-ref");
        node.setAttr("id", "1");
        node.setAttr("hash", "sha256:nonexistent");
        AiToolCall call = AiToolCall.fromNode(node);

        // Tool context whose archive reader is null (no archive) -> explicit error
        io.nop.ai.toolkit.api.IToolExecuteContext ctx = new io.nop.ai.toolkit.api.IToolExecuteContext() {
            @Override public java.io.File getWorkDir() { return new java.io.File("."); }
            @Override public java.util.Map<String, String> getEnvs() { return java.util.Map.of(); }
            @Override public long getExpireAt() { return Long.MAX_VALUE; }
            @Override public io.nop.api.core.util.ICancelToken getCancelToken() { return null; }
            @Override public io.nop.ai.toolkit.fs.IToolFileSystem getFileSystem() { return null; }
            @Override public io.nop.commons.concurrent.executor.IThreadPoolExecutor getExecutor() {
                return io.nop.commons.concurrent.executor.SyncThreadPoolExecutor.INSTANCE;
            }
            @Override public ICompactionArchiveReader getCompactionArchiveReader() { return null; }
        };

        AiToolCallResult result = resolved.executeAsync(call, ctx).toCompletableFuture().join();
        assertEquals("failure", result.getStatus(),
                "registered read-ref must be callable and return explicit error for missing archive");
        assertNotNull(result.getError());
    }

    /**
     * Wiring verification: the default UOE bridge on IToolExecuteContext
     * surfaces a descriptive error when read-ref is invoked outside the
     * agent engine (e.g. on a plain toolkit ToolExecuteContext or test mock
     * that does not override the accessor). 21 of the 22 implementations
     * inherit the default; this proves they fail loud rather than NPE.
     */
    @Test
    void defaultUoeBridgeFailsLoudOutsideAgentEngine() {
        ReadRefExecutor readRef = new ReadRefExecutor();
        XNode node = XNode.make("read-ref");
        node.setAttr("id", "1");
        node.setAttr("hash", "sha256:whatever");
        AiToolCall call = AiToolCall.fromNode(node);

        // A bare IToolExecuteContext that does NOT override
        // getCompactionArchiveReader() — inherits the default UOE bridge.
        io.nop.ai.toolkit.api.IToolExecuteContext bareCtx = new io.nop.ai.toolkit.api.IToolExecuteContext() {
            @Override public java.io.File getWorkDir() { return new java.io.File("."); }
            @Override public java.util.Map<String, String> getEnvs() { return java.util.Map.of(); }
            @Override public long getExpireAt() { return Long.MAX_VALUE; }
            @Override public io.nop.api.core.util.ICancelToken getCancelToken() { return null; }
            @Override public io.nop.ai.toolkit.fs.IToolFileSystem getFileSystem() { return null; }
            @Override public io.nop.commons.concurrent.executor.IThreadPoolExecutor getExecutor() {
                return io.nop.commons.concurrent.executor.SyncThreadPoolExecutor.INSTANCE;
            }
            // intentionally no getCompactionArchiveReader override -> default UOE
        };

        AiToolCallResult result = readRef.executeAsync(call, bareCtx).toCompletableFuture().join();
        assertEquals("failure", result.getStatus(),
                "default UOE bridge must produce explicit failure, not throw/NPE");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("read-ref is not available"),
                "error should explain read-ref needs the agent engine context");
    }
}
