package io.nop.ai.agent.team;

import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for {@link InMemoryTeamTaskStore}: full lifecycle
 * (create → getTask → getTasksByTeam → getTasksByCreator), boundary
 * conditions (empty blockedBy, null-safe), and concurrent safety.
 *
 * <p>See plan 225 (L4-8-team-tools) Phase 1.
 */
public class TestInMemoryTeamTaskStore {

    @Test
    void createTaskReturnsCreatedStatusWithUuid() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = store.createTask("team-1", "Do something", "desc",
                Collections.emptyList(), "caller-sess");

        assertNotNull(task.getTaskId());
        assertEquals("team-1", task.getTeamId());
        assertEquals("Do something", task.getSubject());
        assertEquals("desc", task.getDescription());
        assertEquals(TeamTaskStatus.CREATED, task.getStatus());
        assertEquals("caller-sess", task.getCreatedBy());
        assertTrue(task.getCreatedAt() > 0);
        assertTrue(task.getBlockedBy().isEmpty());
    }

    @Test
    void createTaskStoresBlockedByVerbatim() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        List<String> deps = Arrays.asList("task-a", "task-b");
        TeamTask task = store.createTask("team-1", "Dependent task", null, deps, "caller");

        assertEquals(2, task.getBlockedBy().size());
        assertTrue(task.getBlockedBy().contains("task-a"));
        assertTrue(task.getBlockedBy().contains("task-b"));
    }

    @Test
    void createTaskWithNullDescription() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = store.createTask("team-1", "Subject", null, Collections.emptyList(), "caller");

        assertEquals("Subject", task.getSubject());
        assertEquals(null, task.getDescription());
    }

    @Test
    void getTaskReturnsEmptyForUnknownId() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Optional<TeamTask> opt = store.getTask("nonexistent");
        assertTrue(opt.isEmpty());
    }

    @Test
    void getTaskReturnsNullForNullId() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Optional<TeamTask> opt = store.getTask(null);
        assertTrue(opt.isEmpty());
    }

    @Test
    void fullLifecycleCreateGetByTeamGetByCreator() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        TeamTask t1 = store.createTask("team-A", "Task 1", "d1", Collections.emptyList(), "sess-1");
        TeamTask t2 = store.createTask("team-A", "Task 2", "d2", Collections.emptyList(), "sess-2");
        TeamTask t3 = store.createTask("team-B", "Task 3", null, Collections.emptyList(), "sess-1");

        // getTask
        assertEquals(t1.getTaskId(), store.getTask(t1.getTaskId()).orElseThrow().getTaskId());

        // getTasksByTeam
        List<TeamTask> teamA = store.getTasksByTeam("team-A");
        assertEquals(2, teamA.size());
        List<TeamTask> teamB = store.getTasksByTeam("team-B");
        assertEquals(1, teamB.size());
        assertEquals("Task 3", teamB.get(0).getSubject());

        // getTasksByCreator
        List<TeamTask> bySess1 = store.getTasksByCreator("sess-1");
        assertEquals(2, bySess1.size());
        List<TeamTask> bySess2 = store.getTasksByCreator("sess-2");
        assertEquals(1, bySess2.size());
    }

    @Test
    void getTasksByTeamReturnsEmptyForUnknownTeam() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        assertTrue(store.getTasksByTeam("unknown").isEmpty());
        assertTrue(store.getTasksByTeam(null).isEmpty());
    }

    @Test
    void getTasksByCreatorReturnsEmptyForUnknownCreator() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        assertTrue(store.getTasksByCreator("unknown").isEmpty());
        assertTrue(store.getTasksByCreator(null).isEmpty());
    }

    @Test
    void snapshotListsAreUnmodifiable() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        store.createTask("team-1", "Task", null, Collections.emptyList(), "caller");

        List<TeamTask> byTeam = store.getTasksByTeam("team-1");
        assertThrows(UnsupportedOperationException.class, () -> byTeam.add(t1()));

        List<TeamTask> byCreator = store.getTasksByCreator("caller");
        assertThrows(UnsupportedOperationException.class, () -> byCreator.add(t1()));
    }

    @Test
    void blockedByListIsUnmodifiableOnTask() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask task = store.createTask("team-1", "T", null,
                Arrays.asList("a", "b"), "caller");
        assertThrows(UnsupportedOperationException.class, () -> task.getBlockedBy().add("c"));
    }

    @Test
    void emptySubjectThrows() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        assertThrows(IllegalArgumentException.class,
                () -> store.createTask("team-1", "", null, Collections.emptyList(), "caller"));
    }

    @Test
    void nullRequiredArgsThrow() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        assertThrows(NullPointerException.class,
                () -> store.createTask(null, "s", null, Collections.emptyList(), "c"));
        assertThrows(NullPointerException.class,
                () -> store.createTask("t", null, null, Collections.emptyList(), "c"));
        assertThrows(NullPointerException.class,
                () -> store.createTask("t", "s", null, null, "c"));
        assertThrows(NullPointerException.class,
                () -> store.createTask("t", "s", null, Collections.emptyList(), null));
    }

    @Test
    void concurrentCreateFromMultipleThreadsNoLoss() throws Exception {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        int threadCount = 10;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    String teamId = (idx % 2 == 0) ? "team-even" : "team-odd";
                    for (int j = 0; j < perThread; j++) {
                        store.createTask(teamId, "task-" + idx + "-" + j, null,
                                Collections.emptyList(), "sess-" + idx);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(0, errors.get(), "No errors expected during concurrent creation");

        int expectedEven = (threadCount / 2) * perThread;
        int expectedOdd = (threadCount / 2) * perThread;
        assertEquals(expectedEven, store.getTasksByTeam("team-even").size());
        assertEquals(expectedOdd, store.getTasksByTeam("team-odd").size());
    }

    @Test
    void eachTaskIdIsUnique() {
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTask t1 = store.createTask("t", "s1", null, Collections.emptyList(), "c");
        TeamTask t2 = store.createTask("t", "s2", null, Collections.emptyList(), "c");
        TeamTask t3 = store.createTask("t", "s3", null, Collections.emptyList(), "c");
        assertFalse(t1.getTaskId().equals(t2.getTaskId()));
        assertFalse(t2.getTaskId().equals(t3.getTaskId()));
        assertFalse(t1.getTaskId().equals(t3.getTaskId()));
    }

    private static TeamTask t1() {
        return new TeamTask("x", "t", "s", null, Collections.emptyList(),
                TeamTaskStatus.CREATED, "c", null, 0L);
    }
}
