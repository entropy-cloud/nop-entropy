package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.session.CompactConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import java.util.HashMap;
import java.util.Map;

/**
 * Loop-governance abort handlers for the ReAct loop (extracted from
 * {@link ReActAgentExecutor}, MA4.2-05). Implements the denial-ledger
 * pause, goal-tracker STUCK escalation and the Layer 4 forced-stop hard
 * protection (context-overflow), including the best-effort final-summary
 * compaction and terminal-state events.
 */
public class AgentLoopGuard {
    private static final Logger LOG = LoggerFactory.getLogger(AgentLoopGuard.class);

    private final IDenialLedger denialLedger;
    private final ITokenEstimator tokenEstimator;
    private final AgentHookInvoker hookInvoker;
    private final AgentCompactionCoordinator compactionCoordinator;

    public AgentLoopGuard(IDenialLedger denialLedger,
                          ITokenEstimator tokenEstimator,
                          AgentHookInvoker hookInvoker,
                          AgentCompactionCoordinator compactionCoordinator) {
        this.denialLedger = denialLedger;
        this.tokenEstimator = tokenEstimator;
        this.hookInvoker = hookInvoker;
        this.compactionCoordinator = compactionCoordinator;
    }

    // ---- moved verbatim from ReActAgentExecutor (MA4.2-05 split) ----
    /**
     * Mark the session as paused by the denial ledger and emit the
     * {@link AgentEventType#SESSION_PAUSED} event. Used at the ReAct-loop
     * iteration start when {@code denialLedger.isPaused(sessionId)} returns
     * {@code true} (e.g. a prior iteration's threshold-abort carried over).
     *
     * <p>Does not re-record a denial or increment the count — the session is
     * already over threshold. Only the state transition + event are emitted.
     */
    public void handleSessionPaused(AgentExecutionContext ctx, String sessionId, String agentName) {
        ctx.setStatus(AgentExecStatus.paused);
        int count = denialLedger.getDenialCount(sessionId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("denialCount", count);
        payload.put("reason", "denial threshold exceeded (prior iteration)");
        hookInvoker.publishEvent(AgentEventType.SESSION_PAUSED, sessionId, agentName, payload);
        LOG.warn("Session paused by denial ledger: session={}, denialCount={}", sessionId, count);
    }
    /**
     * Plan 211 (L3-3): abort the ReAct loop because the goal tracker assessed
     * the session as STUCK (design {@code nop-ai-agent-reliability.md} §5.3).
     * Mirrors the {@link #handleSessionPaused} governance-abort pattern: set
     * status to {@code escalated}, record a non-empty lastError describing the
     * abort cause, and emit an event so the escalation is observable (no
     * silent skip — Minimum Rules #24).
     *
     * <p>AR-07 (plan 277): publishes {@link AgentEventType#SESSION_ESCALATED}
     * (not {@link AgentEventType#SESSION_PAUSED}). Escalated is a terminal
     * outcome (human re-evaluation required), semantically distinct from a
     * denial-ledger pause (recoverable via resumeSession).
     */
    public void handleGoalStuck(AgentExecutionContext ctx, String sessionId, String agentName) {
        ctx.setStatus(AgentExecStatus.escalated);
        String reason = "goal tracker detected stuck/looping behavior: sessionId="
                + (sessionId != null ? sessionId : "<anonymous>")
                + ", iteration=" + ctx.getCurrentIteration();
        ctx.setLastError(reason);
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", "goal tracker STUCK assessment");
        payload.put("iteration", ctx.getCurrentIteration());
        hookInvoker.publishEvent(AgentEventType.SESSION_ESCALATED, sessionId, agentName, payload);
        LOG.warn("Session aborted by goal tracker (stuck/looping): session={}, iteration={}",
                sessionId, ctx.getCurrentIteration());
    }
    /**
     * Layer 4 forced-stop hard protection (design §7.2 Layer 4 / §7.3). Uses the
     * calibrated {@link ITokenEstimator}'s <b>pre-call</b> estimate: if the
     * estimated pending request exceeds {@code maxContextTokens *
     * forcedStopPercent} (default 0.9), forced stop fires.
     */
    public boolean shouldForceStop(AgentExecutionContext ctx) {
        long maxContextTokens = compactionCoordinator.resolveMaxContextTokens(ctx);
        double forcedStopPercent = CompactConfig.defaults().getForcedStopPercent();
        long estimate = tokenEstimator.estimateTokens(ctx.getMessages());
        if (estimate > maxContextTokens * forcedStopPercent) {
            LOG.warn("Forced-stop triggered: pre-call estimate {} exceeds {}% of maxContextTokens {}. session={}",
                    estimate, forcedStopPercent, maxContextTokens, ctx.getSessionId());
            return true;
        }
        return false;
    }

    public void handleForcedStop(AgentExecutionContext ctx, String sessionId, String agentName,
                                  int[] checkpointSeq) {
        long maxContextTokens = compactionCoordinator.resolveMaxContextTokens(ctx);
        long estimate = tokenEstimator.estimateTokens(ctx.getMessages());

        // Best-effort final summary: run the compaction pipeline (Layer 1 -> 2 -> 3)
        // so a final summary/tail is retained for the record. Never fails the agent.
        try {
            compactionCoordinator.performCompaction(ctx, agentName, checkpointSeq);
        } catch (Exception e) {
            LOG.warn("Final-summary compaction during forced stop failed, continuing with tail retention. session={}",
                    sessionId, e);
        }

        ctx.setStatus(AgentExecStatus.forced_stopped);
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", "context-window-overflow");
        payload.put("estimatedTokens", estimate);
        payload.put("maxContextTokens", maxContextTokens);
        payload.put("forcedStopPercent", CompactConfig.defaults().getForcedStopPercent());
        hookInvoker.publishEvent(AgentEventType.FORCED_STOP, sessionId, agentName, payload);
    }
}

