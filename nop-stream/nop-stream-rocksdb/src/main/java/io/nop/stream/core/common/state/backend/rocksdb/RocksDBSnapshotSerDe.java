/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateSchemaResolver;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.util.ClassNameValidator;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksIterator;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * Snapshot/restore for {@link RocksDBKeyedStateBackend}.
 *
 * <p>Produces a {@link StateSnapshot} byte-compatible with
 * {@code MemoryStateSerDe}: the same 8 {@code stateType} branches, per-type
 * info-map keys, and entry discriminators. Snapshot persists raw user keys;
 * restore re-routes via the backend's shard logic.
 */
final class RocksDBSnapshotSerDe {

    private RocksDBSnapshotSerDe() {
    }

    // ------------------------------------------------------------------------
    //  snapshot
    // ------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static StateSnapshot snapshotState(RocksDBKeyedStateBackend<?> backend) throws Exception {
        Map<String, Object> states = backend.getStates();
        if (states.isEmpty()) {
            return null;
        }

        Map<String, Object> stateData = new LinkedHashMap<>();
        stateData.put("keyType", backend.getKeyType().getName());

        Map<String, Object> statesMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : states.entrySet()) {
            String stateName = entry.getKey();
            Object stateObj = entry.getValue();

            if (stateObj instanceof RocksDBValueState) {
                statesMap.put(stateName, snapshotValueState(backend, (RocksDBValueState<?>) stateObj));
            } else if (stateObj instanceof RocksDBMapState) {
                statesMap.put(stateName, snapshotMapState(backend, (RocksDBMapState<?, ?>) stateObj));
            } else if (stateObj instanceof RocksDBListState) {
                statesMap.put(stateName, snapshotListState(backend, (RocksDBListState<?>) stateObj, "ListState",
                        StateSchemaResolver.STATE_TYPE_LIST));
            } else if (stateObj instanceof RocksDBInternalListState) {
                statesMap.put(stateName, snapshotInternalListState(backend,
                        (RocksDBInternalListState<?, ?, ?>) stateObj));
            } else if (stateObj instanceof RocksDBInternalAppendingState) {
                statesMap.put(stateName, snapshotAppendingState(backend,
                        (RocksDBInternalAppendingState<?, ?, ?>) stateObj));
            } else if (stateObj instanceof RocksDBInternalAggregatingState) {
                statesMap.put(stateName, snapshotInternalAggregatingState(backend,
                        (RocksDBInternalAggregatingState<?, ?, ?, ?, ?>) stateObj));
            } else if (stateObj instanceof RocksDBReducingState) {
                statesMap.put(stateName, snapshotReducingState(backend, (RocksDBReducingState<?>) stateObj));
            } else if (stateObj instanceof RocksDBAggregatingState) {
                statesMap.put(stateName, snapshotAggregatingState(backend,
                        (RocksDBAggregatingState<?, ?, ?>) stateObj));
            } else {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_DETAIL, "Unknown state type during snapshot: " + stateObj.getClass().getName());
            }
        }
        stateData.put("states", statesMap);

        return new StateSnapshot(stateData);
    }

    private static void embedSchemaFingerprint(Map<String, Object> info, String stateType,
                                               StateDescriptor<?> descriptor, int shardCount) {
        SerializerFingerprint fingerprint = StateSchemaResolver.fromDescriptor(stateType, descriptor);
        info.put("schemaChecksum", fingerprint.getSchemaChecksum());
        info.put("schemaVersion", fingerprint.getSchemaVersion());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }
    }

    private static Map<String, Object> snapshotValueState(RocksDBKeyedStateBackend<?> backend,
                                                          RocksDBValueState<?> state) throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        info.put("valueType", state.descriptor.getValueType().getName());
        embedSchemaFingerprint(info, StateSchemaResolver.STATE_TYPE_VALUE, state.descriptor, backend.getShardCount());

        List<Map<String, Object>> entries = new ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(state.cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
                entry.put("key", dk.rawKey);
                Object value = RocksDBValueSerDe.deserialize(it.value(), state.descriptor.getValueType());
                entry.put("value", value);
                entries.add(entry);
            }
        }
        info.put("entries", entries);
        return info;
    }

    private static Map<String, Object> snapshotMapState(RocksDBKeyedStateBackend<?> backend,
                                                       RocksDBMapState<?, ?> state) throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "MapState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("mapKeyType", state.descriptor.getKeyClass().getName());
        embedSchemaFingerprint(info, StateSchemaResolver.STATE_TYPE_MAP, state.descriptor, backend.getShardCount());

        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        Map<String, List<List<Object>>> groupedMapValues = new LinkedHashMap<>();

        try (RocksIterator it = backend.getDb().newIterator(state.cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                byte[] fullKey = it.key();
                int baseLen = RocksDBKeyEncoder.baseKeyLength(fullKey);
                RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(fullKey, backend.getKeyType());
                String groupKey = dk.namespace + "|" + dk.rawKey;
                Map<String, Object> entry = grouped.get(groupKey);
                if (entry == null) {
                    entry = new LinkedHashMap<>();
                    entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
                    entry.put("key", dk.rawKey);
                    grouped.put(groupKey, entry);
                    groupedMapValues.put(groupKey, new ArrayList<>());
                }
                Object mapKey = extractMapKey(fullKey, baseLen, state.descriptor.getKeyClass());
                Object mapValue = RocksDBValueSerDe.deserialize(it.value(), state.descriptor.getValueType());
                List<Object> pair = new ArrayList<>();
                pair.add(mapKey);
                pair.add(mapValue);
                groupedMapValues.get(groupKey).add(pair);
            }
        }
        for (Map.Entry<String, Map<String, Object>> e : grouped.entrySet()) {
            e.getValue().put("mapValue", groupedMapValues.get(e.getKey()));
            entries.add(e.getValue());
        }
        info.put("entries", entries);
        return info;
    }

    @SuppressWarnings("unchecked")
    private static Object extractMapKey(byte[] fullKey, int baseLen, Class<?> mapKeyClass) {
        int mapKeyLen = ((fullKey[baseLen] & 0xFF) << 24)
                | ((fullKey[baseLen + 1] & 0xFF) << 16)
                | ((fullKey[baseLen + 2] & 0xFF) << 8)
                | (fullKey[baseLen + 3] & 0xFF);
        byte[] mapKeyBytes = new byte[mapKeyLen];
        System.arraycopy(fullKey, baseLen + 4, mapKeyBytes, 0, mapKeyLen);
        if (mapKeyBytes.length == 0) {
            return null;
        }
        String json = new java.lang.String(mapKeyBytes, java.nio.charset.StandardCharsets.UTF_8);
        if (mapKeyClass != null && mapKeyClass != Object.class) {
            return io.nop.core.lang.json.JsonTool.parseBeanFromText(json, mapKeyClass);
        }
        return io.nop.core.lang.json.JsonTool.parseNonStrict(json);
    }

    private static Map<String, Object> snapshotListState(RocksDBKeyedStateBackend<?> backend,
                                                         RocksDBListState<?> state, String stateTypeName,
                                                         String schemaStateType) throws Exception {
        ListStateDescriptor<?> descriptor = state.descriptor;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", stateTypeName);
        info.put("valueType", descriptor.getValueType().getName());
        embedSchemaFingerprint(info, schemaStateType, descriptor, backend.getShardCount());

        List<Map<String, Object>> entries = new ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(state.cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
                entry.put("key", dk.rawKey);
                List<Object> list = RocksDBValueSerDe.deserializeList(it.value(), descriptor.getValueType());
                entry.put("listValue", list);
                entries.add(entry);
            }
        }
        info.put("entries", entries);
        return info;
    }

    private static Map<String, Object> snapshotInternalListState(RocksDBKeyedStateBackend<?> backend,
                                                                 RocksDBInternalListState<?, ?, ?> state) throws Exception {
        ListStateDescriptor<?> descriptor = state.descriptor;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "InternalListState");
        info.put("valueType", descriptor.getValueType().getName());
        embedSchemaFingerprint(info, StateSchemaResolver.STATE_TYPE_INTERNAL_LIST, descriptor, backend.getShardCount());

        List<Map<String, Object>> entries = new ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(state.cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
                entry.put("key", dk.rawKey);
                List<Object> list = RocksDBValueSerDe.deserializeList(it.value(), descriptor.getValueType());
                entry.put("listValue", list);
                entries.add(entry);
            }
        }
        info.put("entries", entries);
        return info;
    }

    private static Map<String, Object> snapshotAppendingState(RocksDBKeyedStateBackend<?> backend,
                                                              RocksDBInternalAppendingState<?, ?, ?> state) throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "AppendingState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("accumulatorType", state.descriptor.getAccumulatorType().getName());
        embedSchemaFingerprint(info, StateSchemaResolver.STATE_TYPE_APPENDING, state.descriptor, backend.getShardCount());

        List<Map<String, Object>> entries = new ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(state.cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
                entry.put("key", dk.rawKey);
                Object value = RocksDBValueSerDe.deserialize(it.value(), state.descriptor.getValueType());
                if (value instanceof List) {
                    entry.put("value", new ArrayList<>((List<?>) value));
                } else {
                    entry.put("value", value);
                }
                entries.add(entry);
            }
        }
        info.put("entries", entries);
        return info;
    }

    private static Map<String, Object> snapshotReducingState(RocksDBKeyedStateBackend<?> backend,
                                                             RocksDBReducingState<?> state) throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ReducingState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("accumulatorType", state.descriptor.getAccumulatorType().getName());
        embedSchemaFingerprint(info, StateSchemaResolver.STATE_TYPE_REDUCING, state.descriptor, backend.getShardCount());

        List<Map<String, Object>> entries = new ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(state.cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
                entry.put("key", dk.rawKey);
                Object value = RocksDBValueSerDe.deserialize(it.value(), state.descriptor.getValueType());
                entry.put("value", value);
                entries.add(entry);
            }
        }
        info.put("entries", entries);
        return info;
    }

    private static Map<String, Object> snapshotAggregatingState(RocksDBKeyedStateBackend<?> backend,
                                                               RocksDBAggregatingState<?, ?, ?> state) throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "AggregatingState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("aggregateFunctionType", state.descriptor.getAggregateFunction().getClass().getName());
        embedSchemaFingerprint(info, StateSchemaResolver.STATE_TYPE_AGGREGATING, state.descriptor, backend.getShardCount());

        List<Map<String, Object>> entries = new ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(state.cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
                entry.put("key", dk.rawKey);
                Object value = RocksDBValueSerDe.deserialize(it.value(), state.descriptor.getValueType());
                entry.put("value", value);
                entries.add(entry);
            }
        }
        info.put("entries", entries);
        return info;
    }

    private static Map<String, Object> snapshotInternalAggregatingState(RocksDBKeyedStateBackend<?> backend,
                                                                       RocksDBInternalAggregatingState<?, ?, ?, ?, ?> state) throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "InternalAggregatingState");
        info.put("valueType", state.descriptor.getValueType().getName());
        info.put("aggregateFunctionType", state.descriptor.getAggregateFunction().getClass().getName());
        embedSchemaFingerprint(info, StateSchemaResolver.STATE_TYPE_INTERNAL_AGGREGATING, state.descriptor, backend.getShardCount());

        List<Map<String, Object>> entries = new ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(state.cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
                entry.put("key", dk.rawKey);
                Object value = RocksDBValueSerDe.deserialize(it.value(), state.descriptor.getValueType());
                entry.put("value", value);
                entries.add(entry);
            }
        }
        info.put("entries", entries);
        return info;
    }

    // ------------------------------------------------------------------------
    //  restore
    // ------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static void restoreState(RocksDBKeyedStateBackend<?> backend, StateSnapshot snapshot) throws Exception {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        Map<String, Object> stateData = snapshot.getStateData();
        Map<String, Object> statesMap = (Map<String, Object>) stateData.get("states");
        if (statesMap == null || statesMap.isEmpty()) {
            return;
        }

        clearAllStates(backend);
        backend.getStates().clear();

        for (Map.Entry<String, Object> entry : statesMap.entrySet()) {
            String stateName = entry.getKey();
            Map<String, Object> stateInfo = (Map<String, Object>) entry.getValue();
            String stateType = (String) stateInfo.get("stateType");

            switch (stateType) {
                case "ValueState":
                    restoreValueState(backend, stateName, stateInfo);
                    break;
                case "MapState":
                    restoreMapState(backend, stateName, stateInfo);
                    break;
                case "AppendingState":
                    restoreAppendingState(backend, stateName, stateInfo);
                    break;
                case "ListState":
                    restoreListState(backend, stateName, stateInfo);
                    break;
                case "InternalListState":
                    restoreInternalListState(backend, stateName, stateInfo);
                    break;
                case "ReducingState":
                    restoreReducingState(backend, stateName, stateInfo);
                    break;
                case "AggregatingState":
                    restoreAggregatingState(backend, stateName, stateInfo);
                    break;
                case "InternalAggregatingState":
                    restoreInternalAggregatingState(backend, stateName, stateInfo);
                    break;
                default:
                    throw new StreamException(ERR_STREAM_STATE_ERROR)
                            .param(ARG_DETAIL, "Unknown state type during restore: " + stateType);
            }
        }
    }

    private static void clearAllStates(RocksDBKeyedStateBackend<?> backend) throws Exception {
        for (ColumnFamilyHandle cf : backend.getCfHandles().values()) {
            try (RocksIterator it = backend.getDb().newIterator(cf)) {
                List<byte[]> keys = new ArrayList<>();
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    keys.add(it.key());
                }
                for (byte[] key : keys) {
                    backend.getDb().delete(cf, key);
                }
            }
        }
    }

    private static void putEntry(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cf,
                                 Object namespace, Object rawKey, byte[] valueBytes) {
        int shardId = backend.computeShardId(rawKey);
        byte[] key = RocksDBKeyEncoder.encode(
                RocksDBKeyEncoder.deserializeNamespace(namespace), rawKey, shardId);
        try {
            backend.getDb().put(cf, key, valueBytes);
        } catch (Exception e) {
            throw new StreamException("Failed to restore entry", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreValueState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                          Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = resolveTypeName(stateInfo, "valueTypeName", "valueType");
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);

        ValueStateDescriptor<Object> descriptor = new ValueStateDescriptor<>(stateName, valueClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBValueState<Object> state = new RocksDBValueState<>(backend, cf, descriptor);
        backend.getStates().put(stateName, state);
        backend.putRestoredDescriptor(stateName, descriptor);
        backend.getStateTypes().put(stateName, ValueState.class);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                Object value = RocksDBValueSerDe.deserializeObject(e.get("value"), valueClass);
                putEntry(backend, cf, e.get("namespace"), e.get("key"), RocksDBValueSerDe.serialize(value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreMapState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                        Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = resolveTypeName(stateInfo, "valueTypeName", "valueType");
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String keyTypeName = resolveTypeName(stateInfo, "mapKeyTypeName", "mapKeyType");
        Class<Object> mapKeyClass = null;
        if (keyTypeName != null) {
            ClassNameValidator.validateClassName(keyTypeName);
            mapKeyClass = (Class<Object>) Class.forName(keyTypeName);
        }

        MapStateDescriptor<Object, Object> descriptor = new MapStateDescriptor<>(stateName, mapKeyClass, valueClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBMapState<Object, Object> state = new RocksDBMapState<>(backend, cf, descriptor);
        backend.getStates().put(stateName, state);
        backend.putRestoredDescriptor(stateName, descriptor);
        backend.getStateTypes().put(stateName, MapState.class);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                Object namespace = RocksDBKeyEncoder.deserializeNamespace(e.get("namespace"));
                Object rawKey = e.get("key");
                int shardId = backend.computeShardId(rawKey);
                byte[] baseKey = RocksDBKeyEncoder.encode(namespace, rawKey, shardId);
                List<List<Object>> mapEntries = (List<List<Object>>) e.get("mapValue");
                if (mapEntries != null) {
                    for (List<Object> me : mapEntries) {
                        Object mk = me.get(0);
                        Object mv = RocksDBValueSerDe.deserializeObject(me.get(1), valueClass);
                        byte[] fullKey = appendMapKey(baseKey, mk);
                        backend.getDb().put(cf, fullKey, RocksDBValueSerDe.serialize(mv));
                    }
                }
            }
        }
    }

    private static byte[] appendMapKey(byte[] baseKey, Object mapKey) {
        byte[] mapKeyBytes = mapKey != null
                ? io.nop.core.lang.json.JsonTool.serialize(mapKey, false).getBytes(java.nio.charset.StandardCharsets.UTF_8)
                : RocksDBKeyEncoder.EMPTY_BYTES;
        byte[] result = new byte[baseKey.length + 4 + mapKeyBytes.length];
        System.arraycopy(baseKey, 0, result, 0, baseKey.length);
        int off = baseKey.length;
        result[off] = (byte) ((mapKeyBytes.length >>> 24) & 0xFF);
        result[off + 1] = (byte) ((mapKeyBytes.length >>> 16) & 0xFF);
        result[off + 2] = (byte) ((mapKeyBytes.length >>> 8) & 0xFF);
        result[off + 3] = (byte) (mapKeyBytes.length & 0xFF);
        System.arraycopy(mapKeyBytes, 0, result, off + 4, mapKeyBytes.length);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void restoreAppendingState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                              Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = resolveTypeName(stateInfo, "valueTypeName", "valueType");
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String accumulatorTypeName = resolveTypeName(stateInfo, "accumulatorTypeName", "accumulatorType");
        ClassNameValidator.validateAccumulatorClass(accumulatorTypeName);
        Class<? extends SimpleAccumulator<Object>> accumulatorClass =
                (Class<? extends SimpleAccumulator<Object>>) Class.forName(accumulatorTypeName);

        ReducingStateDescriptor<Object> descriptor =
                new ReducingStateDescriptor<>(stateName, valueClass, accumulatorClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBInternalAppendingState<Object, Object, Object> state =
                new RocksDBInternalAppendingState<>((RocksDBKeyedStateBackend<Object>) backend, cf, descriptor);
        backend.getStates().put(stateName, state);
        backend.putRestoredDescriptor(stateName, descriptor);
        backend.getStateTypes().put(stateName, InternalAppendingState.class);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                Object value = RocksDBValueSerDe.deserializeObject(e.get("value"), valueClass);
                putEntry(backend, cf, e.get("namespace"), e.get("key"), RocksDBValueSerDe.serialize(value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreListState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                         Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = resolveTypeName(stateInfo, "valueTypeName", "valueType");
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);

        ListStateDescriptor<Object> descriptor = new ListStateDescriptor<>(stateName, valueClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBListState<Object> state = new RocksDBListState<>(backend, cf, descriptor);
        backend.getStates().put(stateName, state);
        backend.putRestoredDescriptor(stateName, descriptor);
        backend.getStateTypes().put(stateName, ListState.class);

        restoreListEntries(backend, cf, stateInfo, valueClass);
    }

    @SuppressWarnings("unchecked")
    private static void restoreInternalListState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                                 Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = resolveTypeName(stateInfo, "valueTypeName", "valueType");
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);

        ListStateDescriptor<Object> descriptor = new ListStateDescriptor<>(stateName, valueClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBInternalListState<Object, Object, Object> state =
                new RocksDBInternalListState<>((RocksDBKeyedStateBackend<Object>) backend, cf, descriptor);
        backend.getStates().put(stateName, state);
        backend.putRestoredDescriptor(stateName, descriptor);
        backend.getStateTypes().put(stateName, InternalListState.class);

        restoreListEntries(backend, cf, stateInfo, valueClass);
    }

    @SuppressWarnings("unchecked")
    private static void restoreListEntries(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cf,
                                           Map<String, Object> stateInfo, Class<?> valueClass) {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                List<Object> values = (List<Object>) e.get("listValue");
                List<Object> list = new ArrayList<>();
                if (values != null) {
                    for (Object v : values) {
                        list.add(RocksDBValueSerDe.deserializeObject(v, valueClass));
                    }
                }
                putEntry(backend, cf, e.get("namespace"), e.get("key"), RocksDBValueSerDe.serialize(list));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreReducingState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                             Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueType");
        ClassNameValidator.validateClassName(valueTypeName);
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String accumulatorTypeName = (String) stateInfo.get("accumulatorType");
        ClassNameValidator.validateAccumulatorClass(accumulatorTypeName);
        Class<? extends SimpleAccumulator<Object>> accumulatorClass =
                (Class<? extends SimpleAccumulator<Object>>) Class.forName(accumulatorTypeName);

        ReducingStateDescriptor<Object> descriptor =
                new ReducingStateDescriptor<>(stateName, valueClass, accumulatorClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBReducingState<Object> state = new RocksDBReducingState<>(backend, cf, descriptor);
        backend.getStates().put(stateName, state);
        backend.putRestoredDescriptor(stateName, descriptor);
        backend.getStateTypes().put(stateName, ReducingState.class);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                Object value = RocksDBValueSerDe.deserializeObject(e.get("value"), valueClass);
                putEntry(backend, cf, e.get("namespace"), e.get("key"), RocksDBValueSerDe.serialize(value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreAggregatingState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                                Map<String, Object> stateInfo) throws Exception {
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
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBAggregatingState<Object, Object, Object> state = new RocksDBAggregatingState<>(backend, cf, descriptor);
        backend.getStates().put(stateName, state);
        backend.putRestoredDescriptor(stateName, descriptor);
        backend.getStateTypes().put(stateName, AggregatingState.class);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                Object value = RocksDBValueSerDe.deserializeObject(e.get("value"), valueClass);
                putEntry(backend, cf, e.get("namespace"), e.get("key"), RocksDBValueSerDe.serialize(value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreInternalAggregatingState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                                       Map<String, Object> stateInfo) throws Exception {
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
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBInternalAggregatingState<Object, Object, Object, Object, Object> state =
                new RocksDBInternalAggregatingState<>((RocksDBKeyedStateBackend<Object>) backend, cf, descriptor);
        backend.getStates().put(stateName, state);
        backend.putRestoredDescriptor(stateName, descriptor);
        backend.getStateTypes().put(stateName, InternalAppendingState.class);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                Object value = RocksDBValueSerDe.deserializeObject(e.get("value"), valueClass);
                putEntry(backend, cf, e.get("namespace"), e.get("key"), RocksDBValueSerDe.serialize(value));
            }
        }
    }

    private static String resolveTypeName(Map<String, Object> stateInfo, String primary, String fallback) {
        String name = (String) stateInfo.get(primary);
        if (name == null) {
            name = (String) stateInfo.get(fallback);
        }
        return name;
    }
}
