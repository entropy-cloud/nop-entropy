package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.scheduler.TeamTaskSchedulerDaemon;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 227 Phase 2 focused tests for {@link DbTeamTaskStore}: each CRUD /
 * state-machine CAS semantic is verified against a real H2 DB (not a mock),
 * satisfying Minimum Rules #22 (Anti-Hollow) and #23 (Wiring Verification).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #constructorAutoCreatesTable} — construction-time schema init</li>
 *   <li>{@link #createAndGetRoundTripsAllFields} — field fidelity incl.
 *       BLOCKED_BY CSV round-trip</li>
 *   <li>{@link #getTasksByTeamAndGetTasksByCreator} — secondary reads</li>
 *   <li>{@link #crossInstanceVisibility} — two stores sharing one DB</li>
 *   <li>{@link #claimTransitionAndClaimedBy} — CREATED→CLAIMED + claimedBy</li>
 *   <li>{@link #completeRequiresClaimed} — CREATED→complete = empty</li>
 *   <li>{@link #completeTransitionPreservesClaimedBy} — CLAIMED→COMPLETED</li>
 *   <li>{@link #abandonFromCreatedAndClaimed} — both abandon sources</li>
 *   <li>{@link #terminalStatesRejectAllTransitions} — terminal guard</li>
 *   <li>{@link #concurrentClaimAtMostOneWinner} — cross-instance CAS race</li>
 *   <li>{@link #transitionOnMissingTaskReturnsEmpty} — missing-task CAS</li>
 * </ul>
 *
 * <p>See plan 227 (team-task-update) Phase 2.
 */
public class TestDbTeamTaskStore {

    private DataSource dataSource;
    private String dbUrl;

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
        // that independent DbTeamTaskStore instances (cross-instance tests)
        // share the same table.
        dbUrl = "jdbc:h2:mem:test-team-task-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    // ========================================================================
    // Construction-time schema init
    // ========================================================================

    @Test
    void constructorAutoCreatesTable() {
        // Constructing the store must auto-create the table (no manual DDL).
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        // If the table exists, this insert + read round-trips successfully.
        TeamTask task = store.createTask("team-1", "Schema probe", null,
                Collections.emptyList(), "creator");
        assertNotNull(task.getTaskId());
        assertTrue(store.getTask(task.getTaskId()).isPresent(),
                "Auto-created table must store and return the task");
    }

    // ========================================================================
    // createTask + getTask field fidelity (incl. BLOCKED_BY CSV round-trip)
    // ========================================================================

    @Test
    void createAndGetRoundTripsAllFields() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        List<String> deps = Arrays.asList("dep-a", "dep-b", "dep-c");
        TeamTask created = store.createTask("team-1", "Round trip", "a description",
                deps, "creator-sess");

        TeamTask read = store.getTask(created.getTaskId()).orElseThrow();

        assertEquals(created.getTaskId(), read.getTaskId());
        assertEquals("team-1", read.getTeamId());
        assertEquals("Round trip", read.getSubject());
        assertEquals("a description", read.getDescription());
        assertEquals(TeamTaskStatus.CREATED, read.getStatus());
        assertEquals("creator-sess", read.getCreatedBy());
        assertNull(read.getClaimedBy(), "claimedBy is null at creation");
        assertTrue(read.getCreatedAt() > 0);
        assertEquals(deps, read.getBlockedBy(),
                "BLOCKED_BY CSV must round-trip back to the original list");
    }

    @Test
    void createWithNullBlockedByRoundTripsToEmptyList() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask created = store.createTask("team-1", "No deps", null,
                Collections.emptyList(), "creator");

        TeamTask read = store.getTask(created.getTaskId()).orElseThrow();
        assertTrue(read.getBlockedBy().isEmpty(),
                "Empty/nullable BLOCKED_BY column reads back as an empty list");
    }

    @Test
    void getTaskReturnsEmptyForUnknownId() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        assertTrue(store.getTask("nonexistent").isEmpty());
        assertTrue(store.getTask(null).isEmpty());
    }

    // ========================================================================
    // getTasksByTeam / getTasksByCreator
    // ========================================================================

    @Test
    void getTasksByTeamAndGetTasksByCreator() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        store.createTask("team-A", "T1", null, Collections.emptyList(), "sess-1");
        store.createTask("team-A", "T2", null, Collections.emptyList(), "sess-2");
        store.createTask("team-B", "T3", null, Collections.emptyList(), "sess-1");

        List<TeamTask> teamA = store.getTasksByTeam("team-A");
        List<TeamTask> teamB = store.getTasksByTeam("team-B");
        List<TeamTask> bySess1 = store.getTasksByCreator("sess-1");

        assertEquals(2, teamA.size());
        assertEquals(1, teamB.size());
        assertEquals(2, bySess1.size(),
                "sess-1 created one task in team-A and one in team-B");
        assertTrue(store.getTasksByTeam("unknown").isEmpty());
        assertTrue(store.getTasksByCreator("unknown").isEmpty());
    }

    // ========================================================================
    // Cross-instance visibility (two stores sharing one DB)
    // ========================================================================

    @Test
    void crossInstanceVisibility() {
        DbTeamTaskStore instanceA = new DbTeamTaskStore(dataSource);
        DbTeamTaskStore instanceB = new DbTeamTaskStore(dataSource);

        TeamTask created = instanceA.createTask("team-1", "Shared task", null,
                Collections.emptyList(), "creator");

        Optional<TeamTask> readFromB = instanceB.getTask(created.getTaskId());
        assertTrue(readFromB.isPresent(),
                "A task created by instance A must be visible to instance B (shared DB)");
        assertEquals("Shared task", readFromB.get().getSubject());
    }

    // ========================================================================
    // claim — CREATED → CLAIMED + claimedBy recording
    // ========================================================================

    @Test
    void claimTransitionAndClaimedBy() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask task = store.createTask("team-1", "Claim me", null,
                Collections.emptyList(), "creator");

        Optional<TeamTask> updated = store.claimTask(task.getTaskId(), "claimer-sess");

        assertTrue(updated.isPresent(), "claim on a CREATED task must succeed");
        assertEquals(TeamTaskStatus.CLAIMED, updated.get().getStatus());
        assertEquals("claimer-sess", updated.get().getClaimedBy(),
                "claim must record the claimer sessionId in CLAIMED_BY");
    }

    @Test
    void duplicateClaimFailsViaCas() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask task = store.createTask("team-1", "Race target", null,
                Collections.emptyList(), "creator");

        assertTrue(store.claimTask(task.getTaskId(), "claimer-a").isPresent());
        Optional<TeamTask> second = store.claimTask(task.getTaskId(), "claimer-b");
        assertTrue(second.isEmpty(),
                "Second claim must fail via STATUS CAS guard (0 rows affected)");

        // The recorded claimer is the winner, not the loser.
        TeamTask stored = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals("claimer-a", stored.getClaimedBy());
    }

    // ========================================================================
    // complete — requires CLAIMED
    // ========================================================================

    @Test
    void completeRequiresClaimed() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask task = store.createTask("team-1", "Not claimed yet", null,
                Collections.emptyList(), "creator");

        // null epoch honestly fails the CAS (a CLAIMED row never has null epoch).
        Optional<TeamTask> directComplete = store.completeTask(task.getTaskId(), "someone", null);
        assertTrue(directComplete.isEmpty(),
                "complete on a CREATED task must fail (CLAIMED required)");
        assertEquals(TeamTaskStatus.CREATED,
                store.getTask(task.getTaskId()).orElseThrow().getStatus());
    }

    @Test
    void completeTransitionPreservesClaimedBy() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask task = store.createTask("team-1", "Complete me", null,
                Collections.emptyList(), "creator");
        // Capture the claim epoch assigned atomically by claimTask (plan 279 /
        // AR-01); the complete CAS must bind it to bind to the owner generation.
        Long epoch = store.claimTask(task.getTaskId(), "claimer-sess")
                .orElseThrow().getClaimEpoch();

        Optional<TeamTask> updated = store.completeTask(task.getTaskId(), "completer-sess", epoch);

        assertTrue(updated.isPresent());
        assertEquals(TeamTaskStatus.COMPLETED, updated.get().getStatus());
        assertEquals("claimer-sess", updated.get().getClaimedBy(),
                "complete must preserve CLAIMED_BY (design 裁定 6)");
    }

    // ========================================================================
    // abandon — from CREATED and CLAIMED
    // ========================================================================

    @Test
    void abandonFromCreatedAndClaimed() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);

        // abandon directly from CREATED (lead gives up on unclaimed task).
        // A CREATED-task abandon passes a null epoch (matches the
        // CLAIM_EPOCH IS NULL branch of the CAS — plan 279 / AR-01).
        TeamTask taskA = store.createTask("team-1", "Abandon from created", null,
                Collections.emptyList(), "creator");
        Optional<TeamTask> ab1 = store.abandonTask(taskA.getTaskId(), "lead-sess", null);
        assertTrue(ab1.isPresent(),
                "abandon on a CREATED task must succeed");
        assertEquals(TeamTaskStatus.ABANDONED, ab1.get().getStatus());
        assertNull(ab1.get().getClaimedBy(),
                "claimedBy stays null when abandoned from CREATED");

        // abandon from CLAIMED (claimer gives up; claimedBy preserved). The
        // CLAIMED branch binds the captured epoch (owner generation).
        TeamTask taskB = store.createTask("team-1", "Abandon from claimed", null,
                Collections.emptyList(), "creator");
        Long epochB = store.claimTask(taskB.getTaskId(), "claimer-sess")
                .orElseThrow().getClaimEpoch();
        Optional<TeamTask> ab2 = store.abandonTask(taskB.getTaskId(), "claimer-sess", epochB);
        assertTrue(ab2.isPresent(),
                "abandon on a CLAIMED task must succeed");
        assertEquals(TeamTaskStatus.ABANDONED, ab2.get().getStatus());
        assertEquals("claimer-sess", ab2.get().getClaimedBy(),
                "abandon preserves CLAIMED_BY (design 裁定 6)");
    }

    // ========================================================================
    // Terminal states reject all transitions
    // ========================================================================

    @Test
    void terminalStatesRejectAllTransitions() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);

        // COMPLETED terminal.
        TeamTask completed = store.createTask("team-1", "Done", null,
                Collections.emptyList(), "creator");
        Long epochC = store.claimTask(completed.getTaskId(), "c").orElseThrow().getClaimEpoch();
        store.completeTask(completed.getTaskId(), "c", epochC);
        assertTrue(store.claimTask(completed.getTaskId(), "x").isEmpty());
        assertTrue(store.completeTask(completed.getTaskId(), "x", null).isEmpty());
        assertTrue(store.abandonTask(completed.getTaskId(), "x", null).isEmpty());

        // ABANDONED terminal.
        TeamTask abandoned = store.createTask("team-1", "Given up", null,
                Collections.emptyList(), "creator");
        store.abandonTask(abandoned.getTaskId(), "lead", null);
        assertTrue(store.claimTask(abandoned.getTaskId(), "x").isEmpty());
        assertTrue(store.completeTask(abandoned.getTaskId(), "x", null).isEmpty());
        assertTrue(store.abandonTask(abandoned.getTaskId(), "x", null).isEmpty());
    }

    @Test
    void transitionOnMissingTaskReturnsEmpty() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        assertTrue(store.claimTask("nonexistent", "x").isEmpty(),
                "claim on a missing task returns empty (CAS affects 0 rows)");
        assertTrue(store.completeTask("nonexistent", "x", null).isEmpty());
        assertTrue(store.abandonTask("nonexistent", "x", null).isEmpty());
    }

    // ========================================================================
    // Cross-instance concurrent CAS — at most one claimer wins
    // ========================================================================

    @Test
    void concurrentClaimAtMostOneWinner() throws Exception {
        // Two independent store instances share the same H2 DB (simulating two
        // processes). Each store instance is backed by the shared table.
        DbTeamTaskStore storeA = new DbTeamTaskStore(dataSource);
        DbTeamTaskStore storeB = new DbTeamTaskStore(dataSource);
        TeamTask task = storeA.createTask("team-1", "Race target", null,
                Collections.emptyList(), "creator");

        int threadCount = 16;
        // Half the threads use storeA, half use storeB (cross-instance race).
        DbTeamTaskStore[] stores = new DbTeamTaskStore[]{storeA, storeB};
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger(0);
        AtomicInteger losers = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final DbTeamTaskStore store = stores[i % stores.length];
            final String claimer = "claimer-" + i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    Optional<TeamTask> r = store.claimTask(task.getTaskId(), claimer);
                    if (r.isPresent()) {
                        winners.incrementAndGet();
                    } else {
                        losers.incrementAndGet();
                    }
                } catch (Exception e) {
                    losers.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, winners.get(),
                "Exactly one concurrent claimer must win the DB CAS race (winners="
                        + winners.get() + ")");
        assertEquals(threadCount - 1, losers.get(),
                "All other claimers must see a CAS failure (losers=" + losers.get() + ")");
        TeamTask stored = storeA.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CLAIMED, stored.getStatus());
        assertNotNull(stored.getClaimedBy());
    }

    // ========================================================================
    // Plan 279 / AR-01: claim-epoch CAS closes the shared-daemon-id
    // double-execution window. Both daemon instances share the SAME
    // DEFAULT_DAEMON_SESSION_ID, so a plain CLAIMED_BY CAS would still match
    // after reclaim+re-claim; the CLAIM_EPOCH binding is what rejects the
    // stale in-flight complete.
    // ========================================================================

    @Test
    void sharedDaemonIdEpochCasClosesDoubleExecutionWindow() {
        // Two store instances (instance A and B) share one DB and the SAME
        // daemon session id — the production daemon configuration.
        DbTeamTaskStore instanceA = new DbTeamTaskStore(dataSource);
        DbTeamTaskStore instanceB = new DbTeamTaskStore(dataSource);
        String daemonSessionId = TeamTaskSchedulerDaemon.DEFAULT_DAEMON_SESSION_ID;

        // 1. Instance A claims T1 under the shared daemon id (epoch e1).
        TeamTask t1 = instanceA.createTask("team-1", "Double-exec target", null,
                Collections.emptyList(), "creator");
        Long e1 = instanceA.claimTask(t1.getTaskId(), daemonSessionId)
                .orElseThrow().getClaimEpoch();
        assertNotNull(e1, "claim must assign a non-null epoch");

        // 2. Simulate a timeout-driven reclaim: clears CLAIMED_BY but
        //    PRESERVES CLAIM_EPOCH (kept monotonic so the next claim's epoch
        //    is strictly larger than the abandoned claim's — closing the
        //    shared-daemon-id double-execution window).
        Optional<TeamTask> reclaimed = instanceA.reclaimTask(t1.getTaskId(), "reclaimer");
        assertTrue(reclaimed.isPresent());
        assertNull(reclaimed.get().getClaimedBy(),
                "reclaim must clear CLAIMED_BY");
        assertEquals(e1, reclaimed.get().getClaimEpoch(),
                "reclaim must PRESERVE CLAIM_EPOCH (monotonic), not null it");

        // 3. Instance B re-claims T1 under the SAME shared daemon id — a fresh
        //    epoch e2 is assigned (e2 = e1 + 1, strictly larger).
        Long e2 = instanceB.claimTask(t1.getTaskId(), daemonSessionId)
                .orElseThrow().getClaimEpoch();
        assertTrue(e2 > e1,
                "re-claim after reclaim must assign a strictly larger epoch");

        // 4. Instance A's stale in-flight complete (holding the OLD epoch e1)
        //    MUST fail the CAS even though it presents the same daemon id —
        //    the double-execution window is closed (returns Optional.empty,
        //    not a silent success).
        Optional<TeamTask> staleComplete = instanceA.completeTask(
                t1.getTaskId(), daemonSessionId, e1);
        assertTrue(staleComplete.isEmpty(),
                "stale-epoch complete must fail the CAS (shared-daemon-id double-exec closed)");

        // 5. Instance B's complete with the current epoch e2 succeeds.
        Optional<TeamTask> freshComplete = instanceB.completeTask(
                t1.getTaskId(), daemonSessionId, e2);
        assertTrue(freshComplete.isPresent(),
                "current-epoch complete must succeed");
        assertEquals(TeamTaskStatus.COMPLETED, freshComplete.get().getStatus());
    }

    @Test
    void abandonClaimedBranchBindsEpochAndCreatedBranchMatchesNullEpoch() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);

        // (a) CLAIMED branch binds the epoch: the owner's epoch succeeds, a
        //     stale epoch is honestly rejected.
        TeamTask claimed = store.createTask("team-1", "Abandon claimed", null,
                Collections.emptyList(), "creator");
        Long epoch = store.claimTask(claimed.getTaskId(), "claimer")
                .orElseThrow().getClaimEpoch();
        // stale epoch (epoch + 999) must fail the CLAIMED abandon CAS.
        assertTrue(store.abandonTask(claimed.getTaskId(), "claimer", epoch + 999).isEmpty(),
                "CLAIMED abandon with a stale epoch must fail the CAS");
        // current epoch succeeds.
        Optional<TeamTask> ab = store.abandonTask(claimed.getTaskId(), "claimer", epoch);
        assertTrue(ab.isPresent());
        assertEquals(TeamTaskStatus.ABANDONED, ab.get().getStatus());

        // (b) CREATED branch is epoch-agnostic: a lead abandons a task that
        //     was reclaimed back to CREATED. reclaim PRESERVES the epoch
        //     (non-null), yet the CREATED abandon must still succeed — proving
        //     the CREATED branch does not (and must not) predicate on epoch.
        reclaimedCreatedAbandonSucceedsEpochAgnostic(store);
    }

    private void reclaimedCreatedAbandonSucceedsEpochAgnostic(DbTeamTaskStore store) {
        TeamTask task = store.createTask("team-1", "Reclaimed then abandoned", null,
                Collections.emptyList(), "creator");
        Long epoch = store.claimTask(task.getTaskId(), "claimer")
                .orElseThrow().getClaimEpoch();
        // reclaim clears CLAIMED_BY but PRESERVES CLAIM_EPOCH → CREATED with a
        // non-null (preserved) epoch.
        store.reclaimTask(task.getTaskId(), "reclaimer");
        TeamTask afterReclaim = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CREATED, afterReclaim.getStatus());
        assertEquals(epoch, afterReclaim.getClaimEpoch(),
                "reclaim must PRESERVE CLAIM_EPOCH (non-null) so claims stay monotonic");

        // Lead abandons the reclaimed (CREATED, non-null-epoch) task with a
        // null epoch — the CREATED branch must match regardless of epoch
        // (no owner to bind). This proves the CREATED branch is epoch-agnostic.
        Optional<TeamTask> ab = store.abandonTask(task.getTaskId(), "lead", null);
        assertTrue(ab.isPresent(),
                "abandon on a reclaimed CREATED task must succeed (CREATED branch is epoch-agnostic)");
        assertEquals(TeamTaskStatus.ABANDONED, ab.get().getStatus());
    }

    // ========================================================================
    // Argument validation (fail-fast on misuse)
    // ========================================================================

    @Test
    void createTaskValidatesArguments() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> store.createTask(null, "s", null, Collections.emptyList(), "c"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> store.createTask("t", null, null, Collections.emptyList(), "c"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> store.createTask("t", "", null, Collections.emptyList(), "c"));
    }

    @Test
    void transitionsValidateTaskId() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        org.junit.jupiter.api.Assertions.assertThrows(NopAiAgentException.class,
                () -> store.claimTask(null, "c"));
        org.junit.jupiter.api.Assertions.assertThrows(NopAiAgentException.class,
                () -> store.completeTask("", "c", null));
        org.junit.jupiter.api.Assertions.assertThrows(NopAiAgentException.class,
                () -> store.abandonTask(null, "c", null));
    }
}
