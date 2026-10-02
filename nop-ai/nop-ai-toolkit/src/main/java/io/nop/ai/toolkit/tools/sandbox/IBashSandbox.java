package io.nop.ai.toolkit.tools.sandbox;

/**
 * The Bash execution isolation seam (plan 335 DR-3a). {@code BashExecutor} routes every command
 * through an {@code IBashSandbox} implementation instead of constructing a host
 * {@code ProcessBuilder("sh","-c",...)} directly.
 *
 * <p><b>Fail-closed guarantee</b>: a functional backend that cannot reach its isolation layer
 * (e.g. Docker daemon down) MUST raise {@link BashSandboxException} and MUST NOT silently fall
 * back to host execution. When {@code BashExecutor} has no backend wired
 * ({@code sandbox == null}) it refuses the call with an explicit error result — it never falls
 * back to the host shell. Unrestricted host execution is only available via the explicit opt-in
 * {@link HostBashSandbox}.
 *
 * <p><b>Execution semantics</b>: {@link #execute} is synchronous and blocking — the caller's
 * thread blocks until the command completes, the {@link BashSandboxConfig#getWallSeconds()}
 * budget is exhausted, or an isolation failure raises {@link BashSandboxException}.
 *
 * <p><b>Thread safety</b>: implementations must be safe to call concurrently from multiple
 * threads; per-execution state must remain confined to a single {@link #execute} call.
 */
public interface IBashSandbox {

    /**
     * Execute the command described by {@code request} in the sandbox environment and block until
     * completion, timeout, or isolation failure.
     *
     * @param request the sandbox execution request; never null
     * @return the execution result; never null
     * @throws BashSandboxException if the sandbox cannot launch the command — never falls back to
     *         host execution
     */
    BashSandboxResult execute(BashSandboxRequest request);

    /**
     * Plan 2285 (WI4): progress snapshot of the in-flight execution registered
     * under {@code callId} (from {@link BashSandboxRequest#getCallId()}).
     * Returns {@link BashProgressSnapshot#empty()} for unknown or finished
     * callIds — an explicit "no tracking information" value, never null.
     * The default (sandbox without introspection) always returns empty.
     */
    default BashProgressSnapshot progressSnapshot(String callId) {
        return BashProgressSnapshot.empty();
    }

    /**
     * Plan 2285 (WI4): cancel the in-flight execution registered under
     * {@code callId} (destroy the process / kill the container). Returns
     * false when no execution is registered for the callId. The default
     * (sandbox without introspection) never cancels anything.
     */
    default boolean cancelExecution(String callId) {
        return false;
    }
}
