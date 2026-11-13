package io.nop.autotest.core.mock;

import io.nop.autotest.core.AutoTestErrors;
import io.nop.autotest.core.exceptions.AutoTestException;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.functional.Functionals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Manual-drive mock of {@link IScheduledExecutor} for tests. Time is modeled by a purely virtual
 * clock: submissions are anchored against the current virtual offset and deadlines decay only as
 * {@link #expireTasks(long)} advances that offset. No real thread
 * is started; task bodies only run when the test calls {@link #triggerNext()}.
 *
 * <p><b>Deterministic virtual clock</b>: time advances ONLY through {@link #expireTasks(long)}.
 * Real wall-clock elapsed time never contributes to expiry: a task fires exactly when the
 * accumulated virtual advance reaches its deadline. This makes contract tests immune to GC
 * pauses / thread preemption under parallel test execution (real-time leakage previously made a
 * 499ms-advance / 500ms-delay assertion leave only 1ms of real-time slack, flaking under
 * surefire parallel=classes).
 *
 * <p><b>Boundary declaration (design adjudication D / R1-B2)</b>: periodic kinds
 * ({@link MockTaskKind#SCHEDULED_AT_FIXED_RATE} / {@link MockTaskKind#SCHEDULED_WITH_FIXED_DELAY})
 * are manually driven for a <b>single round only</b> and are not re-armed after execution. This
 * mock does <b>not</b> honor the {@link IScheduledExecutor} periodic contract (task-body exception
 * insulation, fixed-delay exponential backoff 1s~60s, fixed-rate pacing) because it has no real
 * thread to drive time-based re-arm semantics. Callers must not use this mock to assert periodic
 * insulation/backoff behavior.
 */
public class MockScheduledExecutor implements IScheduledExecutor {
    private String name;
    private volatile boolean started = true;
    private final DelayQueue<MockTask> tasks = new DelayQueue<>();
    // volatile: read via MockTask.getDelay on the DelayQueue-internal remove path (promise
    // whenComplete cleanup) which does not hold the executor monitor
    private volatile long virtualOffsetNanos = 0L;

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Advances the internal virtual clock by the given offset in milliseconds (R2-M3 semantic
     * pin: this advances the clock anchor used by subsequent enqueue anchoring and getDelay
     * decay; it does not run task bodies - use {@link #triggerNext()} for that).
     */
    public synchronized void expireTasks(long millis) {
        virtualOffsetNanos += TimeUnit.MILLISECONDS.toNanos(millis);
    }

    private long nowNanos() {
        return virtualOffsetNanos;
    }

    private void checkStarted() {
        if (!started)
            throw new AutoTestException(AutoTestErrors.ERR_AUTOTEST_EXECUTOR_DESTROYED);
    }

    private void addTask(Callable<?> callable, long delay, TimeUnit unit, MockTaskKind kind,
                         CompletableFuture<Object> promise) {
        checkStarted();
        MockTask task = new MockTask(callable, nowNanos() + unit.toNanos(delay), this::nowNanos, kind, promise);
        tasks.add(task);
        // cleanup callback fires on cancel / exceptional / normal completion alike
        promise.whenComplete((ret, err) -> tasks.remove(task));
    }

    @Override
    public synchronized <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        CompletableFuture<V> promise = new CompletableFuture<>();
        addTask(callable, delay, unit, MockTaskKind.ONCE, castPromise(promise));
        return promise;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CompletableFuture<Object> castPromise(CompletableFuture<?> promise) {
        return (CompletableFuture) promise;
    }

    @Override
    public synchronized Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                                      TimeUnit unit) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        addTask(Functionals.asCallable(command, null), initialDelay, unit, MockTaskKind.SCHEDULED_AT_FIXED_RATE,
                castPromise(promise));
        return promise;
    }

    @Override
    public synchronized Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                                         TimeUnit unit) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        addTask(Functionals.asCallable(command, null), initialDelay, unit, MockTaskKind.SCHEDULED_WITH_FIXED_DELAY,
                castPromise(promise));
        return promise;
    }

    /**
     * Manually drives the single next expired task, if any. For periodic kinds the task is run
     * once and removed (no re-arm, see the class-level boundary declaration).
     */
    public Object triggerNext() {
        MockTask task;
        synchronized (this) {
            task = pollTask();
        }
        if (task == null)
            return null;
        // the task body runs OUTSIDE the executor monitor (like a real executor, whose facility
        // locks are never held while user code runs): holding the monitor here inverted lock
        // order against RateLimitExecutorImpl.debounce's CHM compute lambda (plan 2563 MA-03
        // overlap leg deadlock, fixed here)
        Object ret = task.call();
        if (task.getTaskKind() != MockTaskKind.ONCE) {
            synchronized (this) {
                tasks.remove(task);
            }
        }
        return ret;
    }

    protected synchronized MockTask pollTask() {
        return tasks.poll();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ThreadPoolConfig getConfig() {
        return null;
    }

    @Override
    public ThreadPoolStats stats() {
        return null;
    }

    @Override
    public <V> CompletableFuture<V> submit(Callable<V> callable) {
        return schedule(callable, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public <V> CompletableFuture<V> submit(Runnable task, V result) {
        return submit(Functionals.asCallable(task, result));
    }

    @Override
    public void refreshConfig() {

    }

    @Override
    public synchronized void destroy() {
        started = false;
        // drain the queue (including not-yet-expired entries): every pending task's promise
        // settles exceptionally. DelayQueue.poll() only yields expired entries, so iterate.
        List<MockTask> drained = new ArrayList<>();
        for (Iterator<MockTask> it = tasks.iterator(); it.hasNext(); ) {
            drained.add(it.next());
            it.remove();
        }
        for (MockTask task : drained) {
            task.completeExceptionally(new AutoTestException(AutoTestErrors.ERR_AUTOTEST_EXECUTOR_DESTROYED));
        }
    }

    @Override
    public void execute(Runnable command) {
        submit(command, null);
    }
}
