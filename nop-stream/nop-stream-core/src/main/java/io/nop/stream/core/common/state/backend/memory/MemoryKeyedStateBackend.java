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
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.shard.StateShard;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

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

    private final int shardCount;

    private transient K currentKey;

    private transient Object currentNamespace = DEFAULT_NAMESPACE;

    private final Map<String, Object> states = new HashMap<>();

    private final Map<String, Class<?>> stateTypes = new HashMap<>();

    public MemoryKeyedStateBackend(Class<K> keyType) {
        this(keyType, 1);
    }

    public MemoryKeyedStateBackend(Class<K> keyType, int shardCount) {
        if (shardCount < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "shardCount").param(ARG_DETAIL, "must be at least 1");
        }
        this.keyType = keyType;
        this.shardCount = shardCount;
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
        }
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
        }
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
        }
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
        }
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
        }
        return state;
    }

    @Override
    public <N, IN, ACC> InternalAppendingState<K, N, IN, ACC, ACC> getInternalAppendingState(
            ReducingStateDescriptor<IN> descriptor) {
        @SuppressWarnings("unchecked")
        InternalAppendingState<K, N, IN, ACC, ACC> state =
                (InternalAppendingState<K, N, IN, ACC, ACC>) states.get(descriptor.getName());
        if (state == null) {
            state = new MemoryInternalAppendingState<>(this, descriptor);
            registerStateType(descriptor.getName(), InternalAppendingState.class);
            states.put(descriptor.getName(), state);
        }
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
        }
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
        }
        return state;
    }

    @Override
    public void close() {
        states.clear();
    }

    public Class<K> getKeyType() {
        return keyType;
    }

    int getShardCount() {
        return shardCount;
    }

    protected TypedNamespaceAndKey getTypedNamespaceAndKey() {
        return new TypedNamespaceAndKey(currentNamespace, routeKey(currentKey));
    }

    Object routeKey(Object key) {
        if (shardCount <= 1) {
            return key;
        }
        int shardId = (StateShard.stableHash(key) & 0x7FFFFFFF) % shardCount;
        return new ShardPrefixedKey(shardId, key);
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
