package io.nop.ai.agent.team;

import org.junit.jupiter.api.Test;

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
 * Focused tests for {@link InMemoryTeamTaskStore#reclaimTask} (plan 240
 * Phase 2): the CLAIMED→CREATED recovery transition. Each legal/illegal
 * transition, claimedBy clearing, terminal-state rejection, missing-task
 * handling, and concurrent reclaim CAS (at most one winner) are verified,
 * satisfying Minimum Rules #22 (Anti-Hollow), #23 (Wiring Verification),
 * and #24 (No Silent No-Op).
 *
 * <p>See plan 240 (L4-team-task-reclaim-and-timeout-abandon) Phase 2.
 */
public class TestInMemoryTeamTaskStoreReclaim {

    private static TeamTask newClaimedTask(InMemoryTeamTaskStore store) {
        TeamTask task = store.createTask("team-1", "Do something", "desc",
                Collections.emptyList(), "creator-sess");
        store.claimTask(task.getTaskId(), "claimer-sess");
        return store.getTask(task.getTaskId()).orElseThrow();
    }

    @Test
    void reclaimTransitionsClaimedToCreatedAndClearsClaimedBy() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newClaimedTask(store);

        Optional<TeamTask> updated = store.reclaimTask(task.getTaskId(), "reclaimer-sess");

        assertTrue(updated.isPresent(), "reclaim on a CLAIMED task must succeed");
        assertEquals(TeamTaskStatus.CREATED, updated.get().getStatus(),
                "reclaim must transition CLAIMED → CREATED");
        assertNull(updated.get().getClaimedBy(),
                "reclaim must clear claimedBy to null (re-claimable)");

        // The store's view reflects the transition too.
        TeamTask stored = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CREATED, stored.getStatus());
        assertNull(stored.getClaimedBy());
    }

    @Test
    void reclaimOnAlreadyCreatedReturnsEmpty() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = store.createTask("team-1", "Do something", "desc",
                Collections.emptyList(), "creator-sess");

        // CREATED (never claimed) — reclaim is illegal (source must be CLAIMED).
        Optional<TeamTask> updated = store.reclaimTask(task.getTaskId(), "reclaimer-sess");

        assertTrue(updated.isEmpty(),
                "reclaim on a CREATED task must fail (CAS guard on status)");
        assertEquals(TeamTaskStatus.CREATED, store.getTask(task.getTaskId()).orElseThrow().getStatus(),
                "task status must not change on a failed reclaim");
    }

    @Test
    void reclaimOnCompletedReturnsEmpty() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newClaimedTask(store);
        store.completeTask(task.getTaskId(), "completer-sess", task.getClaimEpoch());

        Optional<TeamTask> updated = store.reclaimTask(task.getTaskId(), "reclaimer-sess");

        assertTrue(updated.isEmpty(),
                "reclaim on a COMPLETED (terminal) task must fail — terminal states are not recoverable");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(task.getTaskId()).orElseThrow().getStatus());
    }

    @Test
    void reclaimOnAbandonedReturnsEmpty() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newClaimedTask(store);
        store.abandonTask(task.getTaskId(), "abandoner-sess", task.getClaimEpoch());

        Optional<TeamTask> updated = store.reclaimTask(task.getTaskId(), "reclaimer-sess");

        assertTrue(updated.isEmpty(),
                "reclaim on an ABANDONED (terminal) task must fail — terminal states are not recoverable");
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(task.getTaskId()).orElseThrow().getStatus());
    }

    @Test
    void reclaimOnMissingTaskReturnsEmpty() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        Optional<TeamTask> updated = store.reclaimTask("nonexistent-task-id", "reclaimer-sess");

        assertTrue(updated.isEmpty(),
                "reclaim on a missing task must return empty (not throw)");
    }

    @Test
    void reclaimedTaskIsReClaimable() {
        // Anti-Hollow: reclaim doesn't just change the status field — the
        // reclaimed task must be re-claimable by another member via claimTask.
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newClaimedTask(store);

        store.reclaimTask(task.getTaskId(), "reclaimer-sess");

        // Another member can now claim the reclaimed task (CREATED → CLAIMED).
        Optional<TeamTask> reclaimed = store.claimTask(task.getTaskId(), "claimer-b");

        assertTrue(reclaimed.isPresent(),
                "a reclaimed task must be re-claimable via claimTask (Anti-Hollow)");
        assertEquals(TeamTaskStatus.CLAIMED, reclaimed.get().getStatus());
        assertEquals("claimer-b", reclaimed.get().getClaimedBy(),
                "re-claim must record the new claimer's sessionId");
    }

    @Test
    void concurrentReclaimAtMostOneWinner() throws InterruptedException {
        // CAS guard: at most one concurrent reclaimer wins a CLAIMED→CREATED
        // race. Even though reclaim is idempotent in result (CREATED), the
        // CAS guard ensures exactly one writer observes success on the
        // transition; the others observe the task is no longer CLAIMED.
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newClaimedTask(store);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                final String reclaimer = "reclaimer-" + i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    Optional<TeamTask> result = store.reclaimTask(task.getTaskId(), reclaimer);
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
        try {
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(1, successes.get(),
                "exactly one concurrent reclaimer must win the CLAIMED→CREATED CAS race");
        // Final state is CREATED with cleared claimedBy.
        TeamTask stored = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CREATED, stored.getStatus());
        assertNull(stored.getClaimedBy());
    }
}
