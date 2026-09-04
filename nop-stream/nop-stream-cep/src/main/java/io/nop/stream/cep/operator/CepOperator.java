/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.cep.operator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.util.Guard;
import io.nop.commons.tuple.Tuple2;
import io.nop.stream.cep.EventComparator;
import io.nop.stream.cep.NopCepConfigs;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.functions.TimedOutPartialMatchHandler;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.time.TimerService;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.VoidNamespace;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.TimestampedCollector;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.OutputTag;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * CEP pattern operator for a keyed input stream. For each key, the operator creates a {@link NFA}
 * and a priority queue to buffer out of order elements. Both data structures are stored using the
 * managed keyed state.
 *
 * <p><b>Design note:</b> This operator uses a pluggable state backend architecture. The
 * {@link io.nop.stream.core.common.state.backend.IStateBackend} is injected via
 * {@link #setStateBackend(io.nop.stream.core.common.state.backend.IStateBackend)} before
 * {@link #open()} is called. In {@code open()}, the operator creates a keyed state backend from
 * the injected factory via {@code stateBackend.createKeyedStateBackend()}. If no state backend
 * is configured, the operator falls back to an in-memory keyed state store.
 *
 * <p>The keyed state restoration lifecycle uses deferred restore: during checkpoint recovery,
 * {@link #restoreState(io.nop.stream.core.checkpoint.OperatorSnapshotResult)} saves the pending
 * keyed state. When {@code open()} creates the keyed state backend,
 * {@link #applyPendingRestoreState()} is called to replay the saved state into the backend.
 *
 * @param <IN>  Type of the input elements
 * @param <KEY> Type of the key on which the input stream is keyed
 * @param <OUT> Type of the output elements
 */
public class CepOperator<IN, KEY, OUT>
        extends AbstractUdfStreamOperator<OUT, PatternProcessFunction<IN, OUT>>
        implements OneInputStreamOperator<IN, OUT>
{

    private static final long serialVersionUID = -4166778210774160757L;
    private static final Logger LOG = LoggerFactory.getLogger(CepOperator.class);

    private static final String LATE_ELEMENTS_DROPPED_METRIC_NAME = "numLateRecordsDropped";

    private final boolean isProcessingTime;

    @Nullable
    private final TypeSerializer<IN> inputSerializer;

    /**
     * AR-10 (Plan 2026-09-04-1326-1 Phase 4, adjudication D4): the explicit key class
     * channel. Non-null pins the keyed state backend's key type at {@link #open()} —
     * the non-keyed global path always passes {@code Byte.class} (its
     * {@code NullByteKeySelector} is deterministic); keyed jobs may leave it null, in
     * which case the class is (i) captured from the first live key and (ii) persisted
     * into every checkpoint that carries keyed state, so a restore re-materializes it
     * BEFORE the backend is created and the {@code MemoryStateSerDe} key
     * re-materialization guard fires for all CEP keyed state.
     */
    @Nullable
    private final Class<KEY> keyClass;

    /**
     * AR-10: key class observed from the first live key (or an explicit restore).
     * Transient — it travels through checkpoints as the {@code cep-key-class} operator
     * state entry, never through Java serialization of the operator.
     */
    private transient Class<?> capturedKeyClass;

    /**
     * AR-10: key class read from a checkpoint in {@link #restoreState} (which runs
     * BEFORE {@link #open()}). Consumed once by {@link #resolveEffectiveKeyClass()}.
     */
    private transient Class<?> restoredKeyClass;

    ///////////////			State			//////////////

    private static final String NFA_STATE_NAME = "nfaStateName";
    private static final String EVENT_QUEUE_STATE_NAME = "eventQueuesStateName";

    private final NFACompiler.NFAFactory<IN> nfaFactory;

    private transient ValueState<NFAState> computationStates;
    private transient MapState<Long, List<IN>> elementQueueState;
    private transient SharedBuffer<IN> partialMatches;

    private transient KeyedStateStore keyedStateStore;

    private transient InternalTimerService<VoidNamespace> timerService;

    private transient NFA<IN> nfa;

    /**
     * Ledger of pending event-time timers, scoped per key: key -> sorted pending
     * timestamps. Timers are registered under the key that is current at registration
     * time (set by the upstream {@code KeyExtractingOutput} before
     * {@code processElement}); the watermark drain
     * ({@link #processWatermark}) switches to each key's context before running
     * {@link #onEventTime(long)} so every key's queue and NFA state are advanced, not
     * just the last processed element's key.
     *
     * <p>A key's entry is removed once all of its timers are consumed, so the map is
     * bounded by the number of keys with genuinely pending work.
     */
    private transient Map<Object, TreeSet<Long>> registeredEventTimeTimersByKey;

    /**
     * Comparator for secondary sorting. Primary sorting is always done on time.
     */
    private final EventComparator<IN> comparator;

    /**
     * {@link OutputTag} to use for late arriving events. Elements with timestamp smaller than the
     * current watermark will be emitted to this.
     */
    private final OutputTag<IN> lateDataOutputTag;

    /**
     * Strategy which element to skip after a match was found.
     */
    private final AfterMatchSkipStrategy afterMatchSkipStrategy;

    /**
     * Context passed to user function.
     */
    private transient ContextFunctionImpl context;

    /**
     * Main output collector, that sets a proper timestamp to the StreamRecord.
     */
    private transient TimestampedCollector<OUT> collector;

    /**
     * Wrapped RuntimeContext that limits the underlying context features.
     */
    private transient CepRuntimeContext cepRuntimeContext;

    /**
     * Thin context passed to NFA that gives access to time related characteristics.
     */
    private transient TimerService cepTimerService;

    // ------------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------------

    private transient LongAdder numLateRecordsDropped;

    private transient long currentWatermark = Long.MIN_VALUE;

    private transient boolean watermarkRestored = false;

    /**
     * Handle of the periodic cache-statistics timer registered via
     * {@link ProcessingTimeService#registerTimer(long, ProcessingTimeCallback)}. Held so that
     * {@link #close()} can cancel it via {@link #releaseCacheStatisticsTimer()}.
     *
     * <p>This timer is intentionally <b>separate</b> from {@link #timerService}
     * ({@code InternalTimerService<VoidNamespace>}) and {@link #cepTimerService}: those route
     * to {@link #onProcessingTime(long)} which performs CEP event processing
     * (drain elementQueue / advanceTime / processEvent / updateNFA). The cache-statistics
     * timer uses a dedicated {@code ProcessingTimeCallback} ({@link #onCacheStatisticsTimer})
     * so periodic logging never triggers CEP event processing.
     */
    private transient ScheduledFuture<?> cacheStatsTimerFuture;

    /**
     * Cache-statistics logging interval in milliseconds, captured from
     * {@link NopCepConfigs#CEP_CACHE_STATISTICS_INTERVAL} at {@link #open()} time and reused
     * by {@link #onCacheStatisticsTimer} when re-arming the timer (anchored to fire time, not
     * current time, to avoid drift).
     */
    private transient long cacheStatsIntervalMs;

    public CepOperator(
            @Nullable final TypeSerializer<IN> inputSerializer,
            final boolean isProcessingTime,
            final NFACompiler.NFAFactory<IN> nfaFactory,
            @Nullable final EventComparator<IN> comparator,
            @Nullable final AfterMatchSkipStrategy afterMatchSkipStrategy,
            final PatternProcessFunction<IN, OUT> function,
            @Nullable final OutputTag<IN> lateDataOutputTag) {
        this(inputSerializer, isProcessingTime, nfaFactory, comparator, afterMatchSkipStrategy,
                function, lateDataOutputTag, null);
    }

    /**
     * AR-10 (D4): full constructor with the explicit key class channel. See
     * {@link #keyClass}.
     */
    public CepOperator(
            @Nullable final TypeSerializer<IN> inputSerializer,
            final boolean isProcessingTime,
            final NFACompiler.NFAFactory<IN> nfaFactory,
            @Nullable final EventComparator<IN> comparator,
            @Nullable final AfterMatchSkipStrategy afterMatchSkipStrategy,
            final PatternProcessFunction<IN, OUT> function,
            @Nullable final OutputTag<IN> lateDataOutputTag,
            @Nullable final Class<KEY> keyClass) {
        super(function);

        this.inputSerializer = inputSerializer; // nullable: SharedBuffer accepts but does not use the serializer
        this.nfaFactory = Guard.notNull(nfaFactory, "nfaFactory");
        this.keyClass = keyClass;

        this.isProcessingTime = isProcessingTime;
        this.comparator = comparator;
        this.lateDataOutputTag = lateDataOutputTag;

        if (afterMatchSkipStrategy == null) {
            this.afterMatchSkipStrategy = AfterMatchSkipStrategy.noSkip();
        } else {
            this.afterMatchSkipStrategy = afterMatchSkipStrategy;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>CEP operator: the NFA factory, user process function, comparator,
     * skip strategy and the explicit key class (all immutable configuration) are
     * shared across subtasks. Per-subtask mutable state (computation states,
     * element queue, shared buffer, NFA, timers, current watermark) is left null
     * and re-initialized by {@link #open()}.
     */
    @Override
    public CepOperator<IN, KEY, OUT> copyForSubtask() {
        return new CepOperator<>(
                inputSerializer,
                isProcessingTime,
                nfaFactory,
                comparator,
                afterMatchSkipStrategy,
                getUserFunction(),
                lateDataOutputTag,
                keyClass);
    }

    @Override
   @SuppressWarnings({"unchecked", "rawtypes"})
   public void open() throws Exception {
          super.open();
          if (!watermarkRestored) {
              currentWatermark = Long.MIN_VALUE;
          }
          watermarkRestored = false;

         IKeyedStateBackend<?> backend = getKeyedStateBackend();
         if (backend == null && this.stateBackend != null) {
             // AR-10 (D4): create the keyed backend with the RESOLVED key class, never
             // a bare Object.class: an explicit key class (non-keyed path pins
             // Byte.class) or the class carried by the restored checkpoint makes the
             // MemoryStateSerDe key re-materialization guard fire for every CEP keyed
             // state (nfaState / eventQueues / SharedBuffer) — without it, non-String
             // keys came back from the JSON persist round-trip as drifted classes
             // (e.g. Long -> Integer) and every runtime lookup missed (silent state
             // loss). A fresh keyed run with unknown class keeps Object.class (safe
             // in-memory; the first live key's class is captured and persisted so the
             // NEXT restore resolves it).
             Class<?> effectiveKeyClass = resolveEffectiveKeyClass();
             this.keyedStateBackend = this.stateBackend.createKeyedStateBackend(effectiveKeyClass);
             // Apply deferred state restore (from checkpoint recovery before open())
             applyPendingRestoreState();
             backend = getKeyedStateBackend();
         }
         if (backend != null) {
             keyedStateStore = backend;
         } else {
             LOG.warn("CepOperator opened without a configured state backend; falling back to " +
                     "MemoryKeyedStateBackend. Checkpoint consistency is not guaranteed. " +
                     "Ensure a state backend is configured when checkpointing is enabled.");
             keyedStateStore = new MemoryKeyedStateBackend<>(resolveEffectiveKeyClass());
         }
        // P2-INV-6 resolution: the NFA state graph (queues / DeweyNumber / node
        // references) is not JSON-@DataBean-shaped, so its ValueState uses a
        // Java-stream serializer and travels through the JSON snapshot as byte[].
        // Without this, every checkpoint persist of a CEP operator failed with
        // only-data-bean-is-serializable and the sink never committed.
        ValueStateDescriptor<NFAState> nfaStateDescriptor =
                new ValueStateDescriptor<>(NFA_STATE_NAME, NFAState.class);
        nfaStateDescriptor.setSerializer(
                io.nop.stream.core.common.typeutils.JavaStreamSerializer.of());
        computationStates = keyedStateStore.getState(nfaStateDescriptor);
        // MapStateDescriptor does not support generic type tokens for value class;
        // (Class) List.class is used as a raw class hint, actual generic safety is ensured by usage
        // raw cast intentional - type erased at runtime
        elementQueueState = keyedStateStore.getMapState(
                new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
        partialMatches = new SharedBuffer<>(keyedStateStore, inputSerializer, new SharedBufferCacheConfig());

        // P1-04: restoreState() may run BEFORE open() (the order pinned by
        // TestCepCheckpointRestoreE2E), so only initialize the timer registry when
        // it is still null. The previous unconditional rebuild wiped every timer
        // restored from a checkpoint — the storage side (AR-9) persisted them, but
        // the consumption side silently lost them on open().
        if (registeredEventTimeTimersByKey == null) {
            registeredEventTimeTimersByKey = new LinkedHashMap<>();
        }

        timerService = new InternalTimerService<VoidNamespace>() {
            @Override
            public long currentProcessingTime() {
                return getProcessingTimeService().getCurrentProcessingTime();
            }

            @Override
            public long currentWatermark() {
                return CepOperator.this.currentWatermark;
            }

            @Override
            public void registerProcessingTimeTimer(VoidNamespace namespace, long time) {
                // Capture the registering key: the processing-time service delivers
                // callbacks on the task thread but WITHOUT any key context, so the
                // callback must restore the owning key's context before running the
                // per-key drain (onProcessingTime). Without this, a multi-key stream
                // would drain only whatever key happened to be current at fire time.
                Object registeringKey = currentRegistrationKey();
                getProcessingTimeService().registerTimer(time, t -> {
                    try {
                        setCurrentKey(registeringKey);
                        onProcessingTime(t);
                    } catch (Exception e) {
                        throw new StreamException(ERR_STREAM_STATE_ERROR, e).param(ARG_DETAIL, "onProcessingTime timer callback");
                    }
                });
            }

            @Override
            public void deleteProcessingTimeTimer(VoidNamespace namespace, long time) {
            }

            @Override
            public void registerEventTimeTimer(VoidNamespace namespace, long time) {
                registerEventTimeTimerForKey(currentRegistrationKey(), time);
            }

            @Override
            public void deleteEventTimeTimer(VoidNamespace namespace, long time) {
                deleteEventTimeTimerForKey(currentRegistrationKey(), time);
            }

            @Override
            public void forEachEventTimeTimer(BiConsumer<VoidNamespace, Long> consumer) {
                for (Map.Entry<Object, TreeSet<Long>> entry : snapshotTimersByKey().entrySet()) {
                    for (Long time : new TreeSet<>(entry.getValue())) {
                        consumer.accept(VoidNamespace.INSTANCE, time);
                    }
                }
            }

            @Override
            public void forEachProcessingTimeTimer(BiConsumer<VoidNamespace, Long> consumer) {
            }
        };

        nfa = nfaFactory.createNFA();

        cepRuntimeContext = new CepRuntimeContext(new io.nop.stream.core.common.functions.RuntimeContext() {
            @Override public int getIndexOfThisSubtask() { return 0; }
            @Override public int getNumberOfParallelSubtasks() { return 1; }
            @Override public String getTaskName() { return "cep-operator"; }
        }, keyedStateStore);
        // Pass an empty Configuration (same as AbstractUdfStreamOperator.open() gives the UDF)
        // so user RichIterativeCondition.open(Configuration) implementations can safely
        // dereference their parameters argument.
        nfa.open(cepRuntimeContext, new io.nop.stream.core.configuration.Configuration() {});

        context = new ContextFunctionImpl();
        collector = new TimestampedCollector<>(output);
        cepTimerService = new TimerServiceImpl();

        this.numLateRecordsDropped = new LongAdder();

        // Register the periodic cache-statistics timer on a dedicated ProcessingTimeCallback
        // (NOT via timerService/cepTimerService — those route to onProcessingTime which drives
        // CEP event processing). See SharedBuffer.logCacheStatistics() and
        // onCacheStatisticsTimer for the consumer side.
        registerCacheStatisticsTimer();
    }

    /**
     * Registers the first fire of the cache-statistics timer via
     * {@link ProcessingTimeService#registerTimer(long, ProcessingTimeCallback)} and stores the
     * returned {@link ScheduledFuture} so {@link #releaseCacheStatisticsTimer()} can cancel it
     * on {@link #close()}.
     */
    private void registerCacheStatisticsTimer() {
        java.time.Duration interval = NopCepConfigs.CEP_CACHE_STATISTICS_INTERVAL.get();
        this.cacheStatsIntervalMs = interval.toMillis();
        if (cacheStatsIntervalMs <= 0) {
            // Non-positive interval disables periodic statistics; do not register a timer that
            // would never fire or fire in a tight loop. Explicit rather than silently skipping.
            LOG.warn("CEP cache statistics timer disabled: interval={}ms (non-positive)",
                    cacheStatsIntervalMs);
            return;
        }
        if (getProcessingTimeService() == null) {
            // Production path injects a real service before open(); a null here means the
            // operator was opened outside a task (direct unit-test usage) with no service.
            // Explicit WARN (not silent skip) so a missing wiring is visible in logs.
            LOG.warn("CEP cache statistics timer not registered: no ProcessingTimeService was "
                    + "injected into the operator chain before open()");
            return;
        }
        long firstFire = getProcessingTimeService().getCurrentProcessingTime() + cacheStatsIntervalMs;
        this.cacheStatsTimerFuture = getProcessingTimeService().registerTimer(
                firstFire, this::onCacheStatisticsTimer);
    }

    /**
     * Dedicated {@link ProcessingTimeService.ProcessingTimeCallback} for periodic cache
     * statistics logging. Logs current stats via {@link SharedBuffer#logCacheStatistics()} and
     * re-arms the timer anchored to the fire timestamp (not current time) to avoid drift.
     *
     * <p><b>Must remain separate from {@link #onProcessingTime(long)}</b>: that callback
     * performs CEP event processing (drain elementQueue / advanceTime / processEvent /
     * updateNFA) and must not be triggered by the cache-statistics timer.
     */
    void onCacheStatisticsTimer(long timestamp) {
        if (partialMatches != null) {
            partialMatches.logCacheStatistics();
        }
        if (cacheStatsIntervalMs > 0) {
            // Re-arm anchored to the fire time (not current time) to avoid drift.
            cacheStatsTimerFuture = getProcessingTimeService().registerTimer(
                    timestamp + cacheStatsIntervalMs, this::onCacheStatisticsTimer);
        }
    }

    /**
     * Cancels the periodic cache-statistics timer (if registered). Called from {@link #close()}.
     *
     * <p>Idempotent: a {@code null} future (timer never registered, or close called twice)
     * is a no-op — there is genuinely nothing to release.
     */
    void releaseCacheStatisticsTimer() {
        if (cacheStatsTimerFuture != null) {
            cacheStatsTimerFuture.cancel(false);
            cacheStatsTimerFuture = null;
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
        // Cancel the periodic cache-statistics timer (close-time cleanup). Done before
        // releasing partialMatches so the timer does not fire during teardown.
        releaseCacheStatisticsTimer();
        if (nfa != null) {
            nfa.close();
        }
    }

    private static final String WATERMARK_STATE_NAME = "cep-current-watermark";
    private static final String EVENT_TIME_TIMERS_STATE_NAME = "cep-event-time-timers";

    @Override
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = super.snapshotState(context);
        result.putOperatorState(WATERMARK_STATE_NAME, currentWatermark);
        // AR-10 (D4): persist the effective key class so the NEXT restore creates the
        // keyed backend typed (MemoryStateSerDe re-materialization guard fires). Only
        // meaningful when the class is actually known (explicit channel or captured
        // from the first live key); Object.class is the "unknown" sentinel and is not
        // persisted (restoring it would pin the drift-prone untyped backend).
        Class<?> effectiveKeyClass = resolveEffectiveKeyClass();
        if (effectiveKeyClass != Object.class) {
            result.putOperatorState(KEY_CLASS_STATE_NAME, effectiveKeyClass.getName());
        }
        if (registeredEventTimeTimersByKey != null) {
            // AR-11: per-key JSON-safe form with TYPED keys — {"keyClass": name,
            // "key": k, "timers": [t1, t2, ...]}. The key class + canonical value let
            // the restore re-materialize the exact original key (a bare JSON key
            // drifts: Long(123) came back as Integer(123), so the drained "key" was
            // never the real backend key and pending timers silently never fired for
            // it). String keys and other JSON-stable classes round-trip unchanged.
            List<Map<String, Object>> timersForm = new ArrayList<>();
            for (Map.Entry<Object, TreeSet<Long>> entry : registeredEventTimeTimersByKey.entrySet()) {
                Map<String, Object> form = new LinkedHashMap<>();
                Object ledgerKey = entry.getKey();
                form.put("keyClass", ledgerKey != null ? ledgerKey.getClass().getName() : null);
                form.put("key", ledgerKey);
                form.put("timers", new ArrayList<>(entry.getValue()));
                timersForm.add(form);
            }
            result.putOperatorState(EVENT_TIME_TIMERS_STATE_NAME, timersForm);
        }
        return result;
    }

    @Override
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        if (snapshotResult != null) {
            Object wmObj = snapshotResult.getOperatorState(WATERMARK_STATE_NAME);
            if (wmObj instanceof Number) {
                currentWatermark = ((Number) wmObj).longValue();
                watermarkRestored = true;
            }
            // AR-10 (D4): resolve the checkpoint-carried key class BEFORE open()
            // creates the keyed backend — that is the whole point of carrying it.
            Object keyClassObj = snapshotResult.getOperatorState(KEY_CLASS_STATE_NAME);
            if (keyClassObj instanceof String className && !className.isBlank()) {
                try {
                    restoredKeyClass = Class.forName(className, true,
                            CepOperator.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                            .param(ARG_DETAIL, "Checkpointed CEP key class " + className
                                    + " is not on the classpath; cannot create a typed keyed"
                                    + " state backend for restore");
                }
            }
            Object timersObj = snapshotResult.getOperatorState(EVENT_TIME_TIMERS_STATE_NAME);
            if (timersObj instanceof List) {
                List<?> timersList = (List<?>) timersObj;
                if (registeredEventTimeTimersByKey == null) {
                    registeredEventTimeTimersByKey = new LinkedHashMap<>();
                }
                if (isPerKeyTimersForm(timersList)) {
                    // Current format: list of {"keyClass": cn, "key": k, "timers":
                    // [t...]} maps (AR-11 typed form; entries written before AR-11 lack
                    // the keyClass member and fall back to the raw key with a WARN).
                    // Timer values may arrive as Integer or Long after the JSON persist
                    // path.
                    for (Object element : timersList) {
                        Map<?, ?> form = (Map<?, ?>) element;
                        Object ledgerKey = rematerializeLedgerKey(
                                form.get("keyClass"), form.get("key"));
                        TreeSet<Long> timers = registeredEventTimeTimersByKey
                                .computeIfAbsent(ledgerKey, k -> new TreeSet<>());
                        addAllTimerTimestamps(form.get("timers"), timers);
                    }
                } else {
                    // Legacy flat List<Long> (written before timers carried a key
                    // dimension). The key association was never recorded, so attach the
                    // timestamps to the null-key bucket: pre-key-aware checkpoints were
                    // only ever correct for single-key usage, which restores to one bucket.
                    TreeSet<Long> timers = registeredEventTimeTimersByKey
                            .computeIfAbsent(null, k -> new TreeSet<>());
                    addAllTimerTimestamps(timersList, timers);
                }
            }
        }
    }

    /**
     * AR-11: re-materialize a ledger key from its typed snapshot form. The canonical
     * mechanism mirrors {@code MemoryStateSerDe.deserializeKey}: JSON-serialize the
     * (possibly drifted — Integer after a JSON round-trip) raw value and parse it back
     * as the recorded class, so {@code Long(123)} restored as {@code Integer(123)}
     * comes back as {@code Long(123)} and the drain loop addresses the REAL backend
     * key. Entries without a recorded class (pre-AR-11 checkpoints) keep the raw key
     * and log a WARN — no silent drop, and the class is present on every checkpoint
     * this build writes.
     */
    private static Object rematerializeLedgerKey(Object keyClassName, Object rawKey) {
        if (rawKey == null) {
            return null;
        }
        if (!(keyClassName instanceof String className) || className.isBlank()) {
            LOG.warn("CEP timer ledger entry for key {} carries no keyClass (pre-AR-11"
                    + " checkpoint form); restoring the raw key untyped", rawKey);
            return rawKey;
        }
        if (rawKey.getClass().getName().equals(className)) {
            return rawKey;
        }
        try {
            Class<?> target = Class.forName(className, true, CepOperator.class.getClassLoader());
            String json = io.nop.core.lang.json.JsonTool.serialize(rawKey, false);
            Object rematerialized = io.nop.core.lang.json.JsonTool.parseBeanFromText(json, target);
            if (rematerialized == null || !target.isInstance(rematerialized)) {
                throw new IllegalStateException("re-materialized key is not a " + className);
            }
            return rematerialized;
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param(ARG_DETAIL, "Failed to re-materialize CEP timer ledger key " + rawKey
                            + " as " + className + " during restore");
        }
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
        long newWatermark = mark.getTimestamp();
        if (newWatermark > currentWatermark) {
            currentWatermark = newWatermark;
            if (!isProcessingTime) {
                // Watermark delivery carries no key context (the upstream
                // KeyExtractingOutput sets the key only for elements), so the drain must
                // explicitly visit every key that has at least one due event-time timer,
                // switching to that key's context first. Without this, onEventTime would
                // only ever drain the last processed element's key: other keys' buffered
                // events would linger in elementQueueState, their partial matches would
                // never time out and their SharedBuffer entries would leak.
                for (Map.Entry<Object, TreeSet<Long>> entry : snapshotTimersByKey().entrySet()) {
                    TreeSet<Long> pending = registeredEventTimeTimersByKey.get(entry.getKey());
                    if (pending == null || pending.isEmpty() || pending.first() > currentWatermark) {
                        continue;
                    }
                    setCurrentKey(entry.getKey());
                    onEventTime(currentWatermark);
                }
            }
        }
        super.processWatermark(mark);
    }

    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        // AR-10 (D4 first-key capture): the upstream KeyExtractingOutput has already
        // set the backend's current key — observe its class once so checkpoints carry
        // the key type for the next restore.
        captureKeyClass(getCurrentKey());
        if (isProcessingTime) {
            if (getProcessingTimeService() == null) {
                // Explicit fail-fast instead of the silent NPE this branch previously produced:
                // processing-time mode requires a real ProcessingTimeService. The production
                // task wiring injects one before open(); a null here means the operator was
                // opened outside a task with no service — fail visibly, never silently drop.
                throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL,
                        "CepOperator in processing-time mode requires a ProcessingTimeService; "
                                + "none was injected into the operator chain before open()");
            }
            if (comparator == null) {
                NFAState nfaState = getNFAState();
                long timestamp = getProcessingTimeService().getCurrentProcessingTime();
                advanceTime(nfaState, timestamp);
                processEvent(nfaState, element.getValue(), timestamp);
                updateNFA(nfaState);
            } else {
                long currentTime = timerService.currentProcessingTime();
                bufferEvent(element.getValue(), currentTime);
            }

        } else {

            long timestamp = element.getTimestamp();
            IN value = element.getValue();

            if (timestamp > timerService.currentWatermark()) {
                bufferEvent(value, timestamp);
            } else if (lateDataOutputTag != null) {
                output.collect(lateDataOutputTag, element);
            } else {
                numLateRecordsDropped.increment();
            }
        }
    }

    private void registerTimer(long timestamp) {
        if (isProcessingTime) {
            timerService.registerProcessingTimeTimer(VoidNamespace.INSTANCE, timestamp + 1);
        } else {
            timerService.registerEventTimeTimer(VoidNamespace.INSTANCE, timestamp);
        }
    }

    /**
     * The key under which a timer registration belongs: the keyed state backend's
     * current key (set by the upstream {@code KeyExtractingOutput} before
     * {@code processElement}, or by this operator's own per-key drain). {@code null}
     * when no keyed backend is wired (unit-test fallback store) — matching the single
     * null-key bucket that fallback uses for all state.
     */
    private Object currentRegistrationKey() {
        IKeyedStateBackend<?> backend = getKeyedStateBackend();
        Object key = backend != null ? backend.getCurrentKey() : null;
        captureKeyClass(key);
        return key;
    }

    /**
     * AR-10 (D4 first-key capture): remember the class of the first live key observed.
     * The class is persisted with every checkpoint that carries keyed state, so a
     * restore resolves the key type BEFORE the backend is created. Capture is
     * write-once (the first non-null key of a run defines the stream's key class).
     */
    private void captureKeyClass(Object key) {
        if (capturedKeyClass == null && key != null) {
            capturedKeyClass = key.getClass();
        }
    }

    /**
     * AR-10: the key class the keyed backend is created with. Explicit constructor
     * channel wins (non-keyed path pins {@code Byte.class}); then the class carried by
     * the restored checkpoint; then {@code Object.class} (fresh keyed run — safe
     * in-memory, and the captured class makes the next restore typed).
     */
    private Class<?> resolveEffectiveKeyClass() {
        if (keyClass != null) {
            return keyClass;
        }
        if (capturedKeyClass != null && capturedKeyClass != Object.class) {
            return capturedKeyClass;
        }
        if (restoredKeyClass != null && restoredKeyClass != Object.class) {
            return restoredKeyClass;
        }
        return Object.class;
    }

    /** Operator-state key under which the captured key class travels in checkpoints. */
    static final String KEY_CLASS_STATE_NAME = "cep-key-class";

    private void registerEventTimeTimerForKey(Object key, long time) {
        if (registeredEventTimeTimersByKey == null) {
            registeredEventTimeTimersByKey = new LinkedHashMap<>();
        }
        registeredEventTimeTimersByKey.computeIfAbsent(key, k -> new TreeSet<>()).add(time);
    }

    private void deleteEventTimeTimerForKey(Object key, long time) {
        if (registeredEventTimeTimersByKey == null) {
            return;
        }
        TreeSet<Long> timers = registeredEventTimeTimersByKey.get(key);
        if (timers != null) {
            timers.remove(time);
            if (timers.isEmpty()) {
                registeredEventTimeTimersByKey.remove(key);
            }
        }
    }

    /**
     * Copy-on-iterate view of the per-key timer ledger: iteration happens inside the
     * watermark drain, and the drain itself may register new timers (bucket / window
     * timers) for the key being processed.
     */
    private Map<Object, TreeSet<Long>> snapshotTimersByKey() {
        if (registeredEventTimeTimersByKey == null) {
            return Collections.emptyMap();
        }
        Map<Object, TreeSet<Long>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Object, TreeSet<Long>> entry : registeredEventTimeTimersByKey.entrySet()) {
            snapshot.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        return snapshot;
    }

    /**
     * Detects the current per-key snapshot form: a non-empty list whose elements are
     * maps (vs. the legacy flat {@code List<Long>} form which holds numbers).
     */
    private static boolean isPerKeyTimersForm(List<?> timersList) {
        return !timersList.isEmpty() && timersList.get(0) instanceof Map;
    }

    /**
     * Adds timer timestamps from a snapshot form (live {@code List<Long>} or a JSON
     * round-tripped list whose elements may be {@code Integer}/{@code Long}).
     */
    private static void addAllTimerTimestamps(Object timersForm, TreeSet<Long> target) {
        if (!(timersForm instanceof List)) {
            return;
        }
        for (Object timer : (List<?>) timersForm) {
            if (timer instanceof Number) {
                target.add(((Number) timer).longValue());
            }
        }
    }

    private void bufferEvent(IN event, long currentTime) throws Exception {
        List<IN> elementsForTimestamp = elementQueueState.get(currentTime);
        if (elementsForTimestamp == null) {
            elementsForTimestamp = new ArrayList<>();
            registerTimer(currentTime);
        }

        elementsForTimestamp.add(event);
        elementQueueState.put(currentTime, elementsForTimestamp);
    }

    /**
     * Drains the event queue and advances the NFA of the CURRENT key context only
     * (all state touched here — elementQueueState, computationStates, SharedBuffer —
     * is keyed). Callers must ensure the key context is set to the owning key:
     * {@link #processWatermark} switches to each key with due timers before calling
     * this; processing-time callbacks restore the key captured at registration time.
     */
    public void onEventTime(long time) throws Exception {

        // STEP 1
        PriorityQueue<Long> sortedTimestamps = getSortedTimestamps();
        NFAState nfaState = getNFAState();

        // STEP 2
        while (!sortedTimestamps.isEmpty()
                && sortedTimestamps.peek() <= timerService.currentWatermark()) {
            long timestamp = sortedTimestamps.poll();
            advanceTime(nfaState, timestamp);
            try (Stream<IN> elements = sort(elementQueueState.get(timestamp))) {
                elements.forEachOrdered(
                        event -> {
                            try {
                                processEvent(nfaState, event, timestamp);
                            } catch (Exception e) {
                                throw new StreamException(ERR_STREAM_STATE_ERROR, e).param(ARG_DETAIL, "onEventTime processEvent");
                            }
                        });
            }
            elementQueueState.remove(timestamp);
        }

        // STEP 3
        advanceTime(nfaState, timerService.currentWatermark());

        // STEP 4
        updateNFA(nfaState);

        // STEP 5: idle-state reset if the only remaining partial match (the start state,
        // which is always re-created on the next event) has fully passed its window.
        resetNfaStateIfFullyTimedOut(nfaState, timerService.currentWatermark());

        // P1-04 (bookkeeping semantics): the registry is a LEDGER of pending
        // event-time timers, not a trigger mechanism — onEventTime is driven by
        // watermark advancement (STEP 2 drains every queue bucket <= watermark
        // directly). A registry entry's work is done once the watermark reaches
        // it: its queue bucket has been consumed (STEP 2) and window cleanup
        // performed (STEP 3-5). Expired entries of the CURRENT key are removed
        // here so the registry (which is checkpointed in FULL on every snapshot)
        // does not grow without bound, and so a restored registry only ever
        // contains genuinely pending timers.
        if (registeredEventTimeTimersByKey != null) {
            Object key = currentRegistrationKey();
            TreeSet<Long> timers = registeredEventTimeTimersByKey.get(key);
            if (timers != null) {
                timers.removeIf(timer -> timer <= time);
                if (timers.isEmpty()) {
                    registeredEventTimeTimersByKey.remove(key);
                }
            }
        }
    }

    public void onProcessingTime(long time) throws Exception {
        // STEP 1
        PriorityQueue<Long> sortedTimestamps = getSortedTimestamps();
        NFAState nfaState = getNFAState();

        // STEP 2
        while (!sortedTimestamps.isEmpty()) {
            long timestamp = sortedTimestamps.poll();
            advanceTime(nfaState, timestamp);
            try (Stream<IN> elements = sort(elementQueueState.get(timestamp))) {
                elements.forEachOrdered(
                        event -> {
                            try {
                                processEvent(nfaState, event, timestamp);
                            } catch (Exception e) {
                                throw new StreamException(ERR_STREAM_STATE_ERROR, e).param(ARG_DETAIL, "onProcessingTime processEvent");
                            }
                        });
            }
            elementQueueState.remove(timestamp);
        }

        // STEP 3
        advanceTime(nfaState, timerService.currentProcessingTime());

        // STEP 4
        updateNFA(nfaState);

        // STEP 5: idle-state reset (same logic as onEventTime)
        resetNfaStateIfFullyTimedOut(nfaState, timerService.currentProcessingTime());
    }

    /**
     * Idle-state reset shared by {@link #onEventTime(long)} and {@link #onProcessingTime(long)}:
     * when the only remaining partial match is the start state and its window has fully passed,
     * clear the keyed NFA state (the start state carries no user data and is re-created lazily by
     * {@link #getNFAState()} on the next event).
     *
     * <p>Invariant: {@code partialMatches.size() == 1} implies the single element is the start
     * state — {@code NFA.doProcess} always re-adds it, and neither {@code NFA.advanceTime}
     * (whose {@code isStateTimedOut} excludes start states) nor the after-match skip strategies
     * ever prune it. Start states hold no SharedBuffer entry ({@code previousBufferEntry == null}),
     * so no buffer release is needed here: entries of timed-out partial matches are released by
     * {@code NFA.advanceTime} itself.
     */
    private void resetNfaStateIfFullyTimedOut(NFAState nfaState, long currentTime) throws IOException {
        if (nfaState.getPartialMatches().size() != 1 || !nfaState.getCompletedMatches().isEmpty()) {
            return;
        }
        for (io.nop.stream.cep.nfa.ComputationState computationState : nfaState.getPartialMatches()) {
            String stateName = computationState.getCurrentStateName();
            Map<String, Long> windowTimes = nfa.getWindowTimes();
            long windowTime = windowTimes.containsKey(stateName)
                    ? windowTimes.get(stateName) : nfa.getWindowTime();
            if (windowTime <= 0 || currentTime < computationState.getStartTimestamp() + windowTime) {
                return;
            }
        }
        computationStates.clear();
    }

    private Stream<IN> sort(Collection<IN> elements) {
        Stream<IN> stream = elements.stream();
        return (comparator == null) ? stream : stream.sorted(comparator);
    }

    private NFAState getNFAState() throws IOException {
        NFAState nfaState = computationStates.value();
        return nfaState != null ? nfaState : nfa.createInitialNFAState();
    }

    private void updateNFA(NFAState nfaState) throws IOException {
        if (nfaState.isStateChanged()) {
            nfaState.resetStateChanged();
            nfaState.resetNewStartPartialMatch();
            computationStates.update(nfaState);
        }
    }

    private PriorityQueue<Long> getSortedTimestamps() throws Exception {
        PriorityQueue<Long> sortedTimestamps = new PriorityQueue<>();
        for (Long timestamp : elementQueueState.keys()) {
            sortedTimestamps.offer(timestamp);
        }
        return sortedTimestamps;
    }

    private void processEvent(NFAState nfaState, IN event, long timestamp) throws Exception {
        try (SharedBufferAccessor<IN> sharedBufferAccessor = partialMatches.getAccessor()) {
            Collection<Map<String, List<IN>>> patterns =
                    nfa.process(
                            sharedBufferAccessor,
                            nfaState,
                            event,
                            timestamp,
                            afterMatchSkipStrategy,
                            cepTimerService);
            if (nfa.getWindowTime() > 0 && nfaState.isNewStartPartialMatch()) {
                registerTimer(timestamp + nfa.getWindowTime());
            }
            if (nfaState.isNewStartPartialMatch()) {
                Map<String, Long> perStateWindowTimes = nfa.getWindowTimes();
                if (perStateWindowTimes != null) {
                    for (Map.Entry<String, Long> entry : perStateWindowTimes.entrySet()) {
                        long stateWindowTime = entry.getValue();
                        if (stateWindowTime > 0 && stateWindowTime != nfa.getWindowTime()) {
                            registerTimer(timestamp + stateWindowTime);
                        }
                    }
                }
            }
            processMatchedSequences(patterns, timestamp);
        }
    }

    private void advanceTime(NFAState nfaState, long timestamp) throws Exception {
        try (SharedBufferAccessor<IN> sharedBufferAccessor = partialMatches.getAccessor()) {
            Tuple2<
                    Collection<Map<String, List<IN>>>,
                    Collection<Tuple2<Map<String, List<IN>>, Long>>>
                    pendingMatchesAndTimeout =
                    nfa.advanceTime(
                            sharedBufferAccessor,
                            nfaState,
                            timestamp,
                            afterMatchSkipStrategy);

            Collection<Map<String, List<IN>>> pendingMatches = pendingMatchesAndTimeout.f0;
            Collection<Tuple2<Map<String, List<IN>>, Long>> timedOut = pendingMatchesAndTimeout.f1;

            if (!pendingMatches.isEmpty()) {
                processMatchedSequences(pendingMatches, timestamp);
            }
            if (!timedOut.isEmpty()) {
                processTimedOutSequences(timedOut);
            }
        }
    }

    private void processMatchedSequences(
            Iterable<Map<String, List<IN>>> matchingSequences, long timestamp) throws Exception {
        PatternProcessFunction<IN, OUT> function = getUserFunction();
        setTimestamp(timestamp);
        for (Map<String, List<IN>> matchingSequence : matchingSequences) {
            function.processMatch(matchingSequence, context, collector);
        }
    }

    private void processTimedOutSequences(
            Collection<Tuple2<Map<String, List<IN>>, Long>> timedOutSequences) throws Exception {
        PatternProcessFunction<IN, OUT> function = getUserFunction();
        if (function instanceof TimedOutPartialMatchHandler) {

            @SuppressWarnings("unchecked")
            TimedOutPartialMatchHandler<IN> timeoutHandler =
                    (TimedOutPartialMatchHandler<IN>) function;

            for (Tuple2<Map<String, List<IN>>, Long> matchingSequence : timedOutSequences) {
                setTimestamp(matchingSequence.f1);
                timeoutHandler.processTimedOutMatch(matchingSequence.f0, context);
            }
        }
    }

    private void setTimestamp(long timestamp) {
        if (!isProcessingTime) {
            collector.setAbsoluteTimestamp(timestamp);
        }

        context.setTimestamp(timestamp);
    }

    /**
     * Gives {@link NFA} access to {@link InternalTimerService} and tells if {@link CepOperator}
     * works in processing time. Should be instantiated once per operator.
     */
    private class TimerServiceImpl implements TimerService {

        @Override
        public long currentProcessingTime() {
            return timerService.currentProcessingTime();
        }
    }

    /**
     * Implementation of {@link PatternProcessFunction.Context}. Design to be instantiated once per
     * operator. It serves three methods:
     *
     * <ul>
     *   <li>gives access to currentProcessingTime through {@link InternalTimerService}
     *   <li>gives access to timestamp of current record (or null if Processing time)
     *   <li>enables side outputs with proper timestamp of StreamRecord handling based on either
     *       Processing or Event time
     * </ul>
     */
    private class ContextFunctionImpl implements PatternProcessFunction.Context {

        private Long timestamp;

        @Override
        public <X> void output(final OutputTag<X> outputTag, final X value) {
            final StreamRecord<X> record;
            if (isProcessingTime) {
                record = new StreamRecord<>(value);
            } else {
                record = new StreamRecord<>(value, timestamp());
            }
            output.collect(outputTag, record);
        }

        void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public long timestamp() {
            return timestamp;
        }

        @Override
        public long currentProcessingTime() {
            return timerService.currentProcessingTime();
        }
    }

    //////////////////////			Testing Methods			//////////////////////

    boolean hasNonEmptySharedBuffer(KEY key) throws Exception {
        setCurrentKey(key);
        return !partialMatches.isEmpty();
    }

    boolean hasNonEmptyPQ(KEY key) throws Exception {
        setCurrentKey(key);
        return !elementQueueState.isEmpty();
    }

    int getPQSize(KEY key) throws Exception {
        setCurrentKey(key);
        int counter = 0;
        for (List<IN> elements : elementQueueState.values()) {
            counter += elements.size();
        }
        return counter;
    }

    public NFAState getNFAStateForTesting() throws IOException {
        return getNFAState();
    }

    public void updateNFAStateForTesting(NFAState state) throws IOException {
        computationStates.update(state);
    }

    public KeyedStateStore getKeyedStateStore() {
        return keyedStateStore;
    }

    public CepRuntimeContext getCepRuntimeContext() {
        return cepRuntimeContext;
    }

    public SharedBuffer<IN> getPartialMatches() {
        return partialMatches;
    }

    public long getCurrentWatermark() {
        return currentWatermark;
    }

    /**
     * P1-04: testing accessor for the event-time timer bookkeeping registry.
     *
     * @return the union of currently registered (pending) event-time timers across all
     *         keys; empty when the registry has not been initialized yet
     */
    java.util.Set<Long> getRegisteredEventTimeTimersForTesting() {
        if (registeredEventTimeTimersByKey == null) {
            return java.util.Collections.emptySet();
        }
        TreeSet<Long> union = new TreeSet<>();
        for (TreeSet<Long> timers : registeredEventTimeTimersByKey.values()) {
            union.addAll(timers);
        }
        return java.util.Collections.unmodifiableSet(union);
    }

    /**
     * AR-10/AR-11 (Plan 2026-09-04-1326-1 Phase 4): testing accessor for the raw
     * per-key timer ledger — assertions can inspect the exact ledger KEY OBJECTS
     * (class identity after restore) and their pending timestamps.
     */
    Map<Object, TreeSet<Long>> getLedgerForTesting() {
        return registeredEventTimeTimersByKey == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(registeredEventTimeTimersByKey);
    }

    /** AR-10: testing accessor for the resolved effective key class. */
    Class<?> resolveEffectiveKeyClassForTesting() {
        return resolveEffectiveKeyClass();
    }
}
