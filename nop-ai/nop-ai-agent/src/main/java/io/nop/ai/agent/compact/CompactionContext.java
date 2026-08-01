package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.toolkit.api.ICompactionArchive;

import java.util.List;
import java.util.Objects;

public class CompactionContext {

    private final List<ChatMessage> messages;
    private final CompactConfig compactConfig;
    private final String sessionId;
    private final String agentName;
    private final AgentExecutionContext executionContext;
    private final ITokenEstimator tokenEstimator;
    /**
     * Per-session compaction archive. When non-null, the reference-style
     * compaction strategy ({@link ReferenceCompactionStrategy}) PUTs original
     * content here before replacing it with a {@code shortRef} pointer.
     * {@code null} when no archive is available (e.g. the context was built
     * outside the agent engine) — the strategy treats null as "no archive,
     * return explicit unchanged" and never NPEs.
     */
    private final ICompactionArchive compactionArchive;
    /**
     * The per-compaction-event snapshot id of the pre-compaction message
     * history archived by the {@code AgentCompactionCoordinator} before
     * handing the messages to the pipeline (design §8.3 Decision A).
     * {@code null} when no snapshot was archived (no session / not yet
     * archived). Flowed coordinator → {@code CompactionContext} →
     * {@code PipelineCompactor} single final construction point, which writes
     * it into the {@link io.nop.ai.agent.session.CompactionResult}.
     */
    private final String snapshotId;

    public CompactionContext(List<ChatMessage> messages, CompactConfig compactConfig,
                             String sessionId, String agentName,
                             AgentExecutionContext executionContext) {
        this(messages, compactConfig, sessionId, agentName, executionContext, null);
    }

    public CompactionContext(List<ChatMessage> messages, CompactConfig compactConfig,
                             String sessionId, String agentName,
                             AgentExecutionContext executionContext,
                             ITokenEstimator tokenEstimator) {
        this(messages, compactConfig, sessionId, agentName, executionContext, tokenEstimator, null);
    }

    /**
     * Full constructor additionally carrying the per-session
     * {@link ICompactionArchive} (design §8.2 Decision G). The
     * {@code AgentCompactionCoordinator} resolves the archive from the
     * {@code AgentSession} and injects it here so the reference-style
     * strategy can PUT original content.
     *
     * @param compactionArchive the per-session archive; {@code null} is a
     *                          legitimate value (reference-style strategy
     *                          returns explicit unchanged when null)
     */
    public CompactionContext(List<ChatMessage> messages, CompactConfig compactConfig,
                             String sessionId, String agentName,
                             AgentExecutionContext executionContext,
                             ITokenEstimator tokenEstimator,
                             ICompactionArchive compactionArchive) {
        this(messages, compactConfig, sessionId, agentName, executionContext,
                tokenEstimator, compactionArchive, null);
    }

    /**
     * Full constructor additionally carrying the per-compaction-event
     * {@code snapshotId} of the pre-compaction message history (design §8.3
     * Decision A + E). The {@code AgentCompactionCoordinator} archives the
     * original messages before calling {@code compact()} and passes the
     * resulting {@code snapshotId} here so the {@code PipelineCompactor}
     * single final construction point can write it into the
     * {@link io.nop.ai.agent.session.CompactionResult}.
     *
     * @param snapshotId the per-event snapshot id of the archived
     *                   pre-compaction history; {@code null} when no snapshot
     *                   was archived (no session / not yet archived)
     */
    public CompactionContext(List<ChatMessage> messages, CompactConfig compactConfig,
                             String sessionId, String agentName,
                             AgentExecutionContext executionContext,
                             ITokenEstimator tokenEstimator,
                             ICompactionArchive compactionArchive,
                             String snapshotId) {
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        this.compactConfig = compactConfig;
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.executionContext = executionContext;
        this.tokenEstimator = tokenEstimator;
        this.compactionArchive = compactionArchive;
        this.snapshotId = snapshotId;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public CompactConfig getCompactConfig() {
        return compactConfig;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public AgentExecutionContext getExecutionContext() {
        return executionContext;
    }

    public ITokenEstimator getTokenEstimator() {
        return tokenEstimator;
    }

    /**
     * @return the per-session compaction archive, or {@code null} when no
     *         archive is wired (reference-style strategy treats null as
     *         "no archive, return explicit unchanged")
     */
    public ICompactionArchive getCompactionArchive() {
        return compactionArchive;
    }

    /**
     * @return the per-compaction-event snapshot id of the archived
     *         pre-compaction message history, or {@code null} when no snapshot
     *         was archived (design §8.3 Decision A)
     */
    public String getSnapshotId() {
        return snapshotId;
    }
}
