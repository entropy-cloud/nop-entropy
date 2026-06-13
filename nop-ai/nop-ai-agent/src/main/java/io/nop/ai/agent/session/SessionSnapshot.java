package io.nop.ai.agent.session;

import java.util.Objects;

public class SessionSnapshot {

    private final String snapshotId;
    private final String sessionId;
    private final long createdAt;
    private final int messageCount;
    private final long tokenEstimate;
    private final String storageRef;

    public SessionSnapshot(String snapshotId, String sessionId, long createdAt,
                           int messageCount, long tokenEstimate, String storageRef) {
        this.snapshotId = snapshotId;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.messageCount = messageCount;
        this.tokenEstimate = tokenEstimate;
        this.storageRef = storageRef;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public long getTokenEstimate() {
        return tokenEstimate;
    }

    public String getStorageRef() {
        return storageRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionSnapshot that = (SessionSnapshot) o;
        return createdAt == that.createdAt
                && messageCount == that.messageCount
                && tokenEstimate == that.tokenEstimate
                && Objects.equals(snapshotId, that.snapshotId)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(storageRef, that.storageRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotId, sessionId, createdAt, messageCount, tokenEstimate, storageRef);
    }

    @Override
    public String toString() {
        return "SessionSnapshot{" +
                "snapshotId='" + snapshotId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", createdAt=" + createdAt +
                ", messageCount=" + messageCount +
                ", tokenEstimate=" + tokenEstimate +
                ", storageRef='" + storageRef + '\'' +
                '}';
    }
}
