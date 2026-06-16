package io.nop.ai.agent.security;

/**
 * Layer 4 sandbox-backend contract (design §7.1): the final layer in the
 * defense-in-depth chain (design §8) that executes high-risk commands in
 * an isolated environment after the Layer 1 ({@link IToolAccessChecker} /
 * {@link IPathAccessChecker} / {@link IPermissionProvider}) → Layer 2
 * ({@link ISecurityLevelResolver} / {@link IPermissionMatrix}) → Layer 3
 * ({@link IApprovalGate} / {@link IDenialLedger} / {@link IPostDenialGuard})
 * checks have all approved the call. Sits at the very end of the chain —
 * an approved command is dispatched to this backend for execution.
 *
 * <p><b>Execution semantics</b>: {@link #execute} is <i>synchronous and
 * blocking</i> — the caller's thread blocks until the command completes,
 * the {@link SandboxConfig#getWallSeconds()} wall-time budget is exhausted,
 * or an isolation failure raises a {@link SandboxException}. The caller
 * never observes a partially-completed sandbox execution: the contract
 * is all-or-nothing (the {@link SandboxResult} describes a finished run,
 * and any isolation/launch failure surfaces as a {@link SandboxException}).
 *
 * <p><b>Fail-closed guarantee</b>: a functional backend (e.g.
 * {@code DockerSandboxBackend}) that cannot reach its isolation layer
 * (Docker daemon down, image missing, container start denied) MUST raise
 * {@link SandboxException} with the matching
 * {@link SandboxFailureReason} and MUST NOT silently fall back to host
 * execution. The shipped default {@link NoOpSandboxBackend} is not a
 * fallback — it is the Layer 1 designable baseline (design §7.1 "Noop |
 * 无隔离（默认）") that runs directly on the host by definition; an
 * integrator that wants isolation explicitly wires a functional backend
 * via {@code DefaultAgentEngine.setSandboxBackend(...)}.
 *
 * <p><b>Package decision</b> (plan 219 Phase 1): the sandbox contract
 * lives in {@code io.nop.ai.agent.security} alongside the other
 * defense-in-depth chain interfaces, not in a new
 * {@code io.nop.ai.agent.sandbox} package. Rationale: (1) ISandboxBackend
 * is the chain's final link and shares the same package-private collaborators;
 * (2) the security package is already home to the parallel Layer 1-3
 * chain; (3) SandboxException extends {@code io.nop.ai.agent.engine.NopAiAgentException}
 * (cross-package import) regardless, so a separate sandbox package gains
 * no decoupling.
 *
 * <p><b>Relationship with nop-ai-shell</b> (plan 219 Phase 1 Decision):
 * {@code nop-ai-shell}'s {@code ShellCommandExecutor} is an in-JVM Bash
 * syntax parser + command dispatcher (built-ins + external commands),
 * while {@code ISandboxBackend} is nop-ai-agent's platform-level
 * isolation contract. They sit in different modules, serve different
 * consumers, and are NOT coupled: {@link NoOpSandboxBackend} uses
 * {@link ProcessBuilder} directly (does not depend on nop-ai-shell),
 * avoiding a nop-ai-agent → nop-ai-shell module dependency. {@link SandboxResult}
 * and nop-ai-shell's {@code ExecutionResult} have similar shapes
 * (exitCode/stdout/stderr) but live in different modules with different
 * semantic payloads — they are NOT reused across modules.
 *
 * <p><b>setSandboxBackend vs warnIfInsecureDefaults</b> (plan 219 Phase 1
 * Decision): the {@code DefaultAgentEngine.setSandboxBackend} setter does
 * NOT call {@code warnIfInsecureDefaults}. {@link NoOpSandboxBackend} has
 * never been superseded by a more-secure shipped alternative — it is the
 * Layer 1 designable baseline (no isolation is the starting state, design
 * §7.1). This differs from {@link AutoApproveGate} (superseded by
 * {@link DefaultApprovalGate} → wiring AutoApproveGate is a downgrade that
 * triggers a WARN). If a future {@code DefaultSandboxBackend} (host-level
 * hardening such as seccomp/chroot) ships and becomes the engine default,
 * wiring NoOp would then be a downgrade and the WARN would be added at
 * that time. See plan 219 Non-Blocking Follow-ups.
 *
 * <p><b>Thread safety</b>: implementations must be safe to call
 * concurrently from multiple threads (the engine dispatch loop is
 * per-session but the same backend instance is shared across sessions).
 * Per-execution state (process handles, output buffers) must remain
 * confined to a single {@link #execute} call.
 */
public interface ISandboxBackend {

    /**
     * Execute the command described by {@code request} in the sandbox
     * environment and block until completion, timeout, or isolation
     * failure.
     *
     * @param request the sandbox execution request (command + working
     *                directory + environment + resource limits); never null
     * @return the execution result (exit code + stdout + stderr + timing +
     *         timeout flag); never null
     * @throws SandboxException if the sandbox cannot launch the command
     *         (isolation layer unavailable, container start failed, resource
     *         limit exceeded as observed by the isolator) — never falls
     *         back to host execution
     */
    SandboxResult execute(SandboxRequest request);
}
