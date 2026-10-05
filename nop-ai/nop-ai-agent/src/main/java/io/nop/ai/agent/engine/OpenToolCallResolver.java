package io.nop.ai.agent.engine;

import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the set of open (un-answered) tool calls from a message list and
 * diffs it against the committed TOOL_EXECUTION checkpoint callIds
 * (roadmap WI3 invariant 3: the crash-recovery open tool_call set is
 * determinable as snapshot messages × TOOL_EXECUTION checkpoint (callId
 * idempotency key) set difference — design
 * {@code nop-ai-agent-async-tool-execution.md} §三.2).
 *
 * <p>Pairing is type-based (instanceof), never role-string matching:
 * a {@link ChatToolCallMessage} is closed iff a
 * {@link ChatToolResponseMessage} carrying the same callId appears later in
 * the list. This treats security-chain deny results and PRE/POST_TOOL_ATTEMPT
 * veto results (both are real ChatToolResponseMessage items) as terminal —
 * only calls with no result at all are open.
 *
 * <p>The checkpoint side of the diff distinguishes two open sub-states at
 * restore time (design §三.3 persistence row / plan 2284 three-state ruling):
 * open-without-checkpoint = the call never committed (recoverable orphan,
 * alarm-worthy); open-with-checkpoint = committed but lost in the
 * checkpoint→session-sync window (already covered by the messageCount
 * consistency warning, not re-alarmed).
 */
public final class OpenToolCallResolver {

    private OpenToolCallResolver() {
    }

    /**
     * Returns the open tool-call messages, in message-list order: each
     * ChatToolCallMessage whose callId has no ChatToolResponseMessage anywhere
     * in the list (callIds are provider-unique, so position-independent
     * answered lookup is equivalent and defensive).
     */
    public static List<ChatToolCallMessage> findOpenToolCalls(List<ChatMessage> messages) {
        Set<String> answered = collectAnsweredCallIds(messages);
        List<ChatToolCallMessage> open = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ChatMessage message : messages) {
            if (!(message instanceof ChatToolCallMessage)) {
                continue;
            }
            ChatToolCallMessage call = (ChatToolCallMessage) message;
            // a repeated callId would be a provider contract violation; keep
            // the first occurrence open iff unanswered (dedup defensive, no
            // silent duplicate entries in the result)
            if (seen.add(call.getCallId()) && !answered.contains(call.getCallId())) {
                open.add(call);
            }
        }
        return open;
    }

    /**
     * Returns the callIds of the open tool calls (see
     * {@link #findOpenToolCalls(List)}), preserving message-list order.
     */
    public static Set<String> findOpenCallIds(List<ChatMessage> messages) {
        Set<String> answered = collectAnsweredCallIds(messages);
        Set<String> open = new LinkedHashSet<>();
        for (ChatMessage message : messages) {
            if (message instanceof ChatToolCallMessage
                    && !answered.contains(((ChatToolCallMessage) message).getCallId())) {
                open.add(((ChatToolCallMessage) message).getCallId());
            }
        }
        return open;
    }

    /**
     * Three-state restore diff (plan 2284): among the message-level open
     * callIds, splits out those never committed (no TOOL_EXECUTION checkpoint)
     * — the recoverable-orphan set that restore should alarm on. Open callIds
     * that DO have a checkpoint belong to the commit→session-sync crash window
     * and are reported separately by {@link #filterCommittedButUnsynced} so the
     * existing messageCount warning stays the single owner of that state.
     */
    public static Set<String> filterNeverCommitted(Set<String> openCallIds, List<Checkpoint> checkpoints) {
        Set<String> committed = collectCheckpointedCallIds(checkpoints);
        Set<String> neverCommitted = new LinkedHashSet<>();
        for (String callId : openCallIds) {
            if (!committed.contains(callId)) {
                neverCommitted.add(callId);
            }
        }
        return neverCommitted;
    }

    /**
     * The complement of {@link #filterNeverCommitted}: open callIds that have
     * a TOOL_EXECUTION checkpoint (committed but the session sync did not
     * persist before the crash). Returned for observability; the restore path
     * logs these at debug only (the messageCount warning already covers them).
     */
    public static Set<String> filterCommittedButUnsynced(Set<String> openCallIds, List<Checkpoint> checkpoints) {
        Set<String> committed = collectCheckpointedCallIds(checkpoints);
        Set<String> unsynced = new LinkedHashSet<>();
        for (String callId : openCallIds) {
            if (committed.contains(callId)) {
                unsynced.add(callId);
            }
        }
        return unsynced;
    }

    private static Set<String> collectAnsweredCallIds(List<ChatMessage> messages) {
        Set<String> answered = new HashSet<>();
        for (ChatMessage message : messages) {
            if (message instanceof ChatToolResponseMessage) {
                answered.add(((ChatToolResponseMessage) message).getCallId());
            }
        }
        return answered;
    }

    private static Set<String> collectCheckpointedCallIds(List<Checkpoint> checkpoints) {
        // plan 2287 (WI6) committed-set narrowing: only TERMINAL checkpoints
        // (no phase segment in the watermark) count as committed — phase-tagged
        // DISPATCHED/EXECUTING checkpoints describe in-flight executions whose
        // results are undeterminable; counting them as committed would
        // silently silence the orphan alarm (invariant ③ state (a))
        Set<String> callIds = new HashSet<>();
        if (checkpoints == null) {
            return callIds;
        }
        for (Checkpoint checkpoint : checkpoints) {
            if (checkpoint.getType() == CheckpointType.TOOL_EXECUTION
                    && checkpoint.getCallId() != null
                    && ToolExecutionPhase.fromWatermark(checkpoint.getWatermark()) == null) {
                callIds.add(checkpoint.getCallId());
            }
        }
        return callIds;
    }

    /**
     * Plan 2287 (WI6): resolves the furthest execution phase recorded for an
     * open callId. COMPLETED (a terminal, phase-less checkpoint exists) beats
     * EXECUTING beats DISPATCHED beats NEVER_STARTED (null). Conservative
     * recovery discipline: anything below COMPLETED means the result is
     * undeterminable — explicit failure, never a blind rerun.
     */
    public static ToolExecutionPhase resolveExecutionPhase(String callId, List<Checkpoint> checkpoints) {
        if (callId == null || checkpoints == null) {
            return null;
        }
        ToolExecutionPhase furthest = null;
        for (Checkpoint checkpoint : checkpoints) {
            if (checkpoint.getType() != CheckpointType.TOOL_EXECUTION
                    || !callId.equals(checkpoint.getCallId())) {
                continue;
            }
            ToolExecutionPhase phase = ToolExecutionPhase.fromWatermark(checkpoint.getWatermark());
            if (phase == null) {
                return null; // terminal beats everything
            }
            if (furthest == null || phase == ToolExecutionPhase.EXECUTING) {
                furthest = phase;
            }
        }
        return furthest;
    }
}
