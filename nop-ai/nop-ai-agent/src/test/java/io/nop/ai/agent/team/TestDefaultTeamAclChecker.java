package io.nop.ai.agent.team;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link DefaultTeamAclChecker} (plan 228 Phase 1).
 *
 * <p>Verifies the §5.1 default role-permission matrix, role resolution via
 * the wired {@link ITeamManager}, and the fail-closed behaviour on unknown
 * team / unknown session / unknown (toolName, action) tuple.
 *
 * <p><b>Wiring Verification (Minimum Rules #23)</b>: the tests construct a
 * real {@link InMemoryTeamManager} with bound LEAD + MEMBER sessions and
 * assert that the checker resolves the caller's role through the manager
 * (not a mock stub that always returns the same answer).
 */
public class TestDefaultTeamAclChecker {

    private static final String LEAD_SESSION = "lead-sess";
    private static final String MEMBER_SESSION = "member-sess";
    private static final String STRANGER_SESSION = "stranger-sess";

    /** All 7 (toolName, action) tuples defined by the §5.1 matrix. */
    private static final String[][] ALL_TUPLES = {
            {"team-send-message", "send"},
            {"team-status", "view"},
            {"team-task-create", "create"},
            {"team-task-update", "claim"},
            {"team-task-update", "complete"},
            {"team-task-update", "abandon-claimed"},
            {"team-task-update", "abandon-unclaimed"},
    };

    private InMemoryTeamManager newTeamWithLeadAndMember() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("MatrixTeam", "matrix test team", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", LEAD_SESSION, "actor-lead");
        mgr.bindMemberSession(team.getTeamId(), "worker", MEMBER_SESSION, "actor-worker");
        return mgr;
    }

    private String teamIdOf(InMemoryTeamManager mgr) {
        return mgr.getActiveTeams().iterator().next().getTeamId();
    }

    // ----- LEAD: all 7 tuples allowed -----

    @Test
    void leadAllowedForAllMatrixTuples() {
        InMemoryTeamManager mgr = newTeamWithLeadAndMember();
        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(mgr);
        String teamId = teamIdOf(mgr);

        for (String[] t : ALL_TUPLES) {
            TeamAclDecision d = checker.checkAccess(teamId, LEAD_SESSION, t[0], t[1]);
            assertTrue(d.isAllowed(),
                    "LEAD must be allowed for " + t[0] + "/" + t[1] + ": " + d);
            assertEquals(MemberRole.LEAD, d.getResolvedRole(),
                    "resolved role must be LEAD for " + t[0] + "/" + t[1]);
            assertNull(d.getReason(),
                    "allow decision must have null reason for " + t[0] + "/" + t[1]);
        }
    }

    // ----- MEMBER: 6 non-ADMIN tuples allowed, abandon-unclaimed denied -----

    @Test
    void memberAllowedForNonAdminTuples() {
        InMemoryTeamManager mgr = newTeamWithLeadAndMember();
        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(mgr);
        String teamId = teamIdOf(mgr);

        String[][] memberAllowed = {
                {"team-send-message", "send"},
                {"team-status", "view"},
                {"team-task-create", "create"},
                {"team-task-update", "claim"},
                {"team-task-update", "complete"},
                {"team-task-update", "abandon-claimed"},
        };
        for (String[] t : memberAllowed) {
            TeamAclDecision d = checker.checkAccess(teamId, MEMBER_SESSION, t[0], t[1]);
            assertTrue(d.isAllowed(),
                    "MEMBER must be allowed for " + t[0] + "/" + t[1] + ": " + d);
            assertEquals(MemberRole.MEMBER, d.getResolvedRole());
        }
    }

    @Test
    void memberDeniedForAbandonUnclaimedAndReasonCitesAdmin() {
        InMemoryTeamManager mgr = newTeamWithLeadAndMember();
        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(mgr);
        String teamId = teamIdOf(mgr);

        TeamAclDecision d = checker.checkAccess(teamId, MEMBER_SESSION,
                "team-task-update", "abandon-unclaimed");

        assertFalse(d.isAllowed(),
                "MEMBER must be DENIED for team-task-update/abandon-unclaimed (ADMIN-only)");
        assertEquals(MemberRole.MEMBER, d.getResolvedRole(),
                "deny decision still records the resolved role");
        assertNotNull(d.getReason());
        // Reason must mention ADMIN so the LLM / caller can understand the policy.
        assertTrue(d.getReason().contains("ADMIN"),
                "deny reason must cite ADMIN: " + d.getReason());
        assertTrue(d.getReason().contains("MEMBER"),
                "deny reason must cite the denied role MEMBER: " + d.getReason());
    }

    @Test
    void memberDeniedReasonCitesAbandonUnclaimedSemantics() {
        InMemoryTeamManager mgr = newTeamWithLeadAndMember();
        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(mgr);
        String teamId = teamIdOf(mgr);

        TeamAclDecision d = checker.checkAccess(teamId, MEMBER_SESSION,
                "team-task-update", "abandon-unclaimed");

        assertTrue(d.getReason().toLowerCase().contains("lead"),
                "deny reason should explain that LEAD-only is required: " + d.getReason());
    }

    // ----- Role resolution: caller not a member -----

    @Test
    void callerSessionNotBoundToAnyMemberIsDeniedWithNullRole() {
        InMemoryTeamManager mgr = newTeamWithLeadAndMember();
        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(mgr);
        String teamId = teamIdOf(mgr);

        TeamAclDecision d = checker.checkAccess(teamId, STRANGER_SESSION,
                "team-status", "view");

        assertFalse(d.isAllowed(),
                "A session not bound to any team member must be denied");
        assertNull(d.getResolvedRole(),
                "resolvedRole must be null when caller is not a member");
        assertNotNull(d.getReason());
        assertTrue(d.getReason().contains("not a member"),
                "deny reason must explain membership failure: " + d.getReason());
    }

    // ----- Team not found -----

    @Test
    void unknownTeamIdIsDeniedWithNullRole() {
        InMemoryTeamManager mgr = newTeamWithLeadAndMember();
        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(mgr);

        TeamAclDecision d = checker.checkAccess(
                "nonexistent-team-id", LEAD_SESSION, "team-status", "view");

        assertFalse(d.isAllowed(), "Unknown teamId must be denied");
        assertNull(d.getResolvedRole(),
                "resolvedRole must be null when the team does not exist");
        assertNotNull(d.getReason());
        assertTrue(d.getReason().contains("team not found"),
                "deny reason must explain team-not-found: " + d.getReason());
    }

    // ----- Fail closed on unknown (toolName, action) -----

    @Test
    void unknownToolActionTupleIsDeniedFailClosed() {
        InMemoryTeamManager mgr = newTeamWithLeadAndMember();
        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(mgr);
        String teamId = teamIdOf(mgr);

        TeamAclDecision d = checker.checkAccess(teamId, LEAD_SESSION,
                "team-unknown-tool", "unknown-action");

        assertFalse(d.isAllowed(),
                "Unknown (toolName, action) tuple must be DENIED, not silently allowed "
                        + "(fail closed — Minimum Rules #24)");
        assertEquals(MemberRole.LEAD, d.getResolvedRole(),
                "role is still resolved even when the tuple is unknown");
        assertNotNull(d.getReason());
        assertTrue(d.getReason().contains("unknown"),
                "deny reason must cite the unknown tuple: " + d.getReason());
    }

    // ----- Wiring verification: resolves role via the manager -----

    @Test
    void checkerResolvesRoleViaTeamManagerSnapshot() {
        // Wiring Verification (Minimum Rules #23): prove the checker reads
        // the live team snapshot from the wired ITeamManager. Rebind the
        // worker session and verify the resolved role follows the binding.
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("WiringTeam", null, "lead",
                List.of(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", "session-A", "actor-A");

        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(mgr);

        // Initially "session-A" resolves to LEAD.
        TeamAclDecision before = checker.checkAccess(
                team.getTeamId(), "session-A", "team-status", "view");
        assertTrue(before.isAllowed());
        assertEquals(MemberRole.LEAD, before.getResolvedRole());

        // A session that is not bound should be denied.
        TeamAclDecision unbound = checker.checkAccess(
                team.getTeamId(), "session-B", "team-status", "view");
        assertFalse(unbound.isAllowed());
        assertNull(unbound.getResolvedRole());
    }

    @Test
    void checkerReflectsLiveMemberBindingChange() {
        // Wiring Verification: bind a new member after constructing the
        // checker; the next checkAccess must reflect the new binding
        // (no caching of the member snapshot inside the checker).
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("LiveTeam", null, "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        // Construct the checker BEFORE binding the worker.
        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(mgr);

        // Pre-bind: worker session is unknown → denied.
        TeamAclDecision pre = checker.checkAccess(
                team.getTeamId(), "worker-sess", "team-status", "view");
        assertFalse(pre.isAllowed());

        // Bind the worker now.
        mgr.bindMemberSession(team.getTeamId(), "worker", "worker-sess", "actor-w");

        // Post-bind: same checker, same session — now resolves to MEMBER → allowed.
        TeamAclDecision post = checker.checkAccess(
                team.getTeamId(), "worker-sess", "team-status", "view");
        assertTrue(post.isAllowed());
        assertEquals(MemberRole.MEMBER, post.getResolvedRole());
    }

    @Test
    void constructorRejectsNullTeamManager() {
        assertThrows(NullPointerException.class, () -> new DefaultTeamAclChecker(null),
                "DefaultTeamAclChecker must reject a null ITeamManager");
    }

    @Test
    void getTeamFromManagerIsConsulted() {
        // Sanity: prove the checker calls ITeamManager.getTeam rather than
        // inventing a team. Use a stub manager that records the call.
        boolean[] called = {false};
        ITeamManager stub = new ITeamManager() {
            @Override
            public Team createTeam(TeamSpec spec) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Team> getTeam(String teamId) {
                called[0] = true;
                return Optional.empty();
            }

            @Override
            public Optional<Team> getTeamBySession(String sessionId) {
                return Optional.empty();
            }

            @Override
            public Team disbandTeam(String teamId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Collection<Team> getActiveTeams() {
                return java.util.Collections.emptyList();
            }

            @Override
            public TeamMember addMember(String teamId, TeamMemberSpec memberSpec) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean removeMember(String teamId, String memberName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean bindMemberSession(String teamId, String memberName,
                                             String sessionId, String actorId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<TeamMember> getMember(String teamId, String memberName) {
                return Optional.empty();
            }
        };

        DefaultTeamAclChecker checker = new DefaultTeamAclChecker(stub);
        TeamAclDecision d = checker.checkAccess("any", "any", "team-status", "view");

        assertTrue(called[0], "checker must call ITeamManager.getTeam");
        assertFalse(d.isAllowed());
        assertNull(d.getResolvedRole());
    }
}
