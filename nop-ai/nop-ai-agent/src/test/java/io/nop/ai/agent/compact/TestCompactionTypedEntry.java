package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentCompactionCoordinator;
import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentHookInvoker;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.engine.TokenEstimators;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.ToolExecutionCheckpoint;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.agent.session.FileBackedSessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.support.ChatResponseFixtures;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.api.core.util.ICancelToken;
import io.nop.ai.api.chat.messages.ChatCompactionMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2296 (WI15): typed compaction artifact entry tests — round-trip through
 * the incremental store, Layer3 production of the typed entry, pipeline pass-
 * through, coordinator identity enrichment (old entries never re-stamped),
 * summary detection dual-form compatibility.
 */
public class TestCompactionTypedEntry {

    @TempDir
    Path tempDir;

    // ========================================================================
    // Round-trip + copy
    // ========================================================================

    @Test
    void typedEntryRoundTripsThroughFileStoreWithAllFields() throws Exception {
        Path root = tempDir.resolve("typed-entry");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = AgentSession.create("sess-typed", "agent");
        ChatCompactionMessage entry = newTypedEntry();
        session.appendMessages(List.of(new ChatUserMessage("q"), entry));
        store.save(session);
        session.appendMessages(List.of(new ChatUserMessage("after")));
        store.save(session); // journal tail carries the entry too

        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession restored = store2.get("sess-typed");
        assertEquals(3, restored.getMessageCount());
        ChatMessage restoredMsg = restored.getMessages().get(1);
        assertTrue(restoredMsg instanceof ChatCompactionMessage,
                "polymorphic reader must restore the typed entry, not degrade it");
        ChatCompactionMessage restoredEntry = (ChatCompactionMessage) restoredMsg;
        assertEquals("compaction", restoredEntry.getRole());
        assertEquals(Long.valueOf(1000L), restoredEntry.getTokensBefore());
        assertEquals(Long.valueOf(300L), restoredEntry.getTokensAfter());
        assertEquals(Long.valueOf(20L), restoredEntry.getOriginalSize());
        assertEquals(Long.valueOf(7L), restoredEntry.getCompactedSize());
        assertEquals(Long.valueOf(700L), restoredEntry.getShadowedTokenCount());
        assertEquals("snap:s1:1:0", restoredEntry.getSnapshotId());
        assertEquals("layer3-full-summary", restoredEntry.getCompactionLayer());
        assertTrue(restoredEntry.getContent().startsWith("## Summary"));
        // Copy preserves every field and the type (no silent user-message degradation).
        ChatCompactionMessage copied = restoredEntry.copy();
        assertEquals("compaction", copied.getRole());
        assertEquals(restoredEntry.getSnapshotId(), copied.getSnapshotId());
        assertEquals(restoredEntry.getTokensBefore(), copied.getTokensBefore());
        assertEquals(restoredEntry.getCompactionLayer(), copied.getCompactionLayer());
    }

    private ChatCompactionMessage newTypedEntry() {
        ChatCompactionMessage entry = new ChatCompactionMessage("## Summary\ncompact summary body");
        entry.setTokensBefore(1000L);
        entry.setTokensAfter(300L);
        entry.setOriginalSize(20L);
        entry.setCompactedSize(7L);
        entry.setShadowedTokenCount(700L);
        entry.setSnapshotId("snap:s1:1:0");
        entry.setCompactionLayer("layer3-full-summary");
        entry.setCompactedAt(1234567890L);
        return entry;
    }

    // ========================================================================
    // Layer3 production
    // ========================================================================

    @Test
    void layer3ProducesTypedEntryWithRealSizesAndProducedEntryReference() {
        List<ChatMessage> messages = buildLongConversation(8);
        Layer3FullSummaryStrategy strategy = new Layer3FullSummaryStrategy(summarizingChatService("summary body"));
        CompactionContext ctx = ctxWith(messages, fixedPerMessageEstimator(10));

        CompactionResult result = strategy.compact(ctx);

        assertNotNull(result.getCompactedMessages());
        ChatCompactionMessage entry = result.getProducedEntry();
        assertNotNull(entry, "Layer3 must expose the produced entry reference");
        assertTrue(result.getCompactedMessages().contains(entry),
                "producedEntry must be the same instance installed in the compacted list");
        assertTrue(entry instanceof ChatUserMessage,
                "entry IS-A user message (dialect/summary-detection compatibility)");
        assertEquals("compaction", entry.getRole());
        assertEquals("layer3-full-summary", entry.getCompactionLayer());
        // Real sizes, not the legacy retained-count proxy.
        assertEquals(Long.valueOf(messages.size()), entry.getOriginalSize());
        assertEquals(Long.valueOf(result.getCompactedMessages().size()), entry.getCompactedSize());
        assertEquals(Long.valueOf(result.getTokensBefore()), entry.getTokensBefore());
        assertEquals(Long.valueOf(result.getTokensAfter()), entry.getTokensAfter());
        // Not yet enriched by the coordinator.
        assertNull(entry.getSnapshotId());
        assertNull(entry.getShadowedTokenCount());
        // Summary detection (legacy protocol) works on the typed entry.
        assertEquals("summary body", Layer3FullSummaryStrategy.findPreviousSummary(result.getCompactedMessages()));
    }

    @Test
    void findPreviousSummaryStillDetectsLegacyMarkerUserMessage() {
        List<ChatMessage> messages = List.of(
                new ChatSystemMessage("sys"),
                new ChatUserMessage(Layer3FullSummaryStrategy.SUMMARY_MARKER + "\nlegacy summary"));
        assertEquals("legacy summary", Layer3FullSummaryStrategy.findPreviousSummary(messages),
                "pre-WI15 sessions keep working unchanged");
    }

    // ========================================================================
    // Pipeline pass-through + coordinator identity enrichment (e2e assembly)
    // ========================================================================

    @Test
    void coordinatorEnrichesProducedEntryThroughRealPipelineAssembly() {
        // Anti-Hollow: the enrichment must fire through the REAL production
        // assembly (PipelineCompactor wrapping Layer3) — a fake
        // IContextCompactor could hand-craft producedEntry and mask the
        // pipeline re-construction pass-through.
        String sessionId = "typed-entry-session";
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        sessionStore.getOrCreate(sessionId, "test-agent");

        Layer3FullSummaryStrategy layer3 = new Layer3FullSummaryStrategy(summarizingChatService("e2e summary"));
        PipelineCompactor pipeline = new PipelineCompactor(layer3);
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        ITokenEstimator estimator = TokenEstimators.defaultEstimator();
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                pipeline, new ToolExecutionCheckpoint(), sessionStore, estimator, invoker);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel(), sessionId);
        ctx.getMessages().addAll(buildLongConversation(10));
        ctx.setTokensUsed(10_000_000L); // force the compaction trigger

        coordinator.performCompaction(ctx, "test-agent", new int[]{0});

        ChatCompactionMessage entry = firstTypedEntry(ctx.getMessages());
        assertNotNull(entry, "typed entry must be present in the compacted history");
        assertEquals("e2e summary", Layer3FullSummaryStrategy.findPreviousSummary(ctx.getMessages()));
        assertNotNull(entry.getSnapshotId(), "coordinator must enrich snapshotId (archived original pointer)");
        assertNotNull(entry.getShadowedTokenCount(), "coordinator must enrich the shadowed count");
        assertTrue(entry.getShadowedTokenCount() > 0);
    }

    @Test
    void microCompressionRoundNeverRestampsOldEntries() {
        // Blocker regression (round 1 review): a later Micro-effective round
        // keeps the old enriched entry in the history; the coordinator must
        // not re-stamp it with the new round's snapshotId.
        String sessionId = "no-restamp-session";
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        sessionStore.getOrCreate(sessionId, "test-agent");

        ChatCompactionMessage oldEntry = newTypedEntry(); // already enriched
        String originalSnapshotId = oldEntry.getSnapshotId();

        PipelineCompactor pipeline = new PipelineCompactor(new MicroCompressionCompactor());
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                pipeline, new ToolExecutionCheckpoint(), sessionStore,
                TokenEstimators.defaultEstimator(), invoker);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel(), sessionId);
        ctx.getMessages().addAll(messagesWithLargeToolResults(12));
        ctx.getMessages().add(1, oldEntry); // stale enriched entry early in history
        ctx.setTokensUsed(10_000_000L);

        coordinator.performCompaction(ctx, "test-agent", new int[]{0});

        ChatCompactionMessage surviving = firstTypedEntry(ctx.getMessages());
        assertNotNull(surviving, "Micro compression retains the old entry");
        assertSame(oldEntry, surviving, "the surviving entry must be the same instance");
        assertEquals(originalSnapshotId, surviving.getSnapshotId(),
                "a Micro-effective round must never re-stamp an old enriched entry");
    }

    @Test
    void pipelinePassesEntryThroughLaterEffectiveLayers() {
        // Layer3 reduces first; a later effective layer (Micro) keeps the
        // entry in its copied list — the final result must still reference it.
        List<ChatMessage> messages = new ArrayList<>(messagesWithLargeToolResults(10));
        messages.add(1, new ChatSystemMessage("filler"));
        PipelineCompactor pipeline = new PipelineCompactor(
                new Layer3FullSummaryStrategy(summarizingChatService("summary")),
                new MicroCompressionCompactor());
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        CompactionContext ctx = new CompactionContext(messages, lowThresholdConfig(),
                "s1", "agent1", execCtx, null, null, "snap:s1:1:0");

        CompactionResult result = pipeline.compact(ctx);

        assertNotNull(result.getProducedEntry(),
                "a later effective layer must not drop the surviving entry reference");
        assertTrue(result.getCompactedMessages().contains(result.getProducedEntry()));
    }

    // ========================================================================
    // Helpers (mirrors of the neighbouring test classes' infrastructure)
    // ========================================================================

    private ChatCompactionMessage firstTypedEntry(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if (message instanceof ChatCompactionMessage) {
                return (ChatCompactionMessage) message;
            }
        }
        return null;
    }

    private AgentModel agentModel() {
        AgentModel model = new AgentModel();
        model.setName("test-agent");
        return model;
    }

    private CompactConfig lowThresholdConfig() {
        return new CompactConfig(0, null, true,
                CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS,
                CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                0.05, 0.9, 0.15, 5, "");
    }

    private CompactionContext ctxWith(List<ChatMessage> messages, ITokenEstimator estimator) {
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        return new CompactionContext(messages, lowThresholdConfig(), "s1", "agent1", execCtx, estimator);
    }

    private ITokenEstimator fixedPerMessageEstimator(long tokensPerMessage) {
        return new ITokenEstimator() {
            @Override
            public long estimateTokens(List<ChatMessage> messages) {
                return tokensPerMessage * messages.size();
            }

            @Override
            public void record(List<ChatMessage> messagesSent, int actualPromptTokens) {
            }
        };
    }

    private IChatService summarizingChatService(String summaryText) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(call(request, cancelToken));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return ChatResponse.success(new io.nop.ai.api.chat.messages.ChatAssistantMessage(summaryText));
            }

            @Override
            public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                    ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private void assistantWithToolCalls(List<ChatMessage> messages, String... ids) {
        ChatToolCall[] calls = new ChatToolCall[ids.length];
        for (int i = 0; i < ids.length; i++) {
            calls[i] = new ChatToolCall();
            calls[i].setId(ids[i]);
            calls[i].setName("bash");
        }
        messages.addAll(ChatResponseFixtures.foldedAssistantWithToolCalls(null, calls));
    }

    private ChatToolResponseMessage toolResponse(String toolCallId, String content) {
        return new ChatToolResponseMessage(toolCallId, "bash", content);
    }

    private List<ChatMessage> buildLongConversation(int turns) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system prompt"));
        messages.add(new ChatUserMessage("initial goal"));
        for (int i = 0; i < turns; i++) {
            String id = "tc-" + i;
            assistantWithToolCalls(messages, id);
            messages.add(toolResponse(id, "result-" + i + "-padding-" + "X".repeat(40)));
        }
        return messages;
    }

    private List<ChatMessage> messagesWithLargeToolResults(int turns) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system prompt"));
        messages.add(new ChatUserMessage("initial goal"));
        for (int i = 0; i < turns; i++) {
            String id = "tc-" + i;
            assistantWithToolCalls(messages, id);
            messages.add(toolResponse(id, "result-" + i + "-" + "Y".repeat(2000)));
        }
        return messages;
    }
}
