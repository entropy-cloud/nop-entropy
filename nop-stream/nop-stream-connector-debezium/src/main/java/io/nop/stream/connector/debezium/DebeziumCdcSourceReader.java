/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import io.nop.credential.api.ICredentialProvider;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_JOB_EXECUTE_FAILED;

/**
 * WI2 (plan 2343 Phase 3): FLIP-27 reader of the debezium source — embeds the legacy
 * {@link DebeziumCdcSourceFunction} as the engine host (wrapper-form migration, plan
 * D-1): the battle-tested offset store lifecycle (AR-03 clear, WI9 first-run seed), the
 * D4 credential-reference handling, and the engine failure listener are reused verbatim
 * through {@link DebeziumSourceSupport#buildInstanceConfig} — the M-1 combination path
 * that scopes one engine instance to the reader's ASSIGNED table share
 * ({@code name-<subtaskIndex>}, instance-qualified schema history).
 *
 * <p>Offset checkpointing travels the FLIP-27 surface: {@link #snapshotState} lifts the
 * embedded function's {@code cdc-offsets} map into every split (instance-granular copy,
 * plan M-2); {@link #restoreState} rebuilds a {@link TaskStateSnapshot} from the first
 * offset-carrying split and replays it through the embedded function's
 * {@code initializeState} — no legacy operator-state path involved.
 */
public class DebeziumCdcSourceReader implements SourceReader<ChangeEvent, DebeziumTableSplit> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumCdcSourceReader.class);

    static final long POLL_TIMEOUT_MILLIS = 100L;
    static final int QUEUE_CAPACITY = 256;
    static final long CLOSE_JOIN_TIMEOUT_MILLIS = 5000L;

    private final DebeziumConfig config;
    private final ICredentialProvider credentialProvider;
    private final Map<String, String> initialOffsets;

    private transient SourceReaderContext context;
    private transient BlockingQueue<Object> queue;
    private transient volatile Thread runnerThread;
    private transient volatile Throwable runnerFailure;
    private final Map<String, DebeziumTableSplit> adoptedSplits = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> restoredOffsetByTable = new LinkedHashMap<>();
    private volatile boolean running;
    private transient DebeziumCdcSourceFunction embeddedFunction;

    public DebeziumCdcSourceReader(DebeziumConfig config, ICredentialProvider credentialProvider,
                                   Map<String, String> initialOffsets) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        this.config = config;
        this.credentialProvider = credentialProvider;
        this.initialOffsets = initialOffsets;
    }

    @Override
    public void start() {
        running = true;
        if (!adoptedSplits.isEmpty()) {
            launchEngine();
        }
        // no tables yet: pollNext requests the assignment, then launches
    }

    private void launchEngine() {
        if (runnerThread != null) {
            return;
        }
        int subtaskIndex = context == null ? 0 : context.getSubtaskIndex();
        int totalParallelism = context == null ? 1 : context.getTotalParallelism();
        String baseName = DebeziumSourceSupport.resolveConnectorName(config);
        String instance = DebeziumSourceSupport.instanceName(baseName, subtaskIndex, totalParallelism);
        List<String> tables = new ArrayList<>(adoptedSplits.keySet());

        DebeziumConfig engineConfig = DebeziumSourceSupport.buildInstanceConfig(
                config, instance, tables, credentialProvider);

        embeddedFunction = createEmbeddedFunction(engineConfig);
        embeddedFunction.setCredentialProvider(credentialProvider);
        if (initialOffsets != null && !initialOffsets.isEmpty()) {
            embeddedFunction.setInitialOffsets(initialOffsets);
        }

        try {
            Map<String, String> offsetMap = firstOffsetMap();
            if (offsetMap != null) {
                // restore path: replay the instance-level offset map through the
                // embedded function's own state contract
                TaskStateSnapshot snapshot = new TaskStateSnapshot(new TaskLocation(
                        "cdc-reader", "pipeline-0", "vertex-" + instance, subtaskIndex));
                snapshot.putOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY, offsetMap);
                embeddedFunction.initializeState(snapshot);
            } else {
                // first run: AR-03 fresh clear + WI9 first-run seed (embedded contract)
                embeddedFunction.initializeState(null);
            }
        } catch (Exception e) {
            throw (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED, e)
                    .param(ARG_DETAIL, "failed to initialize the embedded CDC engine state: "
                            + e.getMessage());
        }

        queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        runnerFailure = null;
        Thread thread = new Thread(this::runEmbedded, "debezium-source-reader-" + subtaskIndex);
        thread.setDaemon(true);
        thread.start();
        runnerThread = thread;
        LOG.info("DebeziumCdcSourceReader launched engine instance {} for tables {}",
                instance, tables);
    }

    /**
     * Test seam: the embedded engine host is created through this factory so reader
     * wiring tests can inject a stub without spinning up a real Debezium engine.
     */
    protected DebeziumCdcSourceFunction createEmbeddedFunction(DebeziumConfig engineConfig) {
        return new DebeziumCdcSourceFunction(engineConfig, credentialProvider);
    }

    private void runEmbedded() {
        try {
            embeddedFunction.run(new QueueSourceContext());
        } catch (Throwable e) {
            // collector failures and terminal engine failures land here (legacy R5-CON-02/
            // CON-03 capture-and-rethrow): surface from pollNext — a dead CDC stream must
            // fail the task, never masquerade as EOS.
            runnerFailure = e;
            LOG.warn("Debezium CDC runner terminated with failure", e);
        }
    }

    @Override
    public void addSplits(List<DebeziumTableSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            return;
        }
        boolean allKnown = true;
        for (DebeziumTableSplit split : splits) {
            if (!adoptedSplits.containsKey(split.splitId())) {
                allKnown = false;
                break;
            }
        }
        if (!allKnown || adoptedSplits.isEmpty()) {
            if (running && !adoptedSplits.isEmpty()) {
                // D-4: the engine's include-list is fixed at launch — a post-start
                // assignment of NEW tables is refused instead of silently ignored
                throw (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED)
                        .param(ARG_DETAIL, "DebeziumCdcSourceReader already launched its engine for "
                                + adoptedSplits.keySet() + "; a late assignment of new tables "
                                + splits + " cannot reconfigure a live engine");
            }
            for (DebeziumTableSplit split : splits) {
                adoptedSplits.put(split.splitId(), split);
                if (split.isOffsetCarrying() && split.getOffsetMap() != null) {
                    restoredOffsetByTable.put(split.splitId(), split.getOffsetMap());
                }
            }
            if (running) {
                launchEngine();
            }
            return;
        }
        // same split set re-delivered (post-restore re-registration): idempotent
    }

    private Map<String, String> firstOffsetMap() {
        for (DebeziumTableSplit split : adoptedSplits.values()) {
            if (split.isOffsetCarrying() && split.getOffsetMap() != null) {
                return split.getOffsetMap();
            }
            Map<String, String> restored = restoredOffsetByTable.get(split.splitId());
            if (restored != null) {
                return restored;
            }
        }
        return null;
    }

    @Override
    public Optional<ChangeEvent> pollNext() throws Exception {
        if (!running) {
            return Optional.empty();
        }
        if (adoptedSplits.isEmpty()) {
            // request the table share once per idle stretch
            if (context != null && context.getAssignmentProxy() != null) {
                context.getAssignmentProxy().requestSplits(context.getSubtaskIndex(),
                        Optional.empty());
            }
            return Optional.empty();
        }
        if (queue == null) {
            launchEngine();
        }
        Object value = queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (value == null) {
            if (runnerFailure != null) {
                throw asException(runnerFailure);
            }
            return Optional.empty();
        }
        if (runnerFailure != null) {
            throw asException(runnerFailure);
        }
        return Optional.of((ChangeEvent) value);
    }

    private StreamException asException(Throwable failure) {
        if (failure instanceof StreamException) {
            return (StreamException) failure;
        }
        if (failure instanceof Exception) {
            return (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED, failure)
                    .param(ARG_DETAIL, "debezium cdc source failed: " + failure.getMessage());
        }
        return (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED)
                .param(ARG_DETAIL, "debezium cdc source failed: " + failure);
    }

    @Override
    public boolean isFinished() {
        return !running;
    }

    @Override
    public List<DebeziumTableSplit> snapshotState(long checkpointId) throws Exception {
        if (adoptedSplits.isEmpty()) {
            return List.of();
        }
        Map<String, String> offsets = null;
        if (embeddedFunction != null) {
            var snapshot = embeddedFunction.snapshotState(checkpointId);
            Object raw = snapshot.getOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY);
            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) raw;
                offsets = map;
            }
        }
        List<DebeziumTableSplit> splits = new ArrayList<>(adoptedSplits.size());
        if (offsets != null) {
            // one instance-level copy per split (plan M-2)
            for (DebeziumTableSplit split : adoptedSplits.values()) {
                splits.add(split.withOffsetMap(offsets));
            }
        } else {
            splits.addAll(adoptedSplits.values());
        }
        return splits;
    }

    @Override
    public void restoreState(List<DebeziumTableSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            return;
        }
        for (DebeziumTableSplit split : splits) {
            adoptedSplits.putIfAbsent(split.splitId(), split);
            if (split.isOffsetCarrying() && split.getOffsetMap() != null) {
                restoredOffsetByTable.put(split.splitId(), split.getOffsetMap());
            }
        }
    }

    @Override
    public void close() throws Exception {
        running = false;
        if (embeddedFunction != null) {
            embeddedFunction.cancel();
        }
        Thread thread = runnerThread;
        if (thread != null) {
            thread.join(CLOSE_JOIN_TIMEOUT_MILLIS);
            if (thread.isAlive()) {
                throw (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED)
                        .param(ARG_DETAIL, "debezium cdc runner did not terminate within "
                                + CLOSE_JOIN_TIMEOUT_MILLIS + "ms of close()");
            }
        }
        if (queue != null) {
            queue.clear();
        }
    }

    void setContextForTest(SourceReaderContext ctx) {
        this.context = ctx;
    }

    /** Test visibility: the live embedded engine host (null before launch). */
    DebeziumCdcSourceFunction getEmbeddedFunctionForTest() {
        return embeddedFunction;
    }

    /** Feeds the embedded legacy function's pushes into the bounded queue. */
    private class QueueSourceContext implements SourceFunction.SourceContext<ChangeEvent> {

        @Override
        public void collect(ChangeEvent element) {
            try {
                queue.put(element);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED, e)
                        .param(ARG_DETAIL, "debezium source queue put interrupted (shutdown)");
            }
        }

        @Override
        public void collectWithTimestamp(ChangeEvent element, long timestamp) {
            collect(element);
        }

        @Override
        public void emitWatermark(long mark) {
            // ChangeEvent carries no watermark semantics
        }

        @Override
        public void markAsTemporarilyIdle() {
            // not observable through the queue bridge
        }

        @Override
        public long getProcessingTime() {
            return System.currentTimeMillis();
        }

        @Override
        public boolean isCancelled() {
            return !running;
        }
    }
}
