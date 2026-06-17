package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.quota.CountingResourceGuard;
import io.nop.ai.agent.quota.DefaultResourceGuard;
import io.nop.ai.agent.quota.QuotaConfig;
import io.nop.ai.agent.quota.QuotaDimension;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 focused tests for the {@link IResourceGuard} wiring into
 * {@link InMemoryTeamManager} and {@link DbTeamManager} (plan 234).
 *
 * <p>Verifies the two team quota dimensions end-to-end against both
 * functional {@link ITeamManager} implementations:
 * <ul>
 *   <li>{@link QuotaDimension#TEAM_MEMBERS} at {@code createTeam} /
 *       {@code addMember} (config-driven via {@link QuotaConfig}).</li>
 *   <li>{@link QuotaDimension#TEAM_PARALLEL_BOUND_MEMBERS} at
 *       {@code bindMemberSession} (per-team override
 *       {@code maxParallelMembers}, the hint→enforced upgrade).</li>
 * </ul>
 *
 * <p>Coverage:
 * <ul>
 *   <li>NoOp default → zero regression (no enforcement, all ops proceed).</li>
 *   <li>Wiring verification — a {@link CountingResourceGuard} asserts the
 *       enforcement point actually called {@code checkConcurrent} at runtime
 *       (Minimum Rules #23).</li>
 *   <li>Denial actually throws {@link NopAiAgentException} and the bound /
 *       member counts are NOT exceeded (Anti-Hollow — state unchanged on
 *       denial, Minimum Rules #24 no silent skip).</li>
 *   <li>{@link DbTeamManager} denial happens BEFORE the UPDATE (no row
 *       mutated past the limit).</li>
 * </ul>
 */
public class TestTeamManagerQuotaEnforcement {

    // ------------------------------------------------------------------
    // InMemoryTeamManager
    // ------------------------------------------------------------------

    @Test
    void inMemoryNoOpDefaultIsZeroRegression() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD),
                        new TeamMemberSpec("m1", "a", MemberRole.MEMBER),
                        new TeamMemberSpec("m2", "a", MemberRole.MEMBER)),
                1));
        // NoOp → no enforcement: 3 members + maxParallelMembers=1 both pass.
        assertEquals(3, team.getMembers().size());
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "lead", "s1", "a1"));
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "m1", "s2", "a2"));
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "m2", "s3", "a3"));
        // All three bound despite maxParallelMembers=1 (NoOp = no restriction).
        assertEquals(3, mgr.getTeam(team.getTeamId()).get().getMembers().values().stream()
                .filter(TeamMember::isBound).count());
    }

    @Test
    void inMemoryMaxParallelMembersEnforcedOnBind() {
        InMemoryTeamManager mgr = new InMemoryTeamManager(new DefaultResourceGuard());
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD),
                        new TeamMemberSpec("m1", "a", MemberRole.MEMBER),
                        new TeamMemberSpec("m2", "a", MemberRole.MEMBER)),
                2));

        // 1st and 2nd binds succeed (projected 1, 2 <= 2).
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "lead", "s1", "a1"));
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "m1", "s2", "a2"));

        // 3rd bind denied (projected 3 > 2).
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> mgr.bindMemberSession(team.getTeamId(), "m2", "s3", "a3"));
        assertTrue(ex.getMessage().contains("TEAM_PARALLEL_BOUND_MEMBERS"));

        // Anti-Hollow: bound count unchanged at 2 (denial prevented the 3rd).
        long boundCount = mgr.getTeam(team.getTeamId()).get().getMembers().values().stream()
                .filter(TeamMember::isBound).count();
        assertEquals(2, boundCount, "denial must not have bound the 3rd member");
        // m2 remains unbound.
        assertFalse(mgr.getMember(team.getTeamId(), "m2").get().isBound());
    }

    @Test
    void inMemoryTeamMembersEnforcedOnCreateTeam() {
        // QuotaConfig teamMaxMembers=2 → createTeam with 3 members denied.
        InMemoryTeamManager mgr = new InMemoryTeamManager(
                new DefaultResourceGuard(new QuotaConfig(2, 10)));
        assertThrows(NopAiAgentException.class, () -> mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD),
                        new TeamMemberSpec("m1", "a", MemberRole.MEMBER),
                        new TeamMemberSpec("m2", "a", MemberRole.MEMBER)),
                0)));
        // Team not created.
        assertTrue(mgr.getActiveTeams().isEmpty());
    }

    @Test
    void inMemoryTeamMembersEnforcedOnAddMember() {
        // QuotaConfig teamMaxMembers=3 → createTeam(2) ok, 4th addMember denied.
        InMemoryTeamManager mgr = new InMemoryTeamManager(
                new DefaultResourceGuard(new QuotaConfig(3, 10)));
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD),
                        new TeamMemberSpec("m1", "a", MemberRole.MEMBER)),
                0));
        assertEquals(2, team.getMembers().size());
        // 3rd member ok (projected 3 <= 3).
        mgr.addMember(team.getTeamId(), new TeamMemberSpec("m2", "a", MemberRole.MEMBER));
        // 4th member denied (projected 4 > 3).
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> mgr.addMember(team.getTeamId(), new TeamMemberSpec("m3", "a", MemberRole.MEMBER)));
        assertTrue(ex.getMessage().contains("TEAM_MEMBERS"));
        // Anti-Hollow: member count unchanged at 3.
        assertEquals(3, mgr.getTeam(team.getTeamId()).get().getMembers().size());
    }

    @Test
    void inMemoryWiringGuardActuallyInvoked() {
        CountingResourceGuard spy = new CountingResourceGuard(new DefaultResourceGuard());
        InMemoryTeamManager mgr = new InMemoryTeamManager(spy);
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD)), 0));
        mgr.addMember(team.getTeamId(), new TeamMemberSpec("m1", "a", MemberRole.MEMBER));
        mgr.bindMemberSession(team.getTeamId(), "lead", "s1", "a1");

        // Minimum Rules #23: assert the guard was actually called at runtime.
        assertTrue(spy.wasCalled(), "createTeam/addMember/bindMemberSession must call the guard");
        // createTeam(TEAM_MEMBERS) + addMember(TEAM_MEMBERS) + bind(TEAM_PARALLEL_BOUND_MEMBERS)
        assertTrue(spy.getDimensions().contains(QuotaDimension.TEAM_MEMBERS));
        assertTrue(spy.getDimensions().contains(QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS));
    }

    @Test
    void inMemoryNullGuardFallsBackToNoOp() {
        // null guard → NoOp fallback (zero regression).
        InMemoryTeamManager mgr = new InMemoryTeamManager((io.nop.ai.agent.quota.IResourceGuard) null);
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD)), 1));
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "lead", "s", "a"));
    }

    // ------------------------------------------------------------------
    // DbTeamManager (H2)
    // ------------------------------------------------------------------

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
        String dbUrl = "jdbc:h2:mem:test-team-quota-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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
                // best-effort
            }
        }
    }

    @Test
    void dbNoOpDefaultIsZeroRegression() {
        DbTeamManager mgr = new DbTeamManager(dataSource);
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD),
                        new TeamMemberSpec("m1", "a", MemberRole.MEMBER),
                        new TeamMemberSpec("m2", "a", MemberRole.MEMBER)),
                1));
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "lead", "s1", "a1"));
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "m1", "s2", "a2"));
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "m2", "s3", "a3"));
        // All three bound despite maxParallelMembers=1 (NoOp = no restriction).
        Team snapshot = mgr.getTeam(team.getTeamId()).get();
        assertEquals(3, snapshot.getMembers().values().stream().filter(TeamMember::isBound).count());
    }

    @Test
    void dbMaxParallelMembersEnforcedOnBind() {
        DbTeamManager mgr = new DbTeamManager(dataSource,
                io.nop.ai.agent.security.NullTenantResolver.INSTANCE,
                new DefaultResourceGuard());
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD),
                        new TeamMemberSpec("m1", "a", MemberRole.MEMBER),
                        new TeamMemberSpec("m2", "a", MemberRole.MEMBER)),
                2));

        assertTrue(mgr.bindMemberSession(team.getTeamId(), "lead", "s1", "a1"));
        assertTrue(mgr.bindMemberSession(team.getTeamId(), "m1", "s2", "a2"));

        // 3rd bind denied (projected 3 > 2); denial before UPDATE.
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> mgr.bindMemberSession(team.getTeamId(), "m2", "s3", "a3"));
        assertTrue(ex.getMessage().contains("TEAM_PARALLEL_BOUND_MEMBERS"));

        // Anti-Hollow: m2 row NOT bound (denial was before the UPDATE).
        TeamMember m2 = mgr.getMember(team.getTeamId(), "m2").get();
        assertFalse(m2.isBound(), "denial must not have bound m2 in the DB");
        long boundCount = mgr.getTeam(team.getTeamId()).get().getMembers().values().stream()
                .filter(TeamMember::isBound).count();
        assertEquals(2, boundCount);
    }

    @Test
    void dbTeamMembersEnforcedOnCreateTeam() {
        DbTeamManager mgr = new DbTeamManager(dataSource,
                io.nop.ai.agent.security.NullTenantResolver.INSTANCE,
                new DefaultResourceGuard(new QuotaConfig(2, 10)));
        assertThrows(NopAiAgentException.class, () -> mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD),
                        new TeamMemberSpec("m1", "a", MemberRole.MEMBER),
                        new TeamMemberSpec("m2", "a", MemberRole.MEMBER)),
                0)));
        // No team persisted.
        assertTrue(mgr.getActiveTeams().isEmpty());
    }

    @Test
    void dbTeamMembersEnforcedOnAddMember() {
        DbTeamManager mgr = new DbTeamManager(dataSource,
                io.nop.ai.agent.security.NullTenantResolver.INSTANCE,
                new DefaultResourceGuard(new QuotaConfig(3, 10)));
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD),
                        new TeamMemberSpec("m1", "a", MemberRole.MEMBER)),
                0));
        mgr.addMember(team.getTeamId(), new TeamMemberSpec("m2", "a", MemberRole.MEMBER));
        // 4th member denied (projected 4 > 3).
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> mgr.addMember(team.getTeamId(), new TeamMemberSpec("m3", "a", MemberRole.MEMBER)));
        assertTrue(ex.getMessage().contains("TEAM_MEMBERS"));
        // Anti-Hollow: 3 members persisted, not 4.
        assertEquals(3, mgr.getTeam(team.getTeamId()).get().getMembers().size());
    }

    @Test
    void dbWiringGuardActuallyInvoked() {
        CountingResourceGuard spy = new CountingResourceGuard(new DefaultResourceGuard());
        DbTeamManager mgr = new DbTeamManager(dataSource,
                io.nop.ai.agent.security.NullTenantResolver.INSTANCE, spy);
        Team team = mgr.createTeam(new TeamSpec("t", null, "lead",
                List.of(new TeamMemberSpec("lead", "a", MemberRole.LEAD)), 0));
        mgr.addMember(team.getTeamId(), new TeamMemberSpec("m1", "a", MemberRole.MEMBER));
        mgr.bindMemberSession(team.getTeamId(), "lead", "s1", "a1");

        assertTrue(spy.wasCalled(), "DbTeamManager must call the guard at runtime");
        assertTrue(spy.getDimensions().contains(QuotaDimension.TEAM_MEMBERS));
        assertTrue(spy.getDimensions().contains(QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS));
    }
}
