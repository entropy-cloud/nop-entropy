/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.runtime.operators.windowing;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.nop.api.core.annotations.core.Internal;
import static io.nop.api.core.util.Guard.checkArgument;
import static io.nop.api.core.util.Guard.notNull;

import io.nop.commons.tuple.Tuple2;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.state.backend.IInternalStateBackend;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.VoidNamespace;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.InternalTimer;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.TimestampedCollector;
import io.nop.stream.core.operators.Triggerable;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.MergingWindowAssigner;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.Window;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;
import io.nop.stream.runtime.operators.WindowOperatorTimerService;

/**
 * An operator that implements the logic for windowing based on a {@link WindowAssigner} and {@link
 * Trigger}.
 *
 * <p>When an element arrives it gets assigned a key using a {@link KeySelector} and it gets
 * assigned to zero or more windows using a {@link WindowAssigner}. Based on this, the element is
 * put into panes. A pane is the bucket of elements that have the same key and same {@code Window}.
 * An element can be in multiple panes if it was assigned to multiple windows by the {@code
 * WindowAssigner}.
 *
 * <p>Each pane gets its own instance of the provided {@code Trigger}. This trigger determines when
 * the contents of the pane should be processed to emit results. When a trigger fires, the given
 * {@link InternalWindowFunction} is invoked to produce the results that are emitted for the pane to
 * which the {@code Trigger} belongs.
 *
 * @param <K>   The type of key returned by the {@code KeySelector}.
 * @param <IN>  The type of the incoming elements.
 * @param <OUT> The type of elements emitted by the {@code InternalWindowFunction}.
 * @param <W>   The type of {@code Window} that the {@code WindowAssigner} assigns.
 */
@Internal
public class WindowOperator<K, IN, ACC, OUT, W extends Window>
        extends AbstractUdfStreamOperator<OUT, InternalWindowFunction<ACC, OUT, K, W>>
        implements OneInputStreamOperator<IN, OUT>, Triggerable<K, W> {

    private static final long serialVersionUID = 1L;
    private static final String WINDOW_VALUE_KEY = "__window_value__";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WindowOperator.class);

    // ------------------------------------------------------------------------
    // Configuration values and user functions
    // ------------------------------------------------------------------------

    protected final WindowAssigner<? super IN, W> windowAssigner;

    protected final KeySelector<IN, K> keySelector;

    private final Trigger<? super IN, ? super W> trigger;

    protected transient InternalAppendingState<K, W, IN, ACC, ACC> windowState;

    /**
     * For serializing the key in checkpoints.
     */
    protected final TypeSerializer<K> keySerializer;

    /**
     * The class of the key type, used for state backend creation.
     */
    protected final Class<K> keyClass;

    /**
     * For serializing the window in checkpoints.
     */
    protected final TypeSerializer<W> windowSerializer;

    /**
     * The allowed lateness for elements. This is used for:
     *
     * <ul>
     *   <li>Deciding if an element should be dropped from a window due to lateness.
     *   <li>Clearing the state of a window if the system time passes the {@code window.maxTimestamp
     *       + allowedLateness} landmark.
     * </ul>
     */
    protected final long allowedLateness;

    /**
     * {@link OutputTag} to use for late arriving events. Elements for which {@code
     * window.maxTimestamp + allowedLateness} is smaller than the current watermark will be emitted
     * to this.
     */
    protected final OutputTag<IN> lateDataOutputTag;

    protected final Class<?> accClass;

    private static final String LATE_ELEMENTS_DROPPED_METRIC_NAME = "numLateRecordsDropped";

    // ------------------------------------------------------------------------
    // State that is not checkpointed
    // ------------------------------------------------------------------------

    /** The state in which the window contents is stored. Each window is a namespace */

    /** The state that holds the merging window metadata (the sets that describe what is merged). */
    private transient InternalListState<K, VoidNamespace, Tuple2<W, W>> mergingSetsState;

    /**
     * This is given to the {@code InternalWindowFunction} for emitting elements with a given
     * timestamp.
     */
    protected transient TimestampedCollector<OUT> timestampedCollector;

    protected transient Context triggerContext = new Context(null, null);

    protected transient WindowContext processContext;

    protected transient WindowAssigner.WindowAssignerContext windowAssignerContext;

    // ------------------------------------------------------------------------
    // State that needs to be checkpointed
    // ------------------------------------------------------------------------

    protected transient InternalTimerService<W> internalTimerService;

    /**
     * Namespace-backed window contents state.
     *
     * <p>Uses `currentKey + namespace(window)` to scope each pane.
     */
    private transient MapState<String, ACC> windowContentsState;

    /**
     * Per-trigger SimpleAccumulator state, keyed by composite key (key + window + descriptor name).
     * Used by triggers like CountTrigger that need to maintain state via getSimpleAccumulator().
     */
    protected Map<String, SimpleAccumulator<?>> triggerAccumulators;

    public WindowOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            TypeSerializer<W> windowSerializer,
            KeySelector<IN, K> keySelector,
            TypeSerializer<K> keySerializer,
            Class<K> keyClass,
            InternalWindowFunction<ACC, OUT, K, W> windowFunction,
            Trigger<? super IN, ? super W> trigger,
            long allowedLateness,
            OutputTag<IN> lateDataOutputTag) {
        this(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                windowFunction, trigger, allowedLateness, lateDataOutputTag,
                (Class<ACC>) (Class<?>) Object.class);
    }

    public WindowOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            TypeSerializer<W> windowSerializer,
            KeySelector<IN, K> keySelector,
            TypeSerializer<K> keySerializer,
            Class<K> keyClass,
            InternalWindowFunction<ACC, OUT, K, W> windowFunction,
            Trigger<? super IN, ? super W> trigger,
            long allowedLateness,
            OutputTag<IN> lateDataOutputTag,
            Class<ACC> accClass) {

        super(windowFunction);

        checkArgument(allowedLateness >= 0);

        this.windowAssigner = notNull(windowAssigner, "windowAssigner");
        this.windowSerializer = notNull(windowSerializer, "windowSerializer");
        this.keySelector = notNull(keySelector, "keySelector");
        this.keySerializer = notNull(keySerializer, "keySerializer");
        this.keyClass = notNull(keyClass, "keyClass");
        this.trigger = notNull(trigger, "trigger");
        this.allowedLateness = allowedLateness;
        this.lateDataOutputTag = lateDataOutputTag;
        this.accClass = notNull(accClass, "accClass");
    }

    @Override
    public void open() throws Exception {
        super.open();

        timestampedCollector = new TimestampedCollector<>(output);
        if (this.triggerAccumulators == null) {
            this.triggerAccumulators = new HashMap<>();
        }

        if (this.stateBackend == null) {
            this.stateBackend = new MemoryStateBackend();
        }
        this.keyedStateBackend = this.stateBackend.createKeyedStateBackend(keyClass);

        // Apply deferred state restore (from checkpoint recovery before open())
        applyPendingRestoreState();

        @SuppressWarnings("unchecked")
        Class<ACC> accType = (Class<ACC>) accClass;
        MapStateDescriptor<String, ACC> windowContentsDescriptor =
                new MapStateDescriptor<>("window-contents", String.class, accType);
        windowContentsState = this.keyedStateBackend.getMapState(windowContentsDescriptor);

        internalTimerService = new WindowOperatorTimerService<>(this,
                () -> getKeyedStateBackend() != null ? (K) getKeyedStateBackend().getCurrentKey() : null);

        triggerContext = new Context(null, null);
        processContext = new WindowContext(null);

        windowAssignerContext =
                new WindowAssigner.WindowAssignerContext() {
                    @Override
                    public long getCurrentProcessingTime() {
                        return internalTimerService.currentProcessingTime();
                    }
                };

        // create (or restore) the state that hold the actual window contents
        // NOTE - the state may be null in the case of the overriding evicting window operator

        // create the typed and helper states for merging windows
        if (windowAssigner instanceof MergingWindowAssigner) {

            @SuppressWarnings("unchecked") final Class<Tuple2<W, W>> typedTuple = (Class<Tuple2<W, W>>) (Class<?>) Tuple2.class;

            final ListStateDescriptor<Tuple2<W, W>> mergingSetsStateDescriptor =
                    new ListStateDescriptor<>("merging-window-set", typedTuple);

            // get the state that stores the merging sets
            if (keyedStateBackend instanceof IInternalStateBackend) {
                @SuppressWarnings("unchecked")
                IInternalStateBackend<K> internalBackend = (IInternalStateBackend<K>) keyedStateBackend;
                mergingSetsState = internalBackend.getInternalListState(mergingSetsStateDescriptor);
                mergingSetsState.setCurrentNamespace(VoidNamespace.INSTANCE);
            }
        }
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
        if (internalTimerService instanceof WindowOperatorTimerService) {
            ((WindowOperatorTimerService<K, W>) internalTimerService).advanceWatermark(mark.getTimestamp());
        }
        super.processWatermark(mark);
    }

    @Override
    public void close() throws Exception {
        super.close();
        timestampedCollector = null;
        triggerContext = null;
        processContext = null;
        windowAssignerContext = null;
        windowContentsState = null;
        triggerAccumulators = null;
    }

    @Override
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = super.snapshotState(context);
        if (triggerAccumulators != null) {
            result.putOperatorState("trigger-accumulators", new HashMap<>(triggerAccumulators));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void restoreState(io.nop.stream.core.checkpoint.OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        if (snapshotResult != null) {
            Object restored = snapshotResult.getOperatorState("trigger-accumulators");
            if (restored instanceof Map) {
                this.triggerAccumulators = (Map<String, SimpleAccumulator<?>>) restored;
            }
        }
    }

    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        final Collection<W> elementWindows =
                windowAssigner.assignWindows(
                        element.getValue(), element.getTimestamp(), windowAssignerContext);

        // if element is handled by none of assigned elementWindows
        boolean isSkippedElement = true;

        final K key = keySelector.getKey(element.getValue());
        if (keyedStateBackend != null) {
            this.<K>getKeyedStateBackend().setCurrentKey(key);
        }

        if (windowAssigner instanceof MergingWindowAssigner) {
            MergingWindowSet<W> mergingWindows = getMergingWindowSet();

            for (W window : elementWindows) {

                // adding the new window might result in a merge, in that case the actualWindow
                // is the merged window and we work with that. If we don't merge then
                // actualWindow == window
                W actualWindow =
                        mergingWindows.addWindow(
                                window,
                                new MergingWindowSet.MergeFunction<W>() {
                                    @Override
                                    public void merge(
                                            W mergeResult,
                                            Collection<W> mergedWindows,
                                            W stateWindowResult,
                                            Collection<W> mergedStateWindows)
                                            throws Exception {

                                        if ((windowAssigner.isEventTime()
                                                && mergeResult.maxTimestamp() + allowedLateness
                                                <= internalTimerService
                                                .currentWatermark())) {
                                            throw new UnsupportedOperationException(
                                                    "The end timestamp of an "
                                                            + "event-time window cannot become earlier than the current watermark "
                                                            + "by merging. Current watermark: "
                                                            + internalTimerService
                                                            .currentWatermark()
                                                            + " window: "
                                                            + mergeResult);
                                        } else if (!windowAssigner.isEventTime()) {
                                            long currentProcessingTime =
                                                    internalTimerService.currentProcessingTime();
                                            if (mergeResult.maxTimestamp()
                                                    <= currentProcessingTime) {
                                                throw new UnsupportedOperationException(
                                                        "The end timestamp of a "
                                                                + "processing-time window cannot become earlier than the current processing time "
                                                                + "by merging. Current processing time: "
                                                                + currentProcessingTime
                                                                + " window: "
                                                                + mergeResult);
                                            }
                                        }

                                        triggerContext.key = key;
                                        triggerContext.window = mergeResult;

                                        triggerContext.onMerge(mergedWindows);

                                        for (W m : mergedWindows) {
                                            triggerContext.window = m;
                                            triggerContext.clear();
                                            deleteCleanupTimer(m);
                                        }

                                        // merge the merged state windows into the newly resulting
                                        // state window
//                                        windowMergingState.mergeNamespaces(
//                                                stateWindowResult, mergedStateWindows);
                                        mergeWindowContents(key, stateWindowResult, mergedStateWindows);
                                    }
                                });

                // drop if the window is already late
                if (isWindowLate(actualWindow)) {
                    mergingWindows.retireWindow(actualWindow);
                    continue;
                }
                isSkippedElement = false;

                W stateWindow = mergingWindows.getStateWindow(actualWindow);
                if (stateWindow == null) {
                    throw new IllegalStateException(
                            "Window " + window + " is not in in-flight window set.");
                }
                addWindowElement(key, stateWindow, element.getValue());

                triggerContext.key = key;
                triggerContext.window = actualWindow;

                TriggerResult triggerResult = triggerContext.onElement(element);

                if (triggerResult.isFire()) {
                    ACC contents = getWindowContents(key, stateWindow);
                    if (contents != null) {
                        emitWindowContents(key, actualWindow, contents);
                    }
                }

                if (triggerResult.isPurge()) {
                    clearWindowContents(key, stateWindow);
                }
                registerCleanupTimer(actualWindow);
            }

            // need to make sure to update the merging state in state
            mergingWindows.persist();
        } else {
            for (W window : elementWindows) {

                // drop if the window is already late
                if (isWindowLate(window)) {
                    continue;
                }
                isSkippedElement = false;
                addWindowElement(key, window, element.getValue());

                triggerContext.key = key;
                triggerContext.window = window;

                TriggerResult triggerResult = triggerContext.onElement(element);

                if (triggerResult.isFire()) {
                    ACC contents = getWindowContents(key, window);
                    if (contents != null) {
                        emitWindowContents(key, window, contents);
                    }
                }

                if (triggerResult.isPurge()) {
                    clearWindowContents(key, window);
                }
                registerCleanupTimer(window);
            }
        }

        // side output input event if
        // element not handled by any window
        // late arriving tag has been set
        // windowAssigner is event time and current timestamp + allowed lateness no less than
        // element timestamp
        if (isSkippedElement && isElementLate(element)) {
            if (lateDataOutputTag != null) {
                sideOutput(element);
            } else {
                //this.numLateRecordsDropped.inc();
            }
        }
    }

    @Override
    public void onEventTime(InternalTimer<K, W> timer) throws Exception {
        triggerContext.key = timer.getKey();
        triggerContext.window = timer.getNamespace();

        MergingWindowSet<W> mergingWindows;

        if (windowAssigner instanceof MergingWindowAssigner) {
            mergingWindows = getMergingWindowSet();
            W stateWindow = mergingWindows.getStateWindow(triggerContext.window);
            if (stateWindow == null) {
                // Timer firing for non-existent window, this can only happen if a
                // trigger did not clean up timers. We have already cleared the merging
                // window and therefore the Trigger state, however, so nothing to do.
                return;
            } else {
            }
        } else {
            mergingWindows = null;
        }

        TriggerResult triggerResult = triggerContext.onEventTime(timer.getTimestamp());

        if (triggerResult.isFire()) {
            W stateWindow = mergingWindows != null
                    ? mergingWindows.getStateWindow(triggerContext.window)
                    : triggerContext.window;
            ACC contents = getWindowContents(triggerContext.key, stateWindow);
            if (contents != null) {
                emitWindowContents(triggerContext.key, triggerContext.window, contents);
            }
        }

        if (triggerResult.isPurge()) {
            W stateWindow = mergingWindows != null
                    ? mergingWindows.getStateWindow(triggerContext.window)
                    : triggerContext.window;
            clearWindowContents(triggerContext.key, stateWindow);
        }

        if (windowAssigner.isEventTime()
                && isCleanupTime(triggerContext.window, timer.getTimestamp())) {
            W stateWindow = mergingWindows != null
                    ? mergingWindows.getStateWindow(triggerContext.window)
                    : triggerContext.window;
            if (stateWindow != null) {
                clearWindowContents(triggerContext.key, stateWindow);
            }
        }

        if (mergingWindows != null) {
            // need to make sure to update the merging state in state
            mergingWindows.persist();
        }
    }

    @Override
    public void onProcessingTime(InternalTimer<K, W> timer) throws Exception {
        triggerContext.key = timer.getKey();
        triggerContext.window = timer.getNamespace();

        MergingWindowSet<W> mergingWindows;

        if (windowAssigner instanceof MergingWindowAssigner) {
            mergingWindows = getMergingWindowSet();
            W stateWindow = mergingWindows.getStateWindow(triggerContext.window);
            if (stateWindow == null) {
                // Timer firing for non-existent window, this can only happen if a
                // trigger did not clean up timers. We have already cleared the merging
                // window and therefore the Trigger state, however, so nothing to do.
                return;
            } else {
            }
        } else {
            mergingWindows = null;
        }

        TriggerResult triggerResult = triggerContext.onProcessingTime(timer.getTimestamp());

        if (triggerResult.isFire()) {
            W stateWindow = mergingWindows != null
                    ? mergingWindows.getStateWindow(triggerContext.window)
                    : triggerContext.window;
            ACC contents = getWindowContents(triggerContext.key, stateWindow);
            if (contents != null) {
                emitWindowContents(triggerContext.key, triggerContext.window, contents);
            }
        }

        if (triggerResult.isPurge()) {
            W stateWindow = mergingWindows != null
                    ? mergingWindows.getStateWindow(triggerContext.window)
                    : triggerContext.window;
            clearWindowContents(triggerContext.key, stateWindow);
        }

        if (!windowAssigner.isEventTime()
                && isCleanupTime(triggerContext.window, timer.getTimestamp())) {
            W stateWindow = mergingWindows != null
                    ? mergingWindows.getStateWindow(triggerContext.window)
                    : triggerContext.window;
            if (stateWindow != null) {
                clearWindowContents(triggerContext.key, stateWindow);
            }
        }

        if (mergingWindows != null) {
            // need to make sure to update the merging state in state
            mergingWindows.persist();
        }
    }

    /**
     * Drops all state for the given window and calls {@link Trigger#clear(Window,
     * Trigger.TriggerContext)}.
     *
     * <p>The caller must ensure that the correct key is set in the state backend and the
     * triggerContext object.
     */
    /**
     * Emits the contents of the given window using the {@link InternalWindowFunction}.
     */
    @SuppressWarnings("unchecked")
    private void emitWindowContents(K key, W window, ACC contents) throws Exception {
        timestampedCollector.setAbsoluteTimestamp(window.maxTimestamp());
        processContext.window = window;
        userFunction.process(
                key, window, processContext, contents, timestampedCollector);
    }

    /**
     * Write skipped late arriving element to SideOutput.
     *
     * @param element skipped late arriving element to side output
     */
    protected void sideOutput(StreamRecord<IN> element) {
        output.collect(lateDataOutputTag, element);
    }

    /**
     * Retrieves the {@link MergingWindowSet} for the currently active key. The caller must ensure
     * that the correct key is set in the state backend.
     *
     * <p>The caller must also ensure to properly persist changes to state using {@link
     * MergingWindowSet#persist()}.
     */
    protected MergingWindowSet<W> getMergingWindowSet() throws Exception {
        @SuppressWarnings("unchecked")
        MergingWindowAssigner<? super IN, W> mergingAssigner =
                (MergingWindowAssigner<? super IN, W>) windowAssigner;
        return new MergingWindowSet<>(mergingAssigner, mergingSetsState);
    }

    /**
     * Returns {@code true} if the watermark is after the end timestamp plus the allowed lateness of
     * the given window.
     */
    protected boolean isWindowLate(W window) {
        return (windowAssigner.isEventTime()
                && (cleanupTime(window) <= internalTimerService.currentWatermark()));
    }

    /**
     * Decide if a record is currently late, based on current watermark and allowed lateness.
     *
     * @param element The element to check
     * @return The element for which should be considered when sideoutputs
     */
    protected boolean isElementLate(StreamRecord<IN> element) {
        return (windowAssigner.isEventTime())
                && (element.getTimestamp() + allowedLateness
                <= internalTimerService.currentWatermark());
    }

    /**
     * Registers a timer to cleanup the content of the window.
     *
     * @param window the window whose state to discard
     */
    protected void registerCleanupTimer(W window) {
        long cleanupTime = cleanupTime(window);
        if (cleanupTime == Long.MAX_VALUE) {
            // don't set a GC timer for "end of time"
            return;
        }

        if (windowAssigner.isEventTime()) {
            triggerContext.registerEventTimeTimer(cleanupTime);
        } else {
            triggerContext.registerProcessingTimeTimer(cleanupTime);
        }
    }

    /**
     * Deletes the cleanup timer set for the contents of the provided window.
     *
     * @param window the window whose state to discard
     */
    protected void deleteCleanupTimer(W window) {
        long cleanupTime = cleanupTime(window);
        if (cleanupTime == Long.MAX_VALUE) {
            // no need to clean up because we didn't set one
            return;
        }
        if (windowAssigner.isEventTime()) {
            triggerContext.deleteEventTimeTimer(cleanupTime);
        } else {
            triggerContext.deleteProcessingTimeTimer(cleanupTime);
        }
    }

    /**
     * Returns the cleanup time for a window, which is {@code window.maxTimestamp +
     * allowedLateness}. In case this leads to a value greater than {@link Long#MAX_VALUE} then a
     * cleanup time of {@link Long#MAX_VALUE} is returned.
     *
     * @param window the window whose cleanup time we are computing.
     */
    private long cleanupTime(W window) {
        if (windowAssigner.isEventTime()) {
            long cleanupTime = window.maxTimestamp() + allowedLateness;
            return cleanupTime >= window.maxTimestamp() ? cleanupTime : Long.MAX_VALUE;
        } else {
            return window.maxTimestamp();
        }
    }

    /**
     * Returns {@code true} if the given time is the cleanup time for the given window.
     */
    protected final boolean isCleanupTime(W window, long time) {
        return time == cleanupTime(window);
    }

    @SuppressWarnings("unchecked")
    private void addWindowElement(K key, W window, IN value) {
        IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
        typedBackend.setCurrentKey(key);
        typedBackend.setCurrentNamespace(windowNamespace(window));

        ACC current = windowContentsState.get(WINDOW_VALUE_KEY);
        if (current == null) {
            // First element: create a new accumulator and add the value to it.
            // This handles cases where IN != ACC (e.g. AggregateFunction with distinct types)
            // by ensuring the first element goes through createAccumulator() -> add(),
            // NOT a direct cast (ACC) value.
            SimpleAccumulator<IN> accumulator = createAccumulatorForWindow();
            if (accumulator != null) {
                accumulator.add(value);
                setWindowContents(key, window, (ACC) accumulator);
            } else {
                // No accumulator factory available; direct store (IN == ACC case)
                setWindowContents(key, window, (ACC) value);
            }
            return;
        }

        if (current instanceof SimpleAccumulator) {
            SimpleAccumulator<IN> accumulator = (SimpleAccumulator<IN>) current;
            accumulator.add(value);
            // Store the accumulator reference itself (not getLocalValue()) so that
            // subsequent addWindowElement calls can detect it as a SimpleAccumulator
            // and keep accumulating, instead of falling through to last-write-wins.
            setWindowContents(key, window, (ACC) accumulator);
            return;
        }

        // Last-write-wins keeps behavior deterministic for non-accumulator ACC types.
        setWindowContents(key, window, (ACC) value);
    }

    /**
     * Creates a new SimpleAccumulator for a window.
     * Returns null if no accumulator factory is available.
     * Subclasses can override to provide custom accumulator creation logic.
     */
    @SuppressWarnings("unchecked")
    protected SimpleAccumulator<IN> createAccumulatorForWindow() {
        return null;
    }

    private ACC getWindowContents(K key, W window) {
        IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
        typedBackend.setCurrentKey(key);
        typedBackend.setCurrentNamespace(windowNamespace(window));
        return windowContentsState.get(WINDOW_VALUE_KEY);
    }

    private void clearWindowContents(K key, W window) {
        IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
        typedBackend.setCurrentKey(key);
        typedBackend.setCurrentNamespace(windowNamespace(window));
        windowContentsState.remove(WINDOW_VALUE_KEY);
    }

    private void setWindowContents(K key, W window, ACC value) {
        IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
        typedBackend.setCurrentKey(key);
        typedBackend.setCurrentNamespace(windowNamespace(window));
        windowContentsState.put(WINDOW_VALUE_KEY, value);
    }

    @SuppressWarnings("unchecked")
    private void mergeWindowContents(K key, W targetWindow, Collection<W> sourceWindows) {
        if (sourceWindows == null || sourceWindows.isEmpty()) {
            return;
        }

        ACC targetValue = getWindowContents(key, targetWindow);
        for (W sourceWindow : sourceWindows) {
            if (sourceWindow == null || sourceWindow.equals(targetWindow)) {
                continue;
            }

            ACC sourceValue = getWindowContents(key, sourceWindow);
            if (sourceValue == null) {
                continue;
            }

            if (targetValue == null) {
                targetValue = sourceValue;
            } else if (targetValue instanceof SimpleAccumulator) {
                SimpleAccumulator<ACC> accumulator = (SimpleAccumulator<ACC>) targetValue;
                if (sourceValue instanceof SimpleAccumulator) {
                    // Both are SimpleAccumulators: merge source accumulator into target.
                    // This is the correct path for session window merging where both
                    // windows have been accumulating values independently.
                    accumulator.merge((SimpleAccumulator<ACC>) sourceValue);
                } else {
                    // Source is a raw value, target is an accumulator.
                    // Type incompatibility should cause a fast failure, not silent data loss.
                    throw new IllegalStateException(
                            "Cannot merge non-accumulator value into accumulator target. " +
                            "targetType=" + targetValue.getClass().getName() +
                            ", sourceType=" + sourceValue.getClass().getName());
                }
                targetValue = (ACC) accumulator;
            } else {
                // Deterministic fallback for non-accumulator values.
                targetValue = sourceValue;
            }

            clearWindowContents(key, sourceWindow);
        }

        if (targetValue != null) {
            setWindowContents(key, targetWindow, targetValue);
        }
    }

    private String windowNamespace(W window) {
        if (window == null) {
            return "_null_window_";
        }
        return window.getClass().getName() + "#" + window.toString();
    }

    /**
     * Base class for per-window {@link KeyedStateStore KeyedStateStores}. Used to allow per-window
     * state access for {@link
     * org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction}.
     */
    public abstract class AbstractPerWindowStateStore { //extends DefaultKeyedStateStore {

        // we have this in the base class even though it's not used in MergingKeyStore so that
        // we can always set it and ignore what actual implementation we have
        protected W window;
    }

    /**
     * Special {@link AbstractPerWindowStateStore} that doesn't allow access to per-window state.
     */
    public class MergingWindowStateStore extends AbstractPerWindowStateStore {
    }

    /**
     * Regular per-window state store for use with {@link
     * org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction}.
     */
    public class PerWindowStateStore extends AbstractPerWindowStateStore {
    }

    /**
     * A utility class for handling {@code ProcessWindowFunction} invocations. This can be reused by
     * setting the {@code key} and {@code window} fields. No internal state must be kept in the
     * {@code WindowContext}.
     */
    public class WindowContext implements InternalWindowFunction.InternalWindowContext {
        protected W window;

        protected AbstractPerWindowStateStore windowState;

        public WindowContext(W window) {
            this.window = window;
            this.windowState = null;
        }

        @Override
        public String toString() {
            return "WindowContext{Window = " + window.toString() + "}";
        }

        public void clear() throws Exception {
            userFunction.clear(window, this);
        }

        @Override
        public long currentProcessingTime() {
            return internalTimerService.currentProcessingTime();
        }

        @Override
        public long currentWatermark() {
            return internalTimerService.currentWatermark();
        }

        @Override
        public KeyedStateStore windowState() {
            IKeyedStateBackend<K> backend = WindowOperator.this.getKeyedStateBackend();
            if (backend == null) {
                return null;
            }
            backend.setCurrentNamespace(windowNamespace(this.window));
            return backend;
        }

        @Override
        public KeyedStateStore globalState() {
            return WindowOperator.this.getKeyedStateBackend();
        }

        @Override
        public <X> void output(OutputTag<X> outputTag, X value) {
            if (outputTag == null) {
                throw new StreamException("OutputTag must not be null.");
            }
            output.collect(outputTag, new StreamRecord<>(value, window.maxTimestamp()));
        }
    }

    /**
     * {@code Context} is a utility for handling {@code Trigger} invocations. It can be reused by
     * setting the {@code key} and {@code window} fields. No internal state must be kept in the
     * {@code Context}
     */
    public class Context implements Trigger.OnMergeContext {
        protected K key;
        protected W window;

        protected Collection<W> mergedWindows;

        public Context(K key, W window) {
            this.key = key;
            this.window = window;
        }

        public long getCurrentWatermark() {
            return internalTimerService.currentWatermark();
        }

        @Override
        public long getCurrentProcessingTime() {
            return internalTimerService.currentProcessingTime();
        }

        @Override
        public void registerProcessingTimeTimer(long time) {
            internalTimerService.registerProcessingTimeTimer(window, time);
        }

        @Override
        public void registerEventTimeTimer(long time) {
            internalTimerService.registerEventTimeTimer(window, time);
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
            internalTimerService.deleteProcessingTimeTimer(window, time);
        }

        @Override
        public void deleteEventTimeTimer(long time) {
            internalTimerService.deleteEventTimeTimer(window, time);
        }

        public TriggerResult onElement(StreamRecord<IN> element) throws Exception {
            return trigger.onElement(element.getValue(), element.getTimestamp(), window, this);
        }

        public TriggerResult onProcessingTime(long time) throws Exception {
            return trigger.onProcessingTime(time, window, this);
        }

        public TriggerResult onEventTime(long time) throws Exception {
            return trigger.onEventTime(time, window, this);
        }

        public void onMerge(Collection<W> mergedWindows) throws Exception {
            this.mergedWindows = mergedWindows;
            trigger.onMerge(window, this);
        }

        public void clear() throws Exception {
            trigger.clear(window, this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> SimpleAccumulator<T> getSimpleAccumulator(StateDescriptor<T> descriptor) {
            // Build a composite key from key + window + descriptor name
            String stateKey = "trigger_" + key + "_" + window + "_" + descriptor.getName();

            // Check for existing accumulator in trigger state map
            SimpleAccumulator<T> existing = (SimpleAccumulator<T>) triggerAccumulators.get(stateKey);
            if (existing != null) {
                return existing;
            }

            // Create new accumulator from ReducingStateDescriptor if applicable
            if (descriptor instanceof ReducingStateDescriptor) {
                ReducingStateDescriptor<T> rsd = (ReducingStateDescriptor<T>) descriptor;
                try {
                    SimpleAccumulator<T> acc = rsd.getAccumulatorType().getDeclaredConstructor().newInstance();
                    triggerAccumulators.put(stateKey, acc);
                    return acc;
                } catch (Exception e) {
                    throw new StreamException("Failed to create trigger state accumulator", e);
                }
            }
            throw new UnsupportedOperationException(
                    "getSimpleAccumulator not supported for descriptor: " + descriptor.getName());
        }

        @Override
        public String toString() {
            return "Context{" + "key=" + key + ", window=" + window + '}';
        }
    }

    /**
     * Internal class for keeping track of in-flight timers.
     */
    protected static class Timer<K, W extends Window> implements Comparable<Timer<K, W>> {
        protected long timestamp;
        protected K key;
        protected W window;

        public Timer(long timestamp, K key, W window) {
            this.timestamp = timestamp;
            this.key = key;
            this.window = window;
        }

        @Override
        public int compareTo(Timer<K, W> o) {
            return Long.compare(this.timestamp, o.timestamp);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Timer<?, ?> timer = (Timer<?, ?>) o;

            return timestamp == timer.timestamp
                    && key.equals(timer.key)
                    && window.equals(timer.window);
        }

        @Override
        public int hashCode() {
            int result = (int) (timestamp ^ (timestamp >>> 32));
            result = 31 * result + key.hashCode();
            result = 31 * result + window.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Timer{"
                    + "timestamp="
                    + timestamp
                    + ", key="
                    + key
                    + ", window="
                    + window
                    + '}';
        }
    }

    // ------------------------------------------------------------------------
    // Getters for testing
    // ------------------------------------------------------------------------

    public KeySelector<IN, K> getKeySelector() {
        return keySelector;
    }

    public WindowAssigner<? super IN, W> getWindowAssigner() {
        return windowAssigner;
    }
}
