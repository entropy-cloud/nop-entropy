package io.nop.batch.exp.state;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.time.CoreMetrics;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

@DataBean
public class EtlTaskState {
    private boolean completed;
    private Timestamp createTime;
    private Timestamp lastStartTime;


    private Map<String, EtlTableState> tableStates;

    public static EtlTaskState create() {
        EtlTaskState state = new EtlTaskState();
        state.setCreateTime(CoreMetrics.currentTimestamp());
        return state;
    }

    public void start() {
        long now = CoreMetrics.currentTimeMillis();
        this.lastStartTime = new Timestamp(now);
        if (this.createTime == null)
            this.createTime = this.lastStartTime;

        if (tableStates != null)
            tableStates.values().forEach(s -> s.setLastSaveTime(now));
    }

    public EtlTableState makeTableState(String tableName) {
        if (tableStates == null)
            tableStates = new HashMap<>();
        return tableStates.computeIfAbsent(tableName, k -> {
            EtlTableState state = new EtlTableState();
            state.setLastSaveTime(CoreMetrics.currentTimeMillis());
            return state;
        });
    }

    public void resetTableState(String tableName) {
        if (tableStates == null)
            tableStates = new HashMap<>();
        tableStates.put(tableName, new EtlTableState());
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public Timestamp getLastStartTime() {
        return lastStartTime;
    }

    public void setLastStartTime(Timestamp lastStartTime) {
        this.lastStartTime = lastStartTime;
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
