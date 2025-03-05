package io.nop.dbtool.exp.state;

import io.nop.api.core.annotations.data.DataBean;

import java.util.HashMap;
import java.util.Map;

@DataBean
public class EtlTaskState {
    private boolean completed;

    private Map<String, EtlTableState> tableStates;

    public EtlTableState makeTableState(String tableName) {
        if (tableStates == null)
            tableStates = new HashMap<>();
        return tableStates.computeIfAbsent(tableName, k -> new EtlTableState());
    }

    public void resetTableState(String tableName) {
        if (tableStates == null)
            tableStates = new HashMap<>();
        tableStates.put(tableName, new EtlTableState());
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Map<String, EtlTableState> getTableStates() {
        return tableStates;
    }

    public void setTableStates(Map<String, EtlTableState> tableStates) {
        this.tableStates = tableStates;
    }
}
