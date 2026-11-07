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
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
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
 * per-server last-emitted position map (plan 2511 C8-001 — the engine store value can
 * lead the emitted boundary by up to {@link #QUEUE_CAPACITY} records, so the snapshot
 * synthesizes from the tracker instead) into every split (instance-granular copy, plan
 * M-2); {@link #restoreState} rebuilds a {@link TaskStateSnapshot} from the restored
 * offset maps (numeric-min per server, conservative replay) and replays it through the
 * embedded function's {@code initializeState} — no legacy operator-state path involved.
 */
public class DebeziumCdcSourceReader implements SourceReader<ChangeEvent, DebeziumTableSplit> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumCdcSourceReader.class);

    static final long POLL_TIMEOUT_MILLIS = 100L;
    static final int QUEUE_CAPACITY = 256;
    static final long CLOSE_JOIN_TIMEOUT_MILLIS = 5000L;

    /**
     * CRA 8.43 R4-03 W-12 (C8-012; adjudication C.6): default per-record byte
     * envelope — SAME default and behavior as the message bridge's
     * {@code MessageSourceReader.DEFAULT_MAX_RECORD_BYTES} (16MB, in-family
     * alignment with the file connector's maxLineBytes bound).
     */
    public static final long DEFAULT_MAX_RECORD_BYTES = 16L * 1024 * 1024;

    /**
     * CRA 8.43 R4-03 W-12: per-record byte envelope enforced at the
     * {@code collect} queue-entry point (BEFORE the put). {@code <= 0} fails
     * fast at construction.
     */
    private final long maxRecordBytes;

    private final DebeziumConfig config;
    private final ICredentialProvider credentialProvider;
    private final Map<String, String> initialOffsets;

    private transient SourceReaderContext context;
    private transient BlockingQueue<Object> queue;
    private transient volatile Thread runnerThread;
    private transient volatile Throwable runnerFailure;
    private final Map<String, DebeziumTableSplit> adoptedSplits = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> restoredOffsetByTable = new LinkedHashMap<>();
    /**
     * Plan 2511 C8-001 (adjudication ⑥): per-server last-emitted position protocol —
     * checkpoint offsets must never run ahead of what this reader actually emitted
     * downstream (the pre-fix engine-store snapshot could be ≤256 records fresh).
     */
    private final LastEmittedOffsetTracker lastEmittedTracker = new LastEmittedOffsetTracker();
    private volatile boolean running;
    private transient DebeziumCdcSourceFunction embeddedFunction;

    public DebeziumCdcSourceReader(DebeziumConfig config, ICredentialProvider credentialProvider,
                                   Map<String, String> initialOffsets) {
        this(config, credentialProvider, initialOffsets, DEFAULT_MAX_RECORD_BYTES);
    }

    /**
     * CRA 8.43 R4-03 W-12: full constructor with the per-record byte envelope.
     * A non-positive bound fails fast typed at construction. The 2511
     * last-emitted offset tracker chain is NOT touched — this bound guards
     * only the queue-entry face.
     */
    public DebeziumCdcSourceReader(DebeziumConfig config, ICredentialProvider credentialProvider,
                                   Map<String, String> initialOffsets, long maxRecordBytes) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        if (maxRecordBytes <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "maxRecordBytes must be > 0, got: " + maxRecordBytes);
        }
        this.config = config;
        this.credentialProvider = credentialProvider;
        this.initialOffsets = initialOffsets;
        this.maxRecordBytes = maxRecordBytes;
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
            Map<String, String> offsetMap = lastEmittedTracker.hasPositions()
                    ? lastEmittedTracker.synthesizeOffsetMap()
                    : firstOffsetMap();
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
                    // C8-001: min-merge seed (same rule as restoreState)
                    lastEmittedTracker.seedFromOffsetMap(split.getOffsetMap());
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
        ChangeEvent event = (ChangeEvent) value;
        // C8-001 emission point: the record is handed downstream here — record its
        // position BEFORE returning so snapshotState can never lift an offset past it.
        // A position-less event fails typed (rule 24), never falls back silently.
        lastEmittedTracker.onEmitted(event);
        return Optional.of(event);
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
        Map<String, String> offsets;
        if (lastEmittedTracker.hasPositions()) {
            // C8-001: snapshot offsets == last-EMITTED boundary (per-server synthesized
            // engine-shape map). The engine store may legitimately be up to
            // QUEUE_CAPACITY + 1 records ahead of this boundary — lifting its value
            // was the pre-fix silent-loss window.
            offsets = lastEmittedTracker.synthesizeOffsetMap();
        } else {
            // C8-001: no emitted position since start (first checkpoint before any
            // record) — carry ONLY the maps restored for THIS run (legacy checkpoint
            // compatibility); the live engine-store value must NEVER lift into a
            // snapshot past the emitted boundary (that lift was the pre-fix
            // ≤256-record silent-loss window, C10-013 inverted carrier)
            offsets = firstOffsetMap();
        }
        List<DebeziumTableSplit> splits = new ArrayList<>(adoptedSplits.size());
        if (offsets != null) {
            // one instance-level copy per split (plan M-2, semantics preserved)
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
                // C8-001: seed the per-server table taking the NUMERIC MINIMUM across
                // offset-carrying splits — conservative replay (duplicate-not-lose),
                // replacing the pre-fix "take the first one" arbitrariness
                lastEmittedTracker.seedFromOffsetMap(split.getOffsetMap());
            }
        }
    }

    @Override
    public void close() throws Exception {
        running = false;
        if (embeddedFunction != null) {
            embeddedFunction.cancel();
        }
        // CRA 8.44 R4-04 W-05 (C3-012; adjudication C.1, three segments per MJ-2):
        // 1) bounded detection — join the runner with the unchanged 5s budget;
        Thread thread = runnerThread;
        boolean joinTimedOut = false;
        if (thread != null) {
            thread.join(CLOSE_JOIN_TIMEOUT_MILLIS);
            joinTimedOut = thread.isAlive();
        }
        // 2) single-shot interrupt on the RUNNER (best-effort; the engine dispatch
        // thread blocked in queue.put is released by the clear below — the
        // adjudication's attribution re-anchoring). This makes the collect()
        // InterruptedException branch reachable again; its body is untouched.
        if (joinTimedOut) {
            thread.interrupt();
        }
        // 3) clear BEFORE/INDEPENDENT of the typed failure — the queue is abandoned
        // after close and its parked events sit beyond the last-emitted boundary
        // (at-least-once replay covers the drop).
        try {
            if (queue != null) {
                queue.clear();
            }
        } finally {
            if (joinTimedOut) {
                // unchanged timeout signal: same code, same message, same shape
                throw (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED)
                        .param(ARG_DETAIL, "debezium cdc runner did not terminate within "
                                + CLOSE_JOIN_TIMEOUT_MILLIS + "ms of close()");
            }
        }
        if (embeddedFunction != null) {
            // C3-014 ② (plan 2503 Phase 3): the close contract requires the embedded engine
            // TERMINATED, not merely asked to stop — the pre-fix「join 成功 ≠ 引擎终止」window
            // (runner exited via latch while the ghost engine survived) is closed with a
            // typed failure on ghost risk. K2 boundary: this verify stays on the
            // join-SUCCESS path — the timeout path already typed out above (a timed-out
            // runner means the verify would ghost-fail anyway; no signal is lost).
            embeddedFunction.verifyEmbeddedEngineTerminated("reader-close", true);
        }
    }

    /**
     * CRA 8.43 R4-03 W-12: string/byte-carrier direct-measure LOWER BOUND of a
     * record's heap footprint (declared measurement caliber — depth-3 bounded
     * recursion, SAME semantics as the message bridge): a {@link ChangeEvent}
     * measures through its key/before/after object graphs; {@code byte[]} its
     * length; {@link String} its UTF-16 code units (correct heap lower bound);
     * maps / collections / arrays recurse bounded-depth; any other carrier
     * contributes 0 (declared residual — capability-matrix envelope statement;
     * depth telemetry = C8-008 deferred governance track).
     */
    static long measureRecordBytes(Object record) {
        return measureValue(record, 3);
    }

    private static long measureValue(Object value, int depth) {
        if (value == null || depth < 0) {
            return 0;
        }
        if (value instanceof ChangeEvent event) {
            return measureValue(event.getKey(), depth - 1)
                    + measureValue(event.getBefore(), depth - 1)
                    + measureValue(event.getAfter(), depth - 1);
        }
        if (value instanceof byte[] bytes) {
            return bytes.length;
        }
        if (value instanceof String text) {
            return text.length();
        }
        if (value instanceof java.util.Map<?, ?> map) {
            long sum = 0;
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                sum += measureValue(entry.getKey(), depth - 1)
                        + measureValue(entry.getValue(), depth - 1);
            }
            return sum;
        }
        if (value instanceof Iterable<?> iterable) {
            long sum = 0;
            for (Object element : iterable) {
                sum += measureValue(element, depth - 1);
            }
            return sum;
        }
        if (value instanceof Object[] array) {
            long sum = 0;
            for (Object element : array) {
                sum += measureValue(element, depth - 1);
            }
            return sum;
        }
        return 0;
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
            // CRA 8.43 R4-03 W-12: per-record byte envelope — reject BEFORE the
            // put; the typed failure rides the bridge's existing first-error
            // channel. The 2511 last-emitted offset protocol is untouched (the
            // envelope guards only the queue entry, after offset extraction).
            if (element != null && measureRecordBytes(element) > maxRecordBytes) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_DETAIL, "debezium CDC record exceeds maxRecordBytes: "
                                + measureRecordBytes(element) + " > " + maxRecordBytes
                                + " bytes (subtask="
                                + (context == null ? "?" : String.valueOf(context.getSubtaskIndex()))
                                + ", table=" + (element.getKey() == null ? "?"
                                        : String.valueOf(element.getKey().keySet()))
                                + ") — enlarge the payload budget (constructor) or compress/"
                                + "shrink the record; the envelope is a string/byte-carrier "
                                + "direct-measure lower bound");
            }
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
