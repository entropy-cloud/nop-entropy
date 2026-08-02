/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.checkpoint.incremental.SharedStateHandle;
import io.nop.stream.core.checkpoint.storage.ISegmentStore;
import io.nop.stream.core.common.state.backend.rocksdb.incremental.RocksDBIncrementalRestore;
import io.nop.stream.core.common.state.backend.rocksdb.incremental.RocksDBIncrementalSnapshotStrategy;
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
import io.nop.stream.core.common.state.StateTtlConfig;
import io.nop.stream.core.common.state.SystemTtlTimeProvider;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.TtlTimeProvider;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.state.backend.IInternalStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroup;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(RocksDBKeyedStateBackend.class);

    static {
        RocksDB.loadLibrary();
    }

    private final Class<K> keyType;
    /**
     * Stage 34: job-global key-group upper bound. Replaces the legacy
     * {@code shardCount} field; semantics are identical (key&#8594;group modulus).
     */
    private final int maxParallelism;
    private final String dbPath;
    private final RocksDBOptionConfig optionConfig;

    /**
     * Stage 31: when {@code true}, {@link #snapshotState()} takes the incremental
     * path — a native RocksDB checkpoint is created and SST files are content-addressed
     * (see {@link RocksDBIncrementalSnapshotStrategy}). Defaults to {@code false} so
     * the Stage 30 full-scan path remains the default (backward compatible).
     */
    private boolean incrementalCheckpointEnabled = false;

    /**
     * Base directory under which per-checkpoint directories ({@code cp-{id}}) are
     * created by the incremental strategy. Defaults to {@code dbPath + "-checkpoints"}.
     */
    private String checkpointBaseDir;

    private final transient AtomicLong incrementalSnapshotIdCounter = new AtomicLong(0);

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

    /**
     * Processing-time source used by all {@link TtlContext}s created in {@link #applyTtl}.
     * Tests inject a controllable clock here before calling {@code getState(...)} so TTL
     * expiry can be exercised deterministically without sleeping.
     */
    private TtlTimeProvider ttlClock = SystemTtlTimeProvider.INSTANCE;

    /**
     * Stage 35: target key-group range for partial (per-subtask) restore on the
     * full-JSON snapshot path. When non-null, {@link RocksDBSnapshotSerDe#restoreState}
     * only writes entries whose key-group id falls inside this range — the
     * in-memory entry-filter counterpart of the incremental SST range scan.
     * {@code null} restores the whole snapshot (backward compatible).
     */
    private KeyGroupRange targetKeyGroupRange;

    public RocksDBKeyedStateBackend(String dbPath, Class<K> keyType, int maxParallelism,
                                    RocksDBOptionConfig optionConfig) {
        this.dbPath = dbPath;
        this.keyType = keyType;
        this.maxParallelism = maxParallelism;
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

    /**
     * Stage 35: copy entries whose key-group prefix falls inside {@code range}
     * from a read-only source RocksDB column family into this backend's
     * corresponding column family. Consumes the Stage 34 sortable binary prefix
     * (key-group id is the big-endian first 4 bytes of every key). Used by
     * {@link RocksDBIncrementalRestore#restoreRangeInto} to perform the real
     * SST range scan during incremental checkpoint restore.
     *
     * @param src    the read-only reconstructed source DB
     * @param srcCf  the source column family handle
     * @param cfName the column-family (state) name to write into on this backend
     * @param range  target key-group range, or {@code null} to copy the whole CF
     * @return the number of entries copied
     */
    public int copyColumnFamilyRange(RocksDB src, ColumnFamilyHandle srcCf, String cfName,
                                     KeyGroupRange range) throws RocksDBException {
        ColumnFamilyHandle dstCf = getOrCreateColumnFamily(cfName);
        int copied = 0;
        try (RocksIterator it = src.newIterator(srcCf)) {
            if (range != null) {
                byte[] startPrefix = int32BigEndian(range.getStartKeyGroup());
                byte[] endPrefix = int32BigEndian(range.getEndKeyGroup());
                for (it.seek(startPrefix); it.isValid(); it.next()) {
                    byte[] key = it.key();
                    if (compareBytes(key, endPrefix) >= 0) {
                        break;
                    }
                    int keyGroupId = readInt32BE(key, 0);
                    if (!range.contains(keyGroupId)) {
                        continue;
                    }
                    db.put(dstCf, key, it.value());
                    copied++;
                }
            } else {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    db.put(dstCf, it.key(), it.value());
                    copied++;
                }
            }
        }
        return copied;
    }

    private static byte[] int32BigEndian(int value) {
        return new byte[]{
                (byte) ((value >>> 24) & 0xFF),
                (byte) ((value >>> 16) & 0xFF),
                (byte) ((value >>> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    private static int readInt32BE(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 24)
                | ((buf[off + 1] & 0xFF) << 16)
                | ((buf[off + 2] & 0xFF) << 8)
                | (buf[off + 3] & 0xFF);
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int cmp = Integer.compare(a[i] & 0xFF, b[i] & 0xFF);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
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
        return maxParallelism;
    }

    /**
     * Stage 34: job-global key-group upper bound for this backend.
     */
    @Override
    public int getMaxParallelism() {
        return maxParallelism;
    }

    /**
     * Stage 35: target key-group range used by the next {@link #restoreState}
     * call to perform partial (per-subtask) restore on the full-JSON path.
     */
    public void setTargetKeyGroupRange(KeyGroupRange targetKeyGroupRange) {
        this.targetKeyGroupRange = targetKeyGroupRange;
    }

    KeyGroupRange getTargetKeyGroupRange() {
        return targetKeyGroupRange;
    }

    /**
     * Stage 34: compute the key-group id for {@code key} under this backend's
     * job-global {@code maxParallelism}. The result drives the sortable binary
     * prefix written by {@link RocksDBKeyEncoder#encode}.
     */
    int computeKeyGroupId(Object key) {
        if (maxParallelism <= 1) {
            return 0;
        }
        return KeyGroupAssignment.assignToKeyGroup(key, maxParallelism);
    }

    /**
     * Build the composite storage key for the current key/namespace.
     */
    byte[] buildStorageKey(Object namespace, Object rawKey) {
        return RocksDBKeyEncoder.encode(namespace, rawKey, computeKeyGroupId(rawKey));
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
        applyTtl(state, stateProperties);
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
        applyTtl(state, stateProperties);
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
        applyTtl(state, stateProperties);
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
        applyTtl(state, stateProperties);
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
        applyTtl(state, stateProperties);
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
        applyTtl(state, descriptor);
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
        applyTtl(state, descriptor);
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
        applyTtl(state, descriptor);
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

    public void setTtlTimeProvider(TtlTimeProvider ttlClock) {
        this.ttlClock = ttlClock != null ? ttlClock : SystemTtlTimeProvider.INSTANCE;
    }

    /**
     * Centralized TTL binding. Called from every {@code getState(...)} overload after the
     * lazy-create-or-verify step. When the live descriptor carries an enabled
     * {@link StateTtlConfig}, a fresh {@link TtlContext} is bound to the state object. This
     * covers both the freshly-created path and the restored path (rebinding TTL after
     * restore): restored entries have a storage value but no sidecar timestamp, and are
     * granted a fresh TTL window on first access by {@link TtlContext#grantFreshWindow}.
     */
    private void applyTtl(Object stateObj, StateDescriptor<?> descriptor) {
        if (!(stateObj instanceof RocksDbTtlAware)) {
            return;
        }
        StateTtlConfig cfg = descriptor.getTtlConfig();
        if (!cfg.isEnabled()) {
            return;
        }
        ((RocksDbTtlAware) stateObj).bindTtl(new TtlContext<>(cfg, ttlClock));
    }

    /**
     * Delete every key in {@code cf} that starts with {@code prefixBytes}. Used by TTL
     * eviction for {@code MapState} (whose TTL unit is the whole map keyed by the base
     * composite key, while individual map entries append a map-key suffix).
     */
    void deleteByPrefix(ColumnFamilyHandle cf, byte[] prefixBytes) {
        List<byte[]> toDelete = new ArrayList<>();
        try (RocksIterator it = db.newIterator(cf)) {
            it.seek(prefixBytes);
            while (it.isValid()) {
                byte[] fullKey = it.key();
                if (!startsWith(fullKey, prefixBytes)) {
                    break;
                }
                toDelete.add(fullKey);
                it.next();
            }
        }
        try {
            for (byte[] k : toDelete) {
                db.delete(cf, k);
            }
        } catch (RocksDBException e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to delete by prefix for TTL eviction");
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Background cleanup: for every TTL-enabled state, scan the sidecar for expired
     * timestamps and delete the corresponding RocksDB entries (single-key delete for
     * scalar/list/accumulator states; prefix delete for {@code MapState}). Returns the
     * total number of expired base keys reclaimed. This is the pure-Java substitute for a
     * RocksDB compaction filter — the {@code rocksdbjni} binding does not expose a
     * pure-Java compaction-filter callback (see
     * {@code ai-dev/design/nop-stream/state-management-design.md} TTL section).
     */
    public int cleanupExpiredEntries() {
        int total = 0;
        for (Object stateObj : states.values()) {
            if (!(stateObj instanceof RocksDbTtlAware)) {
                continue;
            }
            TtlContext<ByteBuffer> ctx = ((RocksDbTtlAware) stateObj).ttlContext();
            if (ctx == null || !ctx.isEnabled()) {
                continue;
            }
            ColumnFamilyHandle cf = ((RocksDbTtlAware) stateObj).cfHandle();
            for (ByteBuffer baseKeyBuf : ctx.expiredKeys()) {
                byte[] baseBytes = new byte[baseKeyBuf.remaining()];
                baseKeyBuf.duplicate().get(baseBytes);
                if (stateObj instanceof RocksDBMapState) {
                    deleteByPrefix(cf, baseBytes);
                } else {
                    try {
                        db.delete(cf, baseBytes);
                    } catch (RocksDBException e) {
                        throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                                .param(ARG_DETAIL, "Failed to delete expired entry during sweep");
                    }
                }
                ctx.removeTimestamp(baseKeyBuf);
                total++;
            }
        }
        return total;
    }

    @Override
    public StateSnapshot snapshotState() throws Exception {
        if (incrementalCheckpointEnabled) {
            // Reclaim expired entries before producing any checkpoint so neither the full
            // scan nor the incremental SST path persists state that should already be gone.
            cleanupExpiredEntries();
            return snapshotIncremental();
        }
        cleanupExpiredEntries();
        return RocksDBSnapshotSerDe.snapshotState(this);
    }

    /**
     * Stage 31 incremental snapshot: create a native RocksDB checkpoint, content-address
     * the SST files, and embed the {@link IncrementalSnapshotResult} into a
     * {@link StateSnapshot} under {@link IncrementalSnapshotResult#MARKER_KEY}. The
     * registry registration and EpochManifest segments building happen at the
     * coordinator (Phase 4); the task side only produces raw handles.
     */
    private StateSnapshot snapshotIncremental() throws Exception {
        if (db == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "RocksDB instance is null; incremental snapshot unavailable");
        }
        RocksDBIncrementalSnapshotStrategy strategy = new RocksDBIncrementalSnapshotStrategy();
        Path baseDir = Paths.get(checkpointBaseDir != null ? checkpointBaseDir : (dbPath + "-checkpoints"));
        long cpId = incrementalSnapshotIdCounter.incrementAndGet();
        IncrementalSnapshotResult result = strategy.doSnapshot(db, baseDir, cpId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(IncrementalSnapshotResult.MARKER_KEY, result);
        // Stage 34: stamp the binary key-layout version so the incremental
        // restore path can fail-fast on SST files produced by the legacy
        // encoder (whose shard-id was not a sortable key-group prefix).
        data.put(RocksDBKeyEncoder.KEY_LAYOUT_VERSION_FIELD, RocksDBKeyEncoder.KEY_LAYOUT_VERSION);
        if (keyType != null) {
            data.put("keyType", keyType.getName());
        }
        return new StateSnapshot(data);
    }

    public boolean isIncrementalCheckpointEnabled() {
        return incrementalCheckpointEnabled;
    }

    public void setIncrementalCheckpointEnabled(boolean incrementalCheckpointEnabled) {
        this.incrementalCheckpointEnabled = incrementalCheckpointEnabled;
    }

    public String getCheckpointBaseDir() {
        return checkpointBaseDir;
    }

    public void setCheckpointBaseDir(String checkpointBaseDir) {
        this.checkpointBaseDir = checkpointBaseDir;
    }

    @Override
    public void restoreState(StateSnapshot snapshot) throws Exception {
        if (snapshot != null && !snapshot.isEmpty()) {
            Object marker = snapshot.getStateData().get(IncrementalSnapshotResult.MARKER_KEY);
            if (marker instanceof IncrementalSnapshotResult) {
                restoreIncremental((IncrementalSnapshotResult) marker);
                return;
            }
            if (marker instanceof Map) {
                // JSON-deserialized form: reconstruct an IncrementalSnapshotResult.
                @SuppressWarnings("unchecked")
                IncrementalSnapshotResult result = io.nop.core.reflect.bean.BeanTool
                        .buildBean(marker, IncrementalSnapshotResult.class);
                if (result != null) {
                    restoreIncremental(result);
                    return;
                }
            }
        }
        RocksDBSnapshotSerDe.restoreState(this, snapshot);
    }

    /**
     * Stage 35: real key-group range restore from an incremental checkpoint (Stage 31
     * deferred item). Reconstructs the content-addressed SST set into a temp RocksDB
     * directory and copies only the entries whose key-group prefix falls inside the
     * backend's {@link #targetKeyGroupRange} (or the whole DB when the range is null).
     *
     * <p>Fail-fast: if no {@link ISegmentStore} is configured the restore cannot locate
     * the shared SST files, so it throws rather than silently degrading to a full-JSON
     * restore (which would both ignore the range and produce an empty state).
     */
    private void restoreIncremental(IncrementalSnapshotResult result) throws Exception {
        if (segmentStore == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "Incremental checkpoint restore requires an ISegmentStore to resolve "
                            + "content-addressed SST files, but none is configured on the RocksDB backend. "
                            + "Configure setSegmentStore(...) before restore, or use the full-snapshot path.");
        }
        Path baseDir = Paths.get(checkpointBaseDir != null ? checkpointBaseDir : (dbPath + "-restore"));
        Files.createDirectories(baseDir);
        int copied = RocksDBIncrementalRestore.restoreRangeInto(
                this, result, segmentStore, targetKeyGroupRange, baseDir);
        LOG.debug("Incremental range restore copied {} entries into RocksDB at {}", copied, dbPath);
    }

    /**
     * Stage 35: content-addressed segment store used by {@link #restoreState} to resolve
     * shared SST files when restoring an incremental checkpoint. Optional: when unset,
     * an incremental checkpoint restore fails fast (no silent full-JSON fallback).
     */
    private ISegmentStore segmentStore;

    public void setSegmentStore(ISegmentStore segmentStore) {
        this.segmentStore = segmentStore;
    }

    public ISegmentStore getSegmentStore() {
        return segmentStore;
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
