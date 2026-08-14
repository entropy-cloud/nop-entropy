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
    /**
     * Message-count dimension: the message count BEFORE compaction (the
     * original history size). Authoritative message-count measure (design
     * §8.3 Decision D). Defaults to {@link #retainedMessageCount} when
     * constructed via the legacy 5/6-param constructors (best-effort proxy
     * when the caller does not distinguish before/after).
     */
    private final int originalSize;
    /**
     * Message-count dimension: the message count AFTER compaction (the
     * compacted history size). Authoritative message-count measure (design
     * §8.3 Decision D). Defaults to {@link #retainedMessageCount} when
     * constructed via the legacy 5/6-param constructors.
     */
    private final int compactedSize;
    /**
     * Shadowed token count — the number of prompt tokens that were silently
     * removed from the visible context by compaction (design
     * {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md}
     * §3.2). A derived convenience dimension ({@code max(0, tokensBefore -
     * tokensAfter)}); not an independently audited measure.
     */
    private final long shadowedTokenCount;

    public CompactionResult(String sessionId, long tokensBefore, long tokensAfter,
                            int retainedMessageCount, String snapshotId) {
        this(sessionId, tokensBefore, tokensAfter, retainedMessageCount, snapshotId, null);
    }

    public CompactionResult(String sessionId, long tokensBefore, long tokensAfter,
                            int retainedMessageCount, String snapshotId,
                            List<ChatMessage> compactedMessages) {
        // Legacy constructor: caller did not distinguish before/after message
        // counts, so both dimensions default to the supplied single count
        // (best-effort backward-compatible proxy). Production callers that
        // track both dimensions use the 8-param constructor below.
        this(sessionId, tokensBefore, tokensAfter, retainedMessageCount, snapshotId,
                compactedMessages, retainedMessageCount, retainedMessageCount);
    }

    /**
     * Full constructor additionally carrying the authoritative two-dimension
     * message-count measures (design §8.3 Decision D). Used by the
     * {@code PipelineCompactor} single final construction point and by
     * {@code NoOpContextCompactor} to set distinct, correct before/after
     * counts. {@code originalSize} = pre-compaction message count,
     * {@code compactedSize} = post-compaction message count.
     *
     * @param originalSize   message count before compaction
     * @param compactedSize  message count after compaction
     */
    public CompactionResult(String sessionId, long tokensBefore, long tokensAfter,
                            int retainedMessageCount, String snapshotId,
                            List<ChatMessage> compactedMessages,
                            int originalSize, int compactedSize) {
        this(sessionId, tokensBefore, tokensAfter, retainedMessageCount, snapshotId,
                compactedMessages, originalSize, compactedSize,
                Math.max(0, tokensBefore - tokensAfter));
    }

    /**
     * Full constructor additionally carrying the shadowed token count (design
     * §3.2). Legacy 5/6/8-param constructors default the shadowed count to
     * {@code max(0, tokensBefore - tokensAfter)}.
     *
     * @param originalSize       message count before compaction
     * @param compactedSize      message count after compaction
     * @param shadowedTokenCount prompt tokens removed from the visible context
     */
    public CompactionResult(String sessionId, long tokensBefore, long tokensAfter,
                            int retainedMessageCount, String snapshotId,
                            List<ChatMessage> compactedMessages,
                            int originalSize, int compactedSize,
                            long shadowedTokenCount) {
        this.sessionId = sessionId;
        this.tokensBefore = tokensBefore;
        this.tokensAfter = tokensAfter;
        this.retainedMessageCount = retainedMessageCount;
        this.snapshotId = snapshotId;
        this.compactedMessages = compactedMessages;
        this.originalSize = originalSize;
        this.compactedSize = compactedSize;
        this.shadowedTokenCount = shadowedTokenCount;
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

    /**
     * @return message count BEFORE compaction (authoritative message-count
     *         dimension, design §8.3 Decision D)
     */
    public int getOriginalSize() {
        return originalSize;
    }

    /**
     * @return message count AFTER compaction (authoritative message-count
     *         dimension, design §8.3 Decision D)
     */
    public int getCompactedSize() {
        return compactedSize;
    }

    /**
     * @return shadowed token count — prompt tokens removed from the visible
     *         context by compaction (derived convenience dimension, design §3.2)
     */
    public long getShadowedTokenCount() {
        return shadowedTokenCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompactionResult that = (CompactionResult) o;
        return tokensBefore == that.tokensBefore
                && tokensAfter == that.tokensAfter
                && retainedMessageCount == that.retainedMessageCount
                && originalSize == that.originalSize
                && compactedSize == that.compactedSize
                && shadowedTokenCount == that.shadowedTokenCount
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(snapshotId, that.snapshotId)
                && Objects.equals(compactedMessages, that.compactedMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, tokensBefore, tokensAfter, retainedMessageCount,
                snapshotId, compactedMessages, originalSize, compactedSize, shadowedTokenCount);
    }

    @Override
    public String toString() {
        return "CompactionResult{" +
                "sessionId='" + sessionId + '\'' +
                ", tokensBefore=" + tokensBefore +
                ", tokensAfter=" + tokensAfter +
                ", retainedMessageCount=" + retainedMessageCount +
                ", originalSize=" + originalSize +
                ", compactedSize=" + compactedSize +
                ", shadowedTokenCount=" + shadowedTokenCount +
                ", snapshotId='" + snapshotId + '\'' +
                ", compactedMessages=" + (compactedMessages != null ? compactedMessages.size() + " messages" : "null") +
                '}';
    }
}
