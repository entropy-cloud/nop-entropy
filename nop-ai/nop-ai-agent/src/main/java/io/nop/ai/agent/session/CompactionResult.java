package io.nop.ai.agent.session;

import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.List;
import java.util.Objects;

public class CompactionResult {

    private final String sessionId;
    private final long tokensBefore;
    private final long tokensAfter;
    private final int retainedMessageCount;
    private final String snapshotId;
    private final List<ChatMessage> compactedMessages;

    public CompactionResult(String sessionId, long tokensBefore, long tokensAfter,
                            int retainedMessageCount, String snapshotId) {
        this(sessionId, tokensBefore, tokensAfter, retainedMessageCount, snapshotId, null);
    }

    public CompactionResult(String sessionId, long tokensBefore, long tokensAfter,
                            int retainedMessageCount, String snapshotId,
                            List<ChatMessage> compactedMessages) {
        this.sessionId = sessionId;
        this.tokensBefore = tokensBefore;
        this.tokensAfter = tokensAfter;
        this.retainedMessageCount = retainedMessageCount;
        this.snapshotId = snapshotId;
        this.compactedMessages = compactedMessages;
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

    public List<ChatMessage> getCompactedMessages() {
        return compactedMessages;
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
                && Objects.equals(snapshotId, that.snapshotId)
                && Objects.equals(compactedMessages, that.compactedMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, tokensBefore, tokensAfter, retainedMessageCount, snapshotId, compactedMessages);
    }

    @Override
    public String toString() {
        return "CompactionResult{" +
                "sessionId='" + sessionId + '\'' +
                ", tokensBefore=" + tokensBefore +
                ", tokensAfter=" + tokensAfter +
                ", retainedMessageCount=" + retainedMessageCount +
                ", snapshotId='" + snapshotId + '\'' +
                ", compactedMessages=" + (compactedMessages != null ? compactedMessages.size() + " messages" : "null") +
                '}';
    }
}
