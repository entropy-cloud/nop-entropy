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
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

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

        CompactionContext compactCtx = new CompactionContext(
                new ArrayList<>(ctx.getMessages()),
                config,
                ctx.getSessionId(),
                agentName,
                ctx,
                tokenEstimator
        );

        hookInvoker.executeWithMiddleware(AgentLifecyclePoint.PRE_COMPACT, ctx, agentName, null, null);

        CompactionResult result = contextCompactor.compact(compactCtx);

        hookInvoker.invokeHooks(AgentLifecyclePoint.POST_COMPACT, ctx, agentName, null, null);

        if (result.getCompactedMessages() != null) {
            if (result.getCompactedMessages().isEmpty()) {
                LOG.warn("Compactor returned empty compactedMessages for session {}, skipping replacement",
                        ctx.getSessionId());
            } else if (result.getTokensAfter() < result.getTokensBefore()) {
                ctx.getMessages().clear();
                ctx.getMessages().addAll(result.getCompactedMessages());
                ctx.setTokensUsed(ctx.getTokensUsed() - (result.getTokensBefore() - result.getTokensAfter()));
                LOG.info("Context compacted: tokens {} -> {}, retained {} messages for session {}",
                        result.getTokensBefore(), result.getTokensAfter(),
                        result.getRetainedMessageCount(), ctx.getSessionId());

                // "snapshot on compaction" trigger point): after the context
                // has actually been compacted (messages replaced + token
                // accounting adjusted), record a COMPACTION checkpoint
                // marking the new post-compaction baseline. Emitted only when
                // real compaction happened — the NoOpContextCompactor default
                // returns compactedMessages == null, so no spurious checkpoint
                // is produced. With the shipped NoOpCheckpoint default the
                // saveCheckpoint call itself is a no-op.
                String compactSummary = "compacted: " + result.getTokensBefore() + "->"
                        + result.getTokensAfter() + " tokens, " + result.getRetainedMessageCount()
                        + " messages";
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

                // the message list, so the persisted session must be
                // re-synchronized. Without this, a crash after compaction
                // would restore pre-compaction messages and break the
                // checkpoint.messageCount <= session.messageCount invariant.
                if (sessionStore != null) {
                    AgentSession persistedCompacted = sessionStore.get(compactionSessionId);
                    if (persistedCompacted != null) {
                        persistedCompacted.replaceMessages(ctx.getMessages());
                        sessionStore.save(persistedCompacted);
                    }
                }
            }
        }
    }
}

