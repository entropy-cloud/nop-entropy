package io.nop.ai.toolkit.tools.sandbox;

/**
 * Plan 2285 (WI4): an immutable progress snapshot of an in-flight bash
 * execution — the incremental output buffer tail and its byte size. The
 * heartbeat wakeup builder carries this as the progress evidence that lets
 * the model distinguish a slow-but-progressing command from a hung one
 * (design nop-ai-agent-async-tool-execution.md §三.6 进度证据接缝).
 */
public final class BashProgressSnapshot {

    private static final BashProgressSnapshot EMPTY = new BashProgressSnapshot("", 0L, false);

    private final String outputTail;
    private final long outputBytes;
    private final boolean inFlight;

    public BashProgressSnapshot(String outputTail, long outputBytes, boolean inFlight) {
        this.outputTail = outputTail != null ? outputTail : "";
        this.outputBytes = outputBytes;
        this.inFlight = inFlight;
    }

    /**
     * The explicit "no tracking information" snapshot — returned when the
     * callId has no registered execution (never executed, already finished,
     * or no sandbox introspection wired). An explicit value, not a silent
     * null (Minimum Rules #24).
     */
    public static BashProgressSnapshot empty() {
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
