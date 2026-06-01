package io.nop.job.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.job.api.JobState;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestLocalJobScheduler {

    private LocalJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> mockInvoker()
        );
        scheduler.activate();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.deactivate();
        }
    }

    static class MockInvoker implements IJobInvoker {
        final AtomicInteger invokeCount = new AtomicInteger();

        @Override
        public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
            invokeCount.incrementAndGet();
            return null;
        }

        @Override
        public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
            return CompletableFuture.completedFuture(true);
        }
    }

    private static IJobInvoker mockInvoker() {
        return new MockInvoker();
    }

    @Test
    void testAddAndRemove() {
        JobSpec spec = newSpec("test-add", 10, true);
        scheduler.addJob(spec, false);

        assertEquals(1, scheduler.getJobNames().size());
        assertTrue(scheduler.getJobNames().contains("test-add"));
        assertNotNull(scheduler.getJobDetail("test-add"));

        assertTrue(scheduler.removeJob("test-add"));
        assertEquals(0, scheduler.getJobNames().size());
        assertNull(scheduler.getJobDetail("test-add"));
    }

    @Test
    void testSuspendResume() {
        JobSpec spec = newSpec("test-suspend", 100, true);
        scheduler.addJob(spec, false);

        assertTrue(scheduler.suspendJob("test-suspend"));
        assertEquals(JobState.SUSPENDED, scheduler.getJobState("test-suspend"));

        assertTrue(scheduler.resumeJob("test-suspend"));
        assertNotNull(scheduler.getJobState("test-suspend"));
        assertNotEquals(JobState.SUSPENDED, scheduler.getJobState("test-suspend"));
    }

    @Test
    void testCancel() {
        JobSpec spec = newSpec("test-cancel", 100, true);
        scheduler.addJob(spec, false);

        assertTrue(scheduler.cancelJob("test-cancel"));
        assertEquals(JobState.COMPLETED, scheduler.getJobState("test-cancel"));
    }

    @Test
    void testPeriodicExecution() throws Exception {
        MockInvoker invoker = new MockInvoker();
        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> invoker
        );
        sched.activate();

        try {
            JobSpec spec = newSpec("test-periodic", 10, true);
            sched.addJob(spec, false);

            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                while (invoker.invokeCount.get() <= 5) {
                    Thread.sleep(20);
                }
            }, "expected > 5 invocations but got " + invoker.invokeCount.get());
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testFireNow() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();

        IJobInvoker fireOnceInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                count.incrementAndGet();
                latch.countDown();
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE);
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> fireOnceInvoker
        );
        sched.activate();

        try {
            JobSpec spec = newSpec("test-fireNow", 100000, true);
            sched.suspendJob("test-fireNow"); // won't exist yet, but addJob auto-schedules
            sched.addJob(spec, false);
            sched.suspendJob("test-fireNow"); // suspend so it doesn't auto-fire

            assertTrue(sched.fireNow("test-fireNow"));
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, count.get());
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testMaxExecutionCount() throws Exception {
        MockInvoker invoker = new MockInvoker();
        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> invoker
        );
        sched.activate();

        try {
            JobSpec spec = new JobSpec();
            spec.setJobName("test-max-count");
            spec.setJobInvoker("mock");
            TriggerSpec ts = new TriggerSpec();
            ts.setRepeatInterval(10);
            ts.setRepeatFixedDelay(true);
            ts.setMaxExecutionCount(3);
            spec.setTriggerSpec(ts);

            sched.addJob(spec, false);
            Thread.sleep(500);

            assertEquals(3, invoker.invokeCount.get());
            assertEquals(JobState.COMPLETED, sched.getJobState("test-max-count"));
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testDeactivate() {
        JobSpec spec = newSpec("test-deactivate", 10, true);
        scheduler.addJob(spec, false);
        scheduler.deactivate();
        assertEquals(0, scheduler.getJobNames().size());
    }

    @Test
    void testCancelWhileRunning() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);
        AtomicInteger invokeCount = new AtomicInteger();

        IJobInvoker blockingInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                executionStarted.countDown();
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        allowComplete.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return JobFireResult.CONTINUE;
                });
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> blockingInvoker
        );
        sched.activate();
        try {
            JobSpec spec = newSpec("test-cancel-running", 10, true);
            sched.addJob(spec, false);

            assertTrue(executionStarted.await(2, TimeUnit.SECONDS));
            assertTrue(sched.cancelJob("test-cancel-running"));
            assertEquals(JobState.COMPLETED, sched.getJobState("test-cancel-running"));

            allowComplete.countDown();
            Thread.sleep(200);

            assertEquals(JobState.COMPLETED, sched.getJobState("test-cancel-running"));
            assertEquals(1, invokeCount.get());
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testRemoveWhileRunning() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);
        AtomicInteger invokeCount = new AtomicInteger();

        IJobInvoker blockingInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                executionStarted.countDown();
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        allowComplete.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return JobFireResult.CONTINUE;
                });
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> blockingInvoker
        );
        sched.activate();
        try {
            JobSpec spec = newSpec("test-remove-running", 10, true);
            sched.addJob(spec, false);

            assertTrue(executionStarted.await(2, TimeUnit.SECONDS));
            assertTrue(sched.removeJob("test-remove-running"));
            assertNull(sched.getJobDetail("test-remove-running"));

            allowComplete.countDown();
            Thread.sleep(200);

            assertNull(sched.getJobDetail("test-remove-running"));
            assertEquals(1, invokeCount.get());
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testUpdateWhileRunningPreservesOldSpec() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);
        AtomicInteger invokeCount = new AtomicInteger();

        IJobInvoker blockingInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                executionStarted.countDown();
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        allowComplete.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return JobFireResult.CONTINUE;
                });
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        IJobInvoker secondInvoker = new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
                invokeCount.incrementAndGet();
                return null;
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
                return CompletableFuture.completedFuture(true);
            }
        };

        AtomicInteger invokerIndex = new AtomicInteger(0);
        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> {
                    if (invokerIndex.getAndIncrement() == 0)
                        return blockingInvoker;
                    return secondInvoker;
                }
        );
        sched.activate();
        try {
            JobSpec oldSpec = newSpec("test-update-running", 10, true);
            oldSpec.setOnceTask(false);
            sched.addJob(oldSpec, false);

            assertTrue(executionStarted.await(2, TimeUnit.SECONDS));

            JobSpec newSpec = newSpec("test-update-running", 100, true);
            newSpec.setOnceTask(true);
            sched.addJob(newSpec, true);

            allowComplete.countDown();
            Thread.sleep(300);

            assertNotNull(sched.getJobDetail("test-update-running"));
            assertTrue(invokeCount.get() >= 2);
        } finally {
            sched.deactivate();
        }
    }

    @Test
    void testCheckActiveThrowsCorrectError() {
        LocalJobScheduler sched = new LocalJobScheduler(
                GlobalExecutors.globalTimer(),
                name -> mockInvoker()
        );

        NopException ex = assertThrows(NopException.class,
                () -> sched.addJob(newSpec("test", 10, true), false));
        assertEquals("nop.err.job.scheduler-not-active", ex.getErrorCode());
    }

    private JobSpec newSpec(String name, long interval, boolean fixedDelay) {
        JobSpec spec = new JobSpec();
        spec.setJobName(name);
        spec.setJobInvoker("mock");
        TriggerSpec ts = new TriggerSpec();
        ts.setRepeatInterval(interval);
        ts.setRepeatFixedDelay(fixedDelay);
        spec.setTriggerSpec(ts);
        return spec;
    }
}
