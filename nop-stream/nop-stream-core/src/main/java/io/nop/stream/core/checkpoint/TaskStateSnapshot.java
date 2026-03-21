/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@DataBean
public class TaskStateSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long taskId;
    private final Map<String, byte[]> operatorStates;
    private final Map<String, byte[]> keyedStates;

    public TaskStateSnapshot(long taskId) {
        this.taskId = taskId;
        this.operatorStates = new HashMap<>();
        this.keyedStates = new HashMap<>();
    }

    public TaskStateSnapshot(long taskId, Map<String, byte[]> operatorStates, Map<String, byte[]> keyedStates) {
        this.taskId = taskId;
        this.operatorStates = operatorStates != null ? operatorStates : new HashMap<>();
        this.keyedStates = keyedStates != null ? keyedStates : new HashMap<>();
    }

    public long getTaskId() {
        return taskId;
    }

    public Map<String, byte[]> getOperatorStates() {
        return operatorStates;
    }

    public Map<String, byte[]> getKeyedStates() {
        return keyedStates;
    }

    public void putOperatorState(String name, byte[] state) {
        operatorStates.put(name, state);
    }

    public byte[] getOperatorState(String name) {
        return operatorStates.get(name);
    }

    public void putKeyedState(String name, byte[] state) {
        keyedStates.put(name, state);
    }

    public byte[] getKeyedState(String name) {
        return keyedStates.get(name);
    }

    public boolean isEmpty() {
        return operatorStates.isEmpty() && keyedStates.isEmpty();
    }

    public int getStateCount() {
        return operatorStates.size() + keyedStates.size();
    }

    public long estimateSize() {
        long size = 0;
        for (byte[] state : operatorStates.values()) {
            if (state != null) size += state.length;
        }
        for (byte[] state : keyedStates.values()) {
            if (state != null) size += state.length;
        }
        return size;
    }

    public static TaskStateSnapshot empty(long taskId) {
        return new TaskStateSnapshot(taskId);
    }

    public static Builder builder(long taskId) {
        return new Builder(taskId);
    }

    public static class Builder {
        private final long taskId;
        private final Map<String, byte[]> operatorStates = new HashMap<>();
        private final Map<String, byte[]> keyedStates = new HashMap<>();

        public Builder(long taskId) {
            this.taskId = taskId;
        }

        public Builder putOperatorState(String name, byte[] state) {
            operatorStates.put(name, state);
            return this;
        }

        public Builder putKeyedState(String name, byte[] state) {
            keyedStates.put(name, state);
            return this;
        }

        public TaskStateSnapshot build() {
            return new TaskStateSnapshot(taskId, operatorStates, keyedStates);
        }
    }
}
