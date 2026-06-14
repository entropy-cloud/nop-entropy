package io.nop.ai.agent.reliability;

/**
 * The trigger-point category of a {@link Checkpoint} (design §5.4a). The
 * checkpoint subsystem records recovery-safe snapshots at well-defined
 * points in the agent dispatch loop, so a crashed or restarted session can
 * later be resumed from the most recent safe point.
 *
 * <p>Layer 3-4 (this layer) delivers the {@link #TOOL_EXECUTION} trigger point:
 * a checkpoint is recorded after every tool execution completes (the tool
 * result has been generated and added to the context). This is the only
 * trigger point implemented by L3-4.
 *
 * <p>Additional trigger points listed in design §5.4a — LLM-turn checkpoints
 * (recorded after each LLM response) and compaction-triggered snapshots
 * (recorded during context compaction) — belong to roadmap item A4 (the
 * journal/snapshot durable-format successor) and are declared here so the enum
 * is forward-compatible, but they are not emitted by the current dispatch-loop
 * wiring.
 */
public enum CheckpointType {

    /**
     * A checkpoint recorded after a single tool execution completes: the tool
     * result has been generated and added to the {@code AgentExecutionContext}.
     * This is the §5.4a "tool execution after" trigger point — the only one
     * wired by L3-4.
     */
    TOOL_EXECUTION,

    /**
     * Reserved for a checkpoint recorded after an LLM turn completes (the
     * assistant response has been received and added to the context). Declared
     * for forward compatibility; not emitted by the current L3-4 dispatch-loop
     * wiring (roadmap A4).
     */
    LLM_TURN,

    /**
     * Reserved for a full snapshot recorded during context compaction. Declared
     * for forward compatibility; not emitted by the current L3-4 dispatch-loop
     * wiring (roadmap A4).
     */
    COMPACTION
}
