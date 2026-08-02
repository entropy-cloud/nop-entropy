/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import io.nop.stream.core.common.state.backend.IInternalStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.StateShard;
import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_CHECKSUM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_CHECKSUM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_SCHEMA_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TYPE_MISMATCH;

/**
 * RocksDB-backed implementation of {@link IInternalStateBackend}.
 *
 * <p>Each registered state maps to one RocksDB column family. The composite
 * storage key encodes (namespace, shard-id, raw-key) as length-prefixed bytes.
 * Values are JSON-serialized via {@code JsonTool}.
 *
 * <p>The backend assumes single-threaded access (mailbox model), matching the
 * memory backend. RocksDB native handles are not thread-safe and do not need
 * synchronization under this assumption.
 */
public class RocksDBKeyedStateBackend<K> implements IInternalStateBackend<K> {

    private static final long serialVersionUID = 1L;

    static {
        RocksDB.loadLibrary();
    }

    private final Class<K> keyType;
    private final int shardCount;
    private final String dbPath;
    private final RocksDBOptionConfig optionConfig;

    private transient RocksDB db;
    private transient ColumnFamilyHandle defaultCF;
    private transient Map<String, ColumnFamilyHandle> cfHandles;
    private transient DBOptions dbOptions;
    private transient ColumnFamilyOptions cfOptions;

    private transient K currentKey;
    private transient Object currentNamespace = DEFAULT_NAMESPACE;

    @SuppressWarnings("unchecked")
    private final Map<String, Object> states = new LinkedHashMap<>();

    private final Map<String, Class<?>> stateTypes = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    private final Map<String, StateDescriptor<?>> restoredDescriptors = new LinkedHashMap<>();

    public RocksDBKeyedStateBackend(String dbPath, Class<K> keyType, int shardCount,
                                    RocksDBOptionConfig optionConfig) {
        this.dbPath = dbPath;
        this.keyType = keyType;
        this.shardCount = shardCount;
        this.optionConfig = optionConfig != null ? optionConfig : new RocksDBOptionConfig();
        openDB();
    }

    // ------------------------------------------------------------------------
    //  RocksDB lifecycle
    // ------------------------------------------------------------------------

    private void openDB() {
        File dbDir = new File(dbPath);
        dbDir.mkdirs();

        dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)
                .setMaxBackgroundJobs(optionConfig.getMaxBackgroundThreads());

        cfOptions = new ColumnFamilyOptions()
                .setWriteBufferSize(optionConfig.getWriteBufferSize());

        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));

        List<byte[]> existingCFs;
        try {
            Options options = new Options(dbOptions, cfOptions);
            existingCFs = RocksDB.listColumnFamilies(options, dbPath);
        } catch (RocksDBException e) {
            existingCFs = Collections.emptyList();
        }
        for (byte[] cfName : existingCFs) {
            if (!Arrays.equals(cfName, RocksDB.DEFAULT_COLUMN_FAMILY)) {
                cfDescriptors.add(new ColumnFamilyDescriptor(cfName, cfOptions));
            }
        }

        List<ColumnFamilyHandle> handles = new ArrayList<>();
        try {
            db = RocksDB.open(dbOptions, dbPath, cfDescriptors, handles);
        } catch (RocksDBException e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to open RocksDB at " + dbPath);
        }

        defaultCF = handles.get(0);
        cfHandles = new HashMap<>();
        for (int i = 1; i < handles.size(); i++) {
            String name = new String(cfDescriptors.get(i).getName(), StandardCharsets.UTF_8);
            cfHandles.put(name, handles.get(i));
        }
    }

    ColumnFamilyHandle getOrCreateColumnFamily(String stateName) {
        ColumnFamilyHandle handle = cfHandles.get(stateName);
        if (handle != null) {
            return handle;
        }
        try {
            ColumnFamilyDescriptor desc = new ColumnFamilyDescriptor(
                    stateName.getBytes(StandardCharsets.UTF_8), cfOptions);
            handle = db.createColumnFamily(desc);
            cfHandles.put(stateName, handle);
            return handle;
        } catch (RocksDBException e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to create column family: " + stateName);
        }
    }

    RocksDB getDb() {
        return db;
    }

    Map<String, ColumnFamilyHandle> getCfHandles() {
        return cfHandles;
    }

    // ------------------------------------------------------------------------
    //  key / namespace management
    // ------------------------------------------------------------------------

    @Override
    public void setCurrentKey(K key) {
        this.currentKey = key;
    }

    @Override
    public K getCurrentKey() {
        return currentKey;
    }

    @Override
    public void setCurrentNamespace(String namespace) {
        this.currentNamespace = namespace != null ? namespace : DEFAULT_NAMESPACE;
    }

    @Override
    public String getCurrentNamespace() {
        return currentNamespace != null ? currentNamespace.toString() : DEFAULT_NAMESPACE;
    }

    Class<K> getKeyType() {
        return keyType;
    }

    int getShardCount() {
        return shardCount;
    }

    int computeShardId(Object key) {
        if (shardCount <= 1) {
            return 0;
        }
        return (StateShard.stableHash(key) & 0x7FFFFFFF) % shardCount;
    }

    /**
     * Build the composite storage key for the current key/namespace.
     */
    byte[] buildStorageKey(Object namespace, Object rawKey) {
        return RocksDBKeyEncoder.encode(namespace, rawKey, computeShardId(rawKey));
    }

    byte[] buildStorageKeyForCurrent() {
        return buildStorageKey(currentNamespace, currentKey);
    }

    // ------------------------------------------------------------------------
    //  schema verification (mirrors MemoryKeyedStateBackend)
    // ------------------------------------------------------------------------

    private void registerStateType(String name, Class<?> type) {
        Class<?> existing = stateTypes.get(name);
        if (existing != null && !existing.equals(type)) {
            throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                    .param(ARG_STATE_NAME, name)
                    .param(ARG_EXPECTED_TYPE, existing.getName())
                    .param(ARG_ACTUAL_TYPE, type.getName());
        }
        stateTypes.put(name, type);
    }

    private void verifySchemaCompatibility(String stateName, String stateType,
                                           StateDescriptor<?> currentDescriptor,
                                           StateDescriptor<?> restoredDescriptor) {
        if (restoredDescriptor == null) {
            return;
        }
        SerializerFingerprint currentFp = StateSchemaResolver.fromDescriptor(stateType, currentDescriptor);
        SerializerFingerprint restoredFp = StateSchemaResolver.fromDescriptor(stateType, restoredDescriptor);
        if (!StateSchemaResolver.fingerprintsCompatible(currentFp, restoredFp)) {
            throw new StreamException(ERR_STREAM_STATE_SCHEMA_MISMATCH)
                    .param(ARG_STATE_NAME, stateName)
                    .param(ARG_EXPECTED_CHECKSUM, currentFp.getSchemaChecksum())
                    .param(ARG_ACTUAL_CHECKSUM, restoredFp.getSchemaChecksum());
        }
    }

    // ------------------------------------------------------------------------
    //  state registration
    // ------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
        RocksDBValueState<T> state = (RocksDBValueState<T>) states.get(stateProperties.getName());
        if (state == null) {
            ColumnFamilyHandle cf = getOrCreateColumnFamily(stateProperties.getName());
            state = new RocksDBValueState<>(this, cf, stateProperties);
            registerStateType(stateProperties.getName(), ValueState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_VALUE,
                    stateProperties, ((RocksDBValueState<?>) state).descriptor);
        }
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
        RocksDBMapState<UK, UV> state = (RocksDBMapState<UK, UV>) states.get(stateProperties.getName());
        if (state == null) {
            ColumnFamilyHandle cf = getOrCreateColumnFamily(stateProperties.getName());
            state = new RocksDBMapState<>(this, cf, stateProperties);
            registerStateType(stateProperties.getName(), MapState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_MAP,
                    stateProperties, ((RocksDBMapState<?, ?>) state).descriptor);
        }
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
        RocksDBListState<T> state = (RocksDBListState<T>) states.get(stateProperties.getName());
        if (state == null) {
            ColumnFamilyHandle cf = getOrCreateColumnFamily(stateProperties.getName());
            state = new RocksDBListState<>(this, cf, stateProperties);
            registerStateType(stateProperties.getName(), ListState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_LIST,
                    stateProperties, ((RocksDBListState<?>) state).descriptor);
        }
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
        RocksDBReducingState<T> state = (RocksDBReducingState<T>) states.get(stateProperties.getName());
        if (state == null) {
            ColumnFamilyHandle cf = getOrCreateColumnFamily(stateProperties.getName());
            state = new RocksDBReducingState<>(this, cf, stateProperties);
            registerStateType(stateProperties.getName(), ReducingState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_REDUCING,
                    stateProperties, ((RocksDBReducingState<?>) state).descriptor);
        }
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
        RocksDBAggregatingState<IN, ACC, OUT> state =
                (RocksDBAggregatingState<IN, ACC, OUT>) states.get(stateProperties.getName());
        if (state == null) {
            ColumnFamilyHandle cf = getOrCreateColumnFamily(stateProperties.getName());
            state = new RocksDBAggregatingState<>(this, cf, stateProperties);
            registerStateType(stateProperties.getName(), AggregatingState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_AGGREGATING,
                    stateProperties, ((RocksDBAggregatingState<?, ?, ?>) state).descriptor);
        }
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <N, IN> InternalAppendingState<K, N, IN, IN, IN> getInternalAppendingState(
            ReducingStateDescriptor<IN> descriptor) {
        RocksDBInternalAppendingState<K, N, IN> state =
                (RocksDBInternalAppendingState<K, N, IN>) states.get(descriptor.getName());
        if (state == null) {
            ColumnFamilyHandle cf = getOrCreateColumnFamily(descriptor.getName());
            state = new RocksDBInternalAppendingState<>(this, cf, descriptor);
            registerStateType(descriptor.getName(), InternalAppendingState.class);
            states.put(descriptor.getName(), state);
        } else {
            verifySchemaCompatibility(descriptor.getName(),
                    StateSchemaResolver.STATE_TYPE_APPENDING,
                    descriptor, ((RocksDBInternalAppendingState<?, ?, ?>) state).descriptor);
        }
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <N, IN, ACC, OUT> InternalAppendingState<K, N, IN, ACC, OUT> getInternalAppendingState(
            AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        RocksDBInternalAggregatingState<K, N, IN, ACC, OUT> state =
                (RocksDBInternalAggregatingState<K, N, IN, ACC, OUT>) states.get(descriptor.getName());
        if (state == null) {
            ColumnFamilyHandle cf = getOrCreateColumnFamily(descriptor.getName());
            state = new RocksDBInternalAggregatingState<>(this, cf, descriptor);
            registerStateType(descriptor.getName(), InternalAppendingState.class);
            states.put(descriptor.getName(), state);
        } else {
            verifySchemaCompatibility(descriptor.getName(),
                    StateSchemaResolver.STATE_TYPE_INTERNAL_AGGREGATING,
                    descriptor, ((RocksDBInternalAggregatingState<?, ?, ?, ?, ?>) state).descriptor);
        }
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <N, T> InternalListState<K, N, T> getInternalListState(ListStateDescriptor<T> descriptor) {
        RocksDBInternalListState<K, N, T> state =
                (RocksDBInternalListState<K, N, T>) states.get(descriptor.getName());
        if (state == null) {
            ColumnFamilyHandle cf = getOrCreateColumnFamily(descriptor.getName());
            state = new RocksDBInternalListState<>(this, cf, descriptor);
            registerStateType(descriptor.getName(), InternalListState.class);
            states.put(descriptor.getName(), state);
        } else {
            verifySchemaCompatibility(descriptor.getName(),
                    StateSchemaResolver.STATE_TYPE_INTERNAL_LIST,
                    descriptor, ((RocksDBInternalListState<?, ?, ?>) state).descriptor);
        }
        return state;
    }

    // ------------------------------------------------------------------------
    //  snapshot / restore (Phase 3)
    // ------------------------------------------------------------------------

    Map<String, Object> getStates() {
        return states;
    }

    Map<String, Class<?>> getStateTypes() {
        return stateTypes;
    }

    void putRestoredDescriptor(String name, StateDescriptor<?> descriptor) {
        restoredDescriptors.put(name, descriptor);
    }

    @Override
    public StateSnapshot snapshotState() throws Exception {
        return RocksDBSnapshotSerDe.snapshotState(this);
    }

    @Override
    public void restoreState(StateSnapshot snapshot) throws Exception {
        RocksDBSnapshotSerDe.restoreState(this, snapshot);
    }

    // ------------------------------------------------------------------------
    //  close
    // ------------------------------------------------------------------------

    @Override
    public void close() {
        if (cfHandles != null) {
            for (ColumnFamilyHandle handle : cfHandles.values()) {
                if (handle != null) {
                    handle.close();
                }
            }
            cfHandles.clear();
        }
        if (defaultCF != null) {
            defaultCF.close();
        }
        if (db != null) {
            db.close();
            db = null;
        }
        if (cfOptions != null) {
            cfOptions.close();
        }
        if (dbOptions != null) {
            dbOptions.close();
        }
    }
}
