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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.nop.api.core.annotations.core.Internal;
import static io.nop.api.core.util.Guard.checkArgument;
import static io.nop.api.core.util.Guard.notNull;

import io.nop.commons.tuple.Tuple2;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
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
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.utils.TimestampedValue;
import io.nop.stream.core.windowing.windows.TimeWindow;
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
    private static final String STATE_KEY_SEPARATOR = "\u0000";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WindowOperator.class);

    // ------------------------------------------------------------------------
    // Configuration values and user functions
    // ------------------------------------------------------------------------

    protected final WindowAssigner<? super IN, W> windowAssigner;

    protected final KeySelector<IN, K> keySelector;

    protected final Trigger<? super IN, ? super W> trigger;

    protected transient InternalAppendingState<K, W, IN, ACC, ACC> windowState;

    protected transient InternalAppendingState<K, W, IN, ACC, ACC> newAppendingWindowState;

    protected transient InternalListState<K, W, IN> newListWindowState;

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

    protected final StateDescriptor<?> windowStateDescriptor;

    protected final BiFunction<ACC, ACC, ACC> mergeFunction;

    protected final Evictor<IN, W> evictor;

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
                (Class<ACC>) (Class<?>) Object.class, null, null, null);
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
        this(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                windowFunction, trigger, allowedLateness, lateDataOutputTag,
                accClass, null, null, null);
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
            StateDescriptor<?> windowStateDescriptor,
            BiFunction<ACC, ACC, ACC> mergeFunction) {
        this(windowAssigner, windowSerializer, keySelector, keySerializer, keyClass,
                windowFunction, trigger, allowedLateness, lateDataOutputTag,
                (Class<ACC>) (Class<?>) Object.class, windowStateDescriptor, mergeFunction, null);
    }

    WindowOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            TypeSerializer<W> windowSerializer,
            KeySelector<IN, K> keySelector,
            TypeSerializer<K> keySerializer,
            Class<K> keyClass,
            InternalWindowFunction<ACC, OUT, K, W> windowFunction,
            Trigger<? super IN, ? super W> trigger,
            long allowedLateness,
            OutputTag<IN> lateDataOutputTag,
            Class<ACC> accClass,
            StateDescriptor<?> windowStateDescriptor,
            BiFunction<ACC, ACC, ACC> mergeFunction,
            Evictor<IN, W> evictor) {

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
        this.windowStateDescriptor = windowStateDescriptor;
        this.mergeFunction = mergeFunction;
        this.evictor = evictor;
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

        applyPendingRestoreState();

        if (windowStateDescriptor != null && keyedStateBackend instanceof IInternalStateBackend) {
            @SuppressWarnings("unchecked")
            IInternalStateBackend<K> internalBackend = (IInternalStateBackend<K>) keyedStateBackend;

            if (windowStateDescriptor instanceof AggregatingStateDescriptor) {
                @SuppressWarnings("unchecked")
                AggregatingStateDescriptor<IN, ACC, ?> aggDesc =
                        (AggregatingStateDescriptor<IN, ACC, ?>) windowStateDescriptor;
                newAppendingWindowState =
                        (InternalAppendingState<K, W, IN, ACC, ACC>)
                                (InternalAppendingState<?, ?, ?, ?, ?>)
                                        internalBackend.getInternalAppendingState(aggDesc);
            } else if (windowStateDescriptor instanceof ListStateDescriptor) {
                @SuppressWarnings("unchecked")
                ListStateDescriptor<IN> listDesc = (ListStateDescriptor<IN>) windowStateDescriptor;
                newListWindowState = internalBackend.getInternalListState(listDesc);
            }
        } else {
            @SuppressWarnings("unchecked")
            Class<ACC> accType = (Class<ACC>) accClass;
            MapStateDescriptor<String, ACC> windowContentsDescriptor =
                    new MapStateDescriptor<>("window-contents", String.class, accType);
            windowContentsState = this.keyedStateBackend.getMapState(windowContentsDescriptor);
        }

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

        if (windowAssigner instanceof MergingWindowAssigner) {

            @SuppressWarnings("unchecked") final Class<Tuple2<W, W>> typedTuple = (Class<Tuple2<W, W>>) (Class<?>) Tuple2.class;

            final ListStateDescriptor<Tuple2<W, W>> mergingSetsStateDescriptor =
                    new ListStateDescriptor<>("merging-window-set", typedTuple);

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
                Map<?, ?> restoredMap = (Map<?, ?>) restored;
                for (Map.Entry<?, ?> entry : restoredMap.entrySet()) {
                    if (!(entry.getValue() instanceof SimpleAccumulator)) {
                        throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                                .param(ARG_EXPECTED_TYPE, "SimpleAccumulator")
                                .param(ARG_ACTUAL_TYPE, entry.getValue().getClass().getName());
                    }
                }
                this.triggerAccumulators = (Map<String, SimpleAccumulator<?>>) restored;
            }
        }
    }

    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        final Collection<W> elementWindows =
                windowAssigner.assignWindows(
                        element.getValue(), element.getTimestamp(), windowAssignerContext);

        boolean isSkippedElement = true;

        final K key = keySelector.getKey(element.getValue());
        if (keyedStateBackend != null) {
            this.<K>getKeyedStateBackend().setCurrentKey(key);
        }

        if (windowAssigner instanceof MergingWindowAssigner) {
            isSkippedElement = processElementForMergingWindow(element, elementWindows, key);
        } else {
            isSkippedElement = processElementForRegularWindow(element, elementWindows, key);
        }

        if (isSkippedElement && isElementLate(element)) {
            if (lateDataOutputTag != null) {
                sideOutput(element);
            }
        }
    }

    private boolean processElementForMergingWindow(
            StreamRecord<IN> element, Collection<W> elementWindows, K key) throws Exception {
        MergingWindowSet<W> mergingWindows = getMergingWindowSet();
        boolean isSkippedElement = true;

        for (W window : elementWindows) {
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
                                        throw new StreamException(ERR_STREAM_WINDOW_MERGE_INVALID_WATERMARK)
                                                .param(ARG_WATERMARK, internalTimerService.currentWatermark())
                                                .param(ARG_WINDOW, mergeResult);
                                    } else if (!windowAssigner.isEventTime()) {
                                        long currentProcessingTime =
                                                internalTimerService.currentProcessingTime();
                                        if (mergeResult.maxTimestamp()
                                                <= currentProcessingTime) {
                                            throw new StreamException(ERR_STREAM_WINDOW_MERGE_INVALID_PROCESSING_TIME)
                                                    .param(ARG_PROCESSING_TIME, currentProcessingTime)
                                                    .param(ARG_WINDOW, mergeResult);
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

                                    mergeWindowContents(key, stateWindowResult, mergedStateWindows);
                                }
                            });

            if (isWindowLate(actualWindow)) {
                mergingWindows.retireWindow(actualWindow);
                continue;
            }
            isSkippedElement = false;

            W stateWindow = mergingWindows.getStateWindow(actualWindow);
            if (stateWindow == null) {
                throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
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

        mergingWindows.persist();
        return isSkippedElement;
    }

    private boolean processElementForRegularWindow(
            StreamRecord<IN> element, Collection<W> elementWindows, K key) throws Exception {
        boolean isSkippedElement = true;

        for (W window : elementWindows) {
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
                triggerContext.clear();
            }
            registerCleanupTimer(window);
        }

        return isSkippedElement;
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
                triggerContext.clear();
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
                triggerContext.clear();
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

        if (evictor != null) {
            Iterable<IN> elements = (Iterable<IN>) contents;
            List<TimestampedValue<IN>> wrapped = new ArrayList<>();
            for (IN element : elements) {
                long elementTimestamp = internalTimerService.currentWatermark();
                if (element instanceof StreamRecord) {
                    elementTimestamp = ((StreamRecord<IN>) element).getTimestamp();
                }
                wrapped.add(new TimestampedValue<>(element, elementTimestamp));
            }
            Evictor.EvictorContext evictorContext = new Evictor.EvictorContext() {
                @Override
                public long getCurrentProcessingTime() {
                    return internalTimerService.currentProcessingTime();
                }

                @Override
                public long getCurrentWatermark() {
                    return internalTimerService.currentWatermark();
                }
            };
            evictor.evictBefore(wrapped, wrapped.size(), window, evictorContext);
            List<IN> evictedElements = new ArrayList<>();
            for (TimestampedValue<IN> tv : wrapped) {
                evictedElements.add(tv.getValue());
            }
            userFunction.process(
                    key, window, processContext, (ACC) (Iterable<IN>) evictedElements, timestampedCollector);
        } else {
            userFunction.process(
                    key, window, processContext, contents, timestampedCollector);
        }
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
        if (newAppendingWindowState != null) {
            IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
            typedBackend.setCurrentKey(key);
            newAppendingWindowState.setCurrentNamespace(window);
            try {
                newAppendingWindowState.add(value);
            } catch (java.io.IOException e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to add element to appending window state");
            }
            return;
        }

        if (newListWindowState != null) {
            IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
            typedBackend.setCurrentKey(key);
            newListWindowState.setCurrentNamespace(window);
            try {
                newListWindowState.add(value);
            } catch (java.io.IOException e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to add element to list window state");
            }
            return;
        }

        IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
        typedBackend.setCurrentKey(key);
        typedBackend.setCurrentNamespace(windowNamespace(window));

        ACC current = windowContentsState.get(WINDOW_VALUE_KEY);
        if (current == null) {
            SimpleAccumulator<IN> accumulator = createAccumulatorForWindow();
            if (accumulator != null) {
                accumulator.add(value);
                setWindowContents(key, window, (ACC) accumulator);
            } else {
                try {
                    setWindowContents(key, window, (ACC) value);
                } catch (ClassCastException e) {
                    throw new StreamException(ERR_STREAM_TYPE_MISMATCH, e)
                            .param(ARG_EXPECTED_TYPE, accClass.getName())
                            .param(ARG_ACTUAL_TYPE, value.getClass().getName());
                }
            }
            return;
        }

        if (current instanceof SimpleAccumulator) {
            SimpleAccumulator<IN> accumulator = (SimpleAccumulator<IN>) current;
            accumulator.add(value);
            setWindowContents(key, window, (ACC) accumulator);
            return;
        }

        try {
            setWindowContents(key, window, (ACC) value);
        } catch (ClassCastException e) {
            throw new StreamException(ERR_STREAM_TYPE_MISMATCH, e)
                    .param(ARG_EXPECTED_TYPE, accClass.getName())
                    .param(ARG_ACTUAL_TYPE, value.getClass().getName());
        }
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

    @SuppressWarnings("unchecked")
    private ACC getWindowContents(K key, W window) {
        if (newAppendingWindowState != null) {
            IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
            typedBackend.setCurrentKey(key);
            newAppendingWindowState.setCurrentNamespace(window);
            try {
                ACC acc = newAppendingWindowState.getAccumulator();
                return acc;
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to get window contents from appending state");
            }
        }

        if (newListWindowState != null) {
            IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
            typedBackend.setCurrentKey(key);
            newListWindowState.setCurrentNamespace(window);
            try {
                Iterable<IN> elements = newListWindowState.get();
                return (ACC) elements;
            } catch (java.io.IOException e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to get window contents from list state");
            }
        }

        IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
        typedBackend.setCurrentKey(key);
        typedBackend.setCurrentNamespace(windowNamespace(window));
        return windowContentsState.get(WINDOW_VALUE_KEY);
    }

    private void clearWindowContents(K key, W window) {
        if (newAppendingWindowState != null) {
            IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
            typedBackend.setCurrentKey(key);
            newAppendingWindowState.setCurrentNamespace(window);
            newAppendingWindowState.clear();
            return;
        }

        if (newListWindowState != null) {
            IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
            typedBackend.setCurrentKey(key);
            newListWindowState.setCurrentNamespace(window);
            newListWindowState.clear();
            return;
        }

        IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
        typedBackend.setCurrentKey(key);
        typedBackend.setCurrentNamespace(windowNamespace(window));
        windowContentsState.remove(WINDOW_VALUE_KEY);
    }

    private void setWindowContents(K key, W window, ACC value) {
        IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
        typedBackend.setCurrentKey(key);

        if (newAppendingWindowState != null) {
            newAppendingWindowState.setCurrentNamespace(window);
            try {
                newAppendingWindowState.add((IN) value);
            } catch (java.io.IOException e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to set window contents in appending state");
            }
            return;
        }

        if (newListWindowState != null) {
            newListWindowState.setCurrentNamespace(window);
            try {
                newListWindowState.add((IN) value);
            } catch (java.io.IOException e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to set window contents in list state");
            }
            return;
        }

        typedBackend.setCurrentNamespace(windowNamespace(window));
        windowContentsState.put(WINDOW_VALUE_KEY, value);
    }

    @SuppressWarnings("unchecked")
    private void mergeWindowContents(K key, W targetWindow, Collection<W> sourceWindows) {
        if (sourceWindows == null || sourceWindows.isEmpty()) {
            return;
        }

        if (newAppendingWindowState != null && mergeFunction != null) {
            IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
            typedBackend.setCurrentKey(key);

            newAppendingWindowState.setCurrentNamespace(targetWindow);
            ACC targetValue;
            try {
                targetValue = newAppendingWindowState.getAccumulator();
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to get target accumulator during merge");
            }

            for (W sourceWindow : sourceWindows) {
                if (sourceWindow == null || sourceWindow.equals(targetWindow)) {
                    continue;
                }

                newAppendingWindowState.setCurrentNamespace(sourceWindow);
                ACC sourceValue;
                try {
                    sourceValue = newAppendingWindowState.getAccumulator();
                } catch (Exception e) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                            .param(ARG_DETAIL, "Failed to get source accumulator during merge");
                }

                if (sourceValue == null) {
                    continue;
                }

                if (targetValue == null) {
                    targetValue = sourceValue;
                } else {
                    targetValue = mergeFunction.apply(targetValue, sourceValue);
                }

                newAppendingWindowState.setCurrentNamespace(sourceWindow);
                newAppendingWindowState.clear();
            }

            if (targetValue != null) {
                newAppendingWindowState.setCurrentNamespace(targetWindow);
                try {
                    newAppendingWindowState.clear();
                    newAppendingWindowState.add((IN) targetValue);
                } catch (java.io.IOException e) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                            .param(ARG_DETAIL, "Failed to set merged accumulator");
                }
            }
            return;
        }

        if (newListWindowState != null) {
            IKeyedStateBackend<K> typedBackend = this.getKeyedStateBackend();
            typedBackend.setCurrentKey(key);

            newListWindowState.setCurrentNamespace(targetWindow);
            List<IN> targetElements = new ArrayList<>();
            try {
                Iterable<IN> existing = newListWindowState.get();
                if (existing != null) {
                    for (IN e : existing) {
                        targetElements.add(e);
                    }
                }
            } catch (java.io.IOException e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to get target list during merge");
            }

            for (W sourceWindow : sourceWindows) {
                if (sourceWindow == null || sourceWindow.equals(targetWindow)) {
                    continue;
                }

                newListWindowState.setCurrentNamespace(sourceWindow);
                try {
                    Iterable<IN> sourceElements = newListWindowState.get();
                    if (sourceElements != null) {
                        for (IN e : sourceElements) {
                            targetElements.add(e);
                        }
                    }
                } catch (java.io.IOException e) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                            .param(ARG_DETAIL, "Failed to get source list during merge");
                }

                newListWindowState.clear();
            }

            newListWindowState.setCurrentNamespace(targetWindow);
            try {
                newListWindowState.update(targetElements);
            } catch (java.io.IOException e) {
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_DETAIL, "Failed to update target list during merge");
            }
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
                    accumulator.merge((SimpleAccumulator<ACC>) sourceValue);
                } else {
                    throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                            "Cannot merge non-accumulator value into accumulator target. " +
                            "targetType=" + targetValue.getClass().getName() +
                            ", sourceType=" + sourceValue.getClass().getName());
                }
                targetValue = (ACC) accumulator;
            } else {
                throw new StreamException(ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT)
                        .param(ARG_DETAIL,
                                "Cannot merge multiple non-accumulator values. targetType="
                                + targetValue.getClass().getName());
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
        if (window instanceof TimeWindow) {
            TimeWindow tw = (TimeWindow) window;
            return "TW:" + tw.getStart() + "," + tw.getEnd();
        }
        return window.getClass().getName() + "#" + window.toString();
    }

    public class PerWindowKeyedStateStore implements KeyedStateStore {
        private final IKeyedStateBackend<K> backend;
        private final String namespace;

        PerWindowKeyedStateStore(IKeyedStateBackend<K> backend, String namespace) {
            this.backend = backend;
            this.namespace = namespace;
        }

        private void setNamespace() {
            backend.setCurrentNamespace(namespace);
        }

        @Override
        public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
            setNamespace();
            return new NamespaceAwareValueState<>(namespace, backend.getState(stateProperties), backend);
        }

        @Override
        public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
            setNamespace();
            return new NamespaceAwareListState<>(namespace, backend.getListState(stateProperties), backend);
        }

        @Override
        public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
            setNamespace();
            return new NamespaceAwareReducingState<>(namespace, backend.getReducingState(stateProperties), backend);
        }

        @Override
        public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
                AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
            setNamespace();
            return new NamespaceAwareAggregatingState<>(namespace, backend.getAggregatingState(stateProperties), backend);
        }

        @Override
        public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
            setNamespace();
            return new NamespaceAwareMapState<>(namespace, backend.getMapState(stateProperties), backend);
        }
    }

    public class GlobalKeyedStateStore implements KeyedStateStore {
        private final IKeyedStateBackend<K> backend;

        GlobalKeyedStateStore(IKeyedStateBackend<K> backend) {
            this.backend = backend;
        }

        private void setNamespace() {
            backend.setCurrentNamespace(IKeyedStateBackend.DEFAULT_NAMESPACE);
        }

        @Override
        public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
            setNamespace();
            return new NamespaceAwareValueState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getState(stateProperties), backend);
        }

        @Override
        public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
            setNamespace();
            return new NamespaceAwareListState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getListState(stateProperties), backend);
        }

        @Override
        public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
            setNamespace();
            return new NamespaceAwareReducingState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getReducingState(stateProperties), backend);
        }

        @Override
        public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
                AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
            setNamespace();
            return new NamespaceAwareAggregatingState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getAggregatingState(stateProperties), backend);
        }

        @Override
        public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
            setNamespace();
            return new NamespaceAwareMapState<>(IKeyedStateBackend.DEFAULT_NAMESPACE, backend.getMapState(stateProperties), backend);
        }
    }

    private static class NamespaceAwareValueState<T> implements ValueState<T> {
        private final String namespace;
        private final ValueState<T> delegate;
        private final IKeyedStateBackend<?> backend;

        NamespaceAwareValueState(String namespace, ValueState<T> delegate, IKeyedStateBackend<?> backend) {
            this.namespace = namespace;
            this.delegate = delegate;
            this.backend = backend;
        }

        @Override
        public T value() throws java.io.IOException {
            backend.setCurrentNamespace(namespace);
            return delegate.value();
        }

        @Override
        public void update(T value) throws java.io.IOException {
            backend.setCurrentNamespace(namespace);
            delegate.update(value);
        }

        @Override
        public void clear() {
            backend.setCurrentNamespace(namespace);
            delegate.clear();
        }
    }

    private static class NamespaceAwareListState<T> implements ListState<T> {
        private final String namespace;
        private final ListState<T> delegate;
        private final IKeyedStateBackend<?> backend;

        NamespaceAwareListState(String namespace, ListState<T> delegate, IKeyedStateBackend<?> backend) {
            this.namespace = namespace;
            this.delegate = delegate;
            this.backend = backend;
        }

        @Override
        public Iterable<T> get() throws java.io.IOException {
            backend.setCurrentNamespace(namespace);
            return delegate.get();
        }

        @Override
        public void add(T value) throws java.io.IOException {
            backend.setCurrentNamespace(namespace);
            delegate.add(value);
        }

        @Override
        public void update(Iterable<T> values) throws java.io.IOException {
            backend.setCurrentNamespace(namespace);
            delegate.update(values);
        }

        @Override
        public void addAll(Iterable<T> values) throws java.io.IOException {
            backend.setCurrentNamespace(namespace);
            delegate.addAll(values);
        }

        @Override
        public void clear() {
            backend.setCurrentNamespace(namespace);
            delegate.clear();
        }
    }

    private static class NamespaceAwareReducingState<T> implements ReducingState<T> {
        private final String namespace;
        private final ReducingState<T> delegate;
        private final IKeyedStateBackend<?> backend;

        NamespaceAwareReducingState(String namespace, ReducingState<T> delegate, IKeyedStateBackend<?> backend) {
            this.namespace = namespace;
            this.delegate = delegate;
            this.backend = backend;
        }

        @Override
        public T get() throws Exception {
            backend.setCurrentNamespace(namespace);
            return delegate.get();
        }

        @Override
        public void add(T value) throws Exception {
            backend.setCurrentNamespace(namespace);
            delegate.add(value);
        }

        @Override
        public void clear() {
            backend.setCurrentNamespace(namespace);
            delegate.clear();
        }
    }

    private static class NamespaceAwareAggregatingState<IN, OUT> implements AggregatingState<IN, OUT> {
        private final String namespace;
        private final AggregatingState<IN, OUT> delegate;
        private final IKeyedStateBackend<?> backend;

        NamespaceAwareAggregatingState(String namespace, AggregatingState<IN, OUT> delegate, IKeyedStateBackend<?> backend) {
            this.namespace = namespace;
            this.delegate = delegate;
            this.backend = backend;
        }

        @Override
        public OUT get() throws Exception {
            backend.setCurrentNamespace(namespace);
            return delegate.get();
        }

        @Override
        public void add(IN value) throws Exception {
            backend.setCurrentNamespace(namespace);
            delegate.add(value);
        }

        @Override
        public void clear() {
            backend.setCurrentNamespace(namespace);
            delegate.clear();
        }
    }

    private static class NamespaceAwareMapState<UK, UV> implements MapState<UK, UV> {
        private final String namespace;
        private final MapState<UK, UV> delegate;
        private final IKeyedStateBackend<?> backend;

        NamespaceAwareMapState(String namespace, MapState<UK, UV> delegate, IKeyedStateBackend<?> backend) {
            this.namespace = namespace;
            this.delegate = delegate;
            this.backend = backend;
        }

        @Override
        public UV get(UK key) {
            backend.setCurrentNamespace(namespace);
            return delegate.get(key);
        }

        @Override
        public void put(UK key, UV value) {
            backend.setCurrentNamespace(namespace);
            delegate.put(key, value);
        }

        @Override
        public void putAll(Map<UK, UV> map) {
            backend.setCurrentNamespace(namespace);
            delegate.putAll(map);
        }

        @Override
        public void remove(UK key) {
            backend.setCurrentNamespace(namespace);
            delegate.remove(key);
        }

        @Override
        public boolean contains(UK key) {
            backend.setCurrentNamespace(namespace);
            return delegate.contains(key);
        }

        @Override
        public Iterable<Map.Entry<UK, UV>> entries() {
            backend.setCurrentNamespace(namespace);
            return delegate.entries();
        }

        @Override
        public Iterable<UK> keys() {
            backend.setCurrentNamespace(namespace);
            return delegate.keys();
        }

        @Override
        public Iterable<UV> values() {
            backend.setCurrentNamespace(namespace);
            return delegate.values();
        }

        @Override
        public java.util.Iterator<Map.Entry<UK, UV>> iterator() {
            backend.setCurrentNamespace(namespace);
            return delegate.iterator();
        }

        @Override
        public boolean isEmpty() {
            backend.setCurrentNamespace(namespace);
            return delegate.isEmpty();
        }

        @Override
        public void clear() {
            backend.setCurrentNamespace(namespace);
            delegate.clear();
        }
    }

    /**
     * A utility class for handling {@code ProcessWindowFunction} invocations. This can be reused by
     * setting the {@code key} and {@code window} fields. No internal state must be kept in the
     * {@code WindowContext}.
     */
    public class WindowContext implements InternalWindowFunction.InternalWindowContext {
        protected W window;

        public WindowContext(W window) {
            this.window = window;
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
            return new PerWindowKeyedStateStore(backend, windowNamespace(window));
        }

        @Override
        public KeyedStateStore globalState() {
            IKeyedStateBackend<K> backend = WindowOperator.this.getKeyedStateBackend();
            if (backend == null) {
                return null;
            }
            return new GlobalKeyedStateStore(backend);
        }

        @Override
        public <X> void output(OutputTag<X> outputTag, X value) {
            if (outputTag == null) {
                throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "outputTag");
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
            String stateKey = "trigger_" + key + STATE_KEY_SEPARATOR + window + STATE_KEY_SEPARATOR + descriptor.getName();

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
                    throw new StreamException(ERR_STREAM_WINDOW_TRIGGER_STATE_ACCUMULATOR_FAILED, e)
                            .param(ARG_DESCRIPTOR_NAME, "trigger-state");
                }
            }
            throw new StreamException(ERR_STREAM_UNSUPPORTED)
                    .param(ARG_OPERATION, "getSimpleAccumulator for descriptor: " + descriptor.getName());
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
