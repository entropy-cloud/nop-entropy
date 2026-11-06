package io.nop.xlang.debug.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 手动调度器：持有一个 {@link ManualClock}，与之共用一条虚拟时间线。任务只在 {@link #advance(long)} 时、
 * 于推进线程上按到期时刻（同刻按提交序）执行，执行前时钟被推到该任务的到期时刻。任务异常按执行器契约
 * 收进对应 future，并留存到 {@link #failures()} 供断言；推进本身不因任务异常抛出。
 * <p>
 * 关停后拒绝新任务；已排队的一次性任务仍在推进时执行，周期任务不再重排。会阻塞等待任务完成的
 * {@code invokeAll}/{@code invokeAny} 在本调度器上无法完成，显式抛 {@link UnsupportedOperationException}。
 */
public final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {
    private final ManualClock clock;
    private final PriorityQueue<Task<?>> queue = new PriorityQueue<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private long nextSeq;
    private boolean shutdown;

    public ManualScheduler(ManualClock clock) {
        this.clock = clock;
    }

    public ManualClock clock() {
        return clock;
    }

    /**
     * 推进虚拟时间，并在当前线程上执行期间到期的任务；任务执行中新调度且仍在窗口内到期的任务同样执行。
     */
    public void advance(long deltaMillis) {
        if (deltaMillis < 0)
            throw new IllegalArgumentException("time cannot move backwards: " + deltaMillis);
        long target = clock.peekMillis() + deltaMillis;
        while (true) {
            Task<?> task;
            synchronized (this) {
                task = queue.peek();
                if (task == null || task.dueAt > target)
                    break;
                queue.poll();
            }
            moveClockTo(task.dueAt);
            task.runScheduled();
        }
        moveClockTo(target);
    }

    public synchronized int pendingTaskCount() {
        int count = 0;
        for (Task<?> task : queue) {
            if (!task.isCancelled())
                count++;
        }
        return count;
    }

    public List<Throwable> failures() {
        return failures;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return enqueue(new Task<>(Executors.callable(command), dueAfter(delay, unit), 0));
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return enqueue(new Task<>(callable, dueAfter(delay, unit), 0));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (period <= 0)
            throw new IllegalArgumentException("period must be positive: " + period);
        return enqueue(new Task<>(Executors.callable(command), dueAfter(initialDelay, unit),
                Math.max(1, unit.toMillis(period))));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (delay <= 0)
            throw new IllegalArgumentException("delay must be positive: " + delay);
        return enqueue(new Task<>(Executors.callable(command), dueAfter(initialDelay, unit),
                -Math.max(1, unit.toMillis(delay))));
    }

    @Override
    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> pending = new ArrayList<>(queue);
        queue.clear();
        return pending;
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown && queue.isEmpty();
    }

    /**
     * 不等待也不消费 timeout：只有 {@code advance()} 会让任务完成，故此处只报告当前是否已终止。
     * 生产代码不调用本方法（两个调试模块 main 源均无 awaitTermination 调用方）。
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return isTerminated();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        throw blockingUnsupported("invokeAll");
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw blockingUnsupported("invokeAll");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
        throw blockingUnsupported("invokeAny");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw blockingUnsupported("invokeAny");
    }

    private static UnsupportedOperationException blockingUnsupported(String method) {
        return new UnsupportedOperationException("ManualScheduler." + method
                + " blocks until tasks run, but tasks only run inside advance()");
    }

    private long dueAfter(long delay, TimeUnit unit) {
        return clock.peekMillis() + Math.max(0, unit.toMillis(delay));
    }

    private void moveClockTo(long targetMillis) {
        long delta = targetMillis - clock.peekMillis();
        if (delta > 0)
            clock.advance(delta);
    }

    private synchronized <V> Task<V> enqueue(Task<V> task) {
        if (shutdown)
            throw new RejectedExecutionException("ManualScheduler is shut down");
        task.seq = nextSeq++;
        queue.add(task);
        return task;
    }

    private synchronized void reschedule(Task<?> task) {
        if (shutdown) {
            task.cancel(false);
            return;
        }
        queue.add(task);
    }

    private synchronized void remove(Task<?> task) {
        queue.remove(task);
    }

    private final class Task<V> extends FutureTask<V> implements RunnableScheduledFuture<V> {
        /** 0：一次性；正：固定速率周期；负：固定延迟（绝对值）。 */
        private final long period;
        private long dueAt;
        private long seq;

        Task(Callable<V> callable, long dueAt, long period) {
            super(callable);
            this.dueAt = dueAt;
            this.period = period;
        }

        void runScheduled() {
            if (period == 0) {
                run();
            } else if (runAndReset()) {
                dueAt = period > 0 ? dueAt + period : clock.peekMillis() - period;
                reschedule(this);
            }
        }

        @Override
        protected void setException(Throwable t) {
            failures.add(t);
            super.setException(t);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled)
                remove(this);
            return cancelled;
        }

        @Override
        public boolean isPeriodic() {
            return period != 0;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(dueAt - clock.peekMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            Task<?> that = (Task<?>) other;
            int byDue = Long.compare(dueAt, that.dueAt);
            return byDue != 0 ? byDue : Long.compare(seq, that.seq);
        }
    }
}
