package io.nop.ai.agent.reliability;

/**
 * The trigger-point category of a {@link Checkpoint} (design §5.4a). The
 * checkpoint subsystem records recovery-safe snapshots at well-defined
 * points in the agent dispatch loop, so a crashed or restarted session can
 * later be resumed from the most recent safe point.
 *
 * <p>All three §5.4a trigger points are wired by the dispatch loop:
 * <ul>
 *   <li>{@link #TOOL_EXECUTION} — after every tool execution completes
 *       (plan 181).</li>
 *   <li>{@link #LLM_TURN} — after each successful LLM response is added to
 *       the context (plan 187, A4 trigger-point wiring).</li>
 *   <li>{@link #COMPACTION} — after actual context compaction succeeds
 *       (plan 187).</li>
 * </ul>
 */
public enum CheckpointType {

    /**
     * A checkpoint recorded after a single tool execution completes: the tool
     * result has been generated and added to the {@code AgentExecutionContext}.
     * This is the §5.4a "tool execution after" trigger point.
     */
    TOOL_EXECUTION,

    /**
     * A checkpoint recorded after an LLM turn completes (the assistant
     * response has been received and added to the context). This provides a
     * finer-grained recovery point than {@link #TOOL_EXECUTION} — a crash
     * between the LLM response and the next tool execution resumes from this
     * turn instead of the previous tool call. Emitted by the dispatch loop
     * after token accounting completes and before the completion judge (plan
     * 187, A4 trigger-point wiring).
     */
    LLM_TURN,

    /**
     * A full snapshot recorded after context compaction succeeds (compacted
     * messages have replaced the context and token accounting has been
     * adjusted). Marks the post-compaction baseline so a crash after
     * compaction restores the compacted message history. Emitted by
     * {@code performCompaction} only when real compaction occurs (plan 187).
     */
    COMPACTION
}
