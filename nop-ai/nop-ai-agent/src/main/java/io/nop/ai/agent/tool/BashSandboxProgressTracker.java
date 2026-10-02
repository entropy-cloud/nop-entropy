package io.nop.ai.agent.tool;

import io.nop.ai.toolkit.tools.sandbox.BashProgressSnapshot;
import io.nop.ai.toolkit.tools.sandbox.IBashSandbox;

/**
 * Plan 2285 (WI4): production adapter from the toolkit {@link IBashSandbox}
 * progress/cancel seam to the agent-side {@link IToolProgressTracker}.
 *
 * <p><b>Assembly constraint</b>: the wrapped sandbox instance MUST be the
 * same instance the executing {@code BashExecutor} holds — registrations are
 * instance state. A mismatched instance yields explicit-empty snapshots and
 * failed cancellations (observable), never a crash.
 */
public final class BashSandboxProgressTracker implements IToolProgressTracker {

    private final IBashSandbox sandbox;

    public BashSandboxProgressTracker(IBashSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public ToolProgressSnapshot progressSnapshot(String callId) {
        BashProgressSnapshot snapshot = sandbox.progressSnapshot(callId);
        return new ToolProgressSnapshot(snapshot.getOutputTail(), snapshot.getOutputBytes(),
                snapshot.isInFlight());
    }

    @Override
    public boolean cancelExecution(String callId) {
        return sandbox.cancelExecution(callId);
    }
}
