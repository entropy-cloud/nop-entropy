package io.nop.batch.exp.state;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
        saveStateFile();
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
            if (ex != null)
                complete = false;
            saveTableState(tableName, complete, context);
        }

        public boolean isCompleted() {
            return isTableCompleted(tableName);
        }
    }

    synchronized void loadTableState(String tableName, IBatchTaskContext context) {
        EtlTableState state = taskState.makeTableState(tableName);
        if (state.getCompletedCount() > 0) {
            context.setCompleteItemCount(state.getCompletedCount());
        }

        if (state.getHistoryCount() > 0) {
            context.setHistoryItemCount(state.getHistoryCount());
        }

        if (state.getCompletedIndex() > 0) {
            context.setCompletedIndex(state.getCompletedIndex());
        }

        if (state.getProcessedCount() > 0)
            context.setProcessItemCount(state.getProcessedCount());

        if (state.getSkipCount() > 0)
            context.setSkipItemCount(state.getSkipCount());

        if (state.getErrorCount() > 0)
            context.setErrorCount(state.getErrorCount());
    }

    synchronized void saveTableState(String tableName, boolean complete, IBatchTaskContext context) {
        EtlTableState state = taskState.makeTableState(tableName);
        long newCount = context.getCompleteItemCount() - state.getCompletedCount();
        state.save(newCount);

        state.setCompletedCount(context.getCompleteItemCount());
        state.setHistoryCount(context.getHistoryItemCount());
        state.setCompletedIndex(context.getCompletedIndex());
        state.setProcessedCount(context.getProcessItemCount());
        state.setSkipCount(context.getSkipItemCount());
        state.setCompleted(complete);
        state.setErrorCount(context.getErrorCount());

        saveStateFile();
    }

    /**
     * 状态文件是断点续传的检查点。进程在写入中途被kill时若直接覆写目标文件，
     * 会留下截断的JSON，下次启动解析失败、全部表断点丢失。因此先写同目录临时文件，
     * 再原子rename替换，保证目标文件要么是旧内容要么是完整新内容。
     */
    private void saveStateFile() {
        String json = JsonTool.serialize(taskState, true);
        File tmpFile = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
        FileHelper.writeText(tmpFile, json, null);
        try {
            try {
                Files.move(tmpFile.toPath(), stateFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // 部分文件系统不支持原子move，退化为非原子替换（仍保证内容完整）
                Files.move(tmpFile.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
