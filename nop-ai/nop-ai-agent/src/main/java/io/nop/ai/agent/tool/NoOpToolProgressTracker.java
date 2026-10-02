package io.nop.ai.agent.tool;

/**
 * Plan 2285 (WI4): shipped default — no tool execution introspection wired.
 * Snapshots are explicitly empty and cancellation reports false: the
 * capability is absent, which is observable behaviour (heartbeat payloads
 * carry no output tail), not a silent skip (Minimum Rules #24).
 */
public final class NoOpToolProgressTracker implements IToolProgressTracker {

    private static final NoOpToolProgressTracker INSTANCE = new NoOpToolProgressTracker();

    public static NoOpToolProgressTracker noOp() {
        return INSTANCE;
    }

    private NoOpToolProgressTracker() {
    }

    @Override
    public ToolProgressSnapshot progressSnapshot(String callId) {
        return ToolProgressSnapshot.empty();
    }

    @Override
    public boolean cancelExecution(String callId) {
        return false;
    }
}
