package io.nop.ai.agent.reliability;

import java.util.Objects;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.StringHelper;

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
 *   <li>{@code idempotencyKey} is the deterministic SHA-256 fingerprint of the
 *       tool-call input ({@code hash(toolName + callId + inputSummary)}) for a
 *       {@code TOOL_EXECUTION} checkpoint, enabling restore-time divergence
 *       detection (design §13.2). {@code null} for other types / old data.</li>
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

    /**
     * The fixed length of the idempotency-key hex string (32 hex chars =
     * 128 bits). Same truncation length as the security-layer
     * {@code ActionFingerprint.FINGERPRINT_HEX_LENGTH} so both fingerprints
     * share collision-probability characteristics.
     */
    static final int IDEMPOTENCY_KEY_HEX_LENGTH = 32;

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
    private final String idempotencyKey;
    private final String waitFor;

    private Checkpoint(String sessionId, String watermark, int seq, long timestamp,
                       CheckpointType type, String toolName, String callId,
                       String inputSummary, String outputSummary,
                       int messageCount, long tokenEstimate, String idempotencyKey,
                       String waitFor) {
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
        this.idempotencyKey = idempotencyKey;
        this.waitFor = waitFor;
    }

    /**
     * Create a checkpoint capturing the structured context at a single
     * dispatch-loop trigger point. The {@code idempotency_key} is computed
     * internally from the raw materials via {@link #computeIdempotencyKey}
     * (design §13.2 Decision C/F): for a {@code TOOL_EXECUTION} checkpoint the
     * key is {@code sha256Hex(toolName + "|" + callId + "|" + inputSummary)};
     * for other types the key is {@code null} (those checkpoints do not
     * represent tool-call divergence points and explicitly do not participate
     * in divergence detection).
     *
     * <p>This overload is the construction entry point used by the dispatch
     * loop (100+ call sites unchanged). Deserialization paths that read a
     * pre-computed/stored key must use {@link #of(String, String, int, long,
     * CheckpointType, String, String, String, String, int, long, String)} to
     * pass the stored key through verbatim (recomputing would silently "fix" a
     * tampered key and defeat divergence detection — design §13.2 Decision D).
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
        return of(sessionId, watermark, seq, timestamp, type, toolName, callId,
                inputSummary, outputSummary, messageCount, tokenEstimate,
                computeIdempotencyKey(type, toolName, callId, inputSummary), null);
    }

    /**
     * Create a checkpoint with an explicit, pre-computed {@code idempotency_key}.
     * Used by the deserialization paths ({@code DBCheckpointManager.readCheckpoint},
     * {@code CheckpointJournalReader.parseSection}) that read the key from
     * persistent storage and must pass it through verbatim rather than
     * recomputing it (design §13.2 Decision D).
     *
     * @param idempotencyKey the pre-computed/stored idempotency key; may be
     *                       null (old data, non-{@code TOOL_EXECUTION} type per
     *                       Decision F, or {@link NoOpCheckpoint} which never
     *                       persists)
     */
    public static Checkpoint of(String sessionId, String watermark, int seq, long timestamp,
                                CheckpointType type, String toolName, String callId,
                                String inputSummary, String outputSummary,
                                int messageCount, long tokenEstimate, String idempotencyKey) {
        return of(sessionId, watermark, seq, timestamp, type, toolName, callId,
                inputSummary, outputSummary, messageCount, tokenEstimate, idempotencyKey, null);
    }

    /**
     * Create a checkpoint with an explicit {@code wait_for} condition JSON
     * (design §13.1 Decision A). Used by the WAIT_FOR checkpoint producer
     * (ReAct loop condition registration point) and by deserialization paths
     * that read the condition from persistent storage. For non-
     * {@code WAIT_FOR} types the {@code waitFor} parameter should be
     * {@code null}.
     *
     * @param idempotencyKey the pre-computed/stored idempotency key; may be null
     * @param waitFor        the wait condition JSON string; may be null
     *                       (non-{@code WAIT_FOR} types / old data)
     */
    public static Checkpoint of(String sessionId, String watermark, int seq, long timestamp,
                                CheckpointType type, String toolName, String callId,
                                String inputSummary, String outputSummary,
                                int messageCount, long tokenEstimate, String idempotencyKey,
                                String waitFor) {
        if (watermark == null) {
            throw new NopAiAgentException("Checkpoint.watermark must not be null");
        }
        if (type == null) {
            throw new NopAiAgentException("Checkpoint.type must not be null");
        }
        if (seq < 0) {
            throw new NopAiAgentException(
                    "Checkpoint.seq must not be negative, got: " + seq);
        }
        if (messageCount < 0) {
            throw new NopAiAgentException(
                    "Checkpoint.messageCount must not be negative, got: " + messageCount);
        }
        return new Checkpoint(sessionId, watermark, seq, timestamp, type, toolName, callId,
                inputSummary, outputSummary, messageCount, tokenEstimate, idempotencyKey,
                waitFor);
    }

    /**
     * Compute the deterministic idempotency key for a checkpoint (design
     * §13.2 Decision F). For {@link CheckpointType#TOOL_EXECUTION} the key is
     * {@code sha256Hex(toolName + "|" + callId + "|" + inputSummary)} truncated
     * to 32 hex chars (128 bits), reusing the platform SHA-256 helper and the
     * same truncation length as the security-layer
     * {@code ActionFingerprint.FINGERPRINT_HEX_LENGTH}. Null components are
     * uniformly treated as empty strings so a missing component consistently
     * maps to a stable key. For {@code LLM_TURN} / {@code COMPACTION} the key
     * is {@code null} — those checkpoint types do not represent tool-call
     * divergence points and explicitly do not participate in divergence
     * detection (restore treats a null key as "no check", not a silent skip).
     *
     * @param type         the checkpoint type; never null
     * @param toolName     the tool name; may be null
     * @param callId       the tool-call id; may be null
     * @param inputSummary the tool-call input fingerprint material; may be null
     * @return the 32-hex-char idempotency key for {@code TOOL_EXECUTION}, or
     *         {@code null} for non-{@code TOOL_EXECUTION} types
     */
    public static String computeIdempotencyKey(CheckpointType type, String toolName,
                                               String callId, String inputSummary) {
        if (type != CheckpointType.TOOL_EXECUTION) {
            return null;
        }
        String kind = toolName != null ? toolName : "";
        String call = callId != null ? callId : "";
        String input = inputSummary != null ? inputSummary : "";
        String material = kind + "|" + call + "|" + input;
        byte[] digest = HashHelper.sha256(StringHelper.utf8Bytes(material), null);
        String hex = StringHelper.bytesToHex(digest);
        if (hex.length() > IDEMPOTENCY_KEY_HEX_LENGTH) {
            hex = hex.substring(0, IDEMPOTENCY_KEY_HEX_LENGTH);
        }
        return hex;
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

    /**
     * @return the deterministic idempotency key for a
     *         {@code TOOL_EXECUTION} checkpoint (32 hex chars), or {@code null}
     *         for other types / old data (design §13.2 Decision F). A non-null
     *         key enables restore-time divergence detection; a null key makes
     *         restore fall back to the best-effort message-count check.
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * @return the wait condition JSON for a {@code WAIT_FOR} checkpoint
     *         (design §13.1 Decision A), or {@code null} for other types /
     *         old data.
     */
    public String getWaitFor() {
        return waitFor;
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
                && Objects.equals(outputSummary, that.outputSummary)
                && Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(waitFor, that.waitFor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, watermark, seq, timestamp, type, toolName, callId,
                inputSummary, outputSummary, messageCount, tokenEstimate, idempotencyKey, waitFor);
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
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", waitFor='" + waitFor + '\'' +
                '}';
    }
}
