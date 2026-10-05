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
import io.nop.stream.core.common.state.backend.AccumulatorTypeInference;
import io.nop.stream.core.common.state.backend.ContainerValueCodec;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.MapValuePairValidator;
import io.nop.stream.core.common.state.backend.RestoredStateFlavor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.common.state.shard.KeyGroupRangeRestoreFilter;
import io.nop.stream.core.common.state.shard.ShardPrefixedKey;
import io.nop.stream.core.common.state.shard.StateKeyRematerializer;
import io.nop.stream.core.common.typeutils.IStreamSerializer;
import io.nop.stream.core.common.typeutils.JsonToolSerializer;
import io.nop.stream.core.util.SnapshotCopyUtil;
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
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_NAME;
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

/**
 * Single-point (item 21 D-1 convergence) snapshot/restore serde for
 * {@link MemoryKeyedStateBackend}.
 *
 * <p>Every state family shares the two generic implementations
 * {@link #snapshotKeyedState} (header + TTL-filtered entry loop + per-family
 * payload writer) and {@link #restoreKeyedEntries} (typeName fallback via
 * {@link #resolveTypeName} + entry loop + per-family payload decoder); the
 * per-family branches only describe their type resolution and payload shape.
 * This is also the single choke point for the item 24 restore-path defensive
 * checks: TimeWindow namespace field validation (see
 * {@link #deserializeNamespace}) and per-pair mapValue validation (see the
 * MapState decoder).
 */
class MemoryStateSerDe {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryStateSerDe.class);

    private final MemoryKeyedStateBackend<?> backend;
    private final Class<?> keyType;
    private final int shardCount;

    /**
     * R5 ST-02: the key type used for snapshot-header writing and restore-side
     * key re-materialization. Declared at construction; a restore snapshot
     * carrying a concrete {@code keyType} header overrides it (header wins, see
     * {@link #restoreState}) so Object-declared backends restore real keys.
     */
    private Class<?> effectiveKeyType;

    MemoryStateSerDe(MemoryKeyedStateBackend<?> backend) {
        this.backend = backend;
        this.keyType = backend.getKeyType();
        this.effectiveKeyType = backend.getSnapshotKeyType();
        this.shardCount = backend.getShardCount();
    }

    // ------------------------------------------------------------------
    //  snapshot (single generic implementation + per-family payload writer)
    // ------------------------------------------------------------------

    /** Writes the per-entry payload (the "value"/"listValue"/"mapValue" field). */
    @FunctionalInterface
    interface EntryWriter {
        void write(Map<String, Object> entry, Object storedValue, IStreamSerializer<Object> valueSer);
    }

    StateSnapshot snapshotState(Map<String, Object> states) throws Exception {
        if (states.isEmpty()) {
            return null;
        }

        Map<String, Object> stateData = new LinkedHashMap<>();
        // R5 ST-02: record the effective key type (first-key capture for
        // Object-declared backends) so restore can re-materialize real keys.
        stateData.put("keyType", effectiveKeyType.getName());

        Map<String, Object> statesMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : states.entrySet()) {
            String stateName = entry.getKey();
            Object stateObj = entry.getValue();
            statesMap.put(stateName, snapshotOneState(stateName, stateObj));
        }
        stateData.put("states", statesMap);

        return new StateSnapshot(stateData);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotOneState(String stateName, Object stateObj) {
        if (stateObj instanceof MemoryValueState) {
            MemoryValueState<?> s = (MemoryValueState<?>) stateObj;
            return snapshotKeyedState(STATE_TYPE_VALUE, s.descriptor, null, s.ttl, s.storage,
                    (entry, v, ser) -> entry.put("value", freezeRawValue(v, ser)));
        }
        if (stateObj instanceof MemoryMapState) {
            MemoryMapState<?, ?> s = (MemoryMapState<?, ?>) stateObj;
            Map<String, Object> headers = new LinkedHashMap<>();
            headers.put("mapKeyType", s.descriptor.getKeyClass().getName());
            return snapshotKeyedState(STATE_TYPE_MAP, s.descriptor, headers, s.ttl, s.storage, MemoryStateSerDe::writeMapPayload);
        }
        if (stateObj instanceof MemoryInternalListState) {
            MemoryInternalListState<?, ?, ?> s = (MemoryInternalListState<?, ?, ?>) stateObj;
            return snapshotKeyedState(STATE_TYPE_INTERNAL_LIST, s.descriptor, null, s.ttl, s.storage,
                    MemoryStateSerDe::writeListPayload);
        }
        if (stateObj instanceof MemoryListState) {
            MemoryListState<?> s = (MemoryListState<?>) stateObj;
            return snapshotKeyedState(STATE_TYPE_LIST, s.descriptor, null, s.ttl, s.storage,
                    MemoryStateSerDe::writeListPayload);
        }
        if (stateObj instanceof MemoryInternalAppendingState) {
            MemoryInternalAppendingState<?, ?, ?, ?> s = (MemoryInternalAppendingState<?, ?, ?, ?>) stateObj;
            Map<String, Object> headers = new LinkedHashMap<>();
            headers.put("accumulatorType", s.descriptor.getAccumulatorType().getName());
            // The appending payload is the raw accumulator-local value (never routed
            // through the custom value serializer). R5 ST-01: the earlier shallow
            // ArrayList copy left element references live; elements are now frozen
            // individually.
            return snapshotKeyedState(STATE_TYPE_APPENDING, s.descriptor, headers, s.ttl, s.storage,
                    (entry, v, ser) -> entry.put("value", copyAppendingPayload(v)));
        }
        if (stateObj instanceof MemoryInternalAggregatingState) {
            MemoryInternalAggregatingState<?, ?, ?, ?, ?> s = (MemoryInternalAggregatingState<?, ?, ?, ?, ?>) stateObj;
            return snapshotKeyedState(STATE_TYPE_INTERNAL_AGGREGATING, s.descriptor, aggregateHeaders(s.descriptor),
                    s.ttl, s.storage, MemoryStateSerDe::writeSerializedValue);
        }
        if (stateObj instanceof MemoryReducingState) {
            MemoryReducingState<?> s = (MemoryReducingState<?>) stateObj;
            Map<String, Object> headers = new LinkedHashMap<>();
            headers.put("accumulatorType", s.descriptor.getAccumulatorType().getName());
            return snapshotKeyedState(STATE_TYPE_REDUCING, s.descriptor, headers, s.ttl, s.storage,
                    (entry, v, ser) -> entry.put("value",
                            freezeRawValue(((SimpleAccumulator<?>) v).getLocalValue(), ser)));
        }
        if (stateObj instanceof MemoryAggregatingState) {
            MemoryAggregatingState<?, ?, ?> s = (MemoryAggregatingState<?, ?, ?>) stateObj;
            return snapshotKeyedState(STATE_TYPE_AGGREGATING, s.descriptor, aggregateHeaders(s.descriptor),
                    s.ttl, s.storage, MemoryStateSerDe::writeSerializedValue);
        }
        throw new StreamException(ERR_STREAM_STATE_ERROR)
                .param(ARG_DETAIL, "Unknown state type during snapshot: " + stateObj.getClass().getName());
    }

    private static Map<String, Object> aggregateHeaders(AggregatingStateDescriptor<?, ?, ?> descriptor) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("aggregateFunctionType", descriptor.getAggregateFunction().getClass().getName());
        return headers;
    }

    // R5 ST-01: appending elements bypass the custom value serializer by
    // contract, so freezing them needs the explicit copy here.
    private static Object copyAppendingPayload(Object v) {
        if (v instanceof List) {
            List<Object> copy = new ArrayList<>(((List<?>) v).size());
            for (Object e : (List<?>) v) {
                copy.add(SnapshotCopyUtil.defensiveCopy(e));
            }
            return copy;
        }
        return SnapshotCopyUtil.defensiveCopy(v);
    }

    private static void writeSerializedValue(Map<String, Object> entry, Object storedValue,
                                             IStreamSerializer<Object> valueSer) {
        entry.put("value", freezeRawValue(storedValue, valueSer));
    }

    /**
     * R5 ST-01: raw scalar/bean values handed straight to the snapshot entry
     * ("value" family) must be frozen on the task thread — post-barrier ACC
     * mutation otherwise leaks into the asynchronously persisted JSON. With a
     * custom serializer the byte[] marker path is already snapshot-safe. Unlike
     * {@link #serializeWithSerializer}, this must NOT be used for list/map
     * payload elements: those go through ContainerValueCodec, which needs the
     * live runtime type to record element type info.
     */
    private static Object freezeRawValue(Object value, IStreamSerializer<Object> valueSer) {
        if (valueSer != null) {
            return serializeWithSerializer(value, valueSer);
        }
        return SnapshotCopyUtil.defensiveCopy(value);
    }

    private static void writeListPayload(Map<String, Object> entry, Object storedValue,
                                         IStreamSerializer<Object> valueSer) {
        List<Object> serializedList = new ArrayList<>();
        for (Object v : (List<?>) storedValue) {
            serializedList.add(serializeWithSerializer(v, valueSer));
        }
        entry.put("listValue", serializedList);
    }

    @SuppressWarnings("unchecked")
    private static void writeMapPayload(Map<String, Object> entry, Object storedValue,
                                        IStreamSerializer<Object> valueSer) {
        List<List<Object>> mapEntries = new ArrayList<>();
        for (Map.Entry<?, ?> me : ((Map<?, ?>) storedValue).entrySet()) {
            List<Object> pair = new ArrayList<>();
            // R5 ST-01: map keys bypass the value serializer path; freeze them
            // like any other live value.
            pair.add(SnapshotCopyUtil.defensiveCopy(me.getKey()));
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
    }

    /**
     * Generic per-state snapshot: fixed header order (stateType, valueType,
     * family extras, schema fingerprint, shardCount), TTL-filtered storage
     * iteration, and per-family payload writing.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> snapshotKeyedState(String stateType, StateDescriptor<?> descriptor,
                                                   Map<String, Object> extraHeaders,
                                                   TtlContext<TypedNamespaceAndKey> ttl,
                                                   Map storage, EntryWriter entryWriter) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", stateType);
        info.put("valueType", descriptor.getValueType().getName());
        if (extraHeaders != null) {
            info.putAll(extraHeaders);
        }
        embedSchemaFingerprint(info, stateType, descriptor);
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        IStreamSerializer<Object> valueSer = getSerializerIfAvailable(descriptor);
        for (Object eObj : storage.entrySet()) {
            Map.Entry<TypedNamespaceAndKey, ?> e = (Map.Entry<TypedNamespaceAndKey, ?>) eObj;
            if (ttl != null && ttl.isExpiredForSnapshot(e.getKey())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entryWriter.write(entry, e.getValue(), valueSer);
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    // ------------------------------------------------------------------
    //  restore (single fallback helper + generic entry loop + per-family decoders)
    // ------------------------------------------------------------------

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

        // R5 ST-02: the snapshot header records the EFFECTIVE key type (first-key
        // capture for Object-declared backends). A concrete header overrides the
        // declaration ONLY when the backend was declared Object.class — that is
        // the ST-02 gap this closes. Concrete-vs-concrete conflicts keep the
        // declared type (and its fail-fast rematerialization) as the guard
        // against foreign/corrupt snapshots (TestMemoryStateSerDeNumericKeyRestore).
        Class<?> declared = keyType;
        Object headerType = stateData.get("keyType");
        Class<?> header = resolveHeaderKeyType(headerType);
        if (declared == Object.class && header != null && header != Object.class) {
            LOG.info("nop.stream.restore-keytype-override: snapshotKeyType={} overrides declaredKeyType=java.lang.Object"
                    + " — using the snapshot header for key re-materialization", header.getName());
            this.effectiveKeyType = header;
        }
        // otherwise effectiveKeyType stays as constructed (declared/effective capture)
        if (effectiveKeyType == Object.class) {
            warnForeignNativeKeys(statesMap);
        }

        // Stage 35: per-subtask partial restore. When the backend carries a
        // target KeyGroupRange, reduce each state's entries to those whose key
        // is owned by the range before re-routing. This is the in-memory
        // equivalent of the RocksDB SST range scan (Stage 31 deferred item).
        // ST-02: the filter must re-materialize each raw key to the effective
        // keyType BEFORE computing its group, or non-primitive keys (Date,
        // bean, ...) are filtered by a hash that differs from the live
        // routeKey decision.
        KeyGroupRange range = backend.getTargetKeyGroupRange();
        if (range != null) {
            statesMap = KeyGroupRangeRestoreFilter.filterKeyedStates(statesMap, range, backend.getMaxParallelism(), effectiveKeyType);
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
                case "InternalListState":
                    restoreListState(states, stateName, stateInfo, "InternalListState".equals(stateType));
                    break;
                case "ReducingState":
                    restoreReducingState(states, stateName, stateInfo);
                    break;
                case "AggregatingState":
                case "InternalAggregatingState":
                    restoreAggregatingState(states, stateName, stateInfo,
                            "InternalAggregatingState".equals(stateType)
                                    ? RestoredStateFlavor.INTERNAL : RestoredStateFlavor.PUBLIC);
                    break;
                default:
                    throw new StreamException(ERR_STREAM_STATE_ERROR)
                            .param(ARG_DETAIL, "Unknown state type during restore: " + stateType);
            }
        }
    }

    /**
     * S-12 legacy fallback, single point (item 21 D-1): current snapshots write
     * {@code valueType}/{@code accumulatorType}/{@code mapKeyType}; legacy
     * snapshots recorded the {@code *TypeName} spellings. Both orders accepted
     * for every family — the pre-convergence drift (Reducing/Aggregating
     * missing the fallback) must never reappear.
     */
    private static String resolveTypeName(Map<String, Object> stateInfo, String primary, String fallback) {
        String name = (String) stateInfo.get(primary);
        if (name == null) {
            name = (String) stateInfo.get(fallback);
        }
        return name;
    }

    /**
     * R5 ST-02: parse the snapshot header keyType; "java.lang.Object" and a
     * missing header leave the decision to the declared type.
     */
    private static Class<?> resolveHeaderKeyType(Object headerType) {
        if (!(headerType instanceof String) || ((String) headerType).isEmpty()) {
            return null;
        }
        String name = (String) headerType;
        if (Object.class.getName().equals(name)) {
            return Object.class;
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            LOG.warn("nop.stream.restore-keytype-unknown: snapshot header keyType={} not loadable;"
                    + " falling back to the declared backend key type", name);
            return null;
        }
    }

    /**
     * R5 ST-02: an Object-effective key type means restore cannot re-materialize
     * keys; non-JDK-native stored keys would silently lose equals/hashCode
     * semantics. Warn once per restore — loud, but not a hard failure (raw
     * JSON-native keys still work).
     */
    private void warnForeignNativeKeys(Map<String, Object> statesMap) {
        for (Object stateObj : statesMap.values()) {
            if (!(stateObj instanceof Map)) {
                continue;
            }
            Object entriesObj = ((Map<?, ?>) stateObj).get("entries");
            if (!(entriesObj instanceof List) || ((List<?>) entriesObj).isEmpty()) {
                continue;
            }
            Object firstEntry = ((List<?>) entriesObj).get(0);
            if (firstEntry instanceof Map) {
                Object key = ((Map<?, ?>) firstEntry).get("key");
                if (key != null && !isJdkNativeKey(key.getClass())) {
                    LOG.warn("nop.stream.restore-object-keytype: snapshot keyType=java.lang.Object but stored"
                            + " keys are of class {} — key re-materialization is identity and non-native keys may"
                            + " not match after restore; declare the key class at the backend", key.getClass().getName());
                    return;
                }
            }
        }
    }

    private static boolean isJdkNativeKey(Class<?> clazz) {
        return clazz == String.class || clazz == Integer.class || clazz == Long.class
                || clazz == Double.class || clazz == Float.class || clazz == Boolean.class
                || clazz == Short.class || clazz == Byte.class;
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> loadClass(String typeName) throws Exception {
        ClassNameValidator.validateClassName(typeName);
        return (Class<Object>) Class.forName(typeName);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends SimpleAccumulator<Object>> loadAccumulatorClass(String typeName) throws Exception {
        ClassNameValidator.validateAccumulatorClass(typeName);
        return (Class<? extends SimpleAccumulator<Object>>) Class.forName(typeName);
    }

    /** Generic restore entry loop payload decoder. */
    private interface EntryDecoder {
        Object decode(Map<String, Object> entry) throws Exception;
    }

    private void restoreKeyedEntries(Map<String, Object> states, String stateName, Map<String, Object> stateInfo,
                                     Object stateObj, Map<TypedNamespaceAndKey, Object> storage,
                                     EntryDecoder decoder) throws Exception {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace"), stateName),
                        backend.routeKey(deserializeKey(e.get("key"))));
                storage.put(nk, decoder.decode(e));
            }
        }
        states.put(stateName, stateObj);
    }

    @SuppressWarnings("unchecked")
    private static Map<TypedNamespaceAndKey, Object> rawStorage(Map<?, ?> storage) {
        return (Map<TypedNamespaceAndKey, Object>) storage;
    }

    private void restoreValueState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        Class<Object> valueClass = loadClass(resolveTypeName(stateInfo, "valueTypeName", "valueType"));
        ValueStateDescriptor<Object> descriptor = new ValueStateDescriptor<>(stateName, valueClass);
        MemoryValueState<Object> state = new MemoryValueState<>(backend, descriptor);
        restoreKeyedEntries(states, stateName, stateInfo, state, state.storage,
                e -> deserializeValue(e.get("value"), valueClass, descriptor));
    }

    @SuppressWarnings("unchecked")
    private void restoreMapState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        final Class<Object> valueClass = loadClass(resolveTypeName(stateInfo, "valueTypeName", "valueType"));
        final Class<Object> mapKeyClass;
        String keyTypeName = resolveTypeName(stateInfo, "mapKeyTypeName", "mapKeyType");
        if (keyTypeName != null) {
            mapKeyClass = loadClass(keyTypeName);
        } else {
            mapKeyClass = null;
        }

        MapStateDescriptor<Object, Object> descriptor = new MapStateDescriptor<>(stateName, mapKeyClass, valueClass);
        MemoryMapState<Object, Object> state = new MemoryMapState<>(backend, descriptor);
        restoreKeyedEntries(states, stateName, stateInfo, state, rawStorage(state.storage), e -> {
            Map<Object, Object> mapValue = new LinkedHashMap<>();
            // Per-pair mapValue validation is the shared MapValuePairValidator
            // (item 24 parity guards converged across Memory/RocksDB serdes).
            MapValuePairValidator.forEachValidPair(e, stateName, (mapKeyObj, mapValueObj) -> {
                Object mk = mapKeyClass != null ? deserializeValue(mapKeyObj, mapKeyClass) : mapKeyObj;
                Object mv = deserializeValue(mapValueObj, valueClass);
                mapValue.put(mk, mv);
            });
            return mapValue;
        });
    }

    private void restoreAppendingState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        Class<Object> valueClass = loadClass(resolveTypeName(stateInfo, "valueTypeName", "valueType"));
        Class<? extends SimpleAccumulator<Object>> accumulatorClass =
                loadAccumulatorClass(resolveTypeName(stateInfo, "accumulatorTypeName", "accumulatorType"));

        ReducingStateDescriptor<Object> descriptor =
                new ReducingStateDescriptor<>(stateName, valueClass, accumulatorClass);
        MemoryInternalAppendingState<Object, Object, Object, Object> state =
                new MemoryInternalAppendingState<>(backend, descriptor);
        restoreKeyedEntries(states, stateName, stateInfo, state, rawStorage(state.storage), e -> {
            Object value = deserializeValue(e.get("value"), valueClass, descriptor);
            requireInstance(value, valueClass);
            return value;
        });
    }

    /** ListState / InternalListState share one restore (only the state class differs). */
    private void restoreListState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo,
                                  boolean internal) throws Exception {
        Class<Object> valueClass = loadClass(resolveTypeName(stateInfo, "valueTypeName", "valueType"));
        ListStateDescriptor<Object> descriptor = new ListStateDescriptor<>(stateName, valueClass);
        if (internal) {
            MemoryInternalListState<Object, Object, Object> state =
                    new MemoryInternalListState<>(backend, descriptor);
            restoreKeyedEntries(states, stateName, stateInfo, state, rawStorage(state.storage),
                    e -> decodeListPayload(e, valueClass, descriptor));
        } else {
            MemoryListState<Object> state = new MemoryListState<>(backend, descriptor);
            restoreKeyedEntries(states, stateName, stateInfo, state, rawStorage(state.storage),
                    e -> decodeListPayload(e, valueClass, descriptor));
        }
    }

    private static List<Object> decodeListPayload(Map<String, Object> e, Class<Object> valueClass,
                                                   ListStateDescriptor<Object> descriptor) throws Exception {
        List<Object> list = new ArrayList<>();
        List<Object> values = (List<Object>) e.get("listValue");
        if (values != null) {
            for (Object v : values) {
                list.add(deserializeValue(v, valueClass, descriptor));
            }
        }
        return list;
    }

    private void restoreReducingState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo) throws Exception {
        Class<Object> valueClass = loadClass(resolveTypeName(stateInfo, "valueTypeName", "valueType"));
        Class<? extends SimpleAccumulator<Object>> accumulatorClass =
                loadAccumulatorClass(resolveTypeName(stateInfo, "accumulatorTypeName", "accumulatorType"));

        ReducingStateDescriptor<Object> descriptor =
                new ReducingStateDescriptor<>(stateName, valueClass, accumulatorClass);
        MemoryReducingState<Object> state = new MemoryReducingState<>(backend, descriptor);
        restoreKeyedEntries(states, stateName, stateInfo, state, rawStorage(state.storage), e -> {
            Object value = deserializeValue(e.get("value"), valueClass, descriptor);
            requireInstance(value, valueClass);
            return wrapInAccumulator(value, accumulatorClass);
        });
    }

    /** AggregatingState / InternalAggregatingState share one restore (only the state class differs). */
    @SuppressWarnings("unchecked")
    private void restoreAggregatingState(Map<String, Object> states, String stateName, Map<String, Object> stateInfo,
                                         RestoredStateFlavor flavor) throws Exception {
        boolean internal = (flavor == RestoredStateFlavor.INTERNAL);
        Class<Object> recordedClass = loadClass(resolveTypeName(stateInfo, "valueTypeName", "valueType"));
        String aggregateFunctionTypeName = (String) stateInfo.get("aggregateFunctionType");
        AggregateFunction<Object, Object, Object> aggregateFunction =
                resolveAggregateFunction(stateName, aggregateFunctionTypeName);
        final Class<Object> valueClass =
                (Class<Object>) AccumulatorTypeInference.inferAccumulatorType(aggregateFunction, recordedClass);

        AggregatingStateDescriptor<Object, Object, Object> descriptor =
                new AggregatingStateDescriptor<>(stateName, aggregateFunction, valueClass);
        if (internal) {
            MemoryInternalAggregatingState<Object, Object, Object, Object, Object> state =
                    new MemoryInternalAggregatingState<>(backend, descriptor);
            restoreKeyedEntries(states, stateName, stateInfo, state, rawStorage(state.storage),
                    e -> deserializeValue(e.get("value"), valueClass, descriptor));
        } else {
            MemoryAggregatingState<Object, Object, Object> state =
                    new MemoryAggregatingState<>(backend, descriptor);
            restoreKeyedEntries(states, stateName, stateInfo, state, rawStorage(state.storage),
                    e -> deserializeValue(e.get("value"), valueClass, descriptor));
        }
    }

    private static void requireInstance(Object value, Class<Object> valueClass) {
        if (value != null && !valueClass.isInstance(value)) {
            throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                    .param(ARG_EXPECTED_TYPE, valueClass.getName())
                    .param(ARG_ACTUAL_TYPE, value.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> SimpleAccumulator<T> wrapInAccumulator(Object value, Class<? extends SimpleAccumulator<T>> accumulatorClass) {
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

    /**
     * Resolves the aggregate function used to rebuild aggregating state on
     * restore. Decision: prefer live-function reuse over reflection.
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
     * broken function (No-Silent-No-Op rule).
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
        if (namespace instanceof io.nop.stream.core.common.state.VoidNamespace) {
            return namespace;
        }
        // R5 ST-01: built-in namespaces are immutable or singleton and handled
        // above; unknown custom namespaces freeze like any other live value.
        return SnapshotCopyUtil.defensiveCopy(namespace);
    }

    /**
     * item 24 (W-4): TimeWindow namespace deserialization guards its fields —
     * a partially-written legacy entry previously surfaced as a bare NPE/CCE
     * from {@code ((Number) m.get("start")).longValue()} with no state context.
     * Bad data now fails fast as a typed {@link StreamException} carrying the
     * state name and the offending content.
     */
    @SuppressWarnings("unchecked")
    private Object deserializeNamespace(Object obj, String stateName) {
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
            Object type = m.get("@type");
            if ("TimeWindow".equals(type)) {
                Object start = m.get("start");
                Object end = m.get("end");
                if (!(start instanceof Number) || !(end instanceof Number)) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR)
                            .param(ARG_STATE_NAME, stateName)
                            .param(ARG_DETAIL, "TimeWindow namespace of state '" + stateName
                                    + "' has non-numeric start/end fields (start=" + start + ", end=" + end
                                    + "); snapshot is corrupt or foreign");
                }
                return new TimeWindow(((Number) start).longValue(), ((Number) end).longValue());
            }
        }
        return obj;
    }

    private Object serializeKey(Object key) {
        // R5 ST-01: keys share the live graph with storage; a mutable POJO key
        // mutated after snapshotState must not leak into the persisted snapshot.
        return SnapshotCopyUtil.defensiveCopy(key);
    }

    /**
     * AR-01 (P0): re-materializes a restored state key to the backend's
     * declared {@link #keyType}. ST-02: the implementation moved to the shared
     * static {@link StateKeyRematerializer#rematerializeKey} so the RocksDB
     * restore path and the rescale/reshard filters reuse the exact same
     * transformation.
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
        return StateKeyRematerializer.rematerializeKey(obj, effectiveKeyType);
    }

    @SuppressWarnings("unchecked")
    private static <T> IStreamSerializer<T> getSerializerIfAvailable(StateDescriptor<?> descriptor) {
        if (descriptor == null) return null;
        TypeSerializer<?> ser = descriptor.getSerializer();
        if (ser instanceof IStreamSerializer && !(ser instanceof JsonToolSerializer)) {
            return (IStreamSerializer<T>) ser;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> Object serializeWithSerializer(Object value, IStreamSerializer<T> serializer) {
        if (value == null) {
            return null;
        }
        if (serializer == null) {
            // R5 ST-01: raw-value writers freeze via freezeRawValue instead.
            // This method is also the per-element path for list/map payloads,
            // where ContainerValueCodec.encode needs the live runtime type to
            // record element type info — copying here would erase it.
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
    private static <T> T deserializeValue(Object obj, Class<T> type) {
        return deserializeValue(obj, type, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserializeValue(Object obj, Class<T> type, StateDescriptor<?> descriptor) {
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
        // info in the snapshot (wrapped by the MapState snapshot path); decode
        // re-materializes inner elements. Unwrapped legacy containers degrade with a
        // LOG.warn instead of silently returning JSON-native elements
        // (No-Silent-No-Op rule #24).
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
