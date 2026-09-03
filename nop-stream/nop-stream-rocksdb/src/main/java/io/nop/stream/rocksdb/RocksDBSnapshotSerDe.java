/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.nio.ByteBuffer;
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
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.state.backend.ContainerValueCodec;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.common.state.shard.KeyGroupRangeRestoreFilter;
import io.nop.stream.core.util.ClassNameValidator;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksIterator;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_NAME;
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

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RocksDBSnapshotSerDe.class);

    private RocksDBSnapshotSerDe() {
    }

    /**
     * Returns {@code true} if the base composite key of {@code fullKey} is marked expired
     * by {@code ttl}. For non-map states the base key is the full key; for map state the
     * base key is the prefix shared by all entries of one map, so the whole map is skipped.
     * When {@code ttl} is {@code null} (TTL disabled) nothing is skipped.
     */
    private static boolean expiredForSnapshot(TtlContext<ByteBuffer> ttl, byte[] fullKey) {
        if (ttl == null) {
            return false;
        }
        int baseLen = RocksDBKeyEncoder.baseKeyLength(fullKey);
        return ttl.isExpiredForSnapshot(ByteBuffer.wrap(fullKey, 0, baseLen));
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
        // Stage 34: stamp the binary key-layout version for observability and
        // so the restore path can detect (and reject) incompatible legacy
        // RocksDB snapshots. The full snapshot stores raw user keys, so an
        // absent version (cross-backend Memory snapshot) remains restorable.
        stateData.put(RocksDBKeyEncoder.KEY_LAYOUT_VERSION_FIELD, RocksDBKeyEncoder.KEY_LAYOUT_VERSION);

        Map<String, Object> statesMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : states.entrySet()) {
            String stateName = entry.getKey();
            Object stateObj = entry.getValue();
            statesMap.put(stateName, snapshotOneState(backend, stateName, stateObj));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> snapshotOneState(RocksDBKeyedStateBackend<?> backend,
                                                        String stateName, Object stateObj) throws Exception {
        // internal subclasses must be dispatched before their public supertypes
        if (stateObj instanceof RocksDBInternalListState) {
            RocksDBInternalListState<?, ?, ?> st = (RocksDBInternalListState<?, ?, ?>) stateObj;
            return snapshotKeyedState(backend, st, st.cfHandle, "InternalListState", st.descriptor, null,
                    RocksDBSnapshotSerDe::writeListPayload);
        }
        if (stateObj instanceof RocksDBListState) {
            RocksDBListState<?> st = (RocksDBListState<?>) stateObj;
            return snapshotKeyedState(backend, st, st.cfHandle, "ListState", st.descriptor, null,
                    RocksDBSnapshotSerDe::writeListPayload);
        }
        if (stateObj instanceof RocksDBInternalAggregatingState) {
            RocksDBInternalAggregatingState<?, ?, ?, ?, ?> st =
                    (RocksDBInternalAggregatingState<?, ?, ?, ?, ?>) stateObj;
            Map<String, Object> headers = new LinkedHashMap<>();
            headers.put("aggregateFunctionType", st.descriptor.getAggregateFunction().getClass().getName());
            return snapshotKeyedState(backend, st, st.cfHandle, "InternalAggregatingState", st.descriptor, headers,
                    RocksDBSnapshotSerDe::writeValuePayload);
        }
        if (stateObj instanceof RocksDBAggregatingState) {
            RocksDBAggregatingState<?, ?, ?> st = (RocksDBAggregatingState<?, ?, ?>) stateObj;
            Map<String, Object> headers = new LinkedHashMap<>();
            headers.put("aggregateFunctionType", st.descriptor.getAggregateFunction().getClass().getName());
            return snapshotKeyedState(backend, st, st.cfHandle, "AggregatingState", st.descriptor, headers,
                    RocksDBSnapshotSerDe::writeValuePayload);
        }
        if (stateObj instanceof RocksDBValueState) {
            RocksDBValueState<?> st = (RocksDBValueState<?>) stateObj;
            return snapshotKeyedState(backend, st, st.cfHandle, "ValueState", st.descriptor, null,
                    RocksDBSnapshotSerDe::writeValuePayload);
        }
        if (stateObj instanceof RocksDBMapState) {
            return snapshotMapState(backend, (RocksDBMapState<?, ?>) stateObj);
        }
        if (stateObj instanceof RocksDBInternalAppendingState) {
            RocksDBInternalAppendingState<?, ?, ?> st = (RocksDBInternalAppendingState<?, ?, ?>) stateObj;
            Map<String, Object> headers = new LinkedHashMap<>();
            headers.put("accumulatorType", st.descriptor.getAccumulatorType().getName());
            return snapshotKeyedState(backend, st, st.cfHandle, "AppendingState", st.descriptor, headers,
                    RocksDBSnapshotSerDe::writeAppendingPayload);
        }
        if (stateObj instanceof RocksDBReducingState) {
            RocksDBReducingState<?> st = (RocksDBReducingState<?>) stateObj;
            Map<String, Object> headers = new LinkedHashMap<>();
            headers.put("accumulatorType", st.descriptor.getAccumulatorType().getName());
            return snapshotKeyedState(backend, st, st.cfHandle, "ReducingState", st.descriptor, headers,
                    RocksDBSnapshotSerDe::writeValuePayload);
        }
        throw new StreamException(ERR_STREAM_STATE_ERROR)
                .param(ARG_DETAIL, "Unknown state type during snapshot: " + stateObj.getClass().getName());
    }

    /**
     * MapState snapshot: groups the RocksDB rows of one user map into a single
     * snapshot entry (map-key suffix stripped, pairs collected into mapValue).
     */
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

        TtlContext<ByteBuffer> ttl = state.ttlContext();
        try (RocksIterator it = backend.getDb().newIterator(state.cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                byte[] fullKey = it.key();
                if (expiredForSnapshot(ttl, fullKey)) {
                    continue;
                }
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
                // P1-21-01: container values are stored as the element-type wrapper
                // (see ContainerValueCodec); parse them JSON-natively so the snapshot
                // carries the wrapper (same format as the Memory backend after the
                // storage-layer JSON round trip). Non-container values keep the plain
                // deserialize path.
                Object mapValue;
                Class<?> valueType = state.descriptor.getValueType();
                if (ContainerValueCodec.isContainerType(valueType)) {
                    mapValue = io.nop.core.lang.json.JsonTool.parseNonStrict(
                            new String(it.value(), java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    mapValue = RocksDBValueSerDe.deserialize(it.value(), valueType);
                }
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

    /** Writes the per-entry payload (the "value"/"listValue"/"mapValue" field). */
    @FunctionalInterface
    interface EntryWriter {
        void write(Map<String, Object> entry, RocksDBKeyEncoder.DecodedKey dk, byte[] fullKey,
                   byte[] valueBytes, StateDescriptor<?> descriptor) throws Exception;
    }

    /**
     * item 30 convergence: generic per-state snapshot (single point, mirrors the
     * core-side MemoryStateSerDe shape): fixed header order, TTL-filtered
     * RocksIterator loop, per-family payload writer.
     */
    private static Map<String, Object> snapshotKeyedState(RocksDBKeyedStateBackend<?> backend,
                                                          AbstractRocksDBState state,
                                                          ColumnFamilyHandle cfHandle,
                                                          String stateType,
                                                          StateDescriptor<?> descriptor,
                                                          Map<String, Object> extraHeaders,
                                                          EntryWriter entryWriter) throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", stateType);
        info.put("valueType", descriptor.getValueType().getName());
        if (extraHeaders != null) {
            info.putAll(extraHeaders);
        }
        embedSchemaFingerprint(info, stateType, descriptor, backend.getShardCount());

        List<Map<String, Object>> entries = new ArrayList<>();
        TtlContext<ByteBuffer> ttl = state.ttlContext();
        try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                if (expiredForSnapshot(ttl, it.key())) {
                    continue;
                }
                RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
                entry.put("key", dk.rawKey);
                entryWriter.write(entry, dk, it.key(), it.value(), descriptor);
                entries.add(entry);
            }
        }
        info.put("entries", entries);
        return info;
    }

    private static void writeValuePayload(Map<String, Object> entry, RocksDBKeyEncoder.DecodedKey dk,
                                          byte[] fullKey, byte[] valueBytes, StateDescriptor<?> descriptor) throws Exception {
        Object value = RocksDBValueSerDe.deserialize(valueBytes, descriptor.getValueType());
        entry.put("value", value);
    }

    @SuppressWarnings("unchecked")
    private static void writeListPayload(Map<String, Object> entry, RocksDBKeyEncoder.DecodedKey dk,
                                         byte[] fullKey, byte[] valueBytes, StateDescriptor<?> descriptor) throws Exception {
        List<Object> list = RocksDBValueSerDe.deserializeList(valueBytes, descriptor.getValueType());
        entry.put("listValue", list);
    }

    private static void writeAppendingPayload(Map<String, Object> entry, RocksDBKeyEncoder.DecodedKey dk,
                                              byte[] fullKey, byte[] valueBytes, StateDescriptor<?> descriptor) throws Exception {
        Object value = RocksDBValueSerDe.deserialize(valueBytes, descriptor.getValueType());
        if (value instanceof List) {
            entry.put("value", new ArrayList<>((List<?>) value));
        } else {
            entry.put("value", value);
        }
    }

    // ------------------------------------------------------------------------
    //  restore
    // ------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static void restoreState(RocksDBKeyedStateBackend<?> backend, StateSnapshot snapshot) throws Exception {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        // Stage 34: reject snapshots that carry an incompatible RocksDB binary
        // key layout. An absent version field is tolerated on the full path
        // (raw user keys are layout-agnostic, e.g. cross-backend Memory snapshots).
        RocksDBKeyEncoder.verifyKeyLayoutVersion(snapshot.getStateData(), false);

        Map<String, Object> stateData = snapshot.getStateData();
        Map<String, Object> statesMap = (Map<String, Object>) stateData.get("states");
        if (statesMap == null || statesMap.isEmpty()) {
            return;
        }

        clearAllStates(backend);
        backend.getStates().clear();

        // Stage 35: per-subtask partial restore. When the backend carries a
        // target KeyGroupRange, reduce each state's entries to those whose key
        // is owned by the range before writing them to RocksDB. This is the
        // in-memory entry-filter counterpart of the incremental SST range scan.
        KeyGroupRange range = backend.getTargetKeyGroupRange();
        Map<String, Object> effectiveStatesMap = statesMap;
        if (range != null) {
            effectiveStatesMap = KeyGroupRangeRestoreFilter.filterKeyedStates(
                    statesMap, range, backend.getMaxParallelism());
        }

        for (Map.Entry<String, Object> entry : effectiveStatesMap.entrySet()) {
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
                    restoreListState(backend, stateName, stateInfo, false);
                    break;
                case "InternalListState":
                    restoreListState(backend, stateName, stateInfo, true);
                    break;
                case "ReducingState":
                    restoreReducingState(backend, stateName, stateInfo);
                    break;
                case "AggregatingState":
                    restoreAggregatingState(backend, stateName, stateInfo, false);
                    break;
                case "InternalAggregatingState":
                    restoreAggregatingState(backend, stateName, stateInfo, true);
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
        int keyGroupId = backend.computeKeyGroupId(rawKey);
        byte[] key = RocksDBKeyEncoder.encode(
                RocksDBKeyEncoder.deserializeNamespace(namespace), rawKey, keyGroupId);
        try {
            backend.getDb().put(cf, key, valueBytes);
        } catch (Exception e) {
            throw new StreamException("Failed to restore entry", e);
        }
    }

    /**
     * item 30 convergence: registers a restored state on the backend (states
     * map + restored-descriptor registry + state-type registry) — previously
     * a verbatim triple in all eight restore branches.
     */
    private static void registerRestoredState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                               StateDescriptor<?> descriptor, Object state, Class<?> publicType) {
        backend.getStates().put(stateName, state);
        backend.putRestoredDescriptor(stateName, descriptor);
        backend.getStateTypes().put(stateName, publicType);
    }

    /**
     * item 30 convergence: the scalar-value restore entry loop (Value /
     * Appending / Reducing / Aggregating / InternalAggregating all share it).
     */
    @SuppressWarnings("unchecked")
    private static void restoreValueEntries(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cf,
                                            Map<String, Object> stateInfo, Class<?> valueClass) throws Exception {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                Object value = RocksDBValueSerDe.deserializeObject(e.get("value"), valueClass);
                putEntry(backend, cf, e.get("namespace"), e.get("key"), RocksDBValueSerDe.serialize(value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreValueState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                          Map<String, Object> stateInfo) throws Exception {
        Class<Object> valueClass = loadValueClass(stateInfo);

        ValueStateDescriptor<Object> descriptor = new ValueStateDescriptor<>(stateName, valueClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBValueState<Object> state = new RocksDBValueState<>(backend, cf, descriptor);
        registerRestoredState(backend, stateName, descriptor, state, ValueState.class);

        restoreValueEntries(backend, cf, stateInfo, valueClass);
    }

    @SuppressWarnings("unchecked")
    private static void restoreMapState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                        Map<String, Object> stateInfo) throws Exception {
        Class<Object> valueClass = loadValueClass(stateInfo);
        Class<Object> mapKeyClass = null;
        String keyTypeName = resolveTypeName(stateInfo, "mapKeyTypeName", "mapKeyType");
        if (keyTypeName != null) {
            mapKeyClass = loadClass(keyTypeName);
        }

        MapStateDescriptor<Object, Object> descriptor = new MapStateDescriptor<>(stateName, mapKeyClass, valueClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBMapState<Object, Object> state = new RocksDBMapState<>(backend, cf, descriptor);
        registerRestoredState(backend, stateName, descriptor, state, MapState.class);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                Object namespace = RocksDBKeyEncoder.deserializeNamespace(e.get("namespace"));
                Object rawKey = e.get("key");
                int keyGroupId = backend.computeKeyGroupId(rawKey);
                byte[] baseKey = RocksDBKeyEncoder.encode(namespace, rawKey, keyGroupId);
                Object raw = e.get("mapValue");
                // item 24 parity guard (Phase 1 adjudication): a corrupt pair
                // previously surfaced as a bare ClassCastException with no state
                // context; fail fast with a typed, locatable error instead.
                if (raw != null && !(raw instanceof List)) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR)
                            .param(ARG_STATE_NAME, stateName)
                            .param(ARG_ACTUAL_TYPE, raw.getClass().getName())
                            .param(ARG_DETAIL, "mapValue of state '" + stateName
                                    + "' is not a list of key/value pairs; snapshot is corrupt or foreign");
                }
                List<List<Object>> mapEntries = (List<List<Object>>) raw;
                if (mapEntries != null) {
                    for (int i = 0; i < mapEntries.size(); i++) {
                        Object pairObj = mapEntries.get(i);
                        if (!(pairObj instanceof List) || ((List<?>) pairObj).size() < 2) {
                            throw new StreamException(ERR_STREAM_STATE_ERROR)
                                    .param(ARG_STATE_NAME, stateName)
                                    .param(ARG_DETAIL, "mapValue pair #" + i + " of state '" + stateName
                                            + "' is not a [key, value] pair (got: "
                                            + (pairObj == null ? "null" : pairObj.toString())
                                            + "); snapshot is corrupt or foreign");
                        }
                        List<Object> me = (List<Object>) pairObj;
                        Object mk = me.get(0);
                        // P1-21-01: container values arrive as the element-type wrapper
                        // (or as a raw JSON form for legacy snapshots); decode re-materializes
                        // inner elements (warn on legacy degradation), then re-encode so the
                        // stored bytes carry the wrapper for the runtime read path.
                        Object mv;
                        if (ContainerValueCodec.isContainerType(valueClass)) {
                            mv = ContainerValueCodec.decode(me.get(1), valueClass,
                                    "RocksDB MapState '" + stateName + "'");
                        } else {
                            mv = RocksDBValueSerDe.deserializeObject(me.get(1), valueClass);
                        }
                        byte[] fullKey = appendMapKey(baseKey, mk);
                        backend.getDb().put(cf, fullKey,
                                RocksDBValueSerDe.serialize(ContainerValueCodec.encode(mv)));
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreAppendingState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                              Map<String, Object> stateInfo) throws Exception {
        Class<Object> valueClass = loadValueClass(stateInfo);
        Class<? extends SimpleAccumulator<Object>> accumulatorClass =
                loadAccumulatorClass(resolveTypeName(stateInfo, "accumulatorTypeName", "accumulatorType"));

        ReducingStateDescriptor<Object> descriptor =
                new ReducingStateDescriptor<>(stateName, valueClass, accumulatorClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBInternalAppendingState<Object, Object, Object> state =
                new RocksDBInternalAppendingState<>((RocksDBKeyedStateBackend<Object>) backend, cf, descriptor);
        registerRestoredState(backend, stateName, descriptor, state, InternalAppendingState.class);

        restoreValueEntries(backend, cf, stateInfo, valueClass);
    }

    /** ListState / InternalListState share one restore (only the state class differs). */
    @SuppressWarnings("unchecked")
    private static void restoreListState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                         Map<String, Object> stateInfo, boolean internal) throws Exception {
        Class<Object> valueClass = loadValueClass(stateInfo);

        ListStateDescriptor<Object> descriptor = new ListStateDescriptor<>(stateName, valueClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        if (internal) {
            RocksDBInternalListState<Object, Object, Object> state =
                    new RocksDBInternalListState<>((RocksDBKeyedStateBackend<Object>) backend, cf, descriptor);
            registerRestoredState(backend, stateName, descriptor, state, InternalListState.class);
        } else {
            RocksDBListState<Object> state = new RocksDBListState<>(backend, cf, descriptor);
            registerRestoredState(backend, stateName, descriptor, state, ListState.class);
        }

        restoreListEntries(backend, cf, stateInfo, valueClass);
    }

    @SuppressWarnings("unchecked")
    private static void restoreReducingState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                             Map<String, Object> stateInfo) throws Exception {
        // Legacy *TypeName keys are accepted with the same fallback order as the other
        // restore branches and as MemoryStateSerDe (item 7 S-12 twin, item 11 RK-3).
        Class<Object> valueClass = loadValueClass(stateInfo);
        Class<? extends SimpleAccumulator<Object>> accumulatorClass =
                loadAccumulatorClass(resolveTypeName(stateInfo, "accumulatorTypeName", "accumulatorType"));

        ReducingStateDescriptor<Object> descriptor =
                new ReducingStateDescriptor<>(stateName, valueClass, accumulatorClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        RocksDBReducingState<Object> state = new RocksDBReducingState<>(backend, cf, descriptor);
        registerRestoredState(backend, stateName, descriptor, state, ReducingState.class);

        restoreValueEntries(backend, cf, stateInfo, valueClass);
    }

    /** AggregatingState / InternalAggregatingState share one restore (only the state class differs). */
    @SuppressWarnings("unchecked")
    private static void restoreAggregatingState(RocksDBKeyedStateBackend<?> backend, String stateName,
                                                Map<String, Object> stateInfo, boolean internal) throws Exception {
        // Legacy *TypeName fallback (item 11 RK-3, mirrors restoreReducingState).
        Class<Object> recordedClass = loadValueClass(stateInfo);
        String aggregateFunctionTypeName = (String) stateInfo.get("aggregateFunctionType");
        AggregateFunction<Object, Object, Object> aggregateFunction =
                resolveAggregateFunction(backend, stateName, aggregateFunctionTypeName);
        Class<Object> valueClass = (Class<Object>) inferAccumulatorType(aggregateFunction, recordedClass);

        AggregatingStateDescriptor<Object, Object, Object> descriptor =
                new AggregatingStateDescriptor<>(stateName, aggregateFunction, valueClass);
        ColumnFamilyHandle cf = backend.getOrCreateColumnFamily(stateName);
        if (internal) {
            RocksDBInternalAggregatingState<Object, Object, Object, Object, Object> state =
                    new RocksDBInternalAggregatingState<>((RocksDBKeyedStateBackend<Object>) backend, cf, descriptor);
            registerRestoredState(backend, stateName, descriptor, state, InternalAppendingState.class);
        } else {
            RocksDBAggregatingState<Object, Object, Object> state =
                    new RocksDBAggregatingState<>(backend, cf, descriptor);
            registerRestoredState(backend, stateName, descriptor, state, AggregatingState.class);
        }

        restoreValueEntries(backend, cf, stateInfo, valueClass);
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
    private static Class<Object> loadValueClass(Map<String, Object> stateInfo) throws Exception {
        return loadClass(resolveTypeName(stateInfo, "valueTypeName", "valueType"));
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
            // correctly either way. Observable degradation (item 11 RK-5, mirrors
            // core S-3): the fallback is logged, never silent.
            LOG.warn("Failed to infer accumulator type from aggregate function {}; keeping recorded type {}",
                    aggregateFunction.getClass().getName(), recordedType.getName(), e);
        }
        return recordedType;
    }

    /**
     * P1-01 (Decision: 方案 1 = live function reuse): resolves the aggregate
     * function used to rebuild aggregating state on restore.
     *
     * <p>Priority order: (1) the LIVE function registered via
     * {@code IKeyedStateBackend#registerRestoreAggregateFunction} (the
     * operator's descriptor function — required for capturing anonymous
     * classes / lambdas, and also restores old snapshots); (2) class-name +
     * no-arg reflection (legacy path). Reflection failure with no registered
     * provider fails fast with a clear error (No-Silent-No-Op rule #24).
     */
    @SuppressWarnings("unchecked")
    private static AggregateFunction<Object, Object, Object> resolveAggregateFunction(
            RocksDBKeyedStateBackend<?> backend, String stateName, String aggregateFunctionTypeName) throws Exception {
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

    private static String resolveTypeName(Map<String, Object> stateInfo, String primary, String fallback) {
        String name = (String) stateInfo.get(primary);
        if (name == null) {
            name = (String) stateInfo.get(fallback);
        }
        return name;
    }
}
