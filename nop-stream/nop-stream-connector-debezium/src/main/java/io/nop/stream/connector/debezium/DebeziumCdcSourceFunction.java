/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.DrainableSource;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * CDC source function wrapping the embedded Debezium engine.
 *
 * <p>Implements {@link CheckpointedSourceFunction} so the CDC consumption offset participates in
 * the nop-stream checkpoint protocol. On checkpoint the Debezium offset map held by a
 * {@link NopStreamOffsetBackingStore} is snapshotted into the operator state under key
 * {@value #CDC_OFFSETS_KEY}; on recovery the offset map is restored into a freshly created store
 * so the engine resumes from the checkpointed position (no duplicates, no data loss).
 *
 * <p>See {@code ai-dev/design/nop-stream/connector-design.md} §5.4 for the full design rationale.
 */
public class DebeziumCdcSourceFunction implements DrainableSource<ChangeEvent>,
        CheckpointedSourceFunction<ChangeEvent> {

    private static final long serialVersionUID = 1L;

    /**
     * Operator-state key under which the CDC offset map is persisted in the checkpoint snapshot.
     */
    public static final String CDC_OFFSETS_KEY = "cdc-offsets";

    /**
     * Debezium connector configuration. No longer {@code transient}: {@link DebeziumConfig}
     * implements {@link java.io.Serializable}, so the connection info survives cross-JVM recovery.
     */
    private DebeziumConfig config;

    private volatile boolean running = true;
    private volatile boolean draining = false;
    private final AtomicBoolean runEntered = new AtomicBoolean(false);
    private transient volatile CountDownLatch completionLatch;
    private volatile DebeziumMessageSource source;
    private volatile ICancellable subscription;

    /**
     * Offset backing store backing the checkpoint round-trip. {@code transient} because it is
     * rebuilt by {@link #initializeState(TaskStateSnapshot)} on recovery (the offset data itself
     * is carried in the checkpoint, not in the serialized source instance).
     */
    private transient NopStreamOffsetBackingStore offsetStore;

    public DebeziumCdcSourceFunction(DebeziumConfig config) {
        if (config == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "config");
        }
        this.config = config;
        this.completionLatch = new CountDownLatch(1);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (config == null) {
            config = new DebeziumConfig();
        }
        completionLatch = new CountDownLatch(1);
    }

    /**
     * Returns the offset store currently bound to this source (may be null before
     * {@link #initializeState(TaskStateSnapshot)} runs). Primarily for tests.
     */
    public NopStreamOffsetBackingStore getOffsetStore() {
        return offsetStore;
    }

    private void initCompletionLatch() {
        if (completionLatch == null) {
            synchronized (this) {
                if (completionLatch == null) {
                    completionLatch = new CountDownLatch(1);
                }
            }
        }
    }

    /**
     * Creates the {@link DebeziumMessageSource} used by {@link #run(SourceContext)}. Protected so
     * tests can inject a mock/test-double source without spinning up a real Debezium engine.
     *
     * @param config      the Debezium configuration
     * @param offsetStore the offset store (may be null on first run with no prior checkpoint)
     * @return a new message source wired to the offset store
     */
    protected DebeziumMessageSource createMessageSource(DebeziumConfig config,
                                                        NopStreamOffsetBackingStore offsetStore) {
        return new DebeziumMessageSource(config, offsetStore);
    }

    @Override
    public void run(SourceContext<ChangeEvent> ctx) throws Exception {
        if (!runEntered.compareAndSet(false, true)) {
            return;
        }
        this.draining = false;
        initCompletionLatch();

        try {
            if (!draining) {
                source = createMessageSource(config, offsetStore);
                try {
                    subscription = source.subscribe(ctx::collect);
                } catch (Exception e) {
                    source.stop();
                    throw e;
                }
            }

            while (running && !draining) {
                if (completionLatch.await(1, TimeUnit.SECONDS)) {
                    break;
                }
            }
        } finally {
            if (subscription != null) {
                try {
                    subscription.cancel();
                } catch (Exception e) {
                    // ignore cleanup errors
                }
                subscription = null;
            }
            if (source != null) {
                try {
                    source.stop();
                } catch (Exception e) {
                    // ignore cleanup errors
                }
                source = null;
            }
            runEntered.set(false);
        }
    }

    @Override
    public void cancel() {
        running = false;
        if (completionLatch != null) {
            completionLatch.countDown();
        }
        if (subscription != null) {
            subscription.cancel();
        }
        if (source != null) {
            source.stop();
        }
    }

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.REPLAYABLE;
    }

    @Override
    public void truncateForDrain() throws Exception {
        draining = true;
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
        if (source != null) {
            source.stop();
            source = null;
        }
        if (completionLatch != null) {
            completionLatch.countDown();
        }
    }

    public boolean isDraining() {
        return draining;
    }

    // ---- CheckpointedSourceFunction ----

    @Override
    public OperatorSnapshotResult snapshotState(long checkpointId) throws Exception {
        OperatorSnapshotResult result = new OperatorSnapshotResult();
        result.setCheckpointId(checkpointId);

        NopStreamOffsetBackingStore store = this.offsetStore;
        if (store == null) {
            // No offset store bound (e.g. snapshot before run/initializeState). Persist an empty
            // offset map so restore sees a well-formed entry rather than a missing one.
            result.putOperatorState(CDC_OFFSETS_KEY, new java.util.TreeMap<>());
            return result;
        }

        Map<java.nio.ByteBuffer, java.nio.ByteBuffer> offsets = store.getOffsets();
        result.putOperatorState(CDC_OFFSETS_KEY, NopStreamOffsetBackingStore.toSerializable(offsets));
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initializeState(TaskStateSnapshot state) throws Exception {
        if (state == null) {
            // First run: no prior checkpoint. Create an empty store so the engine starts fresh.
            this.offsetStore = NopStreamOffsetBackingStore.forConnector(resolveConnectorName());
            return;
        }

        Object raw = state.getOperatorState(CDC_OFFSETS_KEY);
        if (raw == null) {
            // Prior checkpoint existed but carried no CDC offset entry. Start fresh rather than
            // silently dropping the offset: bind an empty store.
            this.offsetStore = NopStreamOffsetBackingStore.forConnector(resolveConnectorName());
            return;
        }

        if (!(raw instanceof Map)) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_STATE_NAME, CDC_OFFSETS_KEY)
                    .param(ARG_DETAIL, "CDC offset state is not a Map: " + raw.getClass().getName());
        }

        Map<String, String> serialized = (Map<String, String>) raw;
        Map<java.nio.ByteBuffer, java.nio.ByteBuffer> restored =
                NopStreamOffsetBackingStore.fromSerializable(serialized);

        this.offsetStore = NopStreamOffsetBackingStore.forConnector(resolveConnectorName());
        this.offsetStore.setOffsets(restored);
    }

    private String resolveConnectorName() {
        DebeziumConfig cfg = this.config;
        if (cfg != null && cfg.getName() != null && !cfg.getName().isEmpty()) {
            return cfg.getName();
        }
        return "_default_";
    }
}
