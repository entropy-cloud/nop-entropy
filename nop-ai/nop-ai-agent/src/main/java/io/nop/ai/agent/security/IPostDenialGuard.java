package io.nop.ai.agent.security;

import java.util.Map;

/**
 * Layer 3 post-denial-guard contract (design §6.3): after a tool call has
 * been denied at any Layer 1 / 2 / 3 checkpoint, this guard prevents the
 * agent from blindly re-submitting the <i>same</i> already-denied intent on a
 * subsequent iteration. Sits in the defense-in-depth chain (design §8)
 * immediately after {@link IDenialLedger} and parallel to it before
 * {@code ISandboxBackend} (Layer 4).
 *
 * <p><b>Dispatch-path integration</b> (two operations):
 * <ol>
 *   <li><b>Consultation</b> ({@link #checkBeforeDispatch}): the
 *       {@code ReActAgentExecutor} dispatch loop consults the guard
 *       <i>before</i> the Layer 1 {@code IToolAccessChecker} check for each
 *       tool call. If the action's fingerprint is already in the session's
 *       denied set (a blind retry), the guard returns a {@link DenialResult}
 *       and the dispatch path skips all Layer 1/2/3 checks for that call
 *       (saving token budget and preventing repeated denials from inflating
 *       the {@link IDenialLedger} count). If the action is not a known-deny,
 *       the guard returns {@code null} and the call proceeds to Layer 1.</li>
 *   <li><b>Recording</b> ({@link #recordDeniedAction}): after every Layer 1 /
 *       2 / 3 deny (and after the guard's own consultation deny), the
 *       dispatch path records the denied action's fingerprint into the
 *       guard's per-session denied set, so a subsequent blind retry is
 *       detectable by {@code checkBeforeDispatch}.</li>
 * </ol>
 *
 * <p><b>Default</b>: {@link PassThroughPostDenialGuard} — consultation always
 * returns {@code null} (no blind-retry detection), recording and reset are
 * no-ops. This is the shipped default injected into the engine, so unattended
 * Layer 1 automation is unaffected unless a functional guard (e.g.
 * {@link FingerprintPostDenialGuard}) is explicitly registered.
 *
 * <p><b>Thread safety</b>: implementations must be thread-safe. Multiple
 * sessions may access the same guard instance concurrently, and per-session
 * fingerprint sets must remain independent — a denial recorded in session A
 * must not affect the denied set of session B.
 *
 * <p><b>Follow-up detection</b>: the contract uses exact-fingerprint matching
 * (same actionKind + argv + cwd + criticalEnv = blind retry). A legitimate
 * follow-up (changed parameters, lower privileges, narrower approval scope)
 * naturally produces a different fingerprint and is therefore not blocked.
 * Explicit follow-up-tag detection (reasoning-text analysis / tool-call
 * metadata annotation) is a deferred successor (design §6.3 non-goal).
 *
 * <p><b>Persistence</b>: the contract does not mandate persistence. The
 * {@link PassThroughPostDenialGuard} / {@link FingerprintPostDenialGuard}
 * defaults are in-memory; a DB-backed successor persists the per-session
 * denied set so blind retries are blocked across session recovery (deferred,
 * symmetric to the {@code DBDenialLedger} successor).
 */
public interface IPostDenialGuard {

    /**
     * Consult the guard before dispatching a tool call to the Layer 1/2/3
     * checks. If the action's fingerprint is already in the session's denied
     * set (a blind retry), return a {@link DenialResult} to block it; if not,
     * return {@code null} to let the call proceed to Layer 1.
     *
     * @param sessionId the session identifier; may be null (anonymous — a
     *                  functional guard typically returns {@code null} for
     *                  anonymous sessions, since no per-session state is
     *                  tracked)
     * @param toolName  the tool name / operation category; may be null
     * @param arguments the tool-call arguments map; may be null or empty
     * @param workDir   the working directory; may be null
     * @return a {@link DenialResult} if the action is a blind retry of an
     *         already-denied action, or {@code null} to allow the call to
     *         proceed to the Layer 1/2/3 checks
     */
    DenialResult checkBeforeDispatch(String sessionId, String toolName,
                                     Map<String, Object> arguments, String workDir);

    /**
     * Record a denied action's fingerprint into the session's denied set, so
     * a subsequent blind retry is detectable by {@link #checkBeforeDispatch}.
     * Called by the dispatch path after every Layer 1/2/3 deny (and after the
     * guard's own consultation deny — forming a closed loop: a guard-deny is
     * itself recorded, preventing "retry the guard-deny result" loops).
     *
     * @param sessionId the session identifier; may be null (anonymous — a
     *                  functional guard typically no-ops for null sessions)
     * @param toolName  the tool name / operation category; may be null
     * @param arguments the tool-call arguments map; may be null or empty
     * @param workDir   the working directory; may be null
     */
    void recordDeniedAction(String sessionId, String toolName,
                            Map<String, Object> arguments, String workDir);

    /**
     * Reset the denied-fingerprint set for a session, clearing all recorded
     * denials so subsequent identical actions are no longer treated as blind
     * retries. This is the human-intervention recovery entry point.
     *
     * @param sessionId the session identifier; may be null
     */
    void reset(String sessionId);
}
