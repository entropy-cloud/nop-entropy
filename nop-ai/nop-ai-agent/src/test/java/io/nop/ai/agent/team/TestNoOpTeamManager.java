package io.nop.ai.agent.team;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link NoOpTeamManager} shipped default behaviour
 * (plan 223 Phase 1).
 *
 * <p>Verifies the No Silent No-Op contract (Minimum Rules #24): the
 * shipped default throws {@link UnsupportedOperationException} from all
 * write operations (createTeam / disbandTeam / addMember / removeMember /
 * bindMemberSession) so a caller bypassing integration gets a fast
 * failure rather than a silent null/false; and all read operations return
 * empty results.
 */
public class TestNoOpTeamManager {

    private final NoOpTeamManager mgr = NoOpTeamManager.noOp();

    @Test
    void createTeamThrowsUnsupportedOperationException() {
        TeamSpec spec = new TeamSpec("t", null, "a", java.util.List.of(), 0);
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class, () -> mgr.createTeam(spec));
        // The message must explain how to enable — not a bare UOE.
        assertTrue(ex.getMessage().contains("not enabled"),
                "UOE message should explain how to enable: " + ex.getMessage());
    }

    @Test
    void disbandTeamThrowsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class, () -> mgr.disbandTeam("any"));
        assertTrue(ex.getMessage().contains("not enabled"));
    }

    @Test
    void addMemberThrowsUnsupportedOperationException() {
        TeamMemberSpec ms = new TeamMemberSpec("a", "m", MemberRole.MEMBER);
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class, () -> mgr.addMember("any", ms));
        assertTrue(ex.getMessage().contains("not enabled"));
    }

    @Test
    void removeMemberThrowsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class, () -> mgr.removeMember("any", "a"));
        assertTrue(ex.getMessage().contains("not enabled"));
    }

    @Test
    void bindMemberSessionThrowsUnsupportedOperationException() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> mgr.bindMemberSession("any", "a", "s", "x"));
        assertTrue(ex.getMessage().contains("not enabled"));
    }

    @Test
    void getTeamReturnsEmpty() {
        assertTrue(mgr.getTeam("anything").isEmpty());
    }

    @Test
    void getTeamBySessionReturnsEmpty() {
        assertTrue(mgr.getTeamBySession("anything").isEmpty());
    }

    @Test
    void getActiveTeamsReturnsEmpty() {
        assertTrue(mgr.getActiveTeams().isEmpty());
    }

    @Test
    void getMemberReturnsEmpty() {
        assertTrue(mgr.getMember("any", "a").isEmpty());
    }

    @Test
    void singletonInstance() {
        assertSame(NoOpTeamManager.noOp(), NoOpTeamManager.noOp(),
                "noOp() must return the same singleton instance");
    }
}
