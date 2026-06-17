package io.nop.ai.agent.team;

/**
 * Lifecycle status of a {@link Team}. The three values and their legal
 * transitions form the team state machine.
 *
 * <h2>State-transition diagram (foundational slice)</h2>
 * <pre>
 *   CREATED ──→ ACTIVE ──→ DISBANDED
 * </pre>
 *
 * <h3>Transitions</h3>
 * <ul>
 *   <li>{@code CREATED → ACTIVE}: the first member of the team binds a
 *       session (via {@code ITeamManager.bindMemberSession}). A team that
 *       never binds any member remains in {@link #CREATED}.</li>
 *   <li>{@code ACTIVE/CREATED → DISBANDED}: explicit
 *       {@code ITeamManager.disbandTeam} call. {@link #DISBANDED} is a
 *       terminal state — no further mutations are permitted on a disbanded
 *       team (addMember / bindMemberSession / removeMember fail fast).</li>
 * </ul>
 *
 * <p>See plan 223 (L4-8-team-manager) and vision §8.
 */
public enum TeamStatus {
    /**
     * Initial state: the team has been created (has a teamId and spec) but
     * no member has yet bound a runtime session. Transitions to
     * {@link #ACTIVE} when the first member binds a session.
     */
    CREATED,

    /**
     * The team has at least one member with a bound runtime session.
     * Transitions to {@link #DISBANDED} on explicit disband.
     */
    ACTIVE,

    /**
     * Terminal state: the team has been disbanded via
     * {@code ITeamManager.disbandTeam}. No further mutations are permitted.
     * The disbanded team remains queryable (history/audit).
     */
    DISBANDED
}
