/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

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
import io.nop.stream.core.common.state.StateSchemaResolver;
import io.nop.stream.core.common.state.StateTtlConfig;
import io.nop.stream.core.common.state.SystemTtlTimeProvider;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.TtlTimeProvider;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
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
 * @param <K> key 的类型
 */
public class MemoryKeyedStateBackend<K> implements IInternalStateBackend<K>, Serializable {

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
     * Processing-time source used by all {@link TtlContext}s created in {@link #applyTtl}.
     * Tests inject a controllable clock here before calling {@code getState(...)} so TTL
     * expiry can be exercised deterministically without sleeping.
     */
    private TtlTimeProvider ttlClock = SystemTtlTimeProvider.INSTANCE;

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

    @Override
    public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
        @SuppressWarnings("unchecked")
        ValueState<T> state = (ValueState<T>) states.get(stateProperties.getName());
        if (state == null) {
            state = new MemoryValueState<>(this, stateProperties);
            registerStateType(stateProperties.getName(), ValueState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_VALUE,
                    stateProperties, ((MemoryValueState<?>) state).descriptor);
        }
        applyTtl(state, stateProperties);
        return state;
    }

    @Override
    public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
        @SuppressWarnings("unchecked")
        MapState<UK, UV> state = (MapState<UK, UV>) states.get(stateProperties.getName());
        if (state == null) {
            state = new MemoryMapState<>(this, stateProperties);
            registerStateType(stateProperties.getName(), MapState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_MAP,
                    stateProperties, ((MemoryMapState<?, ?>) state).descriptor);
        }
        applyTtl(state, stateProperties);
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
        ListState<T> state = (ListState<T>) states.get(stateProperties.getName());
        if (state == null) {
            state = new MemoryListState<>(this, stateProperties);
            registerStateType(stateProperties.getName(), ListState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_LIST,
                    stateProperties, ((MemoryListState<?>) state).descriptor);
        }
        applyTtl(state, stateProperties);
        return state;
    }

    @Override
    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
        @SuppressWarnings("unchecked")
        ReducingState<T> state = (ReducingState<T>) states.get(stateProperties.getName());
        if (state == null) {
            state = new MemoryReducingState<>(this, stateProperties);
            registerStateType(stateProperties.getName(), ReducingState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_REDUCING,
                    stateProperties, ((MemoryReducingState<?>) state).descriptor);
        }
        applyTtl(state, stateProperties);
        return state;
    }

    @Override
    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
        @SuppressWarnings("unchecked")
        AggregatingState<IN, OUT> state = (AggregatingState<IN, OUT>) states.get(stateProperties.getName());
        if (state == null) {
            state = new MemoryAggregatingState<>(this, stateProperties);
            registerStateType(stateProperties.getName(), AggregatingState.class);
            states.put(stateProperties.getName(), state);
        } else {
            verifySchemaCompatibility(stateProperties.getName(),
                    StateSchemaResolver.STATE_TYPE_AGGREGATING,
                    stateProperties, ((MemoryAggregatingState<?, ?, ?>) state).descriptor);
        }
        applyTtl(state, stateProperties);
        return state;
    }

    @Override
    public <N, IN> InternalAppendingState<K, N, IN, IN, IN> getInternalAppendingState(
            ReducingStateDescriptor<IN> descriptor) {
        @SuppressWarnings("unchecked")
        InternalAppendingState<K, N, IN, IN, IN> state =
                (InternalAppendingState<K, N, IN, IN, IN>) states.get(descriptor.getName());
        if (state == null) {
            state = new MemoryInternalAppendingState<>(this, descriptor);
            registerStateType(descriptor.getName(), InternalAppendingState.class);
            states.put(descriptor.getName(), state);
        } else {
            verifySchemaCompatibility(descriptor.getName(),
                    StateSchemaResolver.STATE_TYPE_APPENDING,
                    descriptor, ((MemoryInternalAppendingState<?, ?, ?, ?>) state).descriptor);
        }
        applyTtl(state, descriptor);
        return state;
    }

    @Override
    public <N, IN, ACC, OUT> InternalAppendingState<K, N, IN, ACC, OUT> getInternalAppendingState(
            AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        @SuppressWarnings("unchecked")
        InternalAppendingState<K, N, IN, ACC, OUT> state =
                (InternalAppendingState<K, N, IN, ACC, OUT>) states.get(descriptor.getName());
        if (state == null) {
            state = new MemoryInternalAggregatingState<>(this, descriptor);
            registerStateType(descriptor.getName(), InternalAppendingState.class);
            states.put(descriptor.getName(), state);
        } else {
            verifySchemaCompatibility(descriptor.getName(),
                    StateSchemaResolver.STATE_TYPE_INTERNAL_AGGREGATING,
                    descriptor, ((MemoryInternalAggregatingState<?, ?, ?, ?, ?>) state).descriptor);
        }
        applyTtl(state, descriptor);
        return state;
    }

    @Override
    public <N, T> InternalListState<K, N, T> getInternalListState(ListStateDescriptor<T> descriptor) {
        @SuppressWarnings("unchecked")
        InternalListState<K, N, T> state =
                (InternalListState<K, N, T>) states.get(descriptor.getName());
        if (state == null) {
            state = new MemoryInternalListState<>(this, descriptor);
            registerStateType(descriptor.getName(), InternalListState.class);
            states.put(descriptor.getName(), state);
        } else {
            verifySchemaCompatibility(descriptor.getName(),
                    StateSchemaResolver.STATE_TYPE_INTERNAL_LIST,
                    descriptor, ((MemoryInternalListState<?, ?, ?>) state).descriptor);
        }
        applyTtl(state, descriptor);
        return state;
    }

    /**
     * Stage 29: when {@code getState()} is called on an already-restored state, verify the current
     * descriptor's schema checksum matches the restored descriptor's schema checksum. Both
     * descriptors come from independent sources (current code vs checkpoint), so the comparison
     * is NOT tautological. If they differ, fail fast with {@code ERR_STREAM_STATE_SCHEMA_MISMATCH}.
     * Stage 33 will extend this path to consult registered {@code StateMigrationFunction}s.
     */
    private void verifySchemaCompatibility(String stateName, String stateType,
                                           StateDescriptor<?> currentDescriptor,
                                           StateDescriptor<?> restoredDescriptor) {
        SerializerFingerprint currentFp = StateSchemaResolver.fromDescriptor(stateType, currentDescriptor);
        SerializerFingerprint restoredFp = StateSchemaResolver.fromDescriptor(stateType, restoredDescriptor);
        if (!StateSchemaResolver.fingerprintsCompatible(currentFp, restoredFp)) {
            throw new StreamException(ERR_STREAM_STATE_SCHEMA_MISMATCH)
                    .param(ARG_STATE_NAME, stateName)
                    .param(ARG_EXPECTED_CHECKSUM, currentFp.getSchemaChecksum())
                    .param(ARG_ACTUAL_CHECKSUM, restoredFp.getSchemaChecksum());
        }
    }

    /**
     * Centralized TTL binding. Called from every {@code getState(...)} overload after the
     * lazy-create-or-verify step. When the live descriptor carries an enabled
     * {@link StateTtlConfig}, a fresh {@link TtlContext} is bound to the state object. This
     * covers both the freshly-created path and the restored path (rebinding TTL after
     * restore — see plan Phase 2 "TTL rebind on restore"): restored entries have a storage
     * value but no sidecar timestamp, and are granted a fresh TTL window on first access by
     * {@link TtlContext#readEviction}.
     */
    private void applyTtl(Object stateObj, StateDescriptor<?> descriptor) {
        if (!(stateObj instanceof TtlAware)) {
            return;
        }
        StateTtlConfig cfg = descriptor.getTtlConfig();
        if (!cfg.isEnabled()) {
            return;
        }
        ((TtlAware) stateObj).bindTtl(new TtlContext<>(cfg, ttlClock));
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
    int getMaxParallelism() {
        return maxParallelism;
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

    void rebindStateBackends() {
        for (Map.Entry<String, Object> entry : states.entrySet()) {
            Object stateObj = entry.getValue();
            if (stateObj instanceof MemoryValueState) {
                ((MemoryValueState<?>) stateObj).rebind(this);
            } else if (stateObj instanceof MemoryMapState) {
                ((MemoryMapState<?, ?>) stateObj).rebind(this);
            } else if (stateObj instanceof MemoryListState) {
                ((MemoryListState<?>) stateObj).rebind(this);
            } else if (stateObj instanceof MemoryInternalAppendingState) {
                ((MemoryInternalAppendingState<?, ?, ?, ?>) stateObj).rebind(this);
            } else if (stateObj instanceof MemoryInternalAggregatingState) {
                ((MemoryInternalAggregatingState<?, ?, ?, ?, ?>) stateObj).rebind(this);
            } else if (stateObj instanceof MemoryInternalListState) {
                ((MemoryInternalListState<?, ?, ?>) stateObj).rebind(this);
            } else if (stateObj instanceof MemoryReducingState) {
                ((MemoryReducingState<?>) stateObj).rebind(this);
            } else if (stateObj instanceof MemoryAggregatingState) {
                ((MemoryAggregatingState<?, ?, ?>) stateObj).rebind(this);
            }
        }
    }
}
