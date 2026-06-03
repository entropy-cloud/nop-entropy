/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OperatorSnapshotResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final OperatorSnapshotResult EMPTY =
            new OperatorSnapshotResult(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

    private final Map<String, Object> operatorStates;
    private final Map<String, Object> keyedStates;
    private final Map<String, Object> rawKeyedStates;

    private Exception error;

    public OperatorSnapshotResult() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public OperatorSnapshotResult(
            Map<String, Object> operatorStates,
            Map<String, Object> keyedStates,
            Map<String, Object> rawKeyedStates) {
        this.operatorStates = operatorStates != null ? operatorStates : new HashMap<>();
        this.keyedStates = keyedStates != null ? keyedStates : new HashMap<>();
        this.rawKeyedStates = rawKeyedStates != null ? rawKeyedStates : new HashMap<>();
    }

    public static OperatorSnapshotResult empty() {
        return EMPTY;
    }

    public Map<String, Object> getOperatorStates() {
        return operatorStates;
    }

    public Map<String, Object> getKeyedStates() {
        return keyedStates;
    }

    public Map<String, Object> getRawKeyedStates() {
        return rawKeyedStates;
    }

    public boolean isEmpty() {
        return operatorStates.isEmpty() && keyedStates.isEmpty() && rawKeyedStates.isEmpty();
    }

    public boolean hasError() {
        return error != null;
    }

    public Exception getError() {
        return error;
    }

    public void setError(Exception error) {
        this.error = error;
    }

    public int getStateCount() {
        return operatorStates.size() + keyedStates.size() + rawKeyedStates.size();
    }

    public long estimateSize() {
        return operatorStates.size() + keyedStates.size() + rawKeyedStates.size();
    }

    public void putOperatorState(String name, Object state) {
        operatorStates.put(name, state);
    }

    public Object getOperatorState(String name) {
        return operatorStates.get(name);
    }

    public <T> T getOperatorState(String name, Class<T> typeClass) {
        Object value = operatorStates.get(name);
        if (value == null) return null;
        return typeClass.cast(value);
    }

    public void putKeyedState(String name, Object state) {
        keyedStates.put(name, state);
    }

    public Object getKeyedState(String name) {
        return keyedStates.get(name);
    }

    public <T> T getKeyedState(String name, Class<T> typeClass) {
        Object value = keyedStates.get(name);
        if (value == null) return null;
        return typeClass.cast(value);
    }

    public void putRawKeyedState(String name, Object state) {
        rawKeyedStates.put(name, state);
    }

    public Object getRawKeyedState(String name) {
        return rawKeyedStates.get(name);
    }

    public <T> T getRawKeyedState(String name, Class<T> typeClass) {
        Object value = rawKeyedStates.get(name);
        if (value == null) return null;
        return typeClass.cast(value);
    }

    public void merge(OperatorSnapshotResult other) {
        if (other == null) return;
        operatorStates.putAll(other.operatorStates);
        keyedStates.putAll(other.keyedStates);
        rawKeyedStates.putAll(other.rawKeyedStates);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Object> operatorStates = new HashMap<>();
        private final Map<String, Object> keyedStates = new HashMap<>();
        private final Map<String, Object> rawKeyedStates = new HashMap<>();

        public Builder putOperatorState(String name, Object state) {
            operatorStates.put(name, state);
            return this;
        }

        public Builder putKeyedState(String name, Object state) {
            keyedStates.put(name, state);
            return this;
        }

        public Builder putRawKeyedState(String name, Object state) {
            rawKeyedStates.put(name, state);
            return this;
        }

        public OperatorSnapshotResult build() {
            if (operatorStates.isEmpty() && keyedStates.isEmpty() && rawKeyedStates.isEmpty()) {
                return empty();
            }
            return new OperatorSnapshotResult(operatorStates, keyedStates, rawKeyedStates);
        }
    }
}
