package io.nop.ai.agent.reliability;

/**
 * Reliability-layer checkpoint contract (design §5.4): records and retrieves
 * recovery-safe checkpoints so that a long-running agent session can be
 * resumed from the most recent safe point after a crash or restart.
 *
 * <p>This is the Layer 3-4 sibling of the Layer 3 denial-ledger
 * ({@code IDenialLedger}), approval-gate ({@code IApprovalGate}), and
 * post-denial-guard ({@code IPostDenialGuard}) contracts. It sits in the
 * {@code io.nop.ai.agent.reliability} package (parallel to
 * {@code io.nop.ai.agent.security}) because checkpoints are a reliability
 * enhancement, not a security/governance control.
 *
 * <p><b>Save side vs. restore side</b>: L3-4 delivers the <b>save side</b>
 * (recording checkpoints) and a functional in-memory store (the
 * {@code ToolExecutionCheckpoint} implementation). The <b>restore side</b> —
 * reconstructing an {@code AgentExecutionContext} from a checkpoint on engine
 * restart — is a deferred successor (carry-over from plan 180). The retrieve
 * methods ({@link #getLatestCheckpoint} / {@link #getCheckpoint}) are the
 * contract surface that the restore-side successor will consume; L3-4
 * validates them via an internal save→retrieve round-trip. This mirrors the
 * L3-6 pattern where plan 177 delivered the {@code recordDenial} save side
 * and plan 180 separately delivered the sticky-pause restore side.
 *
 * <p><b>Dispatch-path integration</b>: the {@code ReActAgentExecutor}
 * dispatch loop calls {@link #saveCheckpoint} after every tool execution
 * completes (design §5.4a "tool execution after" trigger point). The
 * checkpoint captures the tool-call payload and a context-size snapshot.
 * With the shipped {@link NoOpCheckpoint} default this is a no-op; with the
 * functional {@code ToolExecutionCheckpoint} implementation the checkpoint is
 * stored in-memory per session.
 *
 * <p><b>Default</b>: {@link NoOpCheckpoint} — no saving, no persisting,
 * retrieve methods return {@code null}. This is the shipped default injected
 * into the engine, so unattended Layer 1 automation is unaffected unless a
 * functional manager is explicitly registered. Consistent with the
 * {@code NoOpDenialLedger} / {@code AutoApproveGate} /
 * {@code PassThroughPostDenialGuard} sibling NoOp pattern.
 *
 * <p><b>Thread safety</b>: implementations must be thread-safe. Multiple
 * sessions may access the same manager instance concurrently, and per-session
 * checkpoint lists must remain independent — a checkpoint in session A must
 * not appear in the retrieval of session B.
 *
 * <p><b>Persistence</b>: the contract does not mandate persistence
 * (consistent with the L3-6 finding L3-G5 narrowing). The
 * {@link NoOpCheckpoint} default does not persist; the
 * {@code ToolExecutionCheckpoint} functional implementation is in-memory (no
 * persistence). The DB-backed persistent checkpoint store has landed as
 * {@link DBCheckpointManager} (plan 186), following the plan 179
 * {@code DBDenialLedger} pattern.
 */
public interface ICheckpointManager {

    /**
     * Record a single checkpoint. Called by the dispatch loop at the
     * appropriate trigger point (L3-4: after tool execution).
     *
     * <p>The {@link NoOpCheckpoint} default ignores the checkpoint entirely
     * (explicit pass-through, not a silent skip of required behavior).
     *
     * @param checkpoint the structured checkpoint to record; never null
     */
    void saveCheckpoint(Checkpoint checkpoint);

    /**
     * Retrieve the most recent checkpoint recorded for a session.
     *
     * <p>This is the retrieval entry point that the crash/restart recovery
     * successor will consume to locate the latest safe resume point. L3-4
     * validates it via an internal save→retrieve round-trip (the functional
     * {@code ToolExecutionCheckpoint} implementation returns the last saved
     * checkpoint).
     *
     * @param sessionId the session identifier; may be null (anonymous —
     *                  always returns {@code null} for the NoOp default)
     * @return the most recent checkpoint for the session, or {@code null} if
     *         no checkpoint has been recorded for the session (or for the
     *         {@link NoOpCheckpoint} default)
     */
    Checkpoint getLatestCheckpoint(String sessionId);

    /**
     * Retrieve a specific checkpoint by its watermark.
     *
     * @param watermark the unique retrieval key; never null
     * @return the checkpoint with the given watermark, or {@code null} if no
     *         such checkpoint exists (or for the {@link NoOpCheckpoint}
     *         default)
     */
    Checkpoint getCheckpoint(String watermark);
}
