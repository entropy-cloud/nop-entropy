/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.core.lang.json.JsonTool;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.util.ClassNameValidator;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.common.state.backend.IKeyedStateBackend.DEFAULT_NAMESPACE;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;

class MemoryStateSerDe {

    private final MemoryKeyedStateBackend<?> backend;
    private final Class<?> keyType;
    private final int shardCount;

    MemoryStateSerDe(MemoryKeyedStateBackend<?> backend) {
        this.backend = backend;
        this.keyType = backend.getKeyType();
        this.shardCount = backend.getShardCount();
    }

    StateSnapshot snapshotState(Map<String, Object> states) throws Exception {
        if (states.isEmpty()) {
            return null;
        }

        Map<String, Object> stateData = new LinkedHashMap<>();
        stateData.put("keyType", keyType.getName());

        Map<String, Object> statesMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : states.entrySet()) {
            String stateName = entry.getKey();
            Object stateObj = entry.getValue();

            if (stateObj instanceof MemoryValueState) {
                statesMap.put(stateName, snapshotValueState((MemoryValueState<?>) stateObj));
            } else if (stateObj instanceof MemoryMapState) {
                statesMap.put(stateName, snapshotMapState((MemoryMapState<?, ?>) stateObj));
            } else if (stateObj instanceof MemoryListState) {
                statesMap.put(stateName, snapshotListStateFromPublic((MemoryListState<?>) stateObj));
            } else if (stateObj instanceof MemoryInternalAppendingState) {
                statesMap.put(stateName, snapshotAppendingState((MemoryInternalAppendingState<?, ?, ?, ?>) stateObj));
            } else if (stateObj instanceof MemoryInternalListState) {
                statesMap.put(stateName, snapshotListState((MemoryInternalListState<?, ?, ?>) stateObj));
            } else if (stateObj instanceof MemoryReducingState) {
                statesMap.put(stateName, snapshotReducingState((MemoryReducingState<?>) stateObj));
            } else if (stateObj instanceof MemoryAggregatingState) {
                statesMap.put(stateName, snapshotAggregatingState((MemoryAggregatingState<?, ?, ?>) stateObj));
            } else {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "Unknown state type during snapshot: " + stateObj.getClass().getName());
            }
        }
        stateData.put("states", statesMap);

        return new StateSnapshot(stateData);
    }

    @SuppressWarnings("unchecked")
    void restoreState(Map<String, Object> states, StateSnapshot snapshot) throws Exception {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        Map<String, Object> stateData = snapshot.getStateData();
        Map<String, Object> statesMap = (Map<String, Object>) stateData.get("states");
        if (statesMap == null || statesMap.isEmpty()) {
            return;
        }

        states.clear();

        for (Map.Entry<String, Object> entry : statesMap.entrySet()) {
            String stateName = entry.getKey();
            Map<String, Object> stateInfo = (Map<String, Object>) entry.getValue();
            String stateType = (String) stateInfo.get("stateType");

            switch (stateType) {
                case "ValueState":
                    restoreValueState(states, stateName, stateInfo);
                    break;
                case "MapState":
                    restoreMapState(states, stateName, stateInfo);
                    break;
                case "AppendingState":
                    restoreAppendingState(states, stateName, stateInfo);
                    break;
                case "ListState":
                    restoreListState(states, stateName, stateInfo);
                    break;
                case "InternalListState":
                    restoreInternalListState(states, stateName, stateInfo);
                    break;
                case "ReducingState":
                    restoreReducingState(states, stateName, stateInfo);
                    break;
                case "AggregatingState":
                    restoreAggregatingState(states, stateName, stateInfo);
                    break;
                default:
                    throw new StreamException(ERR_STREAM_STATE_ERROR)
                            .param(ARG_DETAIL, "Unknown state type during restore: " + stateType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreValueState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);

        ValueStateDescriptor<Object> descriptor = new ValueStateDescriptor<>(stateName, valueClass);
        MemoryValueState<Object> state = new MemoryValueState<>(backend, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        backend.routeKey(deserializeKey(e.get("key"))));
                Object value = deserializeValue(e.get("value"), valueClass);
                state.storage.put(nk, value);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreMapState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String keyTypeName = (String) stateInfo.get("mapKeyTypeName");
        if (keyTypeName == null) {
            keyTypeName = (String) stateInfo.get("mapKeyType");
        }
        Class<Object> mapKeyClass = null;
        if (keyTypeName != null) {
            ClassNameValidator.validateClassName(keyTypeName);
            mapKeyClass = (Class<Object>) Class.forName(keyTypeName);
        }

        MapStateDescriptor<Object, Object> descriptor = new MapStateDescriptor<>(stateName, mapKeyClass, valueClass);
        MemoryMapState<Object, Object> state = new MemoryMapState<>(backend, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        backend.routeKey(deserializeKey(e.get("key"))));
                Map<Object, Object> mapValue = new LinkedHashMap<>();
                List<List<Object>> mapEntries = (List<List<Object>>) e.get("mapValue");
                if (mapEntries != null) {
                    for (List<Object> me : mapEntries) {
                        Object mk = mapKeyClass != null ? deserializeValue(me.get(0), mapKeyClass) : me.get(0);
                        Object mv = deserializeValue(me.get(1), valueClass);
                        mapValue.put(mk, mv);
                    }
                }
                state.storage.put(nk, mapValue);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreAppendingState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String accumulatorTypeName = (String) stateInfo.get("accumulatorTypeName");
        if (accumulatorTypeName == null) {
            accumulatorTypeName = (String) stateInfo.get("accumulatorType");
        }
        ClassNameValidator.validateAccumulatorClass(accumulatorTypeName);
        Class<? extends SimpleAccumulator<Object>> accumulatorClass =
                (Class<? extends SimpleAccumulator<Object>>) Class.forName(accumulatorTypeName);

        ReducingStateDescriptor<Object> descriptor =
                new ReducingStateDescriptor<>(stateName, valueClass, accumulatorClass);
        MemoryInternalAppendingState<Object, Object, Object, Object> state =
                new MemoryInternalAppendingState<>(backend, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        backend.routeKey(deserializeKey(e.get("key"))));
                Object value = deserializeValue(e.get("value"), valueClass);
                state.storage.put(nk, value);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreListState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);

        ListStateDescriptor<Object> descriptor = new ListStateDescriptor<>(stateName, valueClass);
        MemoryListState<Object> state = new MemoryListState<>(backend, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        backend.routeKey(deserializeKey(e.get("key"))));
                List<Object> list = new ArrayList<>();
                List<Object> values = (List<Object>) e.get("listValue");
                if (values != null) {
                    for (Object v : values) {
                        list.add(deserializeValue(v, valueClass));
                    }
                }
                state.storage.put(nk, list);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreInternalListState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);

        ListStateDescriptor<Object> descriptor = new ListStateDescriptor<>(stateName, valueClass);
        MemoryInternalListState<Object, Object, Object> state =
                new MemoryInternalListState<>(backend, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        backend.routeKey(deserializeKey(e.get("key"))));
                List<Object> list = new ArrayList<>();
                List<Object> values = (List<Object>) e.get("listValue");
                if (values != null) {
                    for (Object v : values) {
                        list.add(deserializeValue(v, valueClass));
                    }
                }
                state.storage.put(nk, list);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreReducingState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueType");
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String accumulatorTypeName = (String) stateInfo.get("accumulatorType");
        ClassNameValidator.validateAccumulatorClass(accumulatorTypeName);
        Class<? extends SimpleAccumulator<Object>> accumulatorClass =
                (Class<? extends SimpleAccumulator<Object>>) Class.forName(accumulatorTypeName);

        ReducingStateDescriptor<Object> descriptor =
                new ReducingStateDescriptor<>(stateName, valueClass, accumulatorClass);
        MemoryReducingState<Object> state = new MemoryReducingState<>(backend, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        backend.routeKey(deserializeKey(e.get("key"))));
                Object value = deserializeValue(e.get("value"), valueClass);
                state.storage.put(nk, wrapInAccumulator(value, accumulatorClass));
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private <T> SimpleAccumulator<T> wrapInAccumulator(Object value, Class<? extends SimpleAccumulator<T>> accumulatorClass) {
        if (!SimpleAccumulator.class.isAssignableFrom(accumulatorClass)) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "Type does not implement SimpleAccumulator: " + accumulatorClass.getName());
        }
        try {
            SimpleAccumulator<T> acc = accumulatorClass.getDeclaredConstructor().newInstance();
            if (value != null) {
                acc.add((T) value);
            }
            return acc;
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to create accumulator: " + accumulatorClass.getName());
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreAggregatingState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueType");
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String aggregateFunctionTypeName = (String) stateInfo.get("aggregateFunctionType");
        ClassNameValidator.validateAccumulatorClass(aggregateFunctionTypeName);
        Class<? extends AggregateFunction<?, ?, ?>> aggregateFunctionClass =
                (Class<? extends AggregateFunction<?, ?, ?>>) Class.forName(aggregateFunctionTypeName);
        AggregateFunction<Object, Object, Object> aggregateFunction =
                (AggregateFunction<Object, Object, Object>) aggregateFunctionClass.getDeclaredConstructor().newInstance();

        AggregatingStateDescriptor<Object, Object, Object> descriptor =
                new AggregatingStateDescriptor<>(stateName, aggregateFunction, valueClass);
        MemoryAggregatingState<Object, Object, Object> state =
                new MemoryAggregatingState<>(backend, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        backend.routeKey(deserializeKey(e.get("key"))));
                Object value = deserializeValue(e.get("value"), valueClass);
                state.storage.put(nk, value);
            }
        }

        states.put(stateName, state);
    }

    private Map<String, Object> snapshotValueState(MemoryValueState<?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        info.put("valueType", state.descriptor.getValueType().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", e.getValue());
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotMapState(MemoryMapState<?, ?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "MapState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("mapKeyType", state.descriptor.getKeyClass().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ? extends Map<?, ?>> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            List<List<Object>> mapEntries = new ArrayList<>();
            for (Map.Entry<?, ?> me : e.getValue().entrySet()) {
                List<Object> pair = new ArrayList<>();
                pair.add(me.getKey());
                pair.add(me.getValue());
                mapEntries.add(pair);
            }
            entry.put("mapValue", mapEntries);
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotAppendingState(MemoryInternalAppendingState<?, ?, ?, ?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "AppendingState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("accumulatorType", state.descriptor.getAccumulatorType().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", e.getValue());
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotListStateFromPublic(MemoryListState<?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ListState");
        info.put("valueType", state.descriptor.getValueType().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ? extends List<?>> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("listValue", new ArrayList<>(e.getValue()));
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotListState(MemoryInternalListState<?, ?, ?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "InternalListState");
        info.put("valueType", state.descriptor.getValueType().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ? extends List<?>> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("listValue", new ArrayList<>(e.getValue()));
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotReducingState(MemoryReducingState<?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ReducingState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("accumulatorType", state.descriptor.getAccumulatorType().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ? extends SimpleAccumulator<?>> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", e.getValue().getLocalValue());
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotAggregatingState(MemoryAggregatingState<?, ?, ?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "AggregatingState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("aggregateFunctionType", state.descriptor.getAggregateFunction().getClass().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", e.getValue());
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Object unwrapStorageKey(Object storageKey) {
        if (storageKey instanceof ShardPrefixedKey) {
            return ((ShardPrefixedKey) storageKey).key;
        }
        return storageKey;
    }

    private Object serializeNamespace(Object namespace) {
        if (namespace == null) {
            return DEFAULT_NAMESPACE;
        }
        if (namespace instanceof TimeWindow) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("@type", "TimeWindow");
            m.put("start", ((TimeWindow) namespace).getStart());
            m.put("end", ((TimeWindow) namespace).getEnd());
            return m;
        }
        if (namespace instanceof GlobalWindow) {
            return "GlobalWindow";
        }
        return namespace;
    }

    @SuppressWarnings("unchecked")
    private Object deserializeNamespace(Object obj) {
        if (obj == null) {
            return DEFAULT_NAMESPACE;
        }
        if (obj instanceof String) {
            String s = (String) obj;
            if ("GlobalWindow".equals(s)) {
                return GlobalWindow.get();
            }
            if ("VoidNamespace".equals(s)) {
                return io.nop.stream.core.common.state.VoidNamespace.INSTANCE;
            }
            return s;
        }
        if (obj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) obj;
            String type = (String) m.get("@type");
            if ("TimeWindow".equals(type)) {
                return new TimeWindow(
                        ((Number) m.get("start")).longValue(),
                        ((Number) m.get("end")).longValue());
            }
        }
        return obj;
    }

    private Object serializeKey(Object key) {
        return key;
    }

    private Object deserializeKey(Object obj) {
        return obj;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Object obj, Class<T> type) {
        if (obj == null) {
            return null;
        }
        if (type.isInstance(obj)) {
            return (T) obj;
        }
        String json = JsonTool.serialize(obj, false);
        return JsonTool.parseBeanFromText(json, type);
    }
}
