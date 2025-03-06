package io.nop.dbtool.exp.state;

import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EtlTaskStateStore {
    private final File stateFile;
    private final Map<String, EtlTableStateStore> tableStores = new ConcurrentHashMap<>();

    private EtlTaskState taskState;

    public EtlTaskStateStore(File stateFile) {
        this.stateFile = stateFile;

        if (stateFile.length() > 0) {
            taskState = JsonTool.parseBeanFromResource(new FileResource(stateFile), EtlTaskState.class);
        } else {
            taskState = EtlTaskState.create();
        }

        taskState.start();
    }

    public void reset() {
        taskState = new EtlTaskState();
    }

    public boolean isCompleted() {
        return taskState.isCompleted();
    }

    public synchronized boolean isTableCompleted(String tableName) {
        return taskState.makeTableState(tableName).isCompleted();
    }

    public synchronized void resetTableState(String tableName) {
        taskState.resetTableState(tableName);
    }

    public synchronized void complete() {
        taskState.setCompleted(true);
        FileHelper.writeText(stateFile, JsonTool.serialize(taskState, true), null);
    }

    public IBatchStateStore getTableStore(String tableName) {
        return tableStores.computeIfAbsent(tableName, k -> new EtlTableStateStore(tableName));
    }

    class EtlTableStateStore implements IBatchStateStore {
        private final String tableName;

        public EtlTableStateStore(String tableName) {
            this.tableName = tableName;
        }

        @Override
        public void loadTaskState(IBatchTaskContext context) {
            loadTableState(tableName, context);
        }

        @Override
        public void saveTaskState(boolean complete, Throwable ex, IBatchTaskContext context) {
            saveTableState(tableName, complete, context);
        }

        public boolean isCompleted() {
            return taskState.makeTableState(tableName).isCompleted();
        }
    }

    synchronized void loadTableState(String tableName, IBatchTaskContext context) {
        EtlTableState state = taskState.makeTableState(tableName);
        if (state.getCompletedCount() > 0) {
            context.setCompleteItemCount(state.getCompletedCount());
        }

        if (state.getCompletedIndex() > 0) {
            context.setCompletedIndex(state.getCompletedIndex());
        }

        if (state.getProcessedCount() > 0)
            context.setProcessItemCount(state.getProcessedCount());

        if (state.getSkipCount() > 0)
            context.setSkipItemCount(state.getSkipCount());
    }

    synchronized void saveTableState(String tableName, boolean complete, IBatchTaskContext context) {
        EtlTableState state = taskState.makeTableState(tableName);
        long newCount = context.getCompleteItemCount() - state.getCompletedCount();
        state.save(newCount);

        state.setCompletedCount(context.getCompleteItemCount());
        state.setCompletedIndex(context.getCompletedIndex());
        state.setProcessedCount(context.getProcessItemCount());
        state.setSkipCount(context.getSkipItemCount());
        state.setCompleted(complete);

        FileHelper.writeText(stateFile, JsonTool.serialize(taskState, true), null);
    }
}
