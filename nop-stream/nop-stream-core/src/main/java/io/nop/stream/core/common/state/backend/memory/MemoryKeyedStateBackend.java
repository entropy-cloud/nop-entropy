/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.nop.core.lang.json.JsonTool;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.backend.IInternalStateBackend;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.shard.ShardPrefixedKey;
import io.nop.stream.core.common.state.shard.StateShard;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;

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
     * 分片数量。当 shardCount > 1 时，key 会被路由到不同的分片。
     */
    private final int shardCount;

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
     * 构造函数（无分片，向后兼容）
     *
     * @param keyType key 的类型
     */
    public MemoryKeyedStateBackend(Class<K> keyType) {
        this(keyType, 1);
    }

    /**
     * 构造函数
     *
     * @param keyType    key 的类型
     * @param shardCount 分片数量，必须 >= 1
     */
    public MemoryKeyedStateBackend(Class<K> keyType, int shardCount) {
        if (shardCount < 1) {
            throw new IllegalArgumentException("shardCount must be at least 1");
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
    @SuppressWarnings("unchecked")
    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
        ListState<T> state = (ListState<T>) states.get(stateProperties.getName());
        if (state == null) {
            state = new MemoryListState<>(this, stateProperties);
            states.put(stateProperties.getName(), state);
        }
        return state;
    }

    @Override
    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
        throw new UnsupportedOperationException("Use getInternalAppendingState for namespace-aware reducing state");
    }

    @Override
    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
        @SuppressWarnings("unchecked")
        AggregatingState<IN, OUT> state = (AggregatingState<IN, OUT>) states.get(stateProperties.getName());
        if (state == null) {
            state = new MemoryAggregatingState<>(this, stateProperties);
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
     * 生成 namespace + key 的组合键，用于存储状态。
     * 当 shardCount > 1 时，key 会被路由到对应的分片。
     */
    protected TypedNamespaceAndKey getTypedNamespaceAndKey() {
        return new TypedNamespaceAndKey(currentNamespace, routeKey(currentKey));
    }

    /**
     * 将用户 key 路由为存储 key。
     * <ul>
     *   <li>shardCount == 1 时直接返回原 key（零开销）</li>
     *   <li>shardCount > 1 时返回 ShardPrefixedKey(shardId, key)</li>
     * </ul>
     */
    Object routeKey(Object key) {
        if (shardCount <= 1) {
            return key;
        }
        int shardId = Math.abs(StateShard.stableHash(key)) % shardCount;
        return new ShardPrefixedKey(shardId, key);
    }

    /**
     * 从存储 key 中还原出原始用户 key（用于 snapshot 序列化）
     */
    private Object unwrapStorageKey(Object storageKey) {
        if (storageKey instanceof ShardPrefixedKey) {
            return ((ShardPrefixedKey) storageKey).key;
        }
        return storageKey;
    }

    @Override
    public StateSnapshot snapshotState() throws Exception {
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
            } else if (stateObj instanceof MemoryInternalListState) {
                statesMap.put(stateName, snapshotListState((MemoryInternalListState<?, ?, ?>) stateObj));
            }
        }
        stateData.put("states", statesMap);

        return new StateSnapshot(stateData);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(StateSnapshot snapshot) throws Exception {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        Map<String, Object> stateData = snapshot.getStateData();
        Map<String, Object> statesMap = (Map<String, Object>) stateData.get("states");
        if (statesMap == null || statesMap.isEmpty()) {
            return;
        }

        states.clear();

        for (Map.Entry<String, Object> entry : statesMap.entrySet()) {
            String stateName = entry.getKey();
            Map<String, Object> stateInfo = (Map<String, Object>) entry.getValue();
            String stateType = (String) stateInfo.get("stateType");

            switch (stateType) {
                case "ValueState":
                    restoreValueState(stateName, stateInfo);
                    break;
                case "MapState":
                    restoreMapState(stateName, stateInfo);
                    break;
                case "AppendingState":
                    restoreAppendingState(stateName, stateInfo);
                    break;
                case "ListState":
                    restoreListState(stateName, stateInfo);
                    break;
                case "InternalListState":
                    restoreInternalListState(stateName, stateInfo);
                    break;
                default:
                    throw new IOException("Unknown state type: " + stateType);
            }
        }

        rebindStateBackends();
    }

    @SuppressWarnings("unchecked")
    private void restoreValueState(String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);

        ValueStateDescriptor<Object> descriptor = new ValueStateDescriptor<>(stateName, valueClass);
        MemoryValueState<Object> state = new MemoryValueState<>(this, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        routeKey(deserializeKey(e.get("key"))));
                Object value = deserializeValue(e.get("value"), valueClass);
                state.storage.put(nk, value);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreMapState(String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String keyTypeName = (String) stateInfo.get("mapKeyTypeName");
        if (keyTypeName == null) {
            keyTypeName = (String) stateInfo.get("mapKeyType");
        }
        Class<Object> mapKeyClass = keyTypeName != null ? (Class<Object>) Class.forName(keyTypeName) : null;

        MapStateDescriptor<Object, Object> descriptor = new MapStateDescriptor<>(stateName, mapKeyClass, valueClass);
        MemoryMapState<Object, Object> state = new MemoryMapState<>(this, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        routeKey(deserializeKey(e.get("key"))));
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
    private void restoreAppendingState(String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);
        String accumulatorTypeName = (String) stateInfo.get("accumulatorTypeName");
        if (accumulatorTypeName == null) {
            accumulatorTypeName = (String) stateInfo.get("accumulatorType");
        }
        Class<? extends SimpleAccumulator<Object>> accumulatorClass =
                (Class<? extends SimpleAccumulator<Object>>) Class.forName(accumulatorTypeName);

        ReducingStateDescriptor<Object> descriptor =
                new ReducingStateDescriptor<>(stateName, valueClass, accumulatorClass);
        MemoryInternalAppendingState<Object, Object, Object, Object> state =
                new MemoryInternalAppendingState<>(this, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        routeKey(deserializeKey(e.get("key"))));
                Object value = deserializeValue(e.get("value"), valueClass);
                state.storage.put(nk, value);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreListState(String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);

        ListStateDescriptor<Object> descriptor = new ListStateDescriptor<>(stateName, valueClass);
        MemoryListState<Object> state = new MemoryListState<>(this, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        routeKey(deserializeKey(e.get("key"))));
                List<Object> list = new ArrayList<>();
                List<Object> values = (List<Object>) e.get("listValue");
                if (values != null) {
                    for (Object v : values) {
                        list.add(deserializeValue(v, valueClass));
                    }
                }
                state.storage.put(nk, list);
            }
        }

        states.put(stateName, state);
    }

    @SuppressWarnings("unchecked")
    private void restoreInternalListState(String stateName, Map<String, Object> stateInfo) throws Exception {
        String valueTypeName = (String) stateInfo.get("valueTypeName");
        if (valueTypeName == null) {
            valueTypeName = (String) stateInfo.get("valueType");
        }
        Class<Object> valueClass = (Class<Object>) Class.forName(valueTypeName);

        ListStateDescriptor<Object> descriptor = new ListStateDescriptor<>(stateName, valueClass);
        MemoryInternalListState<Object, Object, Object> state =
                new MemoryInternalListState<>(this, descriptor);

        List<Map<String, Object>> entries = (List<Map<String, Object>>) stateInfo.get("entries");
        if (entries != null) {
            for (Map<String, Object> e : entries) {
                TypedNamespaceAndKey nk = new TypedNamespaceAndKey(
                        deserializeNamespace(e.get("namespace")),
                        routeKey(deserializeKey(e.get("key"))));
                List<Object> list = new ArrayList<>();
                List<Object> values = (List<Object>) e.get("listValue");
                if (values != null) {
                    for (Object v : values) {
                        list.add(deserializeValue(v, valueClass));
                    }
                }
                state.storage.put(nk, list);
            }
        }

        states.put(stateName, state);
    }

    private Map<String, Object> snapshotValueState(MemoryValueState<?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        info.put("valueType", state.descriptor.getValueType().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", e.getValue());
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
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ? extends Map<?, ?>> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            List<List<Object>> mapEntries = new ArrayList<>();
            for (Map.Entry<?, ?> me : e.getValue().entrySet()) {
                List<Object> pair = new ArrayList<>();
                pair.add(me.getKey());
                pair.add(me.getValue());
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
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("value", e.getValue());
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotListStateFromPublic(MemoryListState<?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ListState");
        info.put("valueType", state.descriptor.getValueType().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ? extends List<?>> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("listValue", new ArrayList<>(e.getValue()));
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
    }

    private Map<String, Object> snapshotListState(MemoryInternalListState<?, ?, ?> state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "InternalListState");
        info.put("valueType", state.descriptor.getValueType().getName());
        if (shardCount > 1) {
            info.put("shardCount", shardCount);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<TypedNamespaceAndKey, ? extends List<?>> e : state.storage.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", serializeNamespace(e.getKey().namespace));
            entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));
            entry.put("listValue", new ArrayList<>(e.getValue()));
            entries.add(entry);
        }
        info.put("entries", entries);
        return info;
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

    private Object deserializeKey(Object obj) {
        return obj;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Object obj, Class<T> type) {
        if (obj == null) {
            return null;
        }
        if (type.isInstance(obj)) {
            return (T) obj;
        }
        String json = JsonTool.serialize(obj, false);
        return JsonTool.parseBeanFromText(json, type);
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
            } else if (stateObj instanceof MemoryInternalListState) {
                ((MemoryInternalListState<?, ?, ?>) stateObj).rebind(this);
            }
        }
    }

    // ==================== 内部状态实现类 ====================

    /**
     * 内存 ListState 实现（基于字符串 namespace）
     * <p>
     * 使用 backend 的 getTypedNamespaceAndKey() 组合键，
     * 与 MemoryValueState 的存储模式一致。
     */
    private static class MemoryListState<T> implements ListState<T>, Serializable {
        private static final long serialVersionUID = 1L;

        private MemoryKeyedStateBackend<?> backend;
        private final ListStateDescriptor<T> descriptor;
        private final Map<TypedNamespaceAndKey, List<T>> storage = new HashMap<>();

        MemoryListState(MemoryKeyedStateBackend<?> backend, ListStateDescriptor<T> descriptor) {
            this.backend = backend;
            this.descriptor = descriptor;
        }

        void rebind(MemoryKeyedStateBackend<?> newBackend) {
            this.backend = newBackend;
        }

        @Override
        public Iterable<T> get() throws IOException {
            List<T> list = storage.get(backend.getTypedNamespaceAndKey());
            return list != null ? list : java.util.Collections.emptyList();
        }

        @Override
        public void add(T value) throws IOException {
            storage.computeIfAbsent(backend.getTypedNamespaceAndKey(), k -> new ArrayList<>()).add(value);
        }

        @Override
        public void addAll(Iterable<T> values) throws IOException {
            List<T> list = storage.computeIfAbsent(backend.getTypedNamespaceAndKey(), k -> new ArrayList<>());
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
            storage.put(backend.getTypedNamespaceAndKey(), newList);
        }

        @Override
        public void clear() {
            storage.remove(backend.getTypedNamespaceAndKey());
        }
    }

    /**
     * 内存 ValueState 实现
     */
    private static class MemoryValueState<T> implements ValueState<T>, Serializable {
        private static final long serialVersionUID = 1L;

        private MemoryKeyedStateBackend<?> backend;
        private final ValueStateDescriptor<T> descriptor;
        private final Map<TypedNamespaceAndKey, T> storage = new HashMap<>();

        MemoryValueState(MemoryKeyedStateBackend<?> backend, ValueStateDescriptor<T> descriptor) {
            this.backend = backend;
            this.descriptor = descriptor;
        }

        void rebind(MemoryKeyedStateBackend<?> newBackend) {
            this.backend = newBackend;
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

        private MemoryKeyedStateBackend<?> backend;
        private final MapStateDescriptor<UK, UV> descriptor;

        void rebind(MemoryKeyedStateBackend<?> newBackend) {
            this.backend = newBackend;
        }
        private final Map<TypedNamespaceAndKey, Map<UK, UV>> storage = new HashMap<>();

        MemoryMapState(MemoryKeyedStateBackend<?> backend, MapStateDescriptor<UK, UV> descriptor) {
            this.backend = backend;
            this.descriptor = descriptor;
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
     * 内存 AggregatingState 实现
     */
    private static class MemoryAggregatingState<IN, ACC, OUT> implements AggregatingState<IN, OUT>, Serializable {
        private static final long serialVersionUID = 1L;

        private MemoryKeyedStateBackend<?> backend;
        private final AggregatingStateDescriptor<IN, ACC, OUT> descriptor;
        private final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();

        MemoryAggregatingState(MemoryKeyedStateBackend<?> backend, AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
            this.backend = backend;
            this.descriptor = descriptor;
        }

        void rebind(MemoryKeyedStateBackend<?> newBackend) {
            this.backend = newBackend;
        }

        @Override
        @SuppressWarnings("unchecked")
        public OUT get() throws Exception {
            ACC accumulator = storage.get(backend.getTypedNamespaceAndKey());
            if (accumulator == null) {
                return null;
            }
            return descriptor.getAggregateFunction().getResult(accumulator);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void add(IN value) throws Exception {
            TypedNamespaceAndKey key = backend.getTypedNamespaceAndKey();
            AggregateFunction<IN, ACC, OUT> aggFn = descriptor.getAggregateFunction();
            ACC accumulator = storage.get(key);
            if (accumulator == null) {
                accumulator = aggFn.createAccumulator();
            }
            accumulator = aggFn.add(value, accumulator);
            storage.put(key, accumulator);
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

        private MemoryKeyedStateBackend<?> backend;
        private final ReducingStateDescriptor<IN> descriptor;
        private transient SimpleAccumulator<IN> accumulator;
        private final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();
        
        private transient N currentNamespace;

        @SuppressWarnings("unchecked")
        MemoryInternalAppendingState(MemoryKeyedStateBackend<?> backend, 
                ReducingStateDescriptor<IN> descriptor) {
            this.backend = backend;
            this.descriptor = descriptor;
            this.accumulator = createAccumulator();
        }

        private SimpleAccumulator<IN> createAccumulator() {
            try {
                return descriptor.getAccumulatorType().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create accumulator", e);
            }
        }

        void rebind(MemoryKeyedStateBackend<?> newBackend) {
            this.backend = newBackend;
            if (this.accumulator == null) {
                this.accumulator = createAccumulator();
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
            // Reset accumulator to avoid carrying over state from previous add() calls
            accumulator.resetLocal();
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
            if (currentNamespace == null) {
                throw new IllegalStateException(
                        "currentNamespace is null. Call setCurrentNamespace() before accessing state.");
            }
            return new TypedNamespaceAndKey(currentNamespace, backend.routeKey(backend.getCurrentKey()));
        }
    }

    /**
     * 内存 InternalListState 实现
     */
    private static class MemoryInternalListState<K, N, T> 
            implements InternalListState<K, N, T>, Serializable {
        private static final long serialVersionUID = 1L;

        private MemoryKeyedStateBackend<?> backend;
        private final ListStateDescriptor<T> descriptor;
        private final Map<TypedNamespaceAndKey, List<T>> storage = new HashMap<>();
        
        private transient N currentNamespace;

        MemoryInternalListState(MemoryKeyedStateBackend<?> backend, ListStateDescriptor<T> descriptor) {
            this.backend = backend;
            this.descriptor = descriptor;
        }

        void rebind(MemoryKeyedStateBackend<?> newBackend) {
            this.backend = newBackend;
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
            if (currentNamespace == null) {
                throw new IllegalStateException(
                        "currentNamespace is null. Call setCurrentNamespace() before accessing state.");
            }
            return new TypedNamespaceAndKey(currentNamespace, backend.routeKey(backend.getCurrentKey()));
        }
    }

    // ==================== 辅助类 ====================

    /**
     * 分片前缀键：将 shardId 与原始 key 组合，用于 HashMap 存储。
     * 仅当 shardCount > 1 时使用。
     */
    static class ShardPrefixedKey implements Serializable {
        private static final long serialVersionUID = 1L;

        final int shardId;
        final Object key;

        ShardPrefixedKey(int shardId, Object key) {
            this.shardId = shardId;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ShardPrefixedKey that = (ShardPrefixedKey) o;
            return shardId == that.shardId && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shardId, key);
        }

        @Override
        public String toString() {
            return shardId + "/" + key;
        }
    }

    /**
     * TypedNamespace + Key 的组合键
     * 支持泛型 namespace
     * <p>
     * Defensive: equals() and hashCode() guard against type mismatches that can
     * occur after JSON round-trip deserialization (e.g. Integer vs Long for the same
     * numeric key). If types differ but represent the same logical value, a WARN is
     * logged and the comparison falls through to false to avoid silent hash collisions.
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
            // Defensive: check for type mismatch between namespace keys to prevent
            // silent collisions after JSON deserialization (e.g. Integer vs Long)
            if (namespace != null && that.namespace != null
                    && !namespace.getClass().equals(that.namespace.getClass())) {
                return false;
            }
            if (key != null && that.key != null
                    && !key.getClass().equals(that.key.getClass())) {
                return false;
            }
            return Objects.equals(namespace, that.namespace) &&
                    Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            // Defensive: include runtime type in hash to avoid collisions between
            // keys of different types that have the same value representation
            int nsHash = namespace != null ? Objects.hash(namespace.getClass(), namespace) : 0;
            int keyHash = key != null ? Objects.hash(key.getClass(), key) : 0;
            return 31 * nsHash + keyHash;
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
