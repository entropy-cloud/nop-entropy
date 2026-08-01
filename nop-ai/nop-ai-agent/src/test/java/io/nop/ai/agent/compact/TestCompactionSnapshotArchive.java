package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentCompactionCoordinator;
import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentHookInvoker;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.engine.TokenEstimators;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.ToolExecutionCheckpoint;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.InSessionCompactionSnapshotArchive;
import io.nop.ai.agent.session.ICompactionSnapshotArchive;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.ChatOptionsModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the compaction snapshot archive & two-dimension
 * ratio pipeline (design §8.3, plan W4-2). Covers:
 * <ul>
 *   <li>snapshotId data flow: {@code CompactionContext} → {@code PipelineCompactor}
 *       single final construction point → {@code CompactionResult} (Decision A).</li>
 *   <li>{@code originalSize}/{@code compactedSize} correct on the final result
 *       (Decision D) and the legacy constructor default.</li>
 *   <li>{@code AgentCompactionCoordinator} archives the pre-compaction history
 *       before {@code compact()} and the original is retrievable by snapshotId
 *       (Decision E + B) — the success path (Anti-Hollow).</li>
 *   <li>Failure path: a throwing custom compactor does not abort the agent; the
 *       archive is retained (Decision F try-catch).</li>
 *   <li>No-archive path: when no session is resolvable, snapshotId stays null
 *       (Decision F/G — not a failure, just no archive).</li>
 *   <li>End-to-end COMPACTION checkpoint {@code compactSummary} carries
 *       snapshotId + both ratio dimensions (Phase 3).</li>
 * </ul>
 */
public class TestCompactionSnapshotArchive {

    private static final int MAX_CONTEXT_TOKENS = 1000;

    private AgentModel agentModel() {
        AgentModel m = new AgentModel();
        m.setName("test-agent");
        ChatOptionsModel opts = new ChatOptionsModel();
        opts.setMaxTokens(MAX_CONTEXT_TOKENS);
        m.setChatOptions(opts);
        return m;
    }

    private CompactConfig lowThresholdConfig() {
        return new CompactConfig(0, null, true,
                CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS,
                CompactConfig.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                0.05, CompactConfig.DEFAULT_FORCED_STOP_PERCENT,
                CompactConfig.DEFAULT_KEEP_TAIL_PERCENT, 5,
                CompactConfig.DEFAULT_COMPRESSION_MODEL);
    }

    private ChatAssistantMessage assistantWithToolCall(String id, String name) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        ChatToolCall call = new ChatToolCall();
        call.setId(id);
        call.setName(name);
        msg.setToolCalls(Collections.singletonList(call));
        return msg;
    }

    /** Build messages with large tool results so MicroCompressionCompactor actually reduces. */
    private List<ChatMessage> messagesWithLargeToolResults(int turns) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("goal"));
        for (int i = 0; i < turns; i++) {
            String id = "tc-" + i;
            messages.add(assistantWithToolCall(id, "bash"));
            messages.add(new ChatToolResponseMessage(id, "bash", "X".repeat(5000)));
        }
        return messages;
    }

    // ========================================================================
    // Decision A + D: PipelineCompactor snapshotId flow + message-count dims
    // ========================================================================

    @Test
    void pipelineCompactorCarriesSnapshotIdAndMessageCountDimensionsOnSuccess() {
        // A strategy that genuinely reduces (relieves the context).
        ICompressionStrategy relieving = new ICompressionStrategy() {
            @Override
            public String name() { return "relieving"; }

            @Override
            public CompactionResult compact(CompactionContext ctx) {
                List<ChatMessage> reduced = new ArrayList<>(ctx.getMessages().subList(0, 2));
                long tokens = NoOpContextCompactor.resolveEstimator(ctx).estimateTokens(ctx.getMessages());
                long after = NoOpContextCompactor.resolveEstimator(ctx).estimateTokens(reduced);
                // Strategy-internal intermediate result uses the legacy 6-param
                // constructor (Decision A: do NOT touch strategy-internal construction).
                return new CompactionResult(ctx.getSessionId(), tokens, after, reduced.size(), null, reduced);
            }
        };
        PipelineCompactor pipeline = new PipelineCompactor(relieving);

        List<ChatMessage> messages = messagesWithLargeToolResults(10);
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        // Decision A: snapshotId injected via CompactionContext.
        CompactionContext ctx = new CompactionContext(messages, lowThresholdConfig(),
                "s1", "agent1", execCtx, null, null, "snap:s1:123:1");

        CompactionResult result = pipeline.compact(ctx);

        assertNotNull(result.getCompactedMessages(), "success path produces compacted messages");
        assertTrue(result.getTokensAfter() < result.getTokensBefore());
        assertEquals("snap:s1:123:1", result.getSnapshotId(),
                "Decision A: snapshotId must flow context -> PipelineCompactor -> result");
        assertEquals(messages.size(), result.getOriginalSize(),
                "originalSize must be the pre-compaction message count");
        assertEquals(result.getCompactedMessages().size(), result.getCompactedSize(),
                "compactedSize must be the post-compaction message count");
        assertEquals(result.getCompactedSize(), result.getRetainedMessageCount(),
                "on the success path retainedMessageCount == compactedSize (legacy alias)");
    }

    @Test
    void pipelineCompactorCarriesSnapshotIdEvenWhenNoReduction() {
        // A strategy that does NOT relieve — the no-reduction branch of the
        // single final construction point must still carry snapshotId.
        ICompressionStrategy noOp = new ICompressionStrategy() {
            @Override
            public String name() { return "noop"; }

            @Override
            public CompactionResult compact(CompactionContext ctx) {
                long tokens = NoOpContextCompactor.resolveEstimator(ctx).estimateTokens(ctx.getMessages());
                return new CompactionResult(ctx.getSessionId(), tokens, tokens, ctx.getMessages().size(), null, null);
            }
        };
        PipelineCompactor pipeline = new PipelineCompactor(noOp);

        List<ChatMessage> messages = messagesWithLargeToolResults(10);
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        CompactionContext ctx = new CompactionContext(messages, lowThresholdConfig(),
                "s1", "agent1", execCtx, null, null, "snap:s1:456:2");

        CompactionResult result = pipeline.compact(ctx);

        assertEquals("snap:s1:456:2", result.getSnapshotId(),
                "no-reduction branch must still carry snapshotId (Decision A — both branches)");
        assertNull(result.getCompactedMessages(), "no reduction -> null compactedMessages");
        assertEquals(messages.size(), result.getOriginalSize());
        assertEquals(messages.size(), result.getCompactedSize(),
                "no reduction -> compactedSize == originalSize");
    }

    @Test
    void pipelineCompactorNullSnapshotIdWhenContextHasNone() {
        // Backward compat: when no snapshotId in the context (e.g. no archive),
        // the result carries null snapshotId.
        PipelineCompactor pipeline = new PipelineCompactor(new MicroCompressionCompactor());
        List<ChatMessage> messages = messagesWithLargeToolResults(10);
        AgentExecutionContext execCtx = new AgentExecutionContext(agentModel());
        CompactionContext ctx = new CompactionContext(messages, lowThresholdConfig(),
                "s1", "agent1", execCtx, null); // legacy 6-param, no snapshotId

        CompactionResult result = pipeline.compact(ctx);

        assertNull(result.getSnapshotId(), "no snapshotId in context -> null in result (backward compat)");
    }

    @Test
    void compactionResultLegacyConstructorDefaultsMessageCountDimensions() {
        // Decision D: legacy 5/6-param constructors default originalSize and
        // compactedSize to retainedMessageCount (best-effort backward-compat proxy).
        CompactionResult legacy = new CompactionResult("s1", 100, 40, 5, null, Collections.emptyList());

        assertEquals(5, legacy.getOriginalSize(), "legacy constructor defaults originalSize to retainedMessageCount");
        assertEquals(5, legacy.getCompactedSize(), "legacy constructor defaults compactedSize to retainedMessageCount");
    }

    @Test
    void compactionResultEqualsHashCodeIncludeNewFields() {
        // Decision D: equals/hashCode must include originalSize/compactedSize
        // so two results differing only on the new dimensions are not equal.
        CompactionResult a = new CompactionResult("s1", 100, 40, 5, null, null, 10, 5);
        CompactionResult b = new CompactionResult("s1", 100, 40, 5, null, null, 10, 5);
        CompactionResult diff = new CompactionResult("s1", 100, 40, 5, null, null, 12, 5);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diff, "differing originalSize must break equality");
    }

    // ========================================================================
    // Decision E + B: coordinator archives before compact; original retrievable
    // ========================================================================

    @Test
    void coordinatorArchivesBeforeCompactAndOriginalIsRetrievable() {
        // Anti-Hollow: the full chain performCompaction -> archive -> pipeline
        // -> result carries snapshotId -> archived original retrievable.
        String sessionId = "snap-success-session";
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        AgentSession session = sessionStore.getOrCreate(sessionId, "test-agent");

        PipelineCompactor pipeline = new PipelineCompactor(new MicroCompressionCompactor());
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        ITokenEstimator estimator = TokenEstimators.defaultEstimator();
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                pipeline, new ToolExecutionCheckpoint(), sessionStore, estimator, invoker);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel(), sessionId);
        ctx.getMessages().addAll(messagesWithLargeToolResults(12));
        // Force the trigger guard in performCompaction's pipeline path.
        long initialTokens = 10_000_000L;
        ctx.setTokensUsed(initialTokens);

        coordinator.performCompaction(ctx, "test-agent", new int[]{0});

        // The session now hosts a materialised snapshot archive with one entry.
        ICompactionSnapshotArchive archive = session.getCompactionSnapshotArchive();
        assertNotNull(archive, "session must materialise the snapshot archive after compaction");
        assertTrue(archive instanceof InSessionCompactionSnapshotArchive,
                "the session-created archive must be the in-session impl");
        assertEquals(1, ((InSessionCompactionSnapshotArchive) archive).size(),
                "exactly one pre-compaction snapshot must be archived");

        // MicroCompressionCompactor compresses tool-result content in place
        // (token reduction), so tokens drop — the real signal that compaction
        // happened — while the archived original captured the pre-compaction
        // history (retrievability verified by the next test via compactSummary).
        assertTrue(ctx.getTokensUsed() < initialTokens,
                "compaction must have reduced the token usage");
        boolean liveHasCompressed = ctx.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("COMPRESSED"));
        assertTrue(liveHasCompressed, "live messages must contain in-place compressed tool results");
    }

    @Test
    void coordinatorArchivedOriginalMatchesPreCompactionHistory() {
        // Verify retrievability: build a coordinator, perform compaction, then
        // fetch the archived snapshot by the id embedded in the COMPACTION
        // checkpoint and assert it equals the pre-compaction history.
        String sessionId = "snap-retrieve-session";
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        AgentSession session = sessionStore.getOrCreate(sessionId, "test-agent");

        ToolExecutionCheckpoint checkpointMgr = new ToolExecutionCheckpoint();
        PipelineCompactor pipeline = new PipelineCompactor(new MicroCompressionCompactor());
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        ITokenEstimator estimator = TokenEstimators.defaultEstimator();
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                pipeline, checkpointMgr, sessionStore, estimator, invoker);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel(), sessionId);
        List<ChatMessage> originalHistory = messagesWithLargeToolResults(12);
        ctx.getMessages().addAll(originalHistory);
        ctx.setTokensUsed(10_000_000L);

        coordinator.performCompaction(ctx, "test-agent", new int[]{0});

        // The COMPACTION checkpoint compactSummary embeds the snapshotId.
        List<Checkpoint> compactionCps = checkpointMgr.getCheckpoints(sessionId).stream()
                .filter(c -> c.getType() == CheckpointType.COMPACTION)
                .collect(Collectors.toList());
        assertFalse(compactionCps.isEmpty(), "a COMPACTION checkpoint must be emitted on real compaction");
        String compactSummary = compactionCps.get(0).getOutputSummary();
        assertNotNull(compactSummary);
        assertTrue(compactSummary.contains("snapshotId="),
                "compactSummary must embed the snapshotId for traceability");

        // Extract the snapshotId from the summary and retrieve the archived original.
        String snapshotId = compactSummary.substring(compactSummary.indexOf("snapshotId=") + "snapshotId=".length()).trim();
        ICompactionSnapshotArchive archive = session.getCompactionSnapshotArchive();
        List<ChatMessage> archived = archive.get(snapshotId);
        assertNotNull(archived, "the archived original must be retrievable by the embedded snapshotId");
        assertEquals(originalHistory, archived,
                "the archived original must equal the pre-compaction history (reversibility)");
    }

    // ========================================================================
    // Decision F: failure path (try-catch) + no-archive path
    // ========================================================================

    @Test
    void coordinatorTryCatchKeepsArchiveAndDoesNotAbortWhenCompactorThrows() {
        // A custom (non-PipelineCompactor) compactor that throws. Decision F:
        // the coordinator must catch it, retain the archive, and NOT bubble up.
        String sessionId = "snap-fail-session";
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        AgentSession session = sessionStore.getOrCreate(sessionId, "test-agent");

        IContextCompactor throwingCompactor = ctx -> {
            throw new IllegalStateException("simulated compactor failure");
        };
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        ITokenEstimator estimator = TokenEstimators.defaultEstimator();
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                throwingCompactor, new ToolExecutionCheckpoint(), sessionStore, estimator, invoker);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel(), sessionId);
        List<ChatMessage> originalHistory = messagesWithLargeToolResults(12);
        ctx.getMessages().addAll(originalHistory);
        ctx.setTokensUsed(10_000_000L);

        // Must NOT throw — the coordinator catches and logs.
        coordinator.performCompaction(ctx, "test-agent", new int[]{0});

        // The archive was retained (put happened before compact threw).
        ICompactionSnapshotArchive archive = session.getCompactionSnapshotArchive();
        assertNotNull(archive, "archive must be materialised before the compactor was called");
        assertEquals(1, ((InSessionCompactionSnapshotArchive) archive).size(),
                "the pre-compaction snapshot must be retained after failure");

        // The original messages are intact (compaction did not replace them).
        assertEquals(originalHistory.size(), ctx.getMessages().size(),
                "on compactor failure the original messages must be retained unchanged");
    }

    @Test
    void coordinatorNoArchiveWhenSessionUnresolvableAndSnapshotIdStaysNull() {
        // Decision F/G: when no sessionStore is wired, no archive is created;
        // snapshotId stays null and the result carries null (not a failure).
        PipelineCompactor pipeline = new PipelineCompactor(new MicroCompressionCompactor());
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        ITokenEstimator estimator = TokenEstimators.defaultEstimator();
        // No sessionStore -> resolveSnapshotArchive returns null.
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                pipeline, new ToolExecutionCheckpoint(), null, estimator, invoker);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel(), "no-store-session");
        ctx.getMessages().addAll(messagesWithLargeToolResults(12));
        ctx.setTokensUsed(10_000_000L);

        // Capture the result indirectly: use a tracking pipeline wrapper.
        AtomicReference<CompactionResult> captured = new AtomicReference<>();
        IContextCompactor tracking = ctx1 -> {
            CompactionResult r = pipeline.compact(ctx1);
            captured.set(r);
            return r;
        };
        AgentCompactionCoordinator trackingCoordinator = new AgentCompactionCoordinator(
                tracking, new ToolExecutionCheckpoint(), null, estimator, invoker);

        trackingCoordinator.performCompaction(ctx, "test-agent", new int[]{0});

        assertNotNull(captured.get());
        assertNull(captured.get().getSnapshotId(),
                "no session -> no archive -> snapshotId stays null (Decision F/G, not a failure)");
    }

    // ========================================================================
    // Phase 3: COMPACTION checkpoint two-dimension ratio + snapshotId
    // ========================================================================

    @Test
    void compactionCheckpointCompactSummaryCarriesSnapshotIdAndBothRatios() {
        String sessionId = "snap-ratio-session";
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        sessionStore.getOrCreate(sessionId, "test-agent");

        ToolExecutionCheckpoint checkpointMgr = new ToolExecutionCheckpoint();
        PipelineCompactor pipeline = new PipelineCompactor(new MicroCompressionCompactor());
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        ITokenEstimator estimator = TokenEstimators.defaultEstimator();
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                pipeline, checkpointMgr, sessionStore, estimator, invoker);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel(), sessionId);
        ctx.getMessages().addAll(messagesWithLargeToolResults(12));
        ctx.setTokensUsed(10_000_000L);

        coordinator.performCompaction(ctx, "test-agent", new int[]{0});

        List<Checkpoint> compactionCps = checkpointMgr.getCheckpoints(sessionId).stream()
                .filter(c -> c.getType() == CheckpointType.COMPACTION)
                .collect(Collectors.toList());
        assertFalse(compactionCps.isEmpty(), "COMPACTION checkpoint must be emitted on real compaction");

        String summary = compactionCps.get(0).getOutputSummary();
        assertNotNull(summary);
        // Phase 3: two-dimension ratio (token + message-count) + snapshotId.
        assertTrue(summary.contains("tokens"), "summary must report the token dimension");
        assertTrue(summary.contains("messages"), "summary must report the message-count dimension");
        assertTrue(summary.contains("ratio"), "summary must report the compression ratio");
        assertTrue(summary.contains("snapshotId=snap:"), "summary must embed a non-null snapshotId");
    }

    @Test
    void wiringVerificationCoordinatorInvokesArchiveBeforeCompact() {
        // Minimum Rules #23: verify the coordinator actually archives BEFORE
        // invoking compact(). Observe ordering through the compactor: the
        // compactor reads ctx.getSnapshotId(); if non-null, the archive PUT
        // already ran (Decision E — context is constructed after the archive,
        // carrying the resulting snapshotId as a final field).
        String sessionId = "snap-wiring-session";
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        sessionStore.getOrCreate(sessionId, "test-agent");

        AtomicReference<String> snapshotIdSeenByCompactor = new AtomicReference<>(null);
        IContextCompactor trackingCompactor = ctx -> {
            snapshotIdSeenByCompactor.set(ctx.getSnapshotId());
            return new MicroCompressionCompactor().compact(ctx);
        };
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        ITokenEstimator estimator = TokenEstimators.defaultEstimator();
        AgentCompactionCoordinator coordinator = new AgentCompactionCoordinator(
                trackingCompactor, new ToolExecutionCheckpoint(), sessionStore, estimator, invoker);

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel(), sessionId);
        ctx.getMessages().addAll(messagesWithLargeToolResults(12));
        ctx.setTokensUsed(10_000_000L);

        coordinator.performCompaction(ctx, "test-agent", new int[]{0});

        assertNotNull(snapshotIdSeenByCompactor.get(),
                "Wiring: the snapshotId must already be in the context when compact() is called (archive-before-compact, Decision E)");
        assertTrue(snapshotIdSeenByCompactor.get().startsWith("snap:"),
                "the snapshotId must be a real per-event id, not a placeholder");
    }
}
