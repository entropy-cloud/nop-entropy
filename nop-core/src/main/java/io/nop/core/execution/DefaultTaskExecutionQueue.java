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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultTaskExecutionQueue extends LifeCycleSupport implements ITaskExecutionQueue {
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final AtomicInteger startedTasks = new AtomicInteger();
    private final AtomicLong completedTasks = new AtomicLong();

    private ThreadPoolConfig threadPoolConfig;
    private IThreadPoolExecutor executor;
    private boolean ownExecutor;

    static class State extends Cancellable implements ITaskExecutionState, IProgressListener {
        private final String taskName;
        private final String description;
        private final CompletableFuture<Object> promise = new CompletableFuture<>();
        private final Timestamp queueTime = CoreMetrics.currentTimestamp();
        private volatile Timestamp startTime;
        private volatile String progressMessage;
        private volatile long currentProgress;
        private volatile long progressTotal;

        public State(String taskName, String description) {
            this.taskName = taskName;
            this.description = description;
        }

        @Override
        public String getTaskName() {
            return taskName;
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
        public CompletableFuture<?> getPromise() {
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
    public long getCompletedTaskCount() {
        return completedTasks.get();
    }

    @Override
    public ITaskExecutionState addTaskIfAbsent(String taskName, String description, IExecution<?> task) {
        State taskState = states.computeIfAbsent(taskName, k -> {
            State state = new State(taskName, description);
            queueTask(state, task);
            return state;
        });

        return taskState;
    }

    void queueTask(State state, IExecution<?> task) {
        executor.execute(() -> {
            state.setStartTime(CoreMetrics.currentTimestamp());
            if (state.isCancelled())
                return;

            try {
                CompletionStage<?> future = task.executeAsync(state);
                FutureHelper.bindCancel(future, state.getPromise());
            } catch (Exception e) {
                state.getPromise().completeExceptionally(e);
            }
        });
    }

    @Override
    public ITaskExecutionState replaceTask(String taskName, String description, IExecution<?> task) {
        State state = new State(taskName, description);
        queueTask(state, task);

        State taskState = states.put(taskName, state);
        if (taskState != null)
            taskState.cancel();

        return state;
    }

    @Override
    public ITaskExecutionState getTaskState(String taskName) {
        return states.get(taskName);
    }

    @Override
    public List<? extends ITaskExecutionState> getTaskStates() {
        return new ArrayList<>(states.values());
    }
}
