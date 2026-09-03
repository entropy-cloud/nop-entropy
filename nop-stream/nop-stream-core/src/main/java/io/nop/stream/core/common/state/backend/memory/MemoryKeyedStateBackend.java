/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.util.HashMap;
import java.util.Map;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.backend.IInternalStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.StateMigrationRegistry;
import io.nop.stream.core.common.state.StateSchemaResolver;
import io.nop.stream.core.common.state.StateTtlConfig;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;
import io.nop.stream.core.common.state.SystemTtlTimeProvider;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.TtlTimeProvider;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.common.state.shard.ShardPrefixedKey;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_CHECKSUM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_CHECKSUM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_SCHEMA_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TYPE_MISMATCH;

/**
 * 内存实现的 KeyedStateBackend。
 *
 * <p>所有状态存储在 JVM 内存的 Map 中，支持 key 和 namespace 切换。
 *
 * <p>存储结构：
 * <pre>
 * states: Map<String, State>  // stateName -> State
 *   └── MemoryValueState
 *         └── storage: Map<TypedNamespaceAndKey, value>
 *   └── MemoryMapState
 *         └── storage: Map<TypedNamespaceAndKey, Map<userKey, userValue>>
 *   └── MemoryInternalListState
 *         └── storage: Map<TypedNamespaceAndKey, List<element>>
 * </pre>
 *
 * <p>item 21 D-3 convergence: the eight {@code getXxxState} overloads share
 * the single lazy create-or-verify path {@link #getOrCreateState}, and
 * {@link #rebindStateBackends()} collapses to the {@link AbstractMemoryState}
 * supertype instead of an 8-way {@code instanceof} ladder.
 *
 * @param <K> key 的类型
 */
public class MemoryKeyedStateBackend<K> implements IInternalStateBackend<K>, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private final Class<K> keyType;

    /**
     * Stage 34: job-global key-group upper bound. Replaces the legacy
     * {@code shardCount} field; semantics are identical (key&#8594;group modulus).
     */
    private final int maxParallelism;

    private transient K currentKey;

    private transient Object currentNamespace = DEFAULT_NAMESPACE;

    private final Map<String, Object> states = new HashMap<>();

    private final Map<String, Class<?>> stateTypes = new HashMap<>();

    /**
     * P1-01: live {@code AggregateFunction} providers keyed by state name,
     * consulted by the serde restore path (preferred over class-name
     * reflection, which fails for capturing anonymous classes / lambdas).
     * Operators register their descriptor's function before restore runs
     * (e.g. {@code WindowOperator}).
     */
    private final Map<String, AggregateFunction<?, ?, ?>> restoreAggregateFunctions = new HashMap<>();

    /**
     * Processing-time source used by all {@link TtlContext}s created in {@link #applyTtl}.
     * Tests inject a controllable clock here before calling {@code getState(...)} so TTL
     * expiry can be exercised deterministically without sleeping.
     */
    private TtlTimeProvider ttlClock = SystemTtlTimeProvider.INSTANCE;

    /**
     * Stage 35: target key-group range for partial (per-subtask) restore. When
     * non-null, {@link #restoreState} only materializes entries whose key-group
     * id falls inside this range — the in-memory equivalent of the RocksDB SST
     * range scan. {@code null} restores the whole snapshot (backward compatible).
     */
    private KeyGroupRange targetKeyGroupRange;

    /**
     * Stage 33: migration registry (typically {@code StreamComponents}) consulted
     * by {@link #verifySchemaCompatibility} when a restored state's checksum differs
     * from the current descriptor's checksum. When {@code null}, checksum mismatch
     * always fails fast (Stage 29 behaviour). Set before {@code initializeState}
     * via {@link #setMigrationRegistry}.
     */
    private transient StateMigrationRegistry migrationRegistry;

    public MemoryKeyedStateBackend(Class<K> keyType) {
        this(keyType, 1);
    }

    public MemoryKeyedStateBackend(Class<K> keyType, int maxParallelism) {
        if (maxParallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "maxParallelism").param(ARG_DETAIL, "must be at least 1");
        }
        this.keyType = keyType;
        this.maxParallelism = maxParallelism;
    }

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

    public <N> void setTypedNamespace(N namespace) {
        this.currentNamespace = namespace != null ? namespace : DEFAULT_NAMESPACE;
    }

    public Object getTypedNamespace() {
        return currentNamespace;
    }

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

    /**
     * item 21 D-3 convergence: the single lazy create-or-verify step behind all
     * eight {@code getXxxState} overloads. On miss: create via {@code creator},
     * register the state's public interface type and cache. On hit: run the
     * Stage 29/33 schema-compatibility verification (with migration), and — for
     * the Value/Map families — adopt the operator-supplied custom serializer
     * onto the restored state's descriptor (same rationale as before the
     * convergence).
     */
    @SuppressWarnings("unchecked")
    private <S> S getOrCreateState(StateDescriptor<?> descriptor, String schemaStateType,
                                   Class<?> registeredInterface, java.util.function.Supplier<?> creator,
                                   boolean adoptSerializer) {
        String name = descriptor.getName();
        Object existing = states.get(name);
        if (existing == null) {
            Object state = creator.get();
            registerStateType(name, registeredInterface);
            states.put(name, state);
            return (S) state;
        }
        verifySchemaCompatibility(name, schemaStateType, descriptor, (MigratableKeyedState) existing);
        if (adoptSerializer) {
            adoptCustomSerializer(descriptor, ((MigratableKeyedState) existing).getMigrationDescriptor());
        }
        return (S) existing;
    }

    @Override
    public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
        ValueState<T> state = getOrCreateState(stateProperties, StateSchemaResolver.STATE_TYPE_VALUE,
                ValueState.class, () -> new MemoryValueState<>(this, stateProperties), true);
        applyTtl(state, stateProperties);
        return state;
    }

    /**
     * Copies a custom (non-default) serializer from an operator-supplied descriptor
     * onto a restored state's descriptor. The restored descriptor was rebuilt by
     * {@code MemoryStateSerDe.restoreValueState/restoreMapState} without the
     * operator's custom {@link io.nop.stream.core.common.typeutils.IStreamSerializer} — without this adoption, the
     * first post-restore snapshot would embed raw non-JSON values.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void adoptCustomSerializer(StateDescriptor<?> from, StateDescriptor<?> to) {
        io.nop.stream.core.common.typeutils.TypeSerializer incoming = from.getSerializer();
        if (incoming == null || incoming instanceof io.nop.stream.core.common.typeutils.JsonToolSerializer) {
            return;
        }
        io.nop.stream.core.common.typeutils.TypeSerializer current = to.getSerializer();
        if (current == null || current instanceof io.nop.stream.core.common.typeutils.JsonToolSerializer) {
            to.setSerializer(incoming);
        }
    }

    @Override
    public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
        MapState<UK, UV> state = getOrCreateState(stateProperties, StateSchemaResolver.STATE_TYPE_MAP,
                MapState.class, () -> new MemoryMapState<>(this, stateProperties), true);
        applyTtl(state, stateProperties);
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
        ListState<T> state = getOrCreateState(stateProperties, StateSchemaResolver.STATE_TYPE_LIST,
                ListState.class, () -> new MemoryListState<>(this, stateProperties), false);
        applyTtl(state, stateProperties);
        return state;
    }

    @Override
    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
        ReducingState<T> state = getOrCreateState(stateProperties, StateSchemaResolver.STATE_TYPE_REDUCING,
                ReducingState.class, () -> new MemoryReducingState<>(this, stateProperties), false);
        applyTtl(state, stateProperties);
        return state;
    }

    @Override
    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
        AggregatingState<IN, OUT> state = getOrCreateState(stateProperties, StateSchemaResolver.STATE_TYPE_AGGREGATING,
                AggregatingState.class, () -> new MemoryAggregatingState<>(this, stateProperties), false);
        applyTtl(state, stateProperties);
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <N, IN> InternalAppendingState<K, N, IN, IN, IN> getInternalAppendingState(
            ReducingStateDescriptor<IN> descriptor) {
        InternalAppendingState<K, N, IN, IN, IN> state =
                getOrCreateState(descriptor, StateSchemaResolver.STATE_TYPE_APPENDING,
                        InternalAppendingState.class,
                        () -> new MemoryInternalAppendingState<>(this, descriptor), false);
        applyTtl(state, descriptor);
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <N, IN, ACC, OUT> InternalAppendingState<K, N, IN, ACC, OUT> getInternalAppendingState(
            AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        InternalAppendingState<K, N, IN, ACC, OUT> state =
                getOrCreateState(descriptor, StateSchemaResolver.STATE_TYPE_INTERNAL_AGGREGATING,
                        InternalAppendingState.class,
                        () -> new MemoryInternalAggregatingState<>(this, descriptor), false);
        applyTtl(state, descriptor);
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <N, T> InternalListState<K, N, T> getInternalListState(ListStateDescriptor<T> descriptor) {
        InternalListState<K, N, T> state =
                getOrCreateState(descriptor, StateSchemaResolver.STATE_TYPE_INTERNAL_LIST,
                        InternalListState.class, () -> new MemoryInternalListState<>(this, descriptor), false);
        applyTtl(state, descriptor);
        return state;
    }

    /**
     * Stage 29/33: when {@code getState()} is called on an already-restored state, verify the current
     * descriptor's schema checksum matches the restored descriptor's schema checksum. Both
     * descriptors come from independent sources (current code vs checkpoint), so the comparison
     * is NOT tautological.
     *
     * <p>Stage 33: on checksum mismatch, first consult the registered {@link StateMigrationFunction}s
     * (via {@link #migrationRegistry}). If a matching function is found, perform a full-scan
     * migration (read every stored old-schema value, pass through {@code migrate}, write back as
     * new schema) and swap the state object's descriptor to the current one (idempotency: the next
     * {@code getState()} checksum comparison matches). If no matching function is registered, fail
     * fast with {@code ERR_STREAM_STATE_SCHEMA_MISMATCH} (no silent degradation).
     */
    private void verifySchemaCompatibility(String stateName, String stateType,
                                           StateDescriptor<?> currentDescriptor,
                                           MigratableKeyedState restoredState) {
        StateDescriptor<?> restoredDescriptor = restoredState.getMigrationDescriptor();
        SerializerFingerprint currentFp = StateSchemaResolver.fromDescriptor(stateType, currentDescriptor);
        SerializerFingerprint restoredFp = StateSchemaResolver.fromDescriptor(stateType, restoredDescriptor);
        if (!StateSchemaResolver.fingerprintsCompatible(currentFp, restoredFp)) {
            StateMigrationFunction<?, ?> migration = StateSchemaResolver.findMigration(
                    migrationRegistry, stateName, restoredFp, currentFp);
            if (migration != null) {
                restoredState.applyMigration(migration);
                restoredState.replaceDescriptor(currentDescriptor);
                return;
            }
            throw new StreamException(ERR_STREAM_STATE_SCHEMA_MISMATCH)
                    .param(ARG_STATE_NAME, stateName)
                    .param(ARG_EXPECTED_CHECKSUM, currentFp.getSchemaChecksum())
                    .param(ARG_ACTUAL_CHECKSUM, restoredFp.getSchemaChecksum());
        }
    }

    /**
     * Stage 33: inject the migration registry (e.g. {@code StreamComponents}) so that
     * {@link #verifySchemaCompatibility} can resolve registered {@link StateMigrationFunction}s
     * on checksum mismatch. Must be called before {@code initializeState} (before the first
     * {@code getState()}).
     */
    public void setMigrationRegistry(StateMigrationRegistry migrationRegistry) {
        this.migrationRegistry = migrationRegistry;
    }

    /**
     * Centralized TTL binding. Called from every {@code getState(...)} overload after the
     * lazy-create-or-verify step. When the live descriptor carries an enabled
     * {@link StateTtlConfig}, a fresh {@link TtlContext} is bound to the state object. This
     * covers both the freshly-created path and the restored path (rebinding TTL after
     * restore — see plan Phase 2 "TTL rebind on restore"): restored entries have a storage
     * value but no sidecar timestamp, and are granted a fresh TTL window on first access by
     * {@link TtlContext#readEviction}.
     *
     * <p>RK-4 core twin (item 21 D-3): a repeated {@code getState(...)} with an UNCHANGED
     * TTL config keeps the existing context — rebinding a fresh sidecar here would
     * silently reset every entry's accumulated TTL window.
     */
    private void applyTtl(Object stateObj, StateDescriptor<?> descriptor) {
        if (!(stateObj instanceof TtlAware)) {
            return;
        }
        StateTtlConfig cfg = descriptor.getTtlConfig();
        if (!cfg.isEnabled()) {
            return;
        }
        TtlAware aware = (TtlAware) stateObj;
        TtlContext<TypedNamespaceAndKey> existing = aware.ttlContext();
        if (existing != null && existing.getConfig().equals(cfg)) {
            return;
        }
        aware.bindTtl(new TtlContext<>(cfg, ttlClock));
    }

    public void setTtlTimeProvider(TtlTimeProvider ttlClock) {
        this.ttlClock = ttlClock != null ? ttlClock : SystemTtlTimeProvider.INSTANCE;
    }

    @Override
    public void close() {
        states.clear();
    }

    public Class<K> getKeyType() {
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
     * call to perform partial (per-subtask) restore.
     */
    public void setTargetKeyGroupRange(KeyGroupRange targetKeyGroupRange) {
        this.targetKeyGroupRange = targetKeyGroupRange;
    }

    KeyGroupRange getTargetKeyGroupRange() {
        return targetKeyGroupRange;
    }

    protected TypedNamespaceAndKey getTypedNamespaceAndKey() {
        return new TypedNamespaceAndKey(currentNamespace, routeKey(currentKey));
    }

    Object routeKey(Object key) {
        if (maxParallelism <= 1) {
            return key;
        }
        // Stage 34: route via the stable key-group mapping. maxParallelism is
        // the job-global upper bound; the resulting id is wrapped as
        // ShardPrefixedKey.keyGroupId so the memory HashMap isolates keys by
        // group, mirroring the RocksDB binary key-group prefix.
        int keyGroupId = KeyGroupAssignment.assignToKeyGroup(key, maxParallelism);
        return new ShardPrefixedKey(keyGroupId, key);
    }

    @Override
    public StateSnapshot snapshotState() throws Exception {
        return new MemoryStateSerDe(this).snapshotState(states);
    }

    @Override
    public void restoreState(StateSnapshot snapshot) throws Exception {
        new MemoryStateSerDe(this).restoreState(states, snapshot);
        rebindStateBackends();
    }

    @Override
    public void registerRestoreAggregateFunction(String stateName,
                                                 AggregateFunction<?, ?, ?> function) {
        if (stateName == null || function == null) {
            return;
        }
        restoreAggregateFunctions.put(stateName, function);
    }

    @Override
    public AggregateFunction<?, ?, ?> getRestoreAggregateFunction(String stateName) {
        return restoreAggregateFunctions.get(stateName);
    }

    /**
     * item 21 D-3 convergence: every memory state class extends
     * {@link AbstractMemoryState}, so the former 8-way {@code instanceof}
     * ladder collapses to the supertype's {@code rebind}.
     */
    void rebindStateBackends() {
        for (Object stateObj : states.values()) {
            if (stateObj instanceof AbstractMemoryState) {
                ((AbstractMemoryState) stateObj).rebind(this);
            }
        }
    }
}
