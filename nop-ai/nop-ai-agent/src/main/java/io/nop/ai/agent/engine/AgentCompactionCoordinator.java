package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.CompactionContext;
import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.NoOpContextCompactor;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.session.ICompactionSnapshotArchive;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.toolkit.api.ICompactionArchive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * Context-compaction orchestration for the ReAct loop (extracted from
 * {@link ReActAgentExecutor}, MA4.2-05). Decides when to compact (token
 * percentage / message-count triggers), resolves the effective
 * maxContextTokens, runs the compaction pipeline and records the
 * COMPACTION checkpoint + persisted-session re-sync on real compaction.
 * The PRE_COMPACT / POST_COMPACT lifecycle hooks run through the injected
 * {@link AgentHookInvoker}.
 */
public class AgentCompactionCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(AgentCompactionCoordinator.class);

    private final IContextCompactor contextCompactor;
    private final ICheckpointManager checkpointManager;
    private final ISessionStore sessionStore;
    private final ITokenEstimator tokenEstimator;
    private final AgentHookInvoker hookInvoker;

    public AgentCompactionCoordinator(IContextCompactor contextCompactor,
                                      ICheckpointManager checkpointManager,
                                      ISessionStore sessionStore,
                                      ITokenEstimator tokenEstimator,
                                      AgentHookInvoker hookInvoker) {
        this.contextCompactor = contextCompactor;
        this.checkpointManager = checkpointManager;
        this.sessionStore = sessionStore;
        this.tokenEstimator = tokenEstimator;
        this.hookInvoker = hookInvoker;
    }

    // ---- moved verbatim from ReActAgentExecutor (MA4.2-05 split) ----
    public boolean shouldTriggerCompaction(AgentExecutionContext ctx) {
        long maxContextTokens = resolveMaxContextTokens(ctx);
        if (ctx.getTokensUsed() > maxContextTokens * ReActAgentExecutor.DEFAULT_TRIGGER_TOKEN_PERCENT) {
            return true;
        }
        return ctx.getMessages().size() > ReActAgentExecutor.DEFAULT_TRIGGER_MAX_MESSAGES;
    }

    public long resolveMaxContextTokens(AgentExecutionContext ctx) {
        ChatOptions chatOpts = ctx.getChatOptions();
        if (chatOpts != null && chatOpts.getMaxTokens() != null) {
            return chatOpts.getMaxTokens();
        }
        return ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS;
    }
    public void performCompaction(AgentExecutionContext ctx, String agentName, int[] checkpointSeq) {
        CompactConfig config = CompactConfig.defaults();

        // Resolve the per-session compaction archives (design §8.2 Decision G
        // write side + §8.3 Decision B). The coordinator already holds the
        // ISessionStore and uses the same sessionStore.get(sessionId)->
        // AgentSession pattern that the post-compaction re-sync below uses.
        // Both archives are lazily materialised on the session; when no
        // session is resolvable they stay null.
        ICompactionArchive archive = resolveArchive(ctx.getSessionId());
        ICompactionSnapshotArchive snapshotArchive = resolveSnapshotArchive(ctx.getSessionId());

        hookInvoker.executeWithMiddleware(AgentLifecyclePoint.PRE_COMPACT, ctx, agentName, null, null);

        // Design §8.3 Decision E: archive the pre-compaction message history
        // AFTER PRE_COMPACT and BEFORE compact(), so a failed compaction
        // leaves the original verifiably intact under snapshotId. Only when a
        // snapshot archive is resolvable and there are messages to archive;
        // otherwise snapshotId stays null (the "archive not available" case,
        // not a failure — design §8.3 Decision F).
        String snapshotId = null;
        if (snapshotArchive != null && !ctx.getMessages().isEmpty()) {
            snapshotId = snapshotArchive.put(ctx.getMessages());
        }

        // Decision E: CompactionContext is constructed AFTER the archive so
        // the per-event snapshotId can be passed in (final field, no setter).
        CompactionContext compactCtx = new CompactionContext(
                new ArrayList<>(ctx.getMessages()),
                config,
                ctx.getSessionId(),
                agentName,
                ctx,
                tokenEstimator,
                archive,
                snapshotId
        );

        // Design §8.3 Decision F: wrap compact() so a throwing custom
        // compactor (non-PipelineCompactor) does NOT bubble up and abort the
        // agent — the original is already archived, so we retain it and log
        // the failure explicitly with the snapshotId for traceability.
        CompactionResult result;
        try {
            result = contextCompactor.compact(compactCtx);
        } catch (Exception e) {
            LOG.warn("Context compactor threw exception for session {}; original messages retained (snapshotId={})",
                    ctx.getSessionId(), snapshotId, e);
            hookInvoker.invokeHooks(AgentLifecyclePoint.POST_COMPACT, ctx, agentName, null, null);
            return;
        }

        hookInvoker.invokeHooks(AgentLifecyclePoint.POST_COMPACT, ctx, agentName, null, null);

        if (result.getCompactedMessages() != null) {
            if (result.getCompactedMessages().isEmpty()) {
                LOG.warn("Compactor returned empty compactedMessages for session {}, skipping replacement (snapshotId={})",
                        ctx.getSessionId(), snapshotId);
            } else if (result.getTokensAfter() < result.getTokensBefore()) {
                ctx.getMessages().clear();
                ctx.getMessages().addAll(result.getCompactedMessages());
                ctx.setTokensUsed(ctx.getTokensUsed() - (result.getTokensBefore() - result.getTokensAfter()));
                LOG.info("Context compacted: tokens {} -> {}, messages {} -> {} (snapshotId={}) for session {}",
                        result.getTokensBefore(), result.getTokensAfter(),
                        result.getOriginalSize(), result.getCompactedSize(),
                        snapshotId, ctx.getSessionId());

                // "snapshot on compaction" trigger point: after the context
                // has actually been compacted (messages replaced + token
                // accounting adjusted), record a COMPACTION checkpoint
                // marking the new post-compaction baseline. Emitted only when
                // real compaction happened — the NoOpContextCompactor default
                // returns compactedMessages == null, so no spurious checkpoint
                // is produced. With the shipped NoOpCheckpoint default the
                // saveCheckpoint call itself is a no-op.
                //
                // Design §8.3: compactSummary carries snapshotId (traceability
                // to the archived original) + both ratio dimensions
                // (message-count compactedSize/originalSize + token
                // tokensAfter/tokensBefore) so the compression ratio is
                // measurable on two dimensions.
                String compactSummary = "compacted: tokens " + result.getTokensBefore() + "->"
                        + result.getTokensAfter()
                        + " (ratio " + ratio(result.getTokensAfter(), result.getTokensBefore()) + ")"
                        + ", messages " + result.getOriginalSize() + "->" + result.getCompactedSize()
                        + " (ratio " + ratio(result.getCompactedSize(), result.getOriginalSize()) + ")"
                        + ", snapshotId=" + snapshotId;
                String compactionSessionId = ctx.getSessionId();
                long compactExecStart = ctx.getStartTimeMs();
                checkpointManager.saveCheckpoint(Checkpoint.of(
                        compactionSessionId,
                        compactionSessionId != null
                                ? compactionSessionId + ":compact:" + compactExecStart + ":" + checkpointSeq[0]
                                : "anon:compact:" + compactExecStart + ":" + checkpointSeq[0],
                        checkpointSeq[0],
                        System.currentTimeMillis(),
                        CheckpointType.COMPACTION,
                        null,
                        null,
                        null,
                        compactSummary,
                        ctx.getMessages().size(),
                        ctx.getTokensUsed()));
                checkpointSeq[0]++;

                // The persisted session holds the pre-compaction message
                // list, so the persisted session must be re-synchronized.
                // Without this, a crash after compaction would restore
                // pre-compaction messages and break the
                // checkpoint.messageCount <= session.messageCount invariant.
                if (sessionStore != null) {
                    AgentSession persistedCompacted = sessionStore.get(compactionSessionId);
                    if (persistedCompacted != null) {
                        persistedCompacted.replaceMessages(ctx.getMessages());
                        sessionStore.save(persistedCompacted);
                    }
                }
            } else {
                // Design §8.3 Decision F: compaction was attempted (archive
                // retained) but did not reduce tokens — log explicitly with
                // snapshotId instead of silently skipping (Minimum Rules #24).
                LOG.warn("Compactor returned compactedMessages but no token reduction for session {} (tokensBefore={}, tokensAfter={}, snapshotId={}); original retained",
                        ctx.getSessionId(), result.getTokensBefore(), result.getTokensAfter(), snapshotId);
            }
        } else {
            // Design §8.3 Decision F: compaction produced no compacted
            // messages (every strategy returned unchanged / null) — log
            // explicitly with snapshotId instead of silently skipping.
            LOG.warn("Compactor produced no compactedMessages for session {} (snapshotId={}); original retained",
                    ctx.getSessionId(), snapshotId);
        }
    }

    /**
     * Format a compression ratio as a compact percentage string for the
     * compactSummary. Returns {@code "n/a"} when the before value is zero to
     * avoid division-by-zero (e.g. an empty-history edge case).
     */
    private static String ratio(long after, long before) {
        if (before <= 0) {
            return "n/a";
        }
        return String.format(java.util.Locale.ROOT, "%d%%", after * 100 / before);
    }

    /**
     * Resolve the per-session compaction archive for the write side
     * (reference-style compaction strategy PUT). Uses the same
     * {@code sessionStore.get(sessionId)->AgentSession} pattern as the
     * post-compaction re-sync. Lazily materialises the archive on the session
     * via {@link AgentSession#getOrCreateCompactionArchive()} so the very
     * first reference-style compaction creates it and subsequent read-back
     * ({@code read-ref}) finds the same instance.
     * <p>
     * Returns {@code null} when no sessionStore is wired or the session
     * cannot be resolved — the reference-style strategy treats null as
     * "no archive, return explicit unchanged" (no PUT, no shortRef produced).
     */
    private ICompactionArchive resolveArchive(String sessionId) {
        if (sessionStore == null || sessionId == null) {
            return null;
        }
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            return null;
        }
        return session.getOrCreateCompactionArchive();
    }

    /**
     * Resolve the per-session compaction snapshot archive for the write side
     * (design §8.3 Decision B). Lazily materialises the archive on the
     * session via {@link AgentSession#getOrCreateCompactionSnapshotArchive()}
     * so the first compaction creates it and the archived original is
     * retrievable later by snapshotId.
     * <p>
     * Returns {@code null} when no sessionStore is wired or the session
     * cannot be resolved — the coordinator then skips archiving (snapshotId
     * stays null; not a failure, just no archive available).
     */
    private ICompactionSnapshotArchive resolveSnapshotArchive(String sessionId) {
        if (sessionStore == null || sessionId == null) {
            return null;
        }
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            return null;
        }
        return session.getOrCreateCompactionSnapshotArchive();
    }
}

