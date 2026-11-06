package io.nop.commons.test.time;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestManualScheduler {

    private final ManualClock clock = new ManualClock(10_000);
    private final ManualScheduler scheduler = new ManualScheduler(clock);

    @Test
    public void testTaskRunsOnlyWhenAdvancedToDueTime() {
        List<String> runs = new ArrayList<>();
        scheduler.schedule(() -> runs.add("a"), 100, TimeUnit.MILLISECONDS);

        scheduler.advance(99);
        assertEquals(List.of(), runs);
        assertEquals(1, scheduler.pendingTaskCount());

        scheduler.advance(1);
        assertEquals(List.of("a"), runs);
        assertEquals(0, scheduler.pendingTaskCount());
        assertEquals(10_100, clock.peekMillis());
    }

    @Test
    public void testTasksRunInDueOrderThenSubmissionOrder() {
        List<String> runs = new ArrayList<>();
        scheduler.schedule(() -> runs.add("late"), 50, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> runs.add("first-at-30"), 30, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> runs.add("second-at-30"), 30, TimeUnit.MILLISECONDS);

        scheduler.advance(100);
        assertEquals(List.of("first-at-30", "second-at-30", "late"), runs);
    }

    @Test
    public void testClockShowsDueTimeWhileTaskRuns() {
        List<Long> seen = new ArrayList<>();
        scheduler.schedule(() -> seen.add(clock.peekMillis()), 40, TimeUnit.MILLISECONDS);

        scheduler.advance(100);
        assertEquals(List.of(10_040L), seen);
        assertEquals(10_100, clock.peekMillis());
    }

    @Test
    public void testFixedRateAndFixedDelayAreRescheduled() {
        List<Long> rate = new ArrayList<>();
        List<Long> delay = new ArrayList<>();
        scheduler.scheduleAtFixedRate(() -> rate.add(clock.peekMillis()), 10, 20, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(() -> delay.add(clock.peekMillis()), 5, 25, TimeUnit.MILLISECONDS);

        scheduler.advance(55);
        assertEquals(List.of(10_010L, 10_030L, 10_050L), rate);
        assertEquals(List.of(10_005L, 10_030L, 10_055L), delay);
        assertEquals(2, scheduler.pendingTaskCount());
    }

    @Test
    public void testTaskScheduledInsideWindowRunsInSameAdvance() {
        List<String> runs = new ArrayList<>();
        scheduler.schedule(() -> {
            runs.add("outer");
            scheduler.schedule(() -> runs.add("inner"), 10, TimeUnit.MILLISECONDS);
        }, 10, TimeUnit.MILLISECONDS);

        scheduler.advance(30);
        assertEquals(List.of("outer", "inner"), runs);
    }

    @Test
    public void testCancelledTaskNeverRuns() {
        List<String> runs = new ArrayList<>();
        ScheduledFuture<?> future = scheduler.schedule(() -> runs.add("x"), 10, TimeUnit.MILLISECONDS);
        assertTrue(future.cancel(false));
        assertEquals(0, scheduler.pendingTaskCount());

        scheduler.advance(100);
        assertEquals(List.of(), runs);
    }

    @Test
    public void testTaskFailureGoesToFutureWithoutBreakingAdvance() {
        IllegalStateException boom = new IllegalStateException("boom");
        List<String> runs = new ArrayList<>();
        ScheduledFuture<?> failing = scheduler.schedule(() -> {
            throw boom;
        }, 10, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> runs.add("after"), 20, TimeUnit.MILLISECONDS);

        scheduler.advance(30);
        assertEquals(List.of("after"), runs);
        ExecutionException e = assertThrows(ExecutionException.class, failing::get);
        assertSame(boom, e.getCause());
        assertEquals(List.of(boom), scheduler.failures());
    }

    @Test
    public void testFailingPeriodicTaskStopsRepeating() {
        List<Long> runs = new ArrayList<>();
        scheduler.scheduleAtFixedRate(() -> {
            runs.add(clock.peekMillis());
            throw new IllegalStateException("periodic boom");
        }, 10, 10, TimeUnit.MILLISECONDS);

        scheduler.advance(100);
        assertEquals(List.of(10_010L), runs);
        assertEquals(0, scheduler.pendingTaskCount());
        assertEquals(1, scheduler.failures().size());
    }

    @Test
    public void testShutdownRejectsNewTasks() {
        scheduler.shutdown();
        assertThrows(RejectedExecutionException.class,
                () -> scheduler.schedule(() -> {
                }, 1, TimeUnit.MILLISECONDS));
        assertTrue(scheduler.isShutdown());
        assertTrue(scheduler.isTerminated());
    }

    @Test
    public void testShutdownNowDropsPendingTasks() {
        List<String> runs = new ArrayList<>();
        scheduler.schedule(() -> runs.add("x"), 10, TimeUnit.MILLISECONDS);

        assertEquals(1, scheduler.shutdownNow().size());
        scheduler.advance(100);
        assertEquals(List.of(), runs);
    }

    @Test
    public void testBlockingInvokeMethodsFailExplicitly() {
        List<Callable<String>> tasks = List.of(() -> "x");
        assertThrows(UnsupportedOperationException.class, () -> scheduler.invokeAll(tasks));
        assertThrows(UnsupportedOperationException.class,
                () -> scheduler.invokeAll(tasks, 1, TimeUnit.SECONDS));
        assertThrows(UnsupportedOperationException.class, () -> scheduler.invokeAny(tasks));
        assertThrows(UnsupportedOperationException.class,
                () -> scheduler.invokeAny(tasks, 1, TimeUnit.SECONDS));
    }

    @Test
    public void testNegativeAdvanceRejected() {
        assertThrows(IllegalArgumentException.class, () -> scheduler.advance(-1));
    }
}
