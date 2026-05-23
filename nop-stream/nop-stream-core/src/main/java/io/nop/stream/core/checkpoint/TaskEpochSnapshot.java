/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.stream.core.common.state.shard.StateShard;

import java.util.*;

@DataBean
public class TaskEpochSnapshot extends TaskStateSnapshot {

    private static final long serialVersionUID = 1L;

    private final List<StateShard> shards;
    private final Map<String, Object> timerStates;

    public TaskEpochSnapshot(TaskLocation taskLocation, long checkpointId) {
        super(taskLocation, checkpointId);
        this.shards = new ArrayList<>();
        this.timerStates = new HashMap<>();
    }

    public TaskEpochSnapshot(TaskLocation taskLocation, long checkpointId,
                             Map<String, Object> operatorStates,
                             Map<String, Object> keyedStates,
                             List<StateShard> shards,
                             Map<String, Object> timerStates) {
        super(taskLocation, checkpointId, operatorStates, keyedStates);
        this.shards = shards != null ? new ArrayList<>(shards) : new ArrayList<>();
        this.timerStates = timerStates != null ? new HashMap<>(timerStates) : new HashMap<>();
    }

    public List<StateShard> getShards() {
        return Collections.unmodifiableList(shards);
    }

    public Map<String, Object> getTimerStates() {
        return Collections.unmodifiableMap(timerStates);
    }

    public void addShard(StateShard shard) {
        shards.add(shard);
    }

    public void putTimerState(String name, Object state) {
        timerStates.put(name, state);
    }

    public Object getTimerState(String name) {
        return timerStates.get(name);
    }

    public static TaskEpochSnapshot fromTaskStateSnapshot(TaskStateSnapshot snapshot) {
        if (snapshot instanceof TaskEpochSnapshot) {
            return (TaskEpochSnapshot) snapshot;
        }
        return new TaskEpochSnapshot(
                snapshot.getTaskLocation(),
                snapshot.getCheckpointId(),
                new HashMap<>(snapshot.getOperatorStates()),
                new HashMap<>(snapshot.getKeyedStates()),
                new ArrayList<>(),
                new HashMap<>()
        );
    }
}
