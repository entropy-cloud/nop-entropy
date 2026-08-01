package io.nop.ai.agent.reliability;

/**
 * Default no-op {@link IWaitCoordinator} (design §13.1 Decision G). Returns
 * {@link WaitDecision#none} unconditionally, making WAIT_FOR opt-in. When
 * this coordinator is wired (the shipped default), the ReAct loop's wait
 * registration point is a no-op — no wait is ever registered, no session
 * is ever suspended, zero regression.
 */
public final class NoOpWaitCoordinator implements IWaitCoordinator {

    private static final NoOpWaitCoordinator INSTANCE = new NoOpWaitCoordinator();

    public static NoOpWaitCoordinator noOp() {
        return INSTANCE;
    }

    private NoOpWaitCoordinator() {
    }

    @Override
    public void requestWait(String sessionId, WaitCondition condition) {
        // no-op: wait requests are silently ignored (WAIT_FOR is opt-in)
    }

    @Override
    public WaitDecision checkWait(String sessionId) {
        return WaitDecision.none();
    }

    @Override
    public void deliverWake(String sessionId, Object payload) {
        // no-op
    }

    @Override
    public boolean isWaiting(String sessionId) {
        return false;
    }
}
