package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link InMemoryTeamManager} (plan 223 Phase 2).
 *
 * <p>Covers:
 * <ul>
 *   <li>Full lifecycle: create → addMember → bindMemberSession (status →
 *       ACTIVE) → getTeamBySession reverse-lookup → removeMember →
 *       disbandTeam.</li>
 *   <li>Initial members from spec (createTeam initialises from memberSpecs).</li>
 *   <li>Boundary conditions: duplicate memberName throws, disband then
 *       addMember throws, getTeam on missing id returns empty, disband on
 *       missing id throws.</li>
 *   <li>Concurrency: multiple threads operating on different teams succeed.</li>
 * </ul>
 */
public class TestInMemoryTeamManager {

    private TeamSpec specWithLead() {
        return new TeamSpec("team-a", "desc", "lead",
                List.of(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)),
                0);
    }

    @Test
    void createTeamReturnsTeamWithFreshIdAndCreatedStatus() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());

        assertNotNull(team.getTeamId());
        // teamId must be a valid UUID string representation
        UUID.fromString(team.getTeamId());
        assertEquals(TeamStatus.CREATED, team.getStatus());
        assertEquals("team-a", team.getSpec().getTeamName());
        assertTrue(team.getCreatedAt() > 0);
        assertEquals(0L, team.getDisbandedAt());
        // Initial member from spec is present but unbound
        assertEquals(1, team.getMembers().size());
        TeamMember lead = team.getMembers().get("lead");
        assertNotNull(lead);
        assertFalse(lead.isBound());
    }

    @Test
    void createTeamWithEmptyMemberSpecsYieldsEmptyTeam() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead", List.of(), 0));
        assertTrue(team.getMembers().isEmpty());
        assertEquals(TeamStatus.CREATED, team.getStatus());
    }

    @Test
    void getTeamReturnsTeamById() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());

        Optional<Team> found = mgr.getTeam(team.getTeamId());
        assertTrue(found.isPresent());
        assertEquals(team.getTeamId(), found.get().getTeamId());
    }

    @Test
    void getTeamMissingReturnsEmpty() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        assertTrue(mgr.getTeam("nonexistent").isEmpty());
        assertTrue(mgr.getTeam(null).isEmpty());
    }

    @Test
    void addMemberAddsUnboundMember() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead", List.of(), 0));

        TeamMember added = mgr.addMember(team.getTeamId(),
                new TeamMemberSpec("alice", "coder", MemberRole.MEMBER));
        assertEquals("alice", added.getMemberName());
        assertFalse(added.isBound());

        Optional<TeamMember> found = mgr.getMember(team.getTeamId(), "alice");
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getMemberName());
    }

    @Test
    void addMemberDuplicateThrows() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());

        // "lead" already exists from spec
        assertThrows(NopAiAgentException.class, () -> mgr.addMember(team.getTeamId(),
                new TeamMemberSpec("lead", "agent", MemberRole.LEAD)));
    }

    @Test
    void addMemberMissingTeamThrows() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        assertThrows(NopAiAgentException.class, () -> mgr.addMember("no-such-team",
                new TeamMemberSpec("a", "m", MemberRole.MEMBER)));
    }

    @Test
    void addMemberAfterDisbandThrows() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());
        mgr.disbandTeam(team.getTeamId());

        assertThrows(NopAiAgentException.class, () -> mgr.addMember(team.getTeamId(),
                new TeamMemberSpec("new", "m", MemberRole.MEMBER)));
    }

    @Test
    void bindMemberSessionActivatesTeamOnFirstBinding() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());
        assertEquals(TeamStatus.CREATED, team.getStatus());

        boolean bound = mgr.bindMemberSession(team.getTeamId(), "lead", "sess-1", "actor-1");
        assertTrue(bound);

        // First binding transitions CREATED → ACTIVE
        assertEquals(TeamStatus.ACTIVE, team.getStatus(),
                "first binding must transition team to ACTIVE");

        Optional<TeamMember> lead = mgr.getMember(team.getTeamId(), "lead");
        assertTrue(lead.isPresent());
        assertEquals("sess-1", lead.get().getSessionId());
        assertEquals("actor-1", lead.get().getActorId());
        assertTrue(lead.get().isBound());
    }

    @Test
    void getTeamBySessionReverseLookupWorks() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());

        // Before binding: no reverse lookup
        assertTrue(mgr.getTeamBySession("sess-1").isEmpty());

        mgr.bindMemberSession(team.getTeamId(), "lead", "sess-1", "actor-1");

        Optional<Team> found = mgr.getTeamBySession("sess-1");
        assertTrue(found.isPresent());
        assertEquals(team.getTeamId(), found.get().getTeamId());
    }

    @Test
    void bindMemberSessionMissingTeamReturnsFalse() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        assertFalse(mgr.bindMemberSession("no-team", "a", "s", "x"));
    }

    @Test
    void bindMemberSessionMissingMemberReturnsFalse() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());
        assertFalse(mgr.bindMemberSession(team.getTeamId(), "nonexistent", "s", "x"));
    }

    @Test
    void bindMemberSessionRejectsNullSessionOrActor() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());
        assertThrows(NullPointerException.class,
                () -> mgr.bindMemberSession(team.getTeamId(), "lead", null, "x"));
        assertThrows(NullPointerException.class,
                () -> mgr.bindMemberSession(team.getTeamId(), "lead", "s", null));
    }

    @Test
    void removeMemberReturnsTrueAndCleansUpSessionIndex() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());
        mgr.bindMemberSession(team.getTeamId(), "lead", "sess-1", "actor-1");
        assertTrue(mgr.getTeamBySession("sess-1").isPresent());

        boolean removed = mgr.removeMember(team.getTeamId(), "lead");
        assertTrue(removed);
        assertTrue(mgr.getMember(team.getTeamId(), "lead").isEmpty());
        // Session index cleaned up
        assertTrue(mgr.getTeamBySession("sess-1").isEmpty());
    }

    @Test
    void removeMemberMissingReturnsFalse() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());
        assertFalse(mgr.removeMember(team.getTeamId(), "nonexistent"));
        assertFalse(mgr.removeMember("no-team", "lead"));
    }

    @Test
    void disbandTeamTransitionsToDisbandedAndRecordsTimestamp() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());
        assertEquals(TeamStatus.CREATED, team.getStatus());

        Team result = mgr.disbandTeam(team.getTeamId());
        assertEquals(TeamStatus.DISBANDED, result.getStatus());
        assertTrue(result.getDisbandedAt() > 0);
    }

    @Test
    void disbandTeamMissingThrows() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        assertThrows(NopAiAgentException.class, () -> mgr.disbandTeam("nonexistent"));
    }

    @Test
    void disbandTeamIsIdempotent() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());
        long firstDisband = mgr.disbandTeam(team.getTeamId()).getDisbandedAt();
        long secondDisband = mgr.disbandTeam(team.getTeamId()).getDisbandedAt();

        assertEquals(firstDisband, secondDisband,
                "idempotent disband must not overwrite the disband timestamp");
        assertEquals(TeamStatus.DISBANDED, team.getStatus());
    }

    @Test
    void disbandedTeamRemainsQueryable() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(specWithLead());
        mgr.disbandTeam(team.getTeamId());

        // Still queryable for history/audit
        assertTrue(mgr.getTeam(team.getTeamId()).isPresent());
    }

    @Test
    void getActiveTeamsExcludesDisbanded() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team t1 = mgr.createTeam(specWithLead());
        Team t2 = mgr.createTeam(new TeamSpec("t2", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD)), 0));
        mgr.disbandTeam(t1.getTeamId());

        Collection<Team> active = mgr.getActiveTeams();
        assertEquals(1, active.size());
        assertEquals(t2.getTeamId(), active.iterator().next().getTeamId());
    }

    @Test
    void getActiveTeamsReturnsUnmodifiableSnapshot() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        mgr.createTeam(specWithLead());
        Collection<Team> active = mgr.getActiveTeams();
        assertThrows(UnsupportedOperationException.class,
                () -> active.add(mgr.createTeam(specWithLead())));
    }

    @Test
    void fullLifecycleCreateBindQueryRemoveDisband() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        // Create team with lead + one member
        Team team = mgr.createTeam(new TeamSpec("full", "d", "lead",
                List.of(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)), 0));
        mgr.addMember(team.getTeamId(),
                new TeamMemberSpec("dev", "coder", MemberRole.MEMBER));

        assertEquals(TeamStatus.CREATED, team.getStatus());
        assertEquals(2, team.getMembers().size());

        // Bind lead → ACTIVE
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "lead", "lead-sess", "lead-actor"));
        assertEquals(TeamStatus.ACTIVE, team.getStatus());

        // Bind dev
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "dev", "dev-sess", "dev-actor"));

        // Reverse-lookup both
        assertEquals(team.getTeamId(), mgr.getTeamBySession("lead-sess").get().getTeamId());
        assertEquals(team.getTeamId(), mgr.getTeamBySession("dev-sess").get().getTeamId());

        // Remove dev cleans up its session index
        assertTrue(mgr.removeMember(team.getTeamId(), "dev"));
        assertTrue(mgr.getTeamBySession("dev-sess").isEmpty());
        assertTrue(mgr.getTeamBySession("lead-sess").isPresent());

        // Disband
        Team disbanded = mgr.disbandTeam(team.getTeamId());
        assertEquals(TeamStatus.DISBANDED, disbanded.getStatus());
        // Disbanded team no longer in active set
        assertTrue(mgr.getActiveTeams().isEmpty());
    }

    @Test
    void concurrencyMultipleTeamsConcurrentOperationsSucceed() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);
        List<String> teamIds = new java.util.concurrent.CopyOnWriteArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await();
                        Team team = mgr.createTeam(new TeamSpec("t" + idx, null, "lead",
                                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD)), 0));
                        teamIds.add(team.getTeamId());
                        mgr.addMember(team.getTeamId(),
                                new TeamMemberSpec("m" + idx, "x", MemberRole.MEMBER));
                        mgr.bindMemberSession(team.getTeamId(), "lead",
                                "sess-" + idx, "actor-" + idx);
                        assertEquals(TeamStatus.ACTIVE,
                                mgr.getTeam(team.getTeamId()).get().getStatus());
                        mgr.disbandTeam(team.getTeamId());
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        throw new NopAiAgentException("concurrent operation failed", e);
                    }
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS),
                    "concurrent operations must complete");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, errors.get(), "no thread should encounter an error");
        assertEquals(threads, teamIds.size(), "each thread created a distinct team");
        // Each teamId is distinct
        assertEquals(threads, new java.util.HashSet<>(teamIds).size());
        // All disbanded → active set empty
        assertTrue(mgr.getActiveTeams().isEmpty());
    }
}
