package io.nop.ai.agent.team;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the immutability contracts of {@link TeamSpec} and
 * {@link TeamMemberSpec} (plan 223 Phase 1).
 *
 * <p>Verifies:
 * <ul>
 *   <li>Both objects are immutable: no setters, all-args constructor + getters.</li>
 *   <li>{@link TeamSpec#getMemberSpecs()} returns an unmodifiable view; mutating
 *       the source list after construction does not affect the spec, and the
 *       returned list rejects mutation.</li>
 *   <li>{@code maxParallelMembers <= 0} is the documented "unlimited" sentinel.</li>
 *   <li>{@code null} arguments are rejected.</li>
 * </ul>
 */
public class TestTeamSpec {

    @Test
    void teamMemberSpecIsImmutableAndGettersWork() {
        TeamMemberSpec spec = new TeamMemberSpec("alice", "coder-agent", MemberRole.LEAD);
        assertEquals("alice", spec.getMemberName());
        assertEquals("coder-agent", spec.getAgentModel());
        assertEquals(MemberRole.LEAD, spec.getRole());
    }

    @Test
    void teamMemberSpecRejectsNulls() {
        assertThrows(NullPointerException.class,
                () -> new TeamMemberSpec(null, "model", MemberRole.MEMBER));
        assertThrows(NullPointerException.class,
                () -> new TeamMemberSpec("bob", null, MemberRole.MEMBER));
        assertThrows(NullPointerException.class,
                () -> new TeamMemberSpec("bob", "model", null));
    }

    @Test
    void teamSpecGettersWork() {
        List<TeamMemberSpec> members = List.of(
                new TeamMemberSpec("alice", "lead-agent", MemberRole.LEAD),
                new TeamMemberSpec("bob", "coder-agent", MemberRole.MEMBER));
        TeamSpec spec = new TeamSpec("team-a", "desc", "alice", members, 4);

        assertEquals("team-a", spec.getTeamName());
        assertEquals("desc", spec.getDescription());
        assertEquals("alice", spec.getLeadAgentName());
        assertEquals(4, spec.getMaxParallelMembers());
        assertEquals(2, spec.getMemberSpecs().size());
    }

    @Test
    void teamSpecDescriptionMayBeNull() {
        TeamSpec spec = new TeamSpec("team-a", null, "alice",
                List.of(), 0);
        assertNull(spec.getDescription());
    }

    @Test
    void teamSpecMemberSpecsIsDefensiveCopyAndUnmodifiable() {
        // Source list mutation after construction must NOT leak into the spec.
        List<TeamMemberSpec> source = new ArrayList<>();
        source.add(new TeamMemberSpec("alice", "a", MemberRole.LEAD));
        TeamSpec spec = new TeamSpec("t", null, "alice", source, 0);

        // Mutate the source list after construction
        source.add(new TeamMemberSpec("bob", "b", MemberRole.MEMBER));

        assertEquals(1, spec.getMemberSpecs().size(),
                "spec must hold a defensive copy — source mutation must not leak");
        assertEquals("alice", spec.getMemberSpecs().get(0).getMemberName());

        // The returned view must reject mutation (unmodifiable)
        assertThrows(UnsupportedOperationException.class,
                () -> spec.getMemberSpecs().add(
                        new TeamMemberSpec("eve", "e", MemberRole.MEMBER)));
    }

    @Test
    void maxParallelMembersZeroOrNegativeMeansUnlimited() {
        TeamSpec unlimited0 = new TeamSpec("t", null, "a", List.of(), 0);
        TeamSpec unlimitedNeg = new TeamSpec("t", null, "a", List.of(), -1);

        assertTrue(unlimited0.getMaxParallelMembers() <= 0,
                "<= 0 is the documented unlimited sentinel");
        assertTrue(unlimitedNeg.getMaxParallelMembers() <= 0,
                "<= 0 is the documented unlimited sentinel");
    }

    @Test
    void teamSpecRejectsNulls() {
        assertThrows(NullPointerException.class,
                () -> new TeamSpec(null, "d", "a", List.of(), 0));
        assertThrows(NullPointerException.class,
                () -> new TeamSpec("t", "d", null, List.of(), 0));
        assertThrows(NullPointerException.class,
                () -> new TeamSpec("t", "d", "a", null, 0));
    }

    @Test
    void teamSpecEmptyMemberSpecsAllowed() {
        // An empty member list is allowed; members can be added post-creation.
        TeamSpec spec = new TeamSpec("t", null, "a", List.of(), 0);
        assertTrue(spec.getMemberSpecs().isEmpty());
    }

    @Test
    void teamMemberBindSetsSessionAndActor() {
        // TeamMember is a runtime object: bind() mutates session/actor refs.
        TeamMember m = new TeamMember("alice", MemberRole.LEAD, 100L);
        assertNull(m.getSessionId());
        assertNull(m.getActorId());
        assertTrue(!m.isBound());

        m.bind("sess-1", "actor-1");
        assertEquals("sess-1", m.getSessionId());
        assertEquals("actor-1", m.getActorId());
        assertTrue(m.isBound());
    }

    @Test
    void teamMemberFromSpecIsInitiallyUnbound() {
        TeamMemberSpec spec = new TeamMemberSpec("bob", "coder", MemberRole.MEMBER);
        TeamMember m = new TeamMember(spec, 200L);
        assertEquals("bob", m.getMemberName());
        assertEquals(MemberRole.MEMBER, m.getRole());
        assertEquals(200L, m.getJoinedAt());
        assertNull(m.getSessionId());
        assertNull(m.getActorId());
        assertTrue(!m.isBound());
    }

    @Test
    void teamMemberBindRejectsNulls() {
        TeamMember m = new TeamMember("a", MemberRole.MEMBER, 1L);
        assertThrows(NullPointerException.class, () -> m.bind(null, "x"));
        assertThrows(NullPointerException.class, () -> m.bind("x", null));
    }

    @Test
    void teamStatusAndMemberRoleEnumsAreStable() {
        // Sanity: the enum values used by the contract exist as documented.
        assertEquals(2, MemberRole.values().length);
        assertSame(MemberRole.LEAD, MemberRole.valueOf("LEAD"));
        assertSame(MemberRole.MEMBER, MemberRole.valueOf("MEMBER"));

        assertEquals(3, TeamStatus.values().length);
        assertSame(TeamStatus.CREATED, TeamStatus.valueOf("CREATED"));
        assertSame(TeamStatus.ACTIVE, TeamStatus.valueOf("ACTIVE"));
        assertSame(TeamStatus.DISBANDED, TeamStatus.valueOf("DISBANDED"));
    }
}
