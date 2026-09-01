/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.Serializable;
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
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateSchemaResolver;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.state.backend.ContainerValueCodec;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.common.state.shard.KeyGroupRangeRestoreFilter;
import io.nop.stream.core.common.state.shard.ShardPrefixedKey;
import io.nop.stream.core.common.typeutils.IStreamSerializer;
import io.nop.stream.core.common.typeutils.JsonToolSerializer;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.util.ClassNameValidator;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.exceptions.StreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.stream.core.common.state.backend.IKeyedStateBackend.DEFAULT_NAMESPACE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TYPE_MISMATCH;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_AGGREGATING;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_APPENDING;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_INTERNAL_AGGREGATING;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_INTERNAL_LIST;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_LIST;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_MAP;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_REDUCING;
import static io.nop.stream.core.common.state.StateSchemaResolver.STATE_TYPE_VALUE;

class MemoryStateSerDe {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryStateSerDe.class);

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
            } else if (stateObj instanceof MemoryInternalAggregatingState) {
                statesMap.put(stateName, snapshotInternalAggregatingState((MemoryInternalAggregatingState<?, ?, ?, ?, ?>) stateObj));
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

        // Stage 35: per-subtask partial restore. When the backend carries a
        // target KeyGroupRange, reduce each state's entries to those whose key
        // is owned by the range before re-routing. This is the in-memory
        // equivalent of the RocksDB SST range scan (Stage 31 deferred item).
        KeyGroupRange range = backend.getTargetKeyGroupRange();
        if (range != null) {
            statesMap = KeyGroupRangeRestoreFilter.filterKeyedStates(statesMap, range, backend.getMaxParallelism());
            if (statesMap.isEmpty()) {
                return;
            }
        }

        states.clear();

        for (Map.Entry<String, Object> entry : statesMap.entrySet()) {
            String stateName = entry.getKey();
            Object entryValue = entry.getValue();
            if (!(entryValue instanceof Map)) {
                throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_ACTUAL_TYPE,
                        entryValue == null ? "null" : entryValue.getClass().getName())
                        .param(ARG_DETAIL, "State '" + stateName
                                + "' in snapshot is not a per-state info map; snapshot is corrupt or foreign");
            }
            Map<String, Object> stateInfo = (Map<String, Object>) entryValue;
            String stateType = (String) stateInfo.get("stateType");
            if (stateType == null || stateType.isEmpty()) {
                // S-6 (2026-09-01 core audit): a bare NPE from switch-on-null loses all
                // context; fail fast with the state name so the corrupt snapshot is
                // attributable.
                throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL,
                        "State '" + stateName + "' in snapshot has no stateType; snapshot is corrupt or foreign");
            }

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
                case "InternalAggregatingState":
                    restoreInternalAggregatingState(states, stateName, stateInfo);
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
                Object value = deserializeValue(e.get("value"), valueClass, descriptor);
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
                Object value = deserializeValue(e.get("value"), valueClass, descriptor);
                if (value != null && !valueClass.isInstance(value)) {
                    throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                            .param(ARG_EXPECTED_TYPE, valueClass.getName())
                            .param(ARG_ACTUAL_TYPE, value.getClass().getName());
                }
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
                        list.add(deserializeValue(v, valueClass, descriptor));
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
                        list.add(deserializeValue(v, valueClass, descriptor));
                    }
                }
                state.storage.put(nk, list);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreReducingState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
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
        MemoryReducingState<Object> state = new MemoryReducingState<>(backend, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        backend.routeKey(deserializeKey(e.get("key"))));
                Object value = deserializeValue(e.get("value"), valueClass, descriptor);
                if (value != null && !valueClass.isInstance(value)) {
                    throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                            .param(ARG_EXPECTED_TYPE, valueClass.getName())
                            .param(ARG_ACTUAL_TYPE, value.getClass().getName());
                }
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
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String aggregateFunctionTypeName = (String) stateInfo.get("aggregateFunctionType");
        ClassNameValidator.validateAccumulatorClass(aggregateFunctionTypeName);
        AggregateFunction<Object, Object, Object> aggregateFunction =
                resolveAggregateFunction(stateName, aggregateFunctionTypeName);
        valueClass = (Class<Object>) inferAccumulatorType(aggregateFunction, valueClass);

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
                Object value = deserializeValue(e.get("value"), valueClass, descriptor);
                state.storage.put(nk, value);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreInternalAggregatingState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String aggregateFunctionTypeName = (String) stateInfo.get("aggregateFunctionType");
        ClassNameValidator.validateAccumulatorClass(aggregateFunctionTypeName);
        AggregateFunction<Object, Object, Object> aggregateFunction =
                resolveAggregateFunction(stateName, aggregateFunctionTypeName);
        valueClass = (Class<Object>) inferAccumulatorType(aggregateFunction, valueClass);

        AggregatingStateDescriptor<Object, Object, Object> descriptor =
                new AggregatingStateDescriptor<>(stateName, aggregateFunction, valueClass);
        MemoryInternalAggregatingState<Object, Object, Object, Object, Object> state =
                new MemoryInternalAggregatingState<>(backend, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        backend.routeKey(deserializeKey(e.get("key"))));
                Object value = deserializeValue(e.get("value"), valueClass, descriptor);
                state.storage.put(nk, value);
            }
        }

        states.put(stateName, state);
    }

    /**
     * The window descriptor path (WindowedStreamImpl.aggregate/reduce) records the
     * accumulator type as {@code java.lang.Object} (generic erasure), so the snapshot's
     * recorded valueType cannot drive value re-materialization: a JSON array round-trip
     * of a {@code long[]} accumulator would be restored as an ArrayList and the user
     * function's {@code add} would ClassCastException. When the recorded type is the
     * generic {@code Object}, infer the real accumulator type from the LIVE aggregate
     * function's {@code createAccumulator()} (registered by the operator before restore).
     * Functions whose {@code createAccumulator()} returns {@code null} (e.g. the
     * reduce-function wrapper) keep the recorded type — JSON-native accumulators
     * (String/numbers) restore correctly without the inference.
     */
    private static Class<?> inferAccumulatorType(AggregateFunction<?, ?, ?> aggregateFunction, Class<?> recordedType) {
        if (recordedType != Object.class || aggregateFunction == null) {
            return recordedType;
        }
        try {
            Object accumulator = aggregateFunction.createAccumulator();
            if (accumulator != null) {
                return accumulator.getClass();
            }
        } catch (Exception e) {
            // Keep the recorded (generic) type; JSON-native accumulators restore
            // correctly either way — but the live-function failure must be visible
            // (it surfaces later as a ClassCastException in user add() otherwise).
            LOG.warn("createAccumulator() on aggregate function {} threw; keeping recorded type {}",
                    aggregateFunction.getClass().getName(), recordedType.getName(), e);
        }
        return recordedType;
    }

    /**
     * P1-01 (Decision: 方案 1 = live function reuse): resolves the aggregate
     * function used to rebuild aggregating state on restore.
     *
     * <p>Priority order:
     * <ol>
     *   <li>the LIVE function registered via
     *       {@code IKeyedStateBackend#registerRestoreAggregateFunction} (the
     *       operator's descriptor function — works for capturing anonymous
     *       classes / lambdas AND restores old snapshots written before the
     *       fix);</li>
     *   <li>class-name + no-arg reflection (legacy path for user functions with
     *       a public no-arg constructor).</li>
     * </ol>
     *
     * <p>When reflection fails because the recorded class has no no-arg
     * constructor (e.g. {@code WindowOperatorBuilder.reduceFunctionAsAggregate}
     * wrapper), fail fast with a clear error instead of silently producing a
     * broken function (No-Silent-No-Op rule #24).
     */
    @SuppressWarnings("unchecked")
    private AggregateFunction<Object, Object, Object> resolveAggregateFunction(
            String stateName, String aggregateFunctionTypeName) throws Exception {
        AggregateFunction<?, ?, ?> live = backend.getRestoreAggregateFunction(stateName);
        if (live != null) {
            return (AggregateFunction<Object, Object, Object>) live;
        }
        ClassNameValidator.validateAccumulatorClass(aggregateFunctionTypeName);
        Class<? extends AggregateFunction<?, ?, ?>> aggregateFunctionClass =
                (Class<? extends AggregateFunction<?, ?, ?>>) Class.forName(aggregateFunctionTypeName);
        try {
            return (AggregateFunction<Object, Object, Object>)
                    aggregateFunctionClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "AggregateFunction class " + aggregateFunctionTypeName
                            + " has no no-arg constructor and no live function was registered for state '"
                            + stateName + "' — the operator must register its descriptor's function via "
                            + "IKeyedStateBackend.registerRestoreAggregateFunction before restore "
                            + "(window operators do this in open() prior to applyPendingRestoreState)");
        }
    }

    private Map<String, Object> snapshotValueState(MemoryValueState<?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        info.put("valueType", state.descriptor.getValueType().getName());
        embedSchemaFingerprint(info, STATE_TYPE_VALUE, state.descriptor);
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        IStreamSerializer<Object> valueSer = getSerializerIfAvailable(state.descriptor);
        TtlContext<TypedNamespaceAndKey> ttl = state.ttl;
        for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
            if (ttl != null && ttl.isExpiredForSnapshot(e.getKey())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", serializeWithSerializer(e.getValue(), valueSer));
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
        embedSchemaFingerprint(info, STATE_TYPE_MAP, state.descriptor);
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        IStreamSerializer<Object> valueSer = getSerializerIfAvailable(state.descriptor);
        TtlContext<TypedNamespaceAndKey> ttl = state.ttl;
        for (Map.Entry<TypedNamespaceAndKey, ? extends Map<?, ?>> e : state.storage.entrySet()) {
            if (ttl != null && ttl.isExpiredForSnapshot(e.getKey())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            List<List<Object>> mapEntries = new ArrayList<>();
            for (Map.Entry<?, ?> me : e.getValue().entrySet()) {
                List<Object> pair = new ArrayList<>();
                pair.add(me.getKey());
                // P1-21-01: container values (List/Map, nested) are wrapped with per-level
                // element type info so the JSON storage layer can restore inner element
                // types (the raw List.class declared type carries no element type, and the
                // JSON round trip would turn them into LinkedHashMaps). Only the JSON path
                // (no custom serializer) is wrapped — a custom IStreamSerializer keeps its
                // own byte[] contract (P2-09-02c tracks its silent degradation separately).
                Object value = serializeWithSerializer(me.getValue(), valueSer);
                pair.add(valueSer == null ? ContainerValueCodec.encode(value) : value);
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
        embedSchemaFingerprint(info, STATE_TYPE_APPENDING, state.descriptor);
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        TtlContext<TypedNamespaceAndKey> ttl = state.ttl;
        for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
            if (ttl != null && ttl.isExpiredForSnapshot(e.getKey())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            Object value = e.getValue();
            if (value instanceof List) {
                entry.put("value", new ArrayList<>((List<?>) value));
            } else {
                entry.put("value", value);
            }
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotListStateFromPublic(MemoryListState<?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ListState");
        info.put("valueType", state.descriptor.getValueType().getName());
        embedSchemaFingerprint(info, STATE_TYPE_LIST, state.descriptor);
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        IStreamSerializer<Object> valueSer = getSerializerIfAvailable(state.descriptor);
        TtlContext<TypedNamespaceAndKey> ttl = state.ttl;
        for (Map.Entry<TypedNamespaceAndKey, ? extends List<?>> e : state.storage.entrySet()) {
            if (ttl != null && ttl.isExpiredForSnapshot(e.getKey())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            List<Object> serializedList = new ArrayList<>();
            for (Object v : e.getValue()) {
                serializedList.add(serializeWithSerializer(v, valueSer));
            }
            entry.put("listValue", serializedList);
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotListState(MemoryInternalListState<?, ?, ?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "InternalListState");
        info.put("valueType", state.descriptor.getValueType().getName());
        embedSchemaFingerprint(info, STATE_TYPE_INTERNAL_LIST, state.descriptor);
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        IStreamSerializer<Object> valueSer = getSerializerIfAvailable(state.descriptor);
        TtlContext<TypedNamespaceAndKey> ttl = state.ttl;
        for (Map.Entry<TypedNamespaceAndKey, ? extends List<?>> e : state.storage.entrySet()) {
            if (ttl != null && ttl.isExpiredForSnapshot(e.getKey())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            List<Object> serializedList = new ArrayList<>();
            for (Object v : e.getValue()) {
                serializedList.add(serializeWithSerializer(v, valueSer));
            }
            entry.put("listValue", serializedList);
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
        embedSchemaFingerprint(info, STATE_TYPE_REDUCING, state.descriptor);
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        IStreamSerializer<Object> valueSer = getSerializerIfAvailable(state.descriptor);
        TtlContext<TypedNamespaceAndKey> ttl = state.ttl;
        for (Map.Entry<TypedNamespaceAndKey, ? extends SimpleAccumulator<?>> e : state.storage.entrySet()) {
            if (ttl != null && ttl.isExpiredForSnapshot(e.getKey())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", serializeWithSerializer(e.getValue().getLocalValue(), valueSer));
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
        embedSchemaFingerprint(info, STATE_TYPE_AGGREGATING, state.descriptor);
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        IStreamSerializer<Object> valueSer = getSerializerIfAvailable(state.descriptor);
        TtlContext<TypedNamespaceAndKey> ttl = state.ttl;
        for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
            if (ttl != null && ttl.isExpiredForSnapshot(e.getKey())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", serializeWithSerializer(e.getValue(), valueSer));
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotInternalAggregatingState(MemoryInternalAggregatingState<?, ?, ?, ?, ?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "InternalAggregatingState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("aggregateFunctionType", state.descriptor.getAggregateFunction().getClass().getName());
        embedSchemaFingerprint(info, STATE_TYPE_INTERNAL_AGGREGATING, state.descriptor);
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        IStreamSerializer<Object> valueSer = getSerializerIfAvailable(state.descriptor);
        TtlContext<TypedNamespaceAndKey> ttl = state.ttl;
        for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
            if (ttl != null && ttl.isExpiredForSnapshot(e.getKey())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", serializeWithSerializer(e.getValue(), valueSer));
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Object unwrapStorageKey(Object storageKey) {
        if (storageKey instanceof ShardPrefixedKey) {
            return ((ShardPrefixedKey) storageKey).getKey();
        }
        return storageKey;
    }

    private void embedSchemaFingerprint(Map<String, Object> info, String stateType, StateDescriptor<?> descriptor) {
        SerializerFingerprint fingerprint = StateSchemaResolver.fromDescriptor(stateType, descriptor);
        info.put("schemaChecksum", fingerprint.getSchemaChecksum());
        info.put("schemaVersion", fingerprint.getSchemaVersion());
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

    /**
     * AR-01 (P0): re-materializes a restored state key to the backend's
     * declared {@link #keyType}, mirroring {@code RocksDBKeyEncoder.jsonToKey}.
     *
     * <p>JSON persistence ({@code storageType="local"}) round-trips numeric keys
     * through {@code TextScanner}, which tries {@code Integer.parseInt} first:
     * a {@code Long(123)} written as {@code "123"} comes back as
     * {@code Integer(123)}. {@link TypedNamespaceAndKey#equals} is
     * class-sensitive, so live lookups with the original {@code Long} key missed
     * everything — the keyed state silently restarted from empty on the default
     * backend while the RocksDB backend (which re-materializes by keyType)
     * restored the same checkpoint correctly. Keys that already match the
     * declared keyType (String keys, values that survived as Long) pass through
     * unchanged.
     *
     * <p>No-silent-skip (guide rule #24): a failed re-materialization throws
     * instead of returning the original object.
     */
    private Object deserializeKey(Object obj) throws Exception {
        if (obj == null) {
            return null;
        }
        if (keyType != null && keyType != Object.class && !keyType.isInstance(obj)) {
            String json = JsonTool.serialize(obj, false);
            Object rematerialized;
            try {
                rematerialized = JsonTool.parseBeanFromText(json, keyType);
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to re-materialize state key " + json
                                + " as " + keyType.getName() + " during restore");
            }
            if (rematerialized == null || !keyType.isInstance(rematerialized)) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "Failed to re-materialize state key " + json
                                + " as " + keyType.getName() + " during restore");
            }
            return rematerialized;
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private <T> IStreamSerializer<T> getSerializerIfAvailable(StateDescriptor<?> descriptor) {
        if (descriptor == null) return null;
        TypeSerializer<?> ser = descriptor.getSerializer();
        if (ser instanceof IStreamSerializer && !(ser instanceof JsonToolSerializer)) {
            return (IStreamSerializer<T>) ser;
        }
        return null;
    }

    private <T> Object serializeWithSerializer(Object value, IStreamSerializer<T> serializer) {
        if (serializer == null || value == null) {
            return value;
        }
        try {
            byte[] payload = serializer.serialize((T) value);
            // Wrap the byte payload in a self-describing JSON-safe marker: the
            // local-storage checkpoint persist runs the snapshot through JSON,
            // where a raw byte[] silently degrades to an opaque base64 String
            // that the restore path can no longer recognize. The marker map
            // round-trips through JSON losslessly and is unwrapped on restore.
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put(JAVA_BYTES_MARKER, java.util.Base64.getEncoder().encodeToString(payload));
            return marker;
        } catch (Exception e) {
            // S-3 (2026-09-01 core audit): the raw-object fallback is the deliberate
            // P2-09-02c compatibility path, but it silently degrades the snapshot
            // format (restore only re-enters the serializer for byte[] payloads) —
            // the degradation must be observable, never silent.
            LOG.warn("Custom serializer {} failed for state value of type {}; falling back to raw value in snapshot (format degrades to non-byte[] on restore)",
                    serializer.getClass().getName(), value.getClass().getName(), e);
            return value;
        }
    }

    /** Marker key identifying a Java-serialized custom-serializer payload. */
    static final String JAVA_BYTES_MARKER = "__java_bytes__";

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Object obj, Class<T> type) {
        return deserializeValue(obj, type, null);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Object obj, Class<T> type, StateDescriptor<?> descriptor) {
        if (obj == null) {
            return null;
        }
        if (descriptor != null) {
            TypeSerializer<?> ser = descriptor.getSerializer();
            if (ser instanceof IStreamSerializer && !(ser instanceof JsonToolSerializer)
                    && obj instanceof byte[]) {
                return ((IStreamSerializer<T>) ser).deserialize((byte[]) obj, type);
            }
        }
        // byte[] payloads in a snapshot ALWAYS originate from a custom
        // IStreamSerializer (the JSON path stores raw structures). A state
        // restored by restoreValueState/restoreMapState carries a freshly built
        // descriptor WITHOUT the custom serializer, so decode with the
        // Java-stream serializer (the descriptor supplied by the operator's
        // open() re-attaches the custom serializer for subsequent snapshots).
        if (obj instanceof Map && ((Map<?, ?>) obj).containsKey(JAVA_BYTES_MARKER)) {
            // custom-serializer payload: base64-decode and Java-deserialize
            byte[] payload = java.util.Base64.getDecoder()
                    .decode(String.valueOf(((Map<?, ?>) obj).get(JAVA_BYTES_MARKER)));
            @SuppressWarnings("rawtypes")
            io.nop.stream.core.common.typeutils.JavaStreamSerializer serializer =
                    io.nop.stream.core.common.typeutils.JavaStreamSerializer.INSTANCE;
            return (T) serializer.deserialize(payload, Serializable.class);
        }
        if (obj instanceof byte[]) {
            @SuppressWarnings("rawtypes")
            io.nop.stream.core.common.typeutils.JavaStreamSerializer serializer =
                    io.nop.stream.core.common.typeutils.JavaStreamSerializer.INSTANCE;
            return (T) serializer.deserialize((byte[]) obj, Serializable.class);
        }
        // P1-21-01: container values (List/Map/Collection) carry per-level element type
        // info in the snapshot (wrapped by snapshotMapState); decode re-materializes inner
        // elements. Unwrapped legacy containers degrade with a LOG.warn instead of
        // silently returning JSON-native elements (No-Silent-No-Op rule #24).
        if (ContainerValueCodec.isContainerType(type)) {
            return (T) ContainerValueCodec.decode(obj, type,
                    "state '" + (descriptor != null ? descriptor.getName() : "?") + "'");
        }
        if (type.isInstance(obj)) {
            return (T) obj;
        }
        String json = JsonTool.serialize(obj, false);
        return JsonTool.parseBeanFromText(json, type);
    }
}
