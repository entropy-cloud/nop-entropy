package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 230 Phase 1 focused tests for {@link DbTeamManager}: every
 * {@link ITeamManager} lifecycle / state-machine semantic is verified against
 * a real H2 DB (not a mock), satisfying Minimum Rules #22 (Anti-Hollow) and
 * #24 (No Silent No-Op).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #constructorAutoCreatesTables} — construction-time schema init</li>
 *   <li>{@link #createAndGetRoundTripsAllFields} — field fidelity incl. null
 *       description round-trip</li>
 *   <li>{@link #addMemberAndGetMember} — post-creation addMember + read-back</li>
 *   <li>{@link #addMemberDuplicateThrows} — unique-constraint duplicate detect</li>
 *   <li>{@link #bindMemberSessionActivatesTeam} — first binding CREATED→ACTIVE</li>
 *   <li>{@link #getTeamBySessionReverseLookup} — session reverse-lookup</li>
 *   <li>{@link #getActiveTeamsFiltersDisbanded} — terminal-state filter</li>
 *   <li>{@link #disbandTeamIsIdempotent} — idempotent terminal transition</li>
 *   <li>{@link #disbandUnknownTeamThrows} — missing-team fail-fast</li>
 *   <li>{@link #addMemberOnDisbandedTeamThrows} — disbanded guard</li>
 *   <li>{@link #removeMemberCleanup} — delete + getMember empty</li>
 *   <li>{@link #snapshotIsolationTwoGetTeamCalls} — fresh snapshot per read</li>
 *   <li>{@link #getTeamUnknownReturnsEmpty} — missing-team empty optional</li>
 * </ul>
 *
 * <p>See plan 230 (L4-team-db-persistence) Phase 1.
 */
public class TestDbTeamManager {

    private DataSource dataSource;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        // DB_CLOSE_DELAY=-1 keeps the in-memory DB alive across connections so
        // that independent DbTeamManager instances share the same tables.
        String dbUrl = "jdbc:h2:mem:test-team-mgr-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    private TeamSpec twoMemberSpec() {
        return new TeamSpec("AlphaTeam", "a description", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                4);
    }

    private TeamSpec nullDescriptionSpec() {
        return new TeamSpec("NullDescTeam", null, "lead",
                List.of(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)),
                0);
    }

    // ========================================================================
    // Construction-time schema init
    // ========================================================================

    @Test
    void constructorAutoCreatesTables() {
        // Constructing the manager must auto-create both tables (no manual DDL).
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(twoMemberSpec());
        assertTrue(mgr.getTeam(team.getTeamId()).isPresent(),
                "Auto-created tables must store and return the team");
    }

    // ========================================================================
    // createTeam + getTeam field fidelity (incl. null description)
    // ========================================================================

    @Test
    void createAndGetRoundTripsAllFields() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team created = mgr.createTeam(twoMemberSpec());

        Team read = mgr.getTeam(created.getTeamId()).orElseThrow();

        assertEquals(created.getTeamId(), read.getTeamId());
        assertEquals("AlphaTeam", read.getSpec().getTeamName());
        assertEquals("a description", read.getSpec().getDescription());
        assertEquals("lead", read.getSpec().getLeadAgentName());
        assertEquals(4, read.getSpec().getMaxParallelMembers());
        assertEquals(TeamStatus.CREATED, read.getStatus());
        assertTrue(read.getCreatedAt() > 0);
        assertEquals(0, read.getDisbandedAt(),
                "disbandedAt is 0 before disband");

        // Member specs round-trip (order preserved: lead, worker).
        assertEquals(2, read.getSpec().getMemberSpecs().size());
        assertEquals("lead", read.getSpec().getMemberSpecs().get(0).getMemberName());
        assertEquals(MemberRole.LEAD, read.getSpec().getMemberSpecs().get(0).getRole());
        assertEquals("lead-agent", read.getSpec().getMemberSpecs().get(0).getAgentModel());
        assertEquals("worker", read.getSpec().getMemberSpecs().get(1).getMemberName());
        assertEquals(MemberRole.MEMBER, read.getSpec().getMemberSpecs().get(1).getRole());

        // Member map: both unbound at creation.
        assertEquals(2, read.getMembers().size());
        for (TeamMember m : read.getMembers().values()) {
            assertNull(m.getSessionId(), "members start unbound");
            assertNull(m.getActorId());
            assertTrue(m.getJoinedAt() > 0);
        }
    }

    @Test
    void createWithNullDescriptionRoundTrips() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team created = mgr.createTeam(nullDescriptionSpec());

        Team read = mgr.getTeam(created.getTeamId()).orElseThrow();
        assertNull(read.getSpec().getDescription(),
                "null DESCRIPTION column reads back as null");
        assertEquals("NullDescTeam", read.getSpec().getTeamName());
    }

    @Test
    void getTeamUnknownReturnsEmpty() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        assertTrue(mgr.getTeam("nonexistent").isEmpty());
        assertTrue(mgr.getTeam(null).isEmpty());
    }

    // ========================================================================
    // addMember / getMember
    // ========================================================================

    @Test
    void addMemberAndGetMember() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(nullDescriptionSpec());

        TeamMember added = mgr.addMember(team.getTeamId(),
                new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER));
        assertEquals("worker", added.getMemberName());
        assertEquals(MemberRole.MEMBER, added.getRole());
        assertTrue(added.getJoinedAt() > 0);

        TeamMember read = mgr.getMember(team.getTeamId(), "worker").orElseThrow();
        assertEquals("worker", read.getMemberName());
        assertEquals(MemberRole.MEMBER, read.getRole());
        assertNull(read.getSessionId(), "addMember produces an unbound member");

        // Team snapshot now has 2 members.
        assertEquals(2, mgr.getTeam(team.getTeamId()).orElseThrow().getMembers().size());
    }

    @Test
    void addMemberDuplicateThrows() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(twoMemberSpec());

        // 'lead' already exists from the spec — adding again must fail fast.
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> mgr.addMember(team.getTeamId(),
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)));
        assertTrue(ex.getMessage().contains("already exists"),
                "duplicate member must be reported: " + ex.getMessage());
    }

    @Test
    void addMemberOnDisbandedTeamThrows() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(twoMemberSpec());
        mgr.disbandTeam(team.getTeamId());

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> mgr.addMember(team.getTeamId(),
                        new TeamMemberSpec("newcomer", "agent", MemberRole.MEMBER)));
        assertTrue(ex.getMessage().contains("disbanded"),
                "addMember on a disbanded team must fail fast: " + ex.getMessage());
    }

    @Test
    void addMemberOnUnknownTeamThrows() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> mgr.addMember("nonexistent",
                        new TeamMemberSpec("m", "agent", MemberRole.MEMBER)));
        assertTrue(ex.getMessage().contains("not found"));
    }

    // ========================================================================
    // bindMemberSession — CREATED → ACTIVE activation
    // ========================================================================

    @Test
    void bindMemberSessionActivatesTeam() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(twoMemberSpec());
        assertEquals(TeamStatus.CREATED, mgr.getTeam(team.getTeamId()).orElseThrow().getStatus());

        boolean bound = mgr.bindMemberSession(
                team.getTeamId(), "lead", "lead-sess", "actor-lead");
        assertTrue(bound, "first binding must succeed");

        Team active = mgr.getTeam(team.getTeamId()).orElseThrow();
        assertEquals(TeamStatus.ACTIVE, active.getStatus(),
                "first binding transitions CREATED → ACTIVE");

        TeamMember lead = mgr.getMember(team.getTeamId(), "lead").orElseThrow();
        assertEquals("lead-sess", lead.getSessionId());
        assertEquals("actor-lead", lead.getActorId());
        assertTrue(lead.isBound());

        // A second binding on another member does NOT change status (stays ACTIVE).
        mgr.bindMemberSession(team.getTeamId(), "worker", "worker-sess", "actor-worker");
        assertEquals(TeamStatus.ACTIVE,
                mgr.getTeam(team.getTeamId()).orElseThrow().getStatus());
    }

    @Test
    void bindMemberSessionOnUnknownMemberReturnsFalse() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(twoMemberSpec());
        assertFalse(mgr.bindMemberSession(
                team.getTeamId(), "ghost", "sess", "actor"));
        assertFalse(mgr.bindMemberSession("nonexistent", "lead", "sess", "actor"));
    }

    // ========================================================================
    // getTeamBySession — reverse lookup
    // ========================================================================

    @Test
    void getTeamBySessionReverseLookup() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(twoMemberSpec());
        mgr.bindMemberSession(team.getTeamId(), "lead", "lead-sess", "actor-lead");

        Optional<Team> found = mgr.getTeamBySession("lead-sess");
        assertTrue(found.isPresent(), "reverse-lookup by bound session must find the team");
        assertEquals(team.getTeamId(), found.get().getTeamId());

        assertTrue(mgr.getTeamBySession("unbound-session").isEmpty());
        assertTrue(mgr.getTeamBySession(null).isEmpty());
    }

    // ========================================================================
    // getActiveTeams — filters DISBANDED
    // ========================================================================

    @Test
    void getActiveTeamsFiltersDisbanded() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team a = mgr.createTeam(twoMemberSpec());
        Team b = mgr.createTeam(nullDescriptionSpec());

        assertEquals(2, mgr.getActiveTeams().size());

        mgr.disbandTeam(a.getTeamId());
        assertEquals(1, mgr.getActiveTeams().size(),
                "disbanded team must be excluded from active set");
        assertEquals(b.getTeamId(),
                mgr.getActiveTeams().iterator().next().getTeamId());

        mgr.disbandTeam(b.getTeamId());
        assertTrue(mgr.getActiveTeams().isEmpty());
    }

    // ========================================================================
    // disbandTeam — idempotent + unknown throws
    // ========================================================================

    @Test
    void disbandTeamIsIdempotent() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(twoMemberSpec());

        Team first = mgr.disbandTeam(team.getTeamId());
        assertEquals(TeamStatus.DISBANDED, first.getStatus());
        assertTrue(first.getDisbandedAt() > 0);

        // Second disband is idempotent — still DISBANDED, no exception.
        Team second = mgr.disbandTeam(team.getTeamId());
        assertEquals(TeamStatus.DISBANDED, second.getStatus());
    }

    @Test
    void disbandUnknownTeamThrows() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> mgr.disbandTeam("nonexistent"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    // ========================================================================
    // removeMember — cleanup
    // ========================================================================

    @Test
    void removeMemberCleanup() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(twoMemberSpec());
        mgr.bindMemberSession(team.getTeamId(), "lead", "lead-sess", "actor-lead");

        assertTrue(mgr.removeMember(team.getTeamId(), "lead"),
                "removeMember on an existing member returns true");
        assertTrue(mgr.getMember(team.getTeamId(), "lead").isEmpty(),
                "removed member is no longer readable");
        assertTrue(mgr.getTeamBySession("lead-sess").isEmpty(),
                "removed member's session reverse-lookup is gone");

        assertFalse(mgr.removeMember(team.getTeamId(), "lead"),
                "removing an already-removed member returns false");
        assertFalse(mgr.removeMember("nonexistent", "x"),
                "removeMember on unknown team returns false");
    }

    // ========================================================================
    // Snapshot isolation (design 裁定 3)
    // ========================================================================

    @Test
    void snapshotIsolationTwoGetTeamCalls() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(twoMemberSpec());

        Team snap1 = mgr.getTeam(team.getTeamId()).orElseThrow();
        Team snap2 = mgr.getTeam(team.getTeamId()).orElseThrow();

        assertNotSame(snap1, snap2,
                "two getTeam calls must return distinct snapshot objects");
        assertNotSame(snap1.getMembers(), snap2.getMembers(),
                "the member maps must be distinct instances");

        // Mutating snap1's member map must NOT affect snap2 or the DB.
        snap1.getMembers().put("injected", new TeamMember("injected", MemberRole.MEMBER, 1L));
        Team snap3 = mgr.getTeam(team.getTeamId()).orElseThrow();
        assertFalse(snap3.getMembers().containsKey("injected"),
                "caller mutation on a returned snapshot must NOT persist to the DB");
        assertEquals(2, snap3.getMembers().size());
    }
}
