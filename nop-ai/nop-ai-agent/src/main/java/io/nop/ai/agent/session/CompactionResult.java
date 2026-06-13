package io.nop.ai.agent.session;

import java.util.Objects;

public class CompactionResult {

    private final String sessionId;
    private final long tokensBefore;
    private final long tokensAfter;
    private final int retainedMessageCount;
    private final String snapshotId;

    public CompactionResult(String sessionId, long tokensBefore, long tokensAfter,
                            int retainedMessageCount, String snapshotId) {
        this.sessionId = sessionId;
        this.tokensBefore = tokensBefore;
        this.tokensAfter = tokensAfter;
        this.retainedMessageCount = retainedMessageCount;
        this.snapshotId = snapshotId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getTokensBefore() {
        return tokensBefore;
    }

    public long getTokensAfter() {
        return tokensAfter;
    }

    public int getRetainedMessageCount() {
        return retainedMessageCount;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompactionResult that = (CompactionResult) o;
        return tokensBefore == that.tokensBefore
                && tokensAfter == that.tokensAfter
                && retainedMessageCount == that.retainedMessageCount
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(snapshotId, that.snapshotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, tokensBefore, tokensAfter, retainedMessageCount, snapshotId);
    }

    @Override
    public String toString() {
        return "CompactionResult{" +
                "sessionId='" + sessionId + '\'' +
                ", tokensBefore=" + tokensBefore +
                ", tokensAfter=" + tokensAfter +
                ", retainedMessageCount=" + retainedMessageCount +
                ", snapshotId='" + snapshotId + '\'' +
                '}';
    }
}
