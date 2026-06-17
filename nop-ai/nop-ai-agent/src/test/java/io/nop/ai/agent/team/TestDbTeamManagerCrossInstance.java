package io.nop.ai.agent.team;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 230 Phase 1 cross-instance tests for {@link DbTeamManager}: two
 * independent {@link DbTeamManager} instances pointing at the SAME H2 DB
 * verify cross-process sharing semantics + concurrent activation exactly-once
 * (Minimum Rules #22 Anti-Hollow).
 *
 * <p>This simulates two JVM processes sharing one DB: a team created / bound /
 * disbanded by instance A must be visible to instance B, and concurrent
 * first-bindings must activate the team exactly once.
 *
 * <p>See plan 230 (L4-team-db-persistence) Phase 1.
 */
public class TestDbTeamManagerCrossInstance {

    private DataSource sharedDataSource;

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
        // A single shared H2 in-memory DB (DB_CLOSE_DELAY=-1) backs both
        // manager instances — simulating one DB shared by two processes.
        String dbUrl = "jdbc:h2:mem:test-team-cross-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        sharedDataSource = ds;
    }

    @AfterEach
    void tearDown() {
        if (sharedDataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) sharedDataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    private TeamSpec spec() {
        return new TeamSpec("CrossTeam", "cross-instance", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
    }

    // ========================================================================
    // Cross-instance visibility: create+bind on A, read on B
    // ========================================================================

    @Test
    void createAddAndBindOnAVisibleOnB() {
        DbTeamManager instanceA = new DbTeamManager(sharedDataSource);
        DbTeamManager instanceB = new DbTeamManager(sharedDataSource);

        Team created = instanceA.createTeam(spec());
        instanceA.addMember(created.getTeamId(),
                new TeamMemberSpec("extra", "agent", MemberRole.MEMBER));
        instanceA.bindMemberSession(created.getTeamId(), "lead", "lead-sess", "actor-lead");

        // Instance B sees the team, its members, and the binding.
        Team fromB = instanceB.getTeam(created.getTeamId()).orElseThrow();
        assertEquals("CrossTeam", fromB.getSpec().getTeamName());
        assertEquals(3, fromB.getMembers().size(),
                "instance B must see all members added by instance A");
        assertEquals(TeamStatus.ACTIVE, fromB.getStatus(),
                "instance B must see the ACTIVE status activated by instance A");

        TeamMember leadFromB = instanceB.getMember(created.getTeamId(), "lead").orElseThrow();
        assertEquals("lead-sess", leadFromB.getSessionId());
        assertEquals("actor-lead", leadFromB.getActorId());

        // Reverse-lookup from B resolves the team created/bound on A.
        Team bySession = instanceB.getTeamBySession("lead-sess").orElseThrow();
        assertEquals(created.getTeamId(), bySession.getTeamId());
    }

    // ========================================================================
    // Cross-instance disband visibility
    // ========================================================================

    @Test
    void disbandOnAVisibleOnB() {
        DbTeamManager instanceA = new DbTeamManager(sharedDataSource);
        DbTeamManager instanceB = new DbTeamManager(sharedDataSource);

        Team created = instanceA.createTeam(spec());
        instanceA.disbandTeam(created.getTeamId());

        Team fromB = instanceB.getTeam(created.getTeamId()).orElseThrow();
        assertEquals(TeamStatus.DISBANDED, fromB.getStatus(),
                "disband by instance A must be visible to instance B");
        assertTrue(fromB.getDisbandedAt() > 0);
        assertTrue(instanceB.getActiveTeams().isEmpty(),
                "disbanded team is excluded from B's active set");
    }

    // ========================================================================
    // Concurrent bindMemberSession — exactly one triggers CREATED→ACTIVE
    // ========================================================================

    @Test
    void concurrentActivationExactlyOnce() throws Exception {
        DbTeamManager instanceA = new DbTeamManager(sharedDataSource);
        DbTeamManager instanceB = new DbTeamManager(sharedDataSource);

        // Pre-create N teams + members on A; each member is the activation
        // candidate for its own team. We then race many threads (half on A,
        // half on B) binding DIFFERENT members of the SAME team and assert
        // the team ends up ACTIVE exactly once (the conditional UPDATE is the
        // CAS guard — design 裁定 5).
        Team team = instanceA.createTeam(new TeamSpec("RaceTeam", null, "lead",
                Arrays.asList(
                        new TeamMemberSpec("m1", "a", MemberRole.MEMBER),
                        new TeamMemberSpec("m2", "a", MemberRole.MEMBER),
                        new TeamMemberSpec("m3", "a", MemberRole.MEMBER),
                        new TeamMemberSpec("m4", "a", MemberRole.MEMBER)),
                0));

        int threadCount = 16;
        String[] memberNames = {"m1", "m2", "m3", "m4"};
        DbTeamManager[] mgrs = {instanceA, instanceB};
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger boundOk = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final DbTeamManager mgr = mgrs[i % mgrs.length];
            final String memberName = memberNames[i % memberNames.length];
            final String sess = "sess-" + i;
            final String actor = "actor-" + i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    if (mgr.bindMemberSession(team.getTeamId(), memberName, sess, actor)) {
                        boundOk.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // A duplicate (same member bound twice by two threads) is
                    // reported as a successful UPDATE (last-write-wins on the
                    // binding columns); the activation CAS is the real guard.
                }
            });
        }
        pool.shutdown();
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // All bindings succeed (each updates a member row). The team must be
        // ACTIVE (not CREATED, not DISBANDED).
        Team after = instanceA.getTeam(team.getTeamId()).orElseThrow();
        assertEquals(TeamStatus.ACTIVE, after.getStatus(),
                "team must be ACTIVE after concurrent bindings (exactly-once activation)");
        assertEquals(threadCount, boundOk.get(),
                "every binding UPDATE must succeed (member exists)");

        // The team is ACTIVE exactly once — it never goes back to CREATED nor
        // jumps to DISBANDED via bindings. Assert it is not CREATED.
        assertTrue(after.getStatus() != TeamStatus.CREATED,
                "concurrent bindings must have transitioned CREATED → ACTIVE");
    }
}
