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
 * Focused tests for the {@link InMemoryTeamTaskStore} state machine
 * (plan 227 Phase 1): each legal/illegal transition, claimedBy recording,
 * terminal-state rejection, missing-task handling, and concurrent claim CAS
 * (at most one winner).
 *
 * <p>See plan 227 (team-task-update) Phase 1.
 */
public class TestInMemoryTeamTaskStoreTransitions {

    private static TeamTask newTask(InMemoryTeamTaskStore store) {
        return store.createTask("team-1", "Do something", "desc",
                Collections.emptyList(), "creator-sess");
    }

    @Test
    void claimTransitionsCreatedToClaimedAndRecordsClaimedBy() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newTask(store);

        Optional<TeamTask> updated = store.claimTask(task.getTaskId(), "claimer-sess");

        assertTrue(updated.isPresent(), "claim on a CREATED task must succeed");
        assertEquals(TeamTaskStatus.CLAIMED, updated.get().getStatus());
        assertEquals("claimer-sess", updated.get().getClaimedBy(),
                "claim must record the claimer sessionId in claimedBy");

        // The store's view reflects the transition too.
        TeamTask stored = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CLAIMED, stored.getStatus());
        assertEquals("claimer-sess", stored.getClaimedBy());
    }

    @Test
    void duplicateClaimFailsWhenAlreadyClaimed() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newTask(store);

        assertTrue(store.claimTask(task.getTaskId(), "claimer-a").isPresent());
        Optional<TeamTask> second = store.claimTask(task.getTaskId(), "claimer-b");

        assertTrue(second.isEmpty(),
                "A second claim on a CLAIMED task must fail (CAS guard on status)");

        // The recorded claimer is the winner, not the loser.
        TeamTask stored = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals("claimer-a", stored.getClaimedBy());
    }

    @Test
    void completeRequiresClaimedStatus() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newTask(store);

        // CREATED → complete is illegal (must claim first).
        Optional<TeamTask> directComplete = store.completeTask(task.getTaskId(), "someone");
        assertTrue(directComplete.isEmpty(),
                "complete on a CREATED task must fail (CLAIMED is required first)");
        assertEquals(TeamTaskStatus.CREATED, store.getTask(task.getTaskId()).orElseThrow().getStatus());
    }

    @Test
    void completeTransitionsClaimedToCompletedPreservingClaimedBy() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newTask(store);
        store.claimTask(task.getTaskId(), "claimer-sess");

        Optional<TeamTask> updated = store.completeTask(task.getTaskId(), "completer-sess");

        assertTrue(updated.isPresent(), "complete on a CLAIMED task must succeed");
        assertEquals(TeamTaskStatus.COMPLETED, updated.get().getStatus());
        assertEquals("claimer-sess", updated.get().getClaimedBy(),
                "complete must preserve the recorded claimedBy (design 裁定 6)");
    }

    @Test
    void abandonFromClaimedTransitionsToAbandoned() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newTask(store);
        store.claimTask(task.getTaskId(), "claimer-sess");

        Optional<TeamTask> updated = store.abandonTask(task.getTaskId(), "abandoner-sess");

        assertTrue(updated.isPresent(), "abandon on a CLAIMED task must succeed");
        assertEquals(TeamTaskStatus.ABANDONED, updated.get().getStatus());
        assertEquals("claimer-sess", updated.get().getClaimedBy(),
                "abandon must preserve the recorded claimedBy (design 裁定 6)");
    }

    @Test
    void abandonFromCreatedTransitionsToAbandonedWithNullClaimedBy() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newTask(store);

        Optional<TeamTask> updated = store.abandonTask(task.getTaskId(), "lead-sess");

        assertTrue(updated.isPresent(),
                "abandon on a CREATED task must succeed (lead may abandon an unclaimed task)");
        assertEquals(TeamTaskStatus.ABANDONED, updated.get().getStatus());
        assertNull(updated.get().getClaimedBy(),
                "claimedBy stays null when abandoned directly from CREATED");
    }

    @Test
    void completedTerminalStateRejectsAllTransitions() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newTask(store);
        store.claimTask(task.getTaskId(), "claimer-sess");
        store.completeTask(task.getTaskId(), "completer-sess");

        assertTrue(store.claimTask(task.getTaskId(), "x").isEmpty(),
                "COMPLETED is terminal — claim must fail");
        assertTrue(store.completeTask(task.getTaskId(), "x").isEmpty(),
                "COMPLETED is terminal — complete must fail");
        assertTrue(store.abandonTask(task.getTaskId(), "x").isEmpty(),
                "COMPLETED is terminal — abandon must fail");
    }

    @Test
    void abandonedTerminalStateRejectsAllTransitions() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newTask(store);
        store.abandonTask(task.getTaskId(), "lead-sess");

        assertTrue(store.claimTask(task.getTaskId(), "x").isEmpty(),
                "ABANDONED is terminal — claim must fail");
        assertTrue(store.completeTask(task.getTaskId(), "x").isEmpty(),
                "ABANDONED is terminal — complete must fail");
        assertTrue(store.abandonTask(task.getTaskId(), "x").isEmpty(),
                "ABANDONED is terminal — abandon must fail");
    }

    @Test
    void transitionsOnMissingTaskReturnEmpty() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        assertTrue(store.claimTask("nonexistent", "x").isEmpty(),
                "claim on a missing task returns empty");
        assertTrue(store.completeTask("nonexistent", "x").isEmpty(),
                "complete on a missing task returns empty");
        assertTrue(store.abandonTask("nonexistent", "x").isEmpty(),
                "abandon on a missing task returns empty");
    }

    @Test
    void nullTaskIdReturnsEmpty() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        assertTrue(store.claimTask(null, "x").isEmpty());
        assertTrue(store.completeTask(null, "x").isEmpty());
        assertTrue(store.abandonTask(null, "x").isEmpty());
    }

    @Test
    void concurrentClaimAtMostOneWinner() throws Exception {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = newTask(store);

        int threadCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger(0);
        AtomicInteger losers = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
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
                    // unexpected; fail loudly via loser count distortion
                    losers.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, winners.get(),
                "Exactly one concurrent claimer must win the CAS race (winners=" + winners.get() + ")");
        assertEquals(threadCount - 1, losers.get(),
                "All other claimers must see a CAS failure (losers=" + losers.get() + ")");
        TeamTask stored = store.getTask(task.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.CLAIMED, stored.getStatus());
        assertNotNull(stored.getClaimedBy());
        assertTrue(stored.getClaimedBy().startsWith("claimer-"));
    }
}
