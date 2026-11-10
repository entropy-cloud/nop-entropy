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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.nop.api.core.exceptions.NopException;
import io.nop.message.debezium.DebeziumErrors;

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
 * <p><b>Lifecycle contract (plan 2503 terminal ruling, plan 2558 裁定 B — MA-03 + C1-002 same
 * diff)</b>: each registry entry is a {@link Bucket} holder object that carries the shared data
 * map, a binding generation, and the recovery declaration window state.
 *
 * <ul>
 *   <li><b>Paired release</b>: the bucket is released by the stop contract — the single
 *       release point is {@code DebeziumEngineWrapper.stop()} (its {@code finally}, so the
 *       release also happens when termination verification fails). Bucket lifetime = one
 *       engine-binding generation + explicit recovery declarations; stop releases the entry,
 *       closing the unbounded-ledger face (C5-001) and the silent stale-resume face (C6-004).
 *       {@code DebeziumMessageSource.stop()} deliberately does NOT release (no double
 *       counting).</li>
 *   <li><b>Conditional removal</b>: {@link #clearConnector(String)} only removes an entry with
 *       no live engine binding; removal against a live binding is rejected typed (C1-002
 *       split-write face). The nop-stream first-run {@code clearConnector → forConnector}
 *       same-thread sequence stays legal (no live binding at that point).</li>
 *   <li><b>Orphan writes</b>: after release, old-generation instances still holding the
 *       released bucket reference fail typed on every data operation (writes die fast and
 *       visibly instead of silently merging into a newer generation's bucket).</li>
 *   <li><b>Recovery declaration window</b>: {@link #setOffsets(Map)} keeps its REPLACE
 *       semantics (R5 CON-03: the nop checkpoint is the single source of truth) and is legal
 *       only inside the window after bucket creation/binding and before any binding instance
 *       starts; out-of-window calls are rejected typed.</li>
 *   <li><b>Residency diagnostics</b>: {@link #residencySnapshot()} exposes the bucket count
 *       and per-bucket key count (mandatory observability item of the contract).</li>
 * </ul>
 *
 * <p>See {@code ai-dev/design/nop-stream/connector-design.md} §5.4 for the bridge rationale and
 * {@code ai-dev/design/concurrency/registry-lifecycle-contract.md} for the lifecycle contract.
 */
public class NopStreamOffsetBackingStore implements OffsetBackingStore {

    private static final Logger LOG = LoggerFactory.getLogger(NopStreamOffsetBackingStore.class);

    /**
     * Registry entry holder (plan 2558 裁定 B): the bucket owns the shared offset data map
     * plus the binding-generation state (live binding count, release flag, recovery window).
     */
    static final class Bucket {
        final String connectorName;
        final ConcurrentHashMap<ByteBuffer, ByteBuffer> data = new ConcurrentHashMap<>();
        /** Live engine bindings of the current generation (instances that have started()). */
        final AtomicInteger liveBindings = new AtomicInteger();
        /**
         * Set when the stop contract releases the entry. Old-generation instances holding
         * this bucket are orphans afterwards; their data operations fail typed.
         */
        final AtomicBoolean released = new AtomicBoolean(false);
        /**
         * Recovery declaration window: open at bucket creation; ANY binding instance start()
         * closes it; the window state dies with the generation (release).
         */
        volatile boolean recoveryWindowOpen = true;

        Bucket(String connectorName) {
            this.connectorName = connectorName;
        }
    }

    /**
     * Static registry: connector name -> bucket holder. The source function seeds this map
     * before the engine starts (via {@link #forConnector(String)} + {@link #setOffsets(Map)});
     * the engine-instantiated instance binds to the same bucket in {@link #configure(WorkerConfig)}.
     */
    private static final ConcurrentHashMap<String, Bucket> REGISTRY = new ConcurrentHashMap<>();

    private volatile String connectorName;
    private volatile Bucket bucket;
    private volatile boolean started = false;
    /** Whether THIS instance holds a live binding of its bucket (incremented at start). */
    private volatile boolean bindingHeld = false;

    /**
     * Public no-arg constructor required for reflection-based instantiation by the Debezium
     * embedded engine. The connector-name binding happens later in {@link #configure(WorkerConfig)}.
     */
    public NopStreamOffsetBackingStore() {
    }

    /**
     * Factory used by {@code DebeziumCdcSourceFunction} to obtain a store bound to a connector
     * name. The returned instance shares its backing data map with any engine-instantiated
     * instance for the same connector name (via the static registry). Bucket creation registers
     * the current binding generation.
     *
     * @param connectorName the Debezium connector name (must not be null)
     * @return a store instance bound to the connector's shared data map
     */
    public static NopStreamOffsetBackingStore forConnector(String connectorName) {
        if (connectorName == null) {
            throw new IllegalArgumentException("connectorName must not be null");
        }
        Bucket shared = REGISTRY.computeIfAbsent(connectorName, Bucket::new);
        NopStreamOffsetBackingStore store = new NopStreamOffsetBackingStore();
        store.connectorName = connectorName;
        store.bucket = shared;
        return store;
    }

    /**
     * Clears the registry entry for a connector name, discarding any offsets a previous run in
     * the same JVM may have left behind.
     *
     * <p><b>Call semantics</b>: {@code DebeziumCdcSourceFunction.initializeState} invokes this on
     * its first-run paths (state == null or no persisted offset entry) so a brand-new job never
     * resumes from the stale static offset of a crashed/redeployed/reused connector name
     * (AR-03). Test isolation is a secondary use.
     *
     * <p><b>Conditional removal (plan 2558 裁定 B / C1-002)</b>: removal is only legal when the
     * entry has no live engine binding. A removal against a live binding is rejected typed —
     * a pre-fix unconditional remove split the live instances' writes into an orphan map that
     * silently lost entries. The nop-stream first-run {@code clearConnector → forConnector}
     * same-thread sequence stays legal (no binding has started at that point). Test isolation
     * cleanups after fully-stopped stores stay legal too (their bindings were released at
     * {@link #stop()}).
     */
    public static void clearConnector(String connectorName) {
        REGISTRY.compute(connectorName, (name, current) -> {
            if (current != null && current.liveBindings.get() > 0) {
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_OFFSET_STORE_LIVE_BINDING)
                        .param("connector", name)
                        .param("liveBindings", current.liveBindings.get());
            }
            // remove the entry (null return) only when absent or binding-free
            return null;
        });
    }

    /**
     * Stop-contract paired release (plan 2503 裁定①, plan 2558 裁定 B): removes the registry
     * entry and marks its bucket released. The single production release point is
     * {@code DebeziumEngineWrapper.stop()} — in its {@code finally}, so the release also runs
     * when the termination verification failed (anti-overwrite takes priority over
     * conservative residency). Old-generation instances keep their bucket reference and turn
     * into orphans: their subsequent data operations fail typed (never silently merge into a
     * newer generation's bucket). Unconditional by design — this is the lifecycle pairing
     * point, not a conditional cleanup.
     */
    public static void releaseConnector(String connectorName) {
        if (connectorName == null) {
            return;
        }
        Bucket released = REGISTRY.remove(connectorName);
        if (released != null && released.released.compareAndSet(false, true)) {
            released.recoveryWindowOpen = false;
            LOG.info("nop.message.debezium.offset-registry-release:connector={},keys={} - "
                    + "bucket released by the stop contract (paired release)",
                    connectorName, released.data.size());
        }
    }

    /**
     * Residency diagnostics (plan 2503 裁定⑥ mandatory item): connector name -> number of
     * offset keys currently resident in its registry bucket. The bucket count is
     * {@code snapshot().size()}; the growth upper bound is the number of concurrently active
     * connector names (buckets die at the stop-contract paired release).
     */
    public static Map<String, Integer> residencySnapshot() {
        Map<String, Integer> snapshot = new TreeMap<>();
        for (Map.Entry<String, Bucket> entry : REGISTRY.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().data.size());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public void configure(WorkerConfig config) {
        String name = resolveConnectorName(config);
        this.connectorName = name;
        this.bucket = REGISTRY.computeIfAbsent(name, Bucket::new);
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
        // AR-03: an unnamed connector silently binds to the shared "_default_" bucket and
        // overwrites other unnamed connectors' offsets. Fail fast with a clear message instead.
        throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONNECTOR_NAME_REQUIRED)
                .param("connector", "_default_");
    }

    @Override
    public void start() {
        started = true;
        Bucket b = this.bucket;
        if (b != null) {
            if (b.released.get()) {
                // orphan generation starting (ghost scenario): observable, no counter bump
                LOG.warn("nop.message.debezium.offset-registry-orphan-start:connector={} - "
                        + "a store instance started against a released bucket (orphan generation)",
                        b.connectorName);
            } else {
                b.liveBindings.incrementAndGet();
                bindingHeld = true;
                // recovery declaration window: any binding start seals it for the generation
                b.recoveryWindowOpen = false;
            }
        }
        LOG.debug("NopStreamOffsetBackingStore started for connector '{}'", connectorName);
    }

    @Override
    public void stop() {
        started = false;
        // release THIS instance's live binding (conditional removal then becomes legal);
        // the bucket itself is released by the stop contract (releaseConnector), not here —
        // the single release point is DebeziumEngineWrapper.stop().
        Bucket b = this.bucket;
        if (b != null && bindingHeld) {
            bindingHeld = false;
            b.liveBindings.updateAndGet(count -> Math.max(0, count - 1));
        }
        LOG.debug("NopStreamOffsetBackingStore stopped for connector '{}'", connectorName);
    }

    public boolean isStarted() {
        return started;
    }

    public String getConnectorName() {
        return connectorName;
    }

    /**
     * Resolves the active bucket for a data operation: lazily binds an unnamed instance on
     * first use and fails typed when the bound bucket was already released (orphan
     * generation — its writes must never merge into a newer generation's bucket).
     */
    private Bucket activeBucket() {
        Bucket b = bucket;
        if (b == null) {
            String name = connectorName;
            if (name == null || name.isEmpty()) {
                // AR-03: never fall back to the shared "_default_" bucket silently — an unnamed
                // connector would overwrite other connectors' offsets in the same JVM.
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONNECTOR_NAME_REQUIRED)
                        .param("connector", "_default_");
            }
            connectorName = name;
            b = REGISTRY.computeIfAbsent(name, Bucket::new);
            bucket = b;
        }
        if (b.released.get()) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_OFFSET_STORE_ORPHANED)
                    .param("connector", b.connectorName);
        }
        return b;
    }

    @Override
    public Future<Map<ByteBuffer, ByteBuffer>> get(Collection<ByteBuffer> keys) {
        ConcurrentHashMap<ByteBuffer, ByteBuffer> data = activeBucket().data;
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
        ConcurrentHashMap<ByteBuffer, ByteBuffer> data = activeBucket().data;
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
     *
     * <p>REPLACE semantics are kept verbatim (R5 CON-03, soft-pin #5: the nop checkpoint is
     * the single source of truth for the restored offset map — merge would let checkpoint-
     * absent partition keys survive). plan 2558 裁定 B adds the recovery declaration window:
     * legal only after bucket binding and before any binding instance started; out-of-window
     * calls are rejected typed (a mid-run restore would roll the running engine's fresh
     * writes back to the recovery values).
     */
    public void setOffsets(Map<ByteBuffer, ByteBuffer> offsets) {
        Bucket b = activeBucket();
        if (!b.recoveryWindowOpen) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_OFFSET_RECOVERY_WINDOW_CLOSED)
                    .param("connector", b.connectorName);
        }
        if (offsets == null) {
            return;
        }
        // R5 CON-03: REPLACE semantics — the nop checkpoint is the single source
        // of truth for the restored offset map. The previous put-merge let
        // registry entries from partitions absent in the checkpoint (newer
        //分区 keys surviving a same-JVM restore from an OLDER checkpoint)
        // silently win, skipping reads past the checkpoint position.
        b.data.clear();
        for (Map.Entry<ByteBuffer, ByteBuffer> entry : offsets.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                b.data.put(copy(entry.getKey()), copy(entry.getValue()));
            }
        }
    }

    /**
     * Returns a snapshot of the current offsets. Called by
     * {@code DebeziumCdcSourceFunction.snapshotState} to persist offsets into the checkpoint.
     */
    public Map<ByteBuffer, ByteBuffer> getOffsets() {
        ConcurrentHashMap<ByteBuffer, ByteBuffer> data = activeBucket().data;
        Map<ByteBuffer, ByteBuffer> snapshot = new LinkedHashMap<>();
        for (Map.Entry<ByteBuffer, ByteBuffer> entry : data.entrySet()) {
            snapshot.put(copy(entry.getKey()), copy(entry.getValue()));
        }
        return snapshot;
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
