package io.nop.dbtool.exp.state;

import io.nop.api.core.time.CoreMetrics;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImportTaskStateStore {
    private final File stateFile;
    private final Map<String, IBatchStateStore> tableStores = new ConcurrentHashMap<>();

    private ImportTaskState taskState;
    private long saveTime;

    public ImportTaskStateStore(File stateFile) {
        this.stateFile = stateFile;

        if (stateFile.length() > 0) {
            taskState = JsonTool.parseBeanFromResource(new FileResource(stateFile), ImportTaskState.class);
        } else {
            taskState = new ImportTaskState();
        }

        saveTime = CoreMetrics.currentTimeMillis();
    }

    public boolean isCompleted() {
        return taskState.isCompleted();
    }

    public synchronized void complete() {
        taskState.setCompleted(true);
        FileHelper.writeText(stateFile, JsonTool.serialize(taskState, true), null);
    }

    public IBatchStateStore getTableStore(String tableName) {
        return tableStores.computeIfAbsent(tableName, k -> new ImportTableStateStore(tableName));
    }

    class ImportTableStateStore implements IBatchStateStore {
        private final String tableName;

        public ImportTableStateStore(String tableName) {
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
    }

    synchronized void loadTableState(String tableName, IBatchTaskContext context) {
        ImportTableState state = taskState.makeTableState(tableName);
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
        ImportTableState state = taskState.makeTableState(tableName);
        long newCount = context.getCompleteItemCount() - state.getCompletedCount();
        long now = CoreMetrics.currentTimeMillis();
        long diffTime = now - saveTime;
        saveTime = now;

        state.setSpeed(newCount / (diffTime / 1000.0));

        state.setCompletedCount(context.getCompleteItemCount());
        state.setCompletedIndex(context.getCompletedIndex());
        state.setProcessedCount(context.getProcessItemCount());
        state.setSkipCount(context.getSkipItemCount());
        state.setCompleted(complete);

        FileHelper.writeText(stateFile, JsonTool.serialize(taskState, true), null);
    }
}
