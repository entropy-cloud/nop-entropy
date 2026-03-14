/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IInternalStateBackend;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /**
     * key 的类型，用于 JSON 序列化
     */
    private final Class<K> keyType;

    /**
     * 当前 key
     */
    private transient K currentKey;

    /**
     * 当前 namespace（支持泛型）
     */
    private transient Object currentNamespace = DEFAULT_NAMESPACE;

    /**
     * 已创建的状态实例缓存
     * key: stateName, value: State 实例
     */
    private final Map<String, Object> states = new HashMap<>();

    /**
     * 构造函数
     *
     * @param keyType key 的类型
     */
    public MemoryKeyedStateBackend(Class<K> keyType) {
        this.keyType = keyType;
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

    /**
     * 设置当前 namespace（泛型版本）
     * 用于 Window 等需要泛型 namespace 的场景
     *
     * @param namespace namespace 对象
     * @param <N> namespace 类型
     */
    public <N> void setTypedNamespace(N namespace) {
        this.currentNamespace = namespace != null ? namespace : DEFAULT_NAMESPACE;
    }

    /**
     * 获取当前泛型 namespace
     *
     * @param <N> namespace 类型
     * @return 当前 namespace
     */
    @SuppressWarnings("unchecked")
    public <N> N getTypedNamespace() {
        return (N) currentNamespace;
    }

    @Override
    public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
        @SuppressWarnings("unchecked")
        ValueState<T> state = (ValueState<T>) states.get(stateProperties.getName());
        if (state == null) {
            state = new MemoryValueState<>(this, stateProperties);
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
            states.put(descriptor.getName(), state);
        }
        return state;
    }

    @Override
    public void close() {
        states.clear();
    }

    /**
     * 获取 key 的类型
     */
    public Class<K> getKeyType() {
        return keyType;
    }

    /**
     * 生成 namespace + key 的组合键，用于存储状态
     */
    protected TypedNamespaceAndKey getTypedNamespaceAndKey() {
        return new TypedNamespaceAndKey(currentNamespace, currentKey);
    }

    // ==================== 内部状态实现类 ====================

    /**
     * 内存 ValueState 实现
     */
    private static class MemoryValueState<T> implements ValueState<T>, Serializable {
        private static final long serialVersionUID = 1L;

        private final MemoryKeyedStateBackend<?> backend;
        private final ValueStateDescriptor<T> descriptor;
        private final Map<TypedNamespaceAndKey, T> storage = new HashMap<>();

        MemoryValueState(MemoryKeyedStateBackend<?> backend, ValueStateDescriptor<T> descriptor) {
            this.backend = backend;
            this.descriptor = descriptor;
        }

        @Override
        public T value() throws IOException {
            T result = storage.get(backend.getTypedNamespaceAndKey());
            return result != null ? result : descriptor.getDefaultValue();
        }

        @Override
        public void update(T value) throws IOException {
            if (value == null) {
                clear();
            } else {
                storage.put(backend.getTypedNamespaceAndKey(), value);
            }
        }

        @Override
        public void clear() {
            storage.remove(backend.getTypedNamespaceAndKey());
        }
    }

    /**
     * 内存 MapState 实现
     */
    private static class MemoryMapState<UK, UV> implements MapState<UK, UV>, Serializable {
        private static final long serialVersionUID = 1L;

        private final MemoryKeyedStateBackend<?> backend;
        private final Map<TypedNamespaceAndKey, Map<UK, UV>> storage = new HashMap<>();

        MemoryMapState(MemoryKeyedStateBackend<?> backend, MapStateDescriptor<UK, UV> descriptor) {
            this.backend = backend;
        }

        private Map<UK, UV> getOrCreateMap() {
            return storage.computeIfAbsent(backend.getTypedNamespaceAndKey(), k -> new HashMap<>());
        }

        private Map<UK, UV> getMap() {
            return storage.get(backend.getTypedNamespaceAndKey());
        }

        @Override
        public UV get(UK key) {
            Map<UK, UV> map = getMap();
            return map != null ? map.get(key) : null;
        }

        @Override
        public void put(UK key, UV value) {
            getOrCreateMap().put(key, value);
        }

        @Override
        public void putAll(Map<UK, UV> map) {
            getOrCreateMap().putAll(map);
        }

        @Override
        public void remove(UK key) {
            Map<UK, UV> map = getMap();
            if (map != null) {
                map.remove(key);
            }
        }

        @Override
        public boolean contains(UK key) {
            Map<UK, UV> map = getMap();
            return map != null && map.containsKey(key);
        }

        @Override
        public Iterable<Map.Entry<UK, UV>> entries() {
            Map<UK, UV> map = getMap();
            return map != null ? map.entrySet() : java.util.Collections.emptyList();
        }

        @Override
        public Iterable<UK> keys() {
            Map<UK, UV> map = getMap();
            return map != null ? map.keySet() : java.util.Collections.emptyList();
        }

        @Override
        public Iterable<UV> values() {
            Map<UK, UV> map = getMap();
            return map != null ? map.values() : java.util.Collections.emptyList();
        }

        @Override
        public java.util.Iterator<Map.Entry<UK, UV>> iterator() {
            Map<UK, UV> map = getMap();
            return map != null ? map.entrySet().iterator() : java.util.Collections.emptyIterator();
        }

        @Override
        public boolean isEmpty() {
            Map<UK, UV> map = getMap();
            return map == null || map.isEmpty();
        }

        @Override
        public void clear() {
            storage.remove(backend.getTypedNamespaceAndKey());
        }
    }

    /**
     * 内存 InternalAppendingState 实现
     * 使用 SimpleAccumulator 进行元素累积
     */
    private static class MemoryInternalAppendingState<K, N, IN, ACC> 
            implements InternalAppendingState<K, N, IN, ACC, ACC>, Serializable {
        private static final long serialVersionUID = 1L;

        private final MemoryKeyedStateBackend<?> backend;
        private final ReducingStateDescriptor<IN> descriptor;
        private final SimpleAccumulator<IN> accumulator;
        private final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();
        
        private transient N currentNamespace;

        @SuppressWarnings("unchecked")
        MemoryInternalAppendingState(MemoryKeyedStateBackend<?> backend, 
                ReducingStateDescriptor<IN> descriptor) {
            this.backend = backend;
            this.descriptor = descriptor;
            try {
                this.accumulator = descriptor.getAccumulatorType().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create accumulator", e);
            }
        }

        @Override
        public void setCurrentNamespace(N namespace) {
            this.currentNamespace = namespace;
        }

        @Override
        public N getCurrentNamespace() {
            return currentNamespace;
        }

        @Override
        public ACC getAccumulator() throws Exception {
            TypedNamespaceAndKey key = getStorageKey();
            return storage.get(key);
        }

        @Override
        public ACC get() throws IOException {
            try {
                return getAccumulator();
            } catch (Exception e) {
                throw new IOException("Failed to get accumulator", e);
            }
        }

        @Override
        public void add(IN value) throws IOException {
            TypedNamespaceAndKey key = getStorageKey();
            @SuppressWarnings("unchecked")
            ACC current = storage.get(key);
            // 重置 accumulator 并添加当前值
            if (current != null) {
                accumulator.add((IN) current);
            }
            accumulator.add(value);
            storage.put(key, (ACC) accumulator.getLocalValue());
        }

        @Override
        public void clear() {
            storage.remove(getStorageKey());
        }

        private TypedNamespaceAndKey getStorageKey() {
            return new TypedNamespaceAndKey(currentNamespace, backend.getCurrentKey());
        }
    }

    /**
     * 内存 InternalListState 实现
     */
    private static class MemoryInternalListState<K, N, T> 
            implements InternalListState<K, N, T>, Serializable {
        private static final long serialVersionUID = 1L;

        private final MemoryKeyedStateBackend<?> backend;
        private final ListStateDescriptor<T> descriptor;
        private final Map<TypedNamespaceAndKey, List<T>> storage = new HashMap<>();
        
        private transient N currentNamespace;

        MemoryInternalListState(MemoryKeyedStateBackend<?> backend, ListStateDescriptor<T> descriptor) {
            this.backend = backend;
            this.descriptor = descriptor;
        }

        @Override
        public void setCurrentNamespace(N namespace) {
            this.currentNamespace = namespace;
        }

        @Override
        public N getCurrentNamespace() {
            return currentNamespace;
        }

        @Override
        public Iterable<T> get() throws IOException {
            List<T> list = storage.get(getStorageKey());
            return list != null ? list : java.util.Collections.emptyList();
        }

        @Override
        public void add(T value) throws IOException {
            storage.computeIfAbsent(getStorageKey(), k -> new ArrayList<>()).add(value);
        }

        @Override
        public void addAll(Iterable<T> values) throws IOException {
            List<T> list = storage.computeIfAbsent(getStorageKey(), k -> new ArrayList<>());
            for (T value : values) {
                list.add(value);
            }
        }

        @Override
        public void update(Iterable<T> values) throws IOException {
            List<T> newList = new ArrayList<>();
            for (T value : values) {
                newList.add(value);
            }
            storage.put(getStorageKey(), newList);
        }

        @Override
        public void clear() {
            storage.remove(getStorageKey());
        }

        private TypedNamespaceAndKey getStorageKey() {
            return new TypedNamespaceAndKey(currentNamespace, backend.getCurrentKey());
        }
    }

    // ==================== 辅助类 ====================

    /**
     * TypedNamespace + Key 的组合键
     * 支持泛型 namespace
     */
    protected static class TypedNamespaceAndKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Object namespace;
        private final Object key;

        TypedNamespaceAndKey(Object namespace, Object key) {
            this.namespace = namespace;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TypedNamespaceAndKey that = (TypedNamespaceAndKey) o;
            return Objects.equals(namespace, that.namespace) &&
                    Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(namespace, key);
        }

        @Override
        public String toString() {
            return "TypedNamespaceAndKey{" +
                    "namespace=" + namespace +
                    ", key=" + key +
                    '}';
        }
    }
}
