package io.nop.ai.agent.reliability;

import java.time.Instant;
import java.util.Objects;
import io.nop.ai.agent.engine.NopAiAgentException;

/**
 * The recovery-critical aggregate state captured by a {@code snapshot.json}
 * file (design §5.4a). A snapshot is a derived cache — it can be fully rebuilt
 * from {@code journal.md} — that accelerates recovery by recording the
 * {@code lastWatermark} and context-size snapshot at a known point.
 *
 * <p>This value type carries only the <b>recovery-critical</b> field subset
 * (design §5.4a): {@code snapshotId} / {@code sessionId} / {@code lastWatermark}
 * / {@code messageCount} / {@code tokenEstimate} / {@code createdAt}. The full
 * §5.4a {@code planStatus} and {@code toolResults} aggregations require
 * session-level state access beyond the checkpoint value type and belong to the
 * crash/restart restore successor (Non-Goal of plan 182).
 *
 * <p>Immutable value object: private constructor, static factory, all-field
 * equals/hashCode, no mutators — following the {@link Checkpoint} /
 * {@code DenialRecord} pattern.
 */
public final class CheckpointSnapshot {

    private final String snapshotId;
    private final String sessionId;
    private final String lastWatermark;
    private final int messageCount;
    private final long tokenEstimate;
    private final long createdAtEpochMillis;

    private CheckpointSnapshot(String snapshotId, String sessionId, String lastWatermark,
                               int messageCount, long tokenEstimate, long createdAtEpochMillis) {
        this.snapshotId = snapshotId;
        this.sessionId = sessionId;
        this.lastWatermark = lastWatermark;
        this.messageCount = messageCount;
        this.tokenEstimate = tokenEstimate;
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    /**
     * Create a snapshot capturing the recovery-critical aggregate state at a
     * single point.
     *
     * @param snapshotId            a unique identifier for this snapshot; never null
     * @param sessionId             the session this snapshot belongs to; may be null
     * @param lastWatermark         the watermark of the checkpoint this snapshot
     *                              aggregates up to; never null
     * @param messageCount          the context message count at the snapshot point
     * @param tokenEstimate         the cumulative token estimate at the snapshot point
     * @param createdAtEpochMillis  the snapshot creation timestamp (epoch millis)
     */
    public static CheckpointSnapshot of(String snapshotId, String sessionId, String lastWatermark,
                                        int messageCount, long tokenEstimate, long createdAtEpochMillis) {
        if (snapshotId == null) {
            throw new NopAiAgentException("CheckpointSnapshot.snapshotId must not be null");
        }
        if (lastWatermark == null) {
            throw new NopAiAgentException("CheckpointSnapshot.lastWatermark must not be null");
        }
        if (messageCount < 0) {
            throw new NopAiAgentException(
                    "CheckpointSnapshot.messageCount must not be negative, got: " + messageCount);
        }
        return new CheckpointSnapshot(snapshotId, sessionId, lastWatermark,
                messageCount, tokenEstimate, createdAtEpochMillis);
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getLastWatermark() {
        return lastWatermark;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public long getTokenEstimate() {
        return tokenEstimate;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    /**
     * @return the creation timestamp as ISO-8601 (for §5.4a JSON serialization)
     */
    public String getCreatedAtIso() {
        return Instant.ofEpochMilli(createdAtEpochMillis).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckpointSnapshot that = (CheckpointSnapshot) o;
        return messageCount == that.messageCount
                && tokenEstimate == that.tokenEstimate
                && createdAtEpochMillis == that.createdAtEpochMillis
                && Objects.equals(snapshotId, that.snapshotId)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(lastWatermark, that.lastWatermark);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotId, sessionId, lastWatermark,
                messageCount, tokenEstimate, createdAtEpochMillis);
    }

    @Override
    public String toString() {
        return "CheckpointSnapshot{" +
                "snapshotId='" + snapshotId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", lastWatermark='" + lastWatermark + '\'' +
                ", messageCount=" + messageCount +
                ", tokenEstimate=" + tokenEstimate +
                ", createdAt=" + getCreatedAtIso() +
                '}';
    }
}
