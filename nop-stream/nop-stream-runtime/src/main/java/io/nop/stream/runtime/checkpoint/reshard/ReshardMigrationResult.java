/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.reshard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Stage 37: verification report produced by a {@code maxParallelism} reshard
 * migration ({@link MaxParallelismReshardMigration}). The report is the
 * auditable artifact of an offline reshard: it records the old/new
 * {@code maxParallelism}, the per-state key-conservation counts (which must be
 * equal before and after — no loss/dup), the per-new-subtask key distribution,
 * and any warnings (e.g. no-op on a pure-operator-state job).
 *
 * <p>See {@code checkpoint-design.md} §8.5.1 for the contract.
 */
@DataBean
public class ReshardMigrationResult {

    private String oldSavepointPath;
    private String newSavepointPath;
    private int oldMaxParallelism;
    private int newMaxParallelism;
    private int newParallelism;

    /** stateName -> key count (identical before/after by the conservation invariant). */
    private Map<String, Integer> keyCountByState = new LinkedHashMap<>();

    /** vertexId -> subtaskIndex -> key count (post-migration distribution). */
    private Map<String, Map<Integer, Integer>> keyCountBySubtask = new TreeMap<>();

    private int keyedStateCount;
    private int operatorStateCount;
    private int subtaskCount;

    private final List<String> warnings = new ArrayList<>();

    public String getOldSavepointPath() {
        return oldSavepointPath;
    }

    public void setOldSavepointPath(String oldSavepointPath) {
        this.oldSavepointPath = oldSavepointPath;
    }

    public String getNewSavepointPath() {
        return newSavepointPath;
    }

    public void setNewSavepointPath(String newSavepointPath) {
        this.newSavepointPath = newSavepointPath;
    }

    public int getOldMaxParallelism() {
        return oldMaxParallelism;
    }

    public void setOldMaxParallelism(int oldMaxParallelism) {
        this.oldMaxParallelism = oldMaxParallelism;
    }

    public int getNewMaxParallelism() {
        return newMaxParallelism;
    }

    public void setNewMaxParallelism(int newMaxParallelism) {
        this.newMaxParallelism = newMaxParallelism;
    }

    public int getNewParallelism() {
        return newParallelism;
    }

    public void setNewParallelism(int newParallelism) {
        this.newParallelism = newParallelism;
    }

    public Map<String, Integer> getKeyCountByState() {
        return keyCountByState;
    }

    public void setKeyCountByState(Map<String, Integer> keyCountByState) {
        this.keyCountByState = keyCountByState != null ? keyCountByState : new LinkedHashMap<>();
    }

    public Map<String, Map<Integer, Integer>> getKeyCountBySubtask() {
        return keyCountBySubtask;
    }

    public void setKeyCountBySubtask(Map<String, Map<Integer, Integer>> keyCountBySubtask) {
        this.keyCountBySubtask = keyCountBySubtask != null ? keyCountBySubtask : new TreeMap<>();
    }

    public int getKeyedStateCount() {
        return keyedStateCount;
    }

    public void setKeyedStateCount(int keyedStateCount) {
        this.keyedStateCount = keyedStateCount;
    }

    public int getOperatorStateCount() {
        return operatorStateCount;
    }

    public void setOperatorStateCount(int operatorStateCount) {
        this.operatorStateCount = operatorStateCount;
    }

    public int getSubtaskCount() {
        return subtaskCount;
    }

    public void setSubtaskCount(int subtaskCount) {
        this.subtaskCount = subtaskCount;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        if (warning != null) {
            warnings.add(warning);
        }
    }

    /** Total keyed entries (sum across states). Conservation invariant: stable across migration. */
    public int totalKeyedEntries() {
        int total = 0;
        for (Integer v : keyCountByState.values()) {
            total += v != null ? v : 0;
        }
        return total;
    }

    @Override
    public String toString() {
        return "ReshardMigrationResult{oldMaxP=" + oldMaxParallelism
                + ", newMaxP=" + newMaxParallelism
                + ", newParallelism=" + newParallelism
                + ", keyedStates=" + keyedStateCount
                + ", operatorStates=" + operatorStateCount
                + ", subtasks=" + subtaskCount
                + ", totalKeys=" + totalKeyedEntries()
                + ", newSavepoint=" + newSavepointPath
                + (warnings.isEmpty() ? "" : (", warnings=" + warnings))
                + "}";
    }
}
