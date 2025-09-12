package io.nop.core.execution;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.progress.IProgressListener;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.StringHelper;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class DefaultTaskExecutionQueue extends LifeCycleSupport implements ITaskExecutionQueue {
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final AtomicInteger startedTasks = new AtomicInteger();
    private final AtomicLong completedTasks = new AtomicLong();
    private final AtomicLong failedTasks = new AtomicLong();
    private final AtomicLong cancelledTasks = new AtomicLong();

    private ThreadPoolConfig threadPoolConfig;
    private IThreadPoolExecutor executor;
    private boolean ownExecutor;

    static class State extends Cancellable implements ITaskExecutionState, IProgressListener {
        private final String taskRef;
        private final String taskName;
        private final String source;
        private final String description;
        private final CompletableFuture<Object> promise = new CompletableFuture<>();
        private final Timestamp queueTime = CoreMetrics.currentTimestamp();
        private volatile Timestamp startTime;
        private volatile String progressMessage;
        private volatile long currentProgress;
        private volatile long progressTotal;

        public State(String taskRef, String taskName, String source, String description) {
            this.taskRef = taskRef;
            this.taskName = taskName;
            this.source = source;
            this.description = description;
        }

        public String getTaskRef() {
            return taskRef;
        }

        @Override
        public String getTaskName() {
            return taskName;
        }

        public String getSource() {
            return source;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public boolean isStarted() {
            return startTime != null;
        }

        @Override
        public Timestamp getQueueTime() {
            return queueTime;
        }

        @Override
        public Timestamp getStartTime() {
            return startTime;
        }

        @Override
        public String getProgressMessage() {
            return progressMessage;
        }

        @Override
        public long getCurrentProgress() {
            return currentProgress;
        }

        @Override
        public long getProgressTotal() {
            return progressTotal;
        }

        @Override
        public void cancel(String reason) {
            super.cancel(reason);
            promise.cancel(false);
        }

        @Override
        public CompletableFuture<Object> getPromise() {
            return promise;
        }

        public void setStartTime(Timestamp startTime) {
            this.startTime = startTime;
        }

        @Override
        public void onProgress(Object message, long progress, long total) {
            this.progressMessage = StringHelper.toString(message, "");
            this.currentProgress = progress;
            this.progressTotal = total;
        }
    }

    public void setThreadPoolConfig(ThreadPoolConfig config) {
        this.threadPoolConfig = config;
    }

    public void setExecutor(IThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @Override
    protected void doStart() {
        ownExecutor = false;

        if (executor == null) {
            if (threadPoolConfig == null) {
                this.executor = GlobalExecutors.globalWorker();
            } else {
                ownExecutor = true;
                this.executor = DefaultThreadPoolExecutor.newExecutor(threadPoolConfig);
            }
        }
    }

    @Override
    protected void doStop() {
        if (ownExecutor) {
            this.executor.destroy();
            this.executor = null;
        }
    }

    @Override
    public int getCurrentTaskCount() {
        return states.size();
    }

    @Override
    public int getRunningTaskCount() {
        return startedTasks.get();
    }

    @Override
    public int getPendingTaskCount() {
        return getCurrentTaskCount() - getRunningTaskCount();
    }

    @Override
    public long getCompletedTaskCount() {
        return completedTasks.get();
    }

    @Override
    public long getFailedTaskCount() {
        return failedTasks.get();
    }

    @Override
    public long getCancelledTaskCount() {
        return cancelledTasks.get();
    }

    @Override
    public ITaskExecutionState addTaskIfAbsent(String taskRef, String taskName, String source, String description, IExecution<?> task) {
        State taskState = states.computeIfAbsent(taskRef, k -> {
            State state = new State(taskRef, taskName, source, description);
            queueTask(state, task);
            return state;
        });

        return taskState;
    }

    void queueTask(State state, IExecution<?> task) {
        executor.execute(() -> {
            state.setStartTime(CoreMetrics.currentTimestamp());

            // 已取消则及时清理，避免泄漏
            if (state.isCancelled()) {
                states.remove(state.getTaskRef(), state);
                cancelledTasks.incrementAndGet();
                return;
            }

            startedTasks.incrementAndGet();
            try {
                CompletionStage<?> future = task.executeAsync(state);

                // 任务完成时的清理与指标更新
                future.whenComplete((res, ex) -> {
                    try {
                        if (ex != null) {
                            state.getPromise().completeExceptionally(ex);
                            failedTasks.incrementAndGet();
                        } else {
                            state.getPromise().complete(res);
                        }
                    } finally {
                        completedTasks.incrementAndGet();
                        startedTasks.decrementAndGet();
                        // 仅当 Map 中仍是该 state 时才移除，防止 replaceTask 后误删
                        states.remove(state.getTaskRef(), state);
                    }
                });

                // 继续保留取消绑定，确保 state.promise 取消时可取消 future
                FutureHelper.bindCancel(future, state.getPromise());
            } catch (Exception e) {
                // executeAsync 抛出同步异常时也要做清理
                state.getPromise().completeExceptionally(e);
                failedTasks.incrementAndGet();
                completedTasks.incrementAndGet();
                startedTasks.decrementAndGet();
                states.remove(state.getTaskRef(), state);
            }
        });
    }

    @Override
    public ITaskExecutionState replaceTask(String taskRef, String taskName, String source, String description, IExecution<?> task) {
        State state = new State(taskRef, taskName, source, description);
        queueTask(state, task);

        State oldState = states.put(taskRef, state);
        if (oldState != null) {
            oldState.cancel("replaced by new task");
            cancelledTasks.incrementAndGet();
        }

        return state;
    }

    @Override
    public ITaskExecutionState getTaskState(String taskRef) {
        return states.get(taskRef);
    }

    @Override
    public List<? extends ITaskExecutionState> getTaskStates() {
        return new ArrayList<>(states.values());
    }

    @Override
    public List<? extends ITaskExecutionState> getTaskStatesBySource(String source) {
        return states.values().stream()
                .filter(state -> source.equals(state.getSource()))
                .collect(Collectors.toList());
    }

    @Override
    public List<? extends ITaskExecutionState> getTaskStatesByName(String taskName) {
        return states.values().stream()
                .filter(state -> taskName.equals(state.getTaskName()))
                .collect(Collectors.toList());
    }

    @Override
    public ITaskExecutionState waitForTask(String taskRef, long timeout, TimeUnit unit) throws InterruptedException {
        State state = states.get(taskRef);
        if (state == null)
            return null;

        try {
            state.getPromise().get(timeout, unit);
            return state;
        } catch (java.util.concurrent.TimeoutException e) {
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            return state;
        }
    }

    @Override
    public boolean waitForAllTasks(long timeout, TimeUnit unit) throws InterruptedException {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);

        for (State state : new ArrayList<>(states.values())) {
            long remaining = endTime - System.currentTimeMillis();
            if (remaining <= 0)
                return false;

            try {
                state.getPromise().get(remaining, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                return false;
            } catch (java.util.concurrent.ExecutionException e) {
                // 忽略执行异常，继续等待其他任务
            }
        }

        return true;
    }

    @Override
    public boolean removeCompletedTask(String taskRef) {
        State state = states.get(taskRef);
        if (state != null && state.getPromise().isDone()) {
            states.remove(taskRef, state);
            return true;
        }
        return false;
    }

    @Override
    public void removeAllCompletedTasks() {
        states.entrySet().removeIf(entry -> entry.getValue().getPromise().isDone());
    }
}