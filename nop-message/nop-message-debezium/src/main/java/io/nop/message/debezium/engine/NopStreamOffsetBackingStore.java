/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium.engine;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.storage.OffsetBackingStore;
import org.apache.kafka.connect.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory {@link OffsetBackingStore} for nop-stream CDC checkpoint integration.
 *
 * <p>Holds Debezium connector offsets in a {@code ConcurrentHashMap<ByteBuffer,ByteBuffer>}
 * keyed by source partition. {@code DebeziumCdcSourceFunction} participates in the nop-stream
 * checkpoint protocol by snapshotting the offsets held here and restoring them on recovery.
 *
 * <p><b>Wiring adaptation (Debezium 2.4.0 constraint)</b>: the Debezium 2.4.0
 * {@code DebeziumEngine.Builder} does not expose a {@code using(OffsetBackingStore)} method,
 * so the offset store cannot be passed directly to the engine builder. Instead the engine
 * instantiates the store declared by the {@code offset.storage} property via reflection (public
 * no-arg constructor + {@code configure(WorkerConfig)}). To bridge the instance created by the
 * source function (which pre-populates restored offsets) with the instance created by the engine
 * via reflection, this class keeps a <b>static registry keyed by connector name</b> that maps to a
 * shared data map. Both instances therefore observe the same backing map for a given connector.
 *
 * <p>See {@code ai-dev/design/nop-stream/connector-design.md} §5.4 for the full design rationale.
 */
public class NopStreamOffsetBackingStore implements OffsetBackingStore {

    private static final Logger LOG = LoggerFactory.getLogger(NopStreamOffsetBackingStore.class);

    /**
     * Static registry: connector name -> shared offset data map. The source function seeds this
     * map before the engine starts (via {@link #forConnector(String)} + {@link #setOffsets(Map)});
     * the engine-instantiated instance binds to the same map in {@link #configure(WorkerConfig)}.
     */
    private static final ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer, ByteBuffer>> REGISTRY =
            new ConcurrentHashMap<>();

    private volatile String connectorName;
    private volatile ConcurrentHashMap<ByteBuffer, ByteBuffer> data;
    private volatile boolean started = false;

    /**
     * Public no-arg constructor required for reflection-based instantiation by the Debezium
     * embedded engine. The connector-name binding happens later in {@link #configure(WorkerConfig)}.
     */
    public NopStreamOffsetBackingStore() {
    }

    /**
     * Factory used by {@code DebeziumCdcSourceFunction} to obtain a store bound to a connector
     * name. The returned instance shares its backing data map with any engine-instantiated
     * instance for the same connector name (via the static registry).
     *
     * @param connectorName the Debezium connector name (must not be null)
     * @return a store instance bound to the connector's shared data map
     */
    public static NopStreamOffsetBackingStore forConnector(String connectorName) {
        if (connectorName == null) {
            throw new IllegalArgumentException("connectorName must not be null");
        }
        ConcurrentHashMap<ByteBuffer, ByteBuffer> shared = REGISTRY.computeIfAbsent(connectorName, k -> new ConcurrentHashMap<>());
        NopStreamOffsetBackingStore store = new NopStreamOffsetBackingStore();
        store.connectorName = connectorName;
        store.data = shared;
        return store;
    }

    /**
     * Clears the registry entry for a connector name. Useful for test isolation.
     */
    public static void clearConnector(String connectorName) {
        REGISTRY.remove(connectorName);
    }

    @Override
    public void configure(WorkerConfig config) {
        String name = resolveConnectorName(config);
        this.connectorName = name;
        this.data = REGISTRY.computeIfAbsent(name, k -> new ConcurrentHashMap<>());
        LOG.debug("NopStreamOffsetBackingStore configured for connector '{}'", name);
    }

    private static String resolveConnectorName(WorkerConfig config) {
        if (config != null) {
            Map<String, Object> originals = config.originals();
            if (originals != null) {
                Object name = originals.get("name");
                if (name != null && !name.toString().isEmpty()) {
                    return name.toString();
                }
            }
        }
        return "_default_";
    }

    @Override
    public void start() {
        started = true;
        LOG.debug("NopStreamOffsetBackingStore started for connector '{}'", connectorName);
    }

    @Override
    public void stop() {
        started = false;
        LOG.debug("NopStreamOffsetBackingStore stopped for connector '{}'", connectorName);
    }

    public boolean isStarted() {
        return started;
    }

    public String getConnectorName() {
        return connectorName;
    }

    @Override
    public Future<Map<ByteBuffer, ByteBuffer>> get(Collection<ByteBuffer> keys) {
        ensureBound();
        Map<ByteBuffer, ByteBuffer> result = new HashMap<>();
        if (keys != null) {
            for (ByteBuffer key : keys) {
                ByteBuffer value = data.get(key);
                if (value != null) {
                    result.put(copy(key), copy(value));
                }
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public Future<Void> set(Map<ByteBuffer, ByteBuffer> values, Callback<Void> callback) {
        ensureBound();
        if (values != null) {
            for (Map.Entry<ByteBuffer, ByteBuffer> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    data.put(copy(entry.getKey()), copy(entry.getValue()));
                }
            }
        }
        if (callback != null) {
            callback.onCompletion(null, null);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Set<Map<String, Object>> connectorPartitions(String connector) {
        return Collections.emptySet();
    }

    /**
     * Pre-populates the store with restored offsets. Called by
     * {@code DebeziumCdcSourceFunction.initializeState} before the engine starts.
     */
    public void setOffsets(Map<ByteBuffer, ByteBuffer> offsets) {
        ensureBound();
        if (offsets == null) {
            return;
        }
        for (Map.Entry<ByteBuffer, ByteBuffer> entry : offsets.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                data.put(copy(entry.getKey()), copy(entry.getValue()));
            }
        }
    }

    /**
     * Returns a snapshot of the current offsets. Called by
     * {@code DebeziumCdcSourceFunction.snapshotState} to persist offsets into the checkpoint.
     */
    public Map<ByteBuffer, ByteBuffer> getOffsets() {
        ensureBound();
        Map<ByteBuffer, ByteBuffer> snapshot = new LinkedHashMap<>();
        for (Map.Entry<ByteBuffer, ByteBuffer> entry : data.entrySet()) {
            snapshot.put(copy(entry.getKey()), copy(entry.getValue()));
        }
        return snapshot;
    }

    private void ensureBound() {
        if (data == null) {
            String name = connectorName != null ? connectorName : "_default_";
            connectorName = name;
            data = REGISTRY.computeIfAbsent(name, k -> new ConcurrentHashMap<>());
        }
    }

    /**
     * Converts a {@code Map<ByteBuffer,ByteBuffer>} offset map into a serializable
     * {@code TreeMap<String,String>} (base64-encoded keys and values) suitable for checkpoint
     * persistence. {@code ByteBuffer} is not {@code Serializable}.
     */
    public static TreeMap<String, String> toSerializable(Map<ByteBuffer, ByteBuffer> offsets) {
        TreeMap<String, String> result = new TreeMap<>();
        if (offsets != null) {
            for (Map.Entry<ByteBuffer, ByteBuffer> entry : offsets.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(encodeBuffer(entry.getKey()), encodeBuffer(entry.getValue()));
                }
            }
        }
        return result;
    }

    /**
     * Reverses {@link #toSerializable(Map)} back into a {@code Map<ByteBuffer,ByteBuffer>}.
     */
    public static Map<ByteBuffer, ByteBuffer> fromSerializable(Map<String, String> serialized) {
        Map<ByteBuffer, ByteBuffer> result = new LinkedHashMap<>();
        if (serialized != null) {
            for (Map.Entry<String, String> entry : serialized.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(decodeBuffer(entry.getKey()), decodeBuffer(entry.getValue()));
                }
            }
        }
        return result;
    }

    private static String encodeBuffer(ByteBuffer buf) {
        ByteBuffer dup = buf.duplicate();
        byte[] arr = new byte[dup.remaining()];
        dup.get(arr);
        return Base64.getEncoder().encodeToString(arr);
    }

    private static ByteBuffer decodeBuffer(String encoded) {
        byte[] arr = Base64.getDecoder().decode(encoded);
        return ByteBuffer.wrap(arr);
    }

    private static ByteBuffer copy(ByteBuffer buf) {
        ByteBuffer dup = buf.duplicate();
        byte[] arr = new byte[dup.remaining()];
        dup.get(arr);
        return ByteBuffer.wrap(arr);
    }
}
