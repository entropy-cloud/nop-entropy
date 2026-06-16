package io.nop.ai.agent.reliability;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Functional {@link IGoalTracker} implementing session-level stuck/looping
 * detection via a sliding window of tool-call signatures (design
 * {@code nop-ai-agent-reliability.md} §5.3 / plan 211 / L3-3).
 *
 * <p>Per session, the tracker maintains a sliding window holding the most
 * recent tool-call signatures recorded across iterations. Each
 * {@link #recordIteration} appends the iteration's signatures (one window
 * entry per signature, in request order) and evicts the oldest entries beyond
 * {@code windowSize}. {@link #assessGoal} counts how often each signature
 * appears within the current window; when any signature repeats at least
 * {@code stuckThreshold} times the assessment is {@link GoalAssessment#STUCK},
 * which causes the ReAct loop to abort with status {@code escalated} (the
 * agent is calling the same tool with the same arguments repeatedly without
 * making progress).
 *
 * <p><b>"No tool call" iterations</b>: when {@code recordIteration} receives
 * an empty signature list (the LLM produced no tool calls — the
 * completion-judge branch) no entry is appended. An empty iteration is
 * neither progress evidence nor stuck evidence, so the window state is left
 * unchanged.
 *
 * <p><b>Anonymous execution</b>: a null {@code sessionId} is not tracked
 * (assessGoal reports PROGRESSING). Anonymous execution is a test scenario;
 * production sessions always carry a sessionId (plan 211 Phase 1 adjudication).
 *
 * <p><b>Thread safety</b>: state is tracked per session in a
 * {@link ConcurrentHashMap}. Each session's window mutations are guarded by
 * synchronizing on the {@link SessionState} object, so concurrent callers to
 * the <i>same</i> session see a consistent window, while concurrent callers
 * to <i>different</i> sessions proceed in parallel. This mirrors the
 * {@code ThresholdBreaker} per-entry synchronisation pattern.
 *
 * <p>State is in-memory only (per tracker instance). Persistence /
 * cross-process sharing is a Non-Goal successor (design §11 deferred).
 */
public final class SessionGoalTracker implements IGoalTracker {

    /** Default sliding-window size (number of signature entries retained). */
    public static final int DEFAULT_WINDOW_SIZE = 5;
    /** Default repeat threshold before a session is assessed as STUCK. */
    public static final int DEFAULT_STUCK_THRESHOLD = 3;

    private final int windowSize;
    private final int stuckThreshold;
    private final ConcurrentMap<String, SessionState> entries = new ConcurrentHashMap<>();

    /**
     * Construct a tracker with the default window size (5) and threshold (3).
     */
    public SessionGoalTracker() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_STUCK_THRESHOLD);
    }

    /**
     * @param windowSize     number of signature entries retained in the
     *                       per-session sliding window (must be &gt;= 1)
     * @param stuckThreshold repetitions of the same signature within the
     *                       window required to assess STUCK (must be &gt;= 1)
     */
    public SessionGoalTracker(int windowSize, int stuckThreshold) {
        if (windowSize < 1) {
            throw new IllegalArgumentException(
                    "SessionGoalTracker windowSize must be >= 1: " + windowSize);
        }
        if (stuckThreshold < 1) {
            throw new IllegalArgumentException(
                    "SessionGoalTracker stuckThreshold must be >= 1: " + stuckThreshold);
        }
        this.windowSize = windowSize;
        this.stuckThreshold = stuckThreshold;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public int getStuckThreshold() {
        return stuckThreshold;
    }

    @Override
    public void recordIteration(String sessionId, IterationSnapshot snapshot) {
        if (sessionId == null || snapshot == null) {
            // Anonymous execution is not tracked; a null snapshot is defensive.
            return;
        }
        List<String> signatures = snapshot.getToolCallSignatures();
        if (signatures.isEmpty()) {
            // A no-tool-call iteration is neither progress nor stuck evidence:
            // the window state is left unchanged.
            return;
        }
        SessionState entry = entries.computeIfAbsent(sessionId, k -> new SessionState());
        synchronized (entry) {
            for (String signature : signatures) {
                entry.window.addLast(signature);
            }
            while (entry.window.size() > windowSize) {
                entry.window.removeFirst();
            }
        }
    }

    @Override
    public GoalAssessment assessGoal(String sessionId) {
        if (sessionId == null) {
            return GoalAssessment.PROGRESSING;
        }
        SessionState entry = entries.get(sessionId);
        if (entry == null) {
            return GoalAssessment.PROGRESSING;
        }
        synchronized (entry) {
            // Threshold unreachable given the window cap: no signature can
            // repeat enough times to ever trip — short-circuit to PROGRESSING
            // without counting.
            if (windowSize < stuckThreshold) {
                return GoalAssessment.PROGRESSING;
            }
            for (int count : countSignatures(entry.window).values()) {
                if (count >= stuckThreshold) {
                    return GoalAssessment.STUCK;
                }
            }
            return GoalAssessment.PROGRESSING;
        }
    }

    private static Map<String, Integer> countSignatures(List<String> window) {
        Map<String, Integer> counts = new HashMap<>();
        for (String signature : window) {
            counts.merge(signature, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Per-session mutable state holder. All fields are mutated under the
     * entry's monitor (synchronized on the entry instance).
     */
    private static final class SessionState {
        final LinkedList<String> window = new LinkedList<>();
    }
}
