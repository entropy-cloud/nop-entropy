package io.nop.ai.agent.reliability;

import java.util.Objects;

/**
 * The structured record of a single recovery-safe checkpoint, produced at a
 * dispatch-loop trigger point (design §5.4) and handed to
 * {@link ICheckpointManager#saveCheckpoint}. Follows the same immutable
 * value-object pattern as {@code DenialRecord} and {@code ApprovalDecision}:
 * private constructor, static factory, all-field equals/hashCode, no mutators.
 *
 * <p>A {@code Checkpoint} captures enough context for a crash/restart recovery
 * successor to reconstruct the session state at the checkpoint point:
 * <ul>
 *   <li>{@code sessionId} + {@code seq} identify which session and the
 *       monotonically increasing checkpoint order within that session.</li>
 *   <li>{@code watermark} is the unique retrieval key used by
 *       {@link ICheckpointManager#getCheckpoint} and by the journal/snapshot
 *       successor (roadmap A4).</li>
 *   <li>{@code type} records which dispatch-loop trigger point produced this
 *       checkpoint (L3-4 records {@link CheckpointType#TOOL_EXECUTION}).</li>
 *   <li>{@code toolName} / {@code callId} / {@code inputSummary} /
 *       {@code outputSummary} carry the tool-call payload for a
 *       {@code TOOL_EXECUTION} checkpoint (null for other types).</li>
 *   <li>{@code messageCount} / {@code tokenEstimate} snapshot the context
 *       size at the checkpoint point.</li>
 * </ul>
 *
 * <p><b>Persistence non-mandate</b>: a {@code Checkpoint} is a pure data
 * holder. Whether it is persisted is a property of the
 * {@link ICheckpointManager} implementation (the {@link NoOpCheckpoint}
 * default does not persist; the DB-backed {@link DBCheckpointManager} does).
 * This is consistent with the L3-6 {@code IDenialLedger} persistence-narrowing
 * (finding L3-G5): persistence is an implementation property, not an
 * interface contract.
 */
public final class Checkpoint {

    private final String sessionId;
    private final String watermark;
    private final int seq;
    private final long timestamp;
    private final CheckpointType type;
    private final String toolName;
    private final String callId;
    private final String inputSummary;
    private final String outputSummary;
    private final int messageCount;
    private final long tokenEstimate;

    private Checkpoint(String sessionId, String watermark, int seq, long timestamp,
                       CheckpointType type, String toolName, String callId,
                       String inputSummary, String outputSummary,
                       int messageCount, long tokenEstimate) {
        this.sessionId = sessionId;
        this.watermark = watermark;
        this.seq = seq;
        this.timestamp = timestamp;
        this.type = type;
        this.toolName = toolName;
        this.callId = callId;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.messageCount = messageCount;
        this.tokenEstimate = tokenEstimate;
    }

    /**
     * Create a checkpoint capturing the structured context at a single
     * dispatch-loop trigger point.
     *
     * @param sessionId     the session identifier; may be null (anonymous —
     *                      the NoOp default ignores it, the functional
     *                      implementation treats null as a transient key)
     * @param watermark     the unique retrieval key for this checkpoint;
     *                      never null (used by {@link ICheckpointManager#getCheckpoint})
     * @param seq           the per-session monotonically increasing sequence
     *                      number (0-based)
     * @param timestamp     the checkpoint timestamp (epoch millis)
     * @param type          the dispatch-loop trigger point that produced this
     *                      checkpoint; never null
     * @param toolName      the tool name for a {@code TOOL_EXECUTION} checkpoint;
     *                      may be null for other types
     * @param callId        the tool-call id for a {@code TOOL_EXECUTION} checkpoint;
     *                      may be null for other types
     * @param inputSummary  a short summary of the tool-call input; may be null
     * @param outputSummary a short summary of the tool-call output; may be null
     * @param messageCount  the context message count at the checkpoint point
     * @param tokenEstimate the cumulative token estimate at the checkpoint point
     */
    public static Checkpoint of(String sessionId, String watermark, int seq, long timestamp,
                                CheckpointType type, String toolName, String callId,
                                String inputSummary, String outputSummary,
                                int messageCount, long tokenEstimate) {
        if (watermark == null) {
            throw new IllegalArgumentException("Checkpoint.watermark must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Checkpoint.type must not be null");
        }
        if (seq < 0) {
            throw new IllegalArgumentException(
                    "Checkpoint.seq must not be negative, got: " + seq);
        }
        if (messageCount < 0) {
            throw new IllegalArgumentException(
                    "Checkpoint.messageCount must not be negative, got: " + messageCount);
        }
        return new Checkpoint(sessionId, watermark, seq, timestamp, type, toolName, callId,
                inputSummary, outputSummary, messageCount, tokenEstimate);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getWatermark() {
        return watermark;
    }

    public int getSeq() {
        return seq;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public CheckpointType getType() {
        return type;
    }

    public String getToolName() {
        return toolName;
    }

    public String getCallId() {
        return callId;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public long getTokenEstimate() {
        return tokenEstimate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Checkpoint that = (Checkpoint) o;
        return seq == that.seq
                && timestamp == that.timestamp
                && messageCount == that.messageCount
                && tokenEstimate == that.tokenEstimate
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(watermark, that.watermark)
                && type == that.type
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(callId, that.callId)
                && Objects.equals(inputSummary, that.inputSummary)
                && Objects.equals(outputSummary, that.outputSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, watermark, seq, timestamp, type, toolName, callId,
                inputSummary, outputSummary, messageCount, tokenEstimate);
    }

    @Override
    public String toString() {
        return "Checkpoint{" +
                "sessionId='" + sessionId + '\'' +
                ", watermark='" + watermark + '\'' +
                ", seq=" + seq +
                ", timestamp=" + timestamp +
                ", type=" + type +
                ", toolName='" + toolName + '\'' +
                ", callId='" + callId + '\'' +
                ", messageCount=" + messageCount +
                ", tokenEstimate=" + tokenEstimate +
                '}';
    }
}
