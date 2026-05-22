/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.core.lang.json.JsonTool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 算子快照结果，保存算子的状态数据。
 */
public class OperatorSnapshotResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final OperatorSnapshotResult EMPTY = 
            new OperatorSnapshotResult(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

    private final Map<String, byte[]> operatorStates;
    private final Map<String, byte[]> keyedStates;
    private final Map<String, byte[]> rawKeyedStates;

    public OperatorSnapshotResult() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public OperatorSnapshotResult(
            Map<String, byte[]> operatorStates,
            Map<String, byte[]> keyedStates,
            Map<String, byte[]> rawKeyedStates) {
        this.operatorStates = operatorStates != null ? operatorStates : new HashMap<>();
        this.keyedStates = keyedStates != null ? keyedStates : new HashMap<>();
        this.rawKeyedStates = rawKeyedStates != null ? rawKeyedStates : new HashMap<>();
    }

    public static OperatorSnapshotResult empty() {
        return EMPTY;
    }

    public Map<String, byte[]> getOperatorStates() {
        return operatorStates;
    }

    public Map<String, byte[]> getKeyedStates() {
        return keyedStates;
    }

    public Map<String, byte[]> getRawKeyedStates() {
        return rawKeyedStates;
    }

    public boolean isEmpty() {
        return operatorStates.isEmpty() && keyedStates.isEmpty() && rawKeyedStates.isEmpty();
    }

    public int getStateCount() {
        return operatorStates.size() + keyedStates.size() + rawKeyedStates.size();
    }

    public long estimateSize() {
        long size = 0;
        for (byte[] state : operatorStates.values()) {
            if (state != null) size += state.length;
        }
        for (byte[] state : keyedStates.values()) {
            if (state != null) size += state.length;
        }
        for (byte[] state : rawKeyedStates.values()) {
            if (state != null) size += state.length;
        }
        return size;
    }

    public void putOperatorState(String name, byte[] state) {
        operatorStates.put(name, state);
    }

    public void putKeyedState(String name, byte[] state) {
        keyedStates.put(name, state);
    }

    public void putRawKeyedState(String name, byte[] state) {
        rawKeyedStates.put(name, state);
    }

    public void putOperatorStateJson(String name, Object state) {
        operatorStates.put(name,
                JsonTool.serialize(state, false).getBytes(StandardCharsets.UTF_8));
    }

    public <T> T getOperatorStateJson(String name, Class<T> type) {
        byte[] data = operatorStates.get(name);
        if (data == null) return null;
        return JsonTool.parseBeanFromText(new String(data, StandardCharsets.UTF_8), type);
    }

    public void putOperatorStateJava(String name, Serializable state) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(state);
        }
        operatorStates.put(name, baos.toByteArray());
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T getOperatorStateJava(String name) throws Exception {
        byte[] data = operatorStates.get(name);
        if (data == null) return null;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (T) ois.readObject();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, byte[]> operatorStates = new HashMap<>();
        private final Map<String, byte[]> keyedStates = new HashMap<>();
        private final Map<String, byte[]> rawKeyedStates = new HashMap<>();

        public Builder putOperatorState(String name, byte[] state) {
            operatorStates.put(name, state);
            return this;
        }

        public Builder putKeyedState(String name, byte[] state) {
            keyedStates.put(name, state);
            return this;
        }

        public Builder putRawKeyedState(String name, byte[] state) {
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
