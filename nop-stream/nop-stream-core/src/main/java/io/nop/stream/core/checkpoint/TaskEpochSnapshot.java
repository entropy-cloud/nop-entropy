/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nop.api.core.annotations.data.DataBean;

import io.nop.stream.core.common.state.shard.KeyGroup;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.common.state.shard.StateShard;

@DataBean
public class TaskEpochSnapshot extends TaskStateSnapshot {

    private static final long serialVersionUID = 1L;

    private final List<StateShard> shards;
    private final Map<String, Object> timerStates;

    /**
     * Stage 35: the per-vertex parallelism under which this subtask snapshot was
     * taken. Together with {@link #maxParallelism} it lets the restore path
     * recompute every old subtask's {@link KeyGroupRange} and route keyed state
     * to the new owners on a rescale. Defaults to {@code 1} (legacy single-subtask
     * snapshot); the production checkpoint path stamps the real value.
     */
    private int parallelism = 1;

    /**
     * Stage 35: job-global key-group upper bound in effect when this snapshot was
     * taken. Constant for the job lifetime, so it must match across a rescale.
     */
    private int maxParallelism = KeyGroup.DEFAULT_MAX_PARALLELISM;

    /**
     * Stage 35: inclusive lower bound of the {@link KeyGroupRange} owned by this
     * subtask under {@link #parallelism}/{@link #maxParallelism}. {@code -1} means
     * "ownership not materialized" (legacy snapshot) — the restore path then
     * derives ranges from {@link #parallelism} alone.
     */
    private int keyGroupRangeStart = -1;

    /**
     * Stage 35: exclusive upper bound of the owned {@link KeyGroupRange}, or
     * {@code -1} when not materialized.
     */
    private int keyGroupRangeEnd = -1;

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

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public int getMaxParallelism() {
        return maxParallelism;
    }

    public void setMaxParallelism(int maxParallelism) {
        this.maxParallelism = maxParallelism;
    }

    public int getKeyGroupRangeStart() {
        return keyGroupRangeStart;
    }

    public void setKeyGroupRangeStart(int keyGroupRangeStart) {
        this.keyGroupRangeStart = keyGroupRangeStart;
    }

    public int getKeyGroupRangeEnd() {
        return keyGroupRangeEnd;
    }

    public void setKeyGroupRangeEnd(int keyGroupRangeEnd) {
        this.keyGroupRangeEnd = keyGroupRangeEnd;
    }

    /**
     * Stage 35: materialize the key-group ownership of this subtask. Called from
     * the production checkpoint snapshot path so {@code shards} is no longer the
     * only ownership record (and was in fact never populated in production).
     */
    public void setKeyGroupOwnership(int parallelism, int maxParallelism, KeyGroupRange range) {
        this.parallelism = parallelism;
        this.maxParallelism = maxParallelism;
        if (range != null) {
            this.keyGroupRangeStart = range.getStartKeyGroup();
            this.keyGroupRangeEnd = range.getEndKeyGroup();
        } else {
            this.keyGroupRangeStart = -1;
            this.keyGroupRangeEnd = -1;
        }
    }

    /**
     * @return the materialized {@link KeyGroupRange}, or {@code null} when
     * {@link #keyGroupRangeStart} is {@code -1} (ownership not recorded).
     */
    public KeyGroupRange getKeyGroupRange() {
        if (keyGroupRangeStart < 0) {
            return null;
        }
        return new KeyGroupRange(keyGroupRangeStart, keyGroupRangeEnd);
    }

    /**
     * @return {@code true} iff this snapshot carries a materialized key-group
     * ownership record (start &ge; 0).
     */
    public boolean isKeyGroupOwnershipMaterialized() {
        return keyGroupRangeStart >= 0;
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
