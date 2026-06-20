package io.nop.ai.agent.team;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused tests for the {@link Team} runtime data object (plan 276 / 15-1).
 *
 * <p>Verifies the {@code members} field type narrowing from {@code Map} to
 * {@link ConcurrentMap}: the thread-safety contract (already documented in
 * the Javadoc — "the manager serialises access via ConcurrentHashMap") is
 * now expressed in the static type system. Every construction site supplies
 * a {@code ConcurrentHashMap} (a {@code ConcurrentMap} subtype), so this is
 * a narrowing convergence, not a behavioural change.
 *
 * <ul>
 *   <li>{@link Team#getMembers()} returns a {@link ConcurrentMap} (verified
 *       via {@code instanceof} — this test would fail to compile if the
 *       getter returned a plain {@code Map}).</li>
 *   <li>The live member map passed to the constructor is the same instance
 *       returned by {@code getMembers()} (no defensive copy — the manager
 *       owns the live map).</li>
 *   <li>{@code null} arguments are rejected.</li>
 * </ul>
 */
public class TestTeam {

    private static TeamSpec singleLeadSpec() {
        return new TeamSpec("team-a", "desc", "lead",
                java.util.List.of(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)),
                0);
    }

    @Test
    void getMembersReturnsConcurrentMapType() {
        // The type-narrowing contract: getMembers() is statically typed as
        // ConcurrentMap. This assertion both compiles only because the
        // return type IS ConcurrentMap, and checks the runtime instance.
        ConcurrentHashMap<String, TeamMember> live = new ConcurrentHashMap<>();
        live.put("lead", new TeamMember("lead", MemberRole.LEAD, 1L));
        Team team = new Team("team-id", singleLeadSpec(), live,
                TeamStatus.CREATED, 100L);

        ConcurrentMap<String, TeamMember> members = team.getMembers();
        assertInstanceOf(ConcurrentMap.class, members,
                "getMembers() must expose the ConcurrentMap thread-safety contract in the type system");
    }

    @Test
    void getMembersIsLiveMapOwnedByManager() {
        // The contract: getMembers() returns the very map instance the
        // manager owns (no defensive copy), so the manager's serialised
        // mutations are visible. ConcurrentHashMap satisfies ConcurrentMap.
        ConcurrentHashMap<String, TeamMember> live = new ConcurrentHashMap<>();
        Team team = new Team("team-id", singleLeadSpec(), live,
                TeamStatus.CREATED, 100L);

        assertSame(live, team.getMembers(),
                "getMembers() must return the live map instance owned by the manager");
    }

    @Test
    void constructorRejectsNullMembers() {
        assertThrows(NullPointerException.class,
                () -> new Team("team-id", singleLeadSpec(), null,
                        TeamStatus.CREATED, 100L));
    }
}
