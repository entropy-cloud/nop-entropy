package io.nop.ai.agent.engine;

/**
 * Plan 2287 (WI6): in-flight tool execution phases recorded as phase-tagged
 * TOOL_EXECUTION checkpoints. COMPLETED is NOT a value here — the terminal
 * state is the pre-existing phase-less TOOL_EXECUTION checkpoint written at
 * commit; "no phase segment" is what marks a checkpoint as terminal
 * (design nop-ai-agent-async-tool-execution.md §三.2 不变量③, plan 2287
 * committed-set narrowing).
 */
public enum ToolExecutionPhase {
    /** future created and armed (dispatch intent); side effects may not have started */
    DISPATCHED,
    /** the sandbox/executor observably started the execution (side effects possible) */
    EXECUTING;

    /** Marker embedded in the checkpoint watermark's phase segment. */
    public String watermarkSegment() {
        return "[phase:" + name() + "]";
    }

    /**
     * Extracts the phase segment from a checkpoint watermark, or null when the
     * watermark carries no phase segment (= terminal checkpoint). Sole
     * authority for phase tagging — checkpoint payloads are never parsed.
     */
    public static ToolExecutionPhase fromWatermark(String watermark) {
        if (watermark == null) {
            return null;
        }
        for (ToolExecutionPhase phase : values()) {
            if (watermark.contains(phase.watermarkSegment())) {
                return phase;
            }
        }
        return null;
    }
}
