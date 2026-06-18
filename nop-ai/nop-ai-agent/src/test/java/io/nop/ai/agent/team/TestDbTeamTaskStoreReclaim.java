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
import java.util.Collections;
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
 * Plan 240 Phase 2 focused tests for {@link DbTeamTaskStore#reclaimTask}:
 * the CLAIMED→CREATED recovery transition is verified against a real H2 DB
 * (not a mock), satisfying Minimum Rules #22 (Anti-Hollow), #23 (Wiring
 * Verification), and #24 (No Silent No-Op).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #reclaimTransitionsClaimedToCreatedAndClearsClaimedBy} — CLAIMED→CREATED + claimedBy=null</li>
 *   <li>{@link #reclaimOnNonClaimedReturnsEmpty} — CREATED/COMPLETED/ABANDONED affected=0</li>
 *   <li>{@link #crossInstanceVisibilityAfterReclaim} — instance A reclaims, instance B sees CREATED</li>
 *   <li>{@link #concurrentReclaimAtMostOneWinner} — two instances racing, exactly one wins</li>
 *   <li>{@link #reclaimedTaskIsReClaimable} — Anti-Hollow: reclaimed task re-claimable</li>
 * </ul>
 *
 * <p>See plan 240 (L4-team-task-reclaim-and-timeout-abandon) Phase 2.
 */
public class TestDbTeamTaskStoreReclaim {

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
        dbUrl = "jdbc:h2:mem:test-team-task-reclaim-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    @Test
    void reclaimTransitionsClaimedToCreatedAndClearsClaimedBy() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask task = store.createTask("team-1", "Do something", "desc",
                Collections.emptyList(), "creator-sess");
        store.claimTask(task.getTaskId(), "claimer-sess");

        Optional<TeamTask> updated = store.reclaimTask(task.getTaskId(), "reclaimer-sess");

        assertTrue(updated.isPresent(), "reclaim on a CLAIMED task must succeed");
        assertEquals(TeamTaskStatus.CREATED, updated.get().getStatus());
        assertNull(updated.get().getClaimedBy(),
                "reclaim must clear claimedBy to null (re-claimable)");

        // Re-read from DB to verify the row was persisted.
        TeamTask stored = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CREATED, stored.getStatus());
        assertNull(stored.getClaimedBy());
    }

    @Test
    void reclaimOnNonClaimedReturnsEmpty() {
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask createdTask = store.createTask("team-1", "created", "desc",
                Collections.emptyList(), "creator-sess");
        // CREATED → reclaim = empty (affected=0, WHERE STATUS='CLAIMED' guard).
        assertTrue(store.reclaimTask(createdTask.getTaskId(), "reclaimer-sess").isEmpty(),
                "reclaim on a CREATED task must fail (affected=0)");
        assertEquals(TeamTaskStatus.CREATED, store.getTask(createdTask.getTaskId()).orElseThrow().getStatus());

        TeamTask claimedTask = store.createTask("team-1", "claimed", "desc",
                Collections.emptyList(), "creator-sess");
        store.claimTask(claimedTask.getTaskId(), "claimer-sess");
        store.completeTask(claimedTask.getTaskId(), "completer-sess");
        assertTrue(store.reclaimTask(claimedTask.getTaskId(), "reclaimer-sess").isEmpty(),
                "reclaim on a COMPLETED (terminal) task must fail (affected=0)");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(claimedTask.getTaskId()).orElseThrow().getStatus());

        TeamTask claimedTask2 = store.createTask("team-1", "claimed2", "desc",
                Collections.emptyList(), "creator-sess");
        store.claimTask(claimedTask2.getTaskId(), "claimer-sess");
        store.abandonTask(claimedTask2.getTaskId(), "abandoner-sess");
        assertTrue(store.reclaimTask(claimedTask2.getTaskId(), "reclaimer-sess").isEmpty(),
                "reclaim on an ABANDONED (terminal) task must fail (affected=0)");
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(claimedTask2.getTaskId()).orElseThrow().getStatus());
    }

    @Test
    void crossInstanceVisibilityAfterReclaim() {
        // Two store instances sharing the same DB. Instance A reclaims a
        // CLAIMED task; instance B's getTask must observe CREATED.
        DbTeamTaskStore instanceA = new DbTeamTaskStore(dataSource);
        DbTeamTaskStore instanceB = new DbTeamTaskStore(dataSource);

        TeamTask task = instanceA.createTask("team-1", "cross-instance", "desc",
                Collections.emptyList(), "creator-sess");
        instanceA.claimTask(task.getTaskId(), "claimer-a");

        // Instance A reclaims.
        Optional<TeamTask> reclaimed = instanceA.reclaimTask(task.getTaskId(), "reclaimer-a");
        assertTrue(reclaimed.isPresent());

        // Instance B sees the CREATED state (cross-process visibility).
        TeamTask fromB = instanceB.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CREATED, fromB.getStatus(),
                "instance B must observe the CREATED state after instance A's reclaim");
        assertNull(fromB.getClaimedBy(),
                "instance B must observe claimedBy=null after reclaim");
    }

    @Test
    void concurrentReclaimAtMostOneWinner() throws InterruptedException {
        // Two store instances racing to reclaim the same CLAIMED task.
        // Exactly one wins (affected=1), the other loses (affected=0).
        DbTeamTaskStore setupStore = new DbTeamTaskStore(dataSource);
        TeamTask task = setupStore.createTask("team-1", "race", "desc",
                Collections.emptyList(), "creator-sess");
        setupStore.claimTask(task.getTaskId(), "claimer-sess");

        DbTeamTaskStore instanceA = new DbTeamTaskStore(dataSource);
        DbTeamTaskStore instanceB = new DbTeamTaskStore(dataSource);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                final DbTeamTaskStore s = (i % 2 == 0) ? instanceA : instanceB;
                final String reclaimer = "reclaimer-" + i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    Optional<TeamTask> result = s.reclaimTask(task.getTaskId(), reclaimer);
                    if (result.isPresent()) {
                        successes.incrementAndGet();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            pool.shutdown();
        }
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, successes.get(),
                "exactly one concurrent reclaimer must win the CLAIMED→CREATED CAS race");

        // Final state is CREATED with cleared claimedBy.
        TeamTask stored = setupStore.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CREATED, stored.getStatus());
        assertNull(stored.getClaimedBy());
    }

    @Test
    void reclaimedTaskIsReClaimable() {
        // Anti-Hollow: reclaim doesn't just change the status field — the
        // reclaimed task must be re-claimable by another member via claimTask.
        DbTeamTaskStore store = new DbTeamTaskStore(dataSource);
        TeamTask task = store.createTask("team-1", "re-claimable", "desc",
                Collections.emptyList(), "creator-sess");
        store.claimTask(task.getTaskId(), "claimer-a");

        store.reclaimTask(task.getTaskId(), "reclaimer-sess");

        // Another member can now claim the reclaimed task.
        Optional<TeamTask> reclaimed = store.claimTask(task.getTaskId(), "claimer-b");

        assertTrue(reclaimed.isPresent(),
                "a reclaimed task must be re-claimable via claimTask (Anti-Hollow)");
        assertEquals(TeamTaskStatus.CLAIMED, reclaimed.get().getStatus());
        assertEquals("claimer-b", reclaimed.get().getClaimedBy());
    }
}
