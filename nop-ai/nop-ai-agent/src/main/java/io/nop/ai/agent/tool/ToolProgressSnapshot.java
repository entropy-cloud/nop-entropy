package io.nop.ai.agent.tool;

/**
 * Plan 2285 (WI4): agent-side view of a running tool execution's progress —
 * the incremental output tail and its byte size. Produced by
 * {@link IToolProgressTracker#progressSnapshot(String)} and carried in the
 * heartbeat wakeup payload as the evidence the model uses to distinguish a
 * slow-but-progressing command from a hung one (design
 * nop-ai-agent-async-tool-execution.md §三.6 进度证据接缝).
 */
public final class ToolProgressSnapshot {

    private static final ToolProgressSnapshot EMPTY = new ToolProgressSnapshot("", 0L, false);

    private final String outputTail;
    private final long outputBytes;
    private final boolean inFlight;

    public ToolProgressSnapshot(String outputTail, long outputBytes, boolean inFlight) {
        this.outputTail = outputTail != null ? outputTail : "";
        this.outputBytes = outputBytes;
        this.inFlight = inFlight;
    }

    /**
     * The explicit "no tracking information" snapshot — no tracker wired, or
     * the callId has no registered execution. An explicit value, not a silent
     * null (Minimum Rules #24).
     */
    public static ToolProgressSnapshot empty() {
        return EMPTY;
    }

    public String getOutputTail() {
        return outputTail;
    }

    public long getOutputBytes() {
        return outputBytes;
    }

    public boolean isInFlight() {
        return inFlight;
    }
}
