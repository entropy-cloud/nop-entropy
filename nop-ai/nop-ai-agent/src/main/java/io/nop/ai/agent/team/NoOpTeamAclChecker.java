package io.nop.ai.agent.team;

/**
 * Shipped no-op default for {@link ITeamAclChecker}.
 *
 * <p>{@link #checkAccess} <strong>always returns {@code allow(null)}</strong>
 * — an explicit allow decision with no resolved role. This is the engine
 * default out-of-the-box (via {@code DefaultAgentEngine.teamAclChecker}),
 * so integrators see zero behaviour regression: every team-tool operation
 * that previously went through with no ACL now still goes through, with no
 * extra authorisation overhead. The functional ACL matrix is only enforced
 * when an integrator explicitly wires a {@link DefaultTeamAclChecker} via
 * {@code DefaultAgentEngine.setTeamAclChecker(...)}.
 *
 * <p><b>Why allow and not deny?</b> Unlike {@link NoOpTeamManager} /
 * {@link NoOpTeamTaskStore} — whose write operations throw
 * {@link UnsupportedOperationException} so a caller bypassing integration
 * fails fast instead of mistaking a silent null/false for a real effect —
 * the ACL NoOp's semantics is "team functionality is enabled but ACL
 * authorisation is not enabled = do not add any restriction". A functional
 * teamManager + NoOp ACL is the backward-compatible configuration that
 * preserves the pre-ACL team-tool behaviour (all bound members may perform
 * all operations regardless of role). The {@code allow(null)} return value
 * explicitly encodes "no role information; permitted" — it is not a
 * swallowed null / silent skip (Minimum Rules #24).
 *
 * <p>See plan 228 (L4-team-acl-enforcement), Design Decision §4.
 */
public final class NoOpTeamAclChecker implements ITeamAclChecker {

    private static final NoOpTeamAclChecker INSTANCE = new NoOpTeamAclChecker();

    private NoOpTeamAclChecker() {
    }

    /**
     * @return the singleton NoOp checker instance
     */
    public static NoOpTeamAclChecker noOp() {
        return INSTANCE;
    }

    @Override
    public TeamAclDecision checkAccess(String teamId, String callerSessionId,
                                       String toolName, String action) {
        // Explicit allow with null role: "ACL not enabled → no restriction
        // added". Not a silent skip — the decision object is returned, the
        // caller proceeds normally (Minimum Rules #24).
        return TeamAclDecision.allow(null);
    }
}
