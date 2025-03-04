package io.nop.dbtool.exp.state;

import io.nop.api.core.annotations.data.DataBean;

import java.util.HashMap;
import java.util.Map;

@DataBean
public class ImportTaskState {
    private boolean completed;

    private Map<String, ImportTableState> tableStates;

    public ImportTableState makeTableState(String tableName) {
        if (tableStates == null)
            tableStates = new HashMap<>();
        return tableStates.computeIfAbsent(tableName, k -> new ImportTableState());
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Map<String, ImportTableState> getTableStates() {
        return tableStates;
    }

    public void setTableStates(Map<String, ImportTableState> tableStates) {
        this.tableStates = tableStates;
    }
}
