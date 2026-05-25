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
import java.util.function.BiConsumer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Stream;
import java.util.TreeSet;

import jakarta.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.tuple.Tuple2;

import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.EventComparator;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.functions.TimedOutPartialMatchHandler;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.time.TimerService;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.VoidNamespace;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.TimestampedCollector;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.OutputTag;

/**
 * CEP pattern operator for a keyed input stream. For each key, the operator creates a {@link NFA}
 * and a priority queue to buffer out of order elements. Both data structures are stored using the
 * managed keyed state.
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

    private static final String LATE_ELEMENTS_DROPPED_METRIC_NAME = "numLateRecordsDropped";

    private final boolean isProcessingTime;

    private final TypeSerializer<IN> inputSerializer;

    ///////////////			State			//////////////

    private static final String NFA_STATE_NAME = "nfaStateName";
    private static final String EVENT_QUEUE_STATE_NAME = "eventQueuesStateName";

    private final NFACompiler.NFAFactory<IN> nfaFactory;

    private transient ValueState<NFAState> computationStates;
    private transient MapState<Long, List<IN>> elementQueueState;
    private transient SharedBuffer<IN> partialMatches;

    private transient SimpleKeyedStateStore stateStore;

    private transient InternalTimerService<VoidNamespace> timerService;

    private transient NFA<IN> nfa;

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

    private transient Counter numLateRecordsDropped;

    private transient long currentWatermark = Long.MIN_VALUE;

    public CepOperator(
            final TypeSerializer<IN> inputSerializer,
            final boolean isProcessingTime,
            final NFACompiler.NFAFactory<IN> nfaFactory,
            @Nullable final EventComparator<IN> comparator,
            @Nullable final AfterMatchSkipStrategy afterMatchSkipStrategy,
            final PatternProcessFunction<IN, OUT> function,
            @Nullable final OutputTag<IN> lateDataOutputTag) {
        super(function);

        this.inputSerializer = Preconditions.checkNotNull(inputSerializer);
        this.nfaFactory = Preconditions.checkNotNull(nfaFactory);

        this.isProcessingTime = isProcessingTime;
        this.comparator = comparator;
        this.lateDataOutputTag = lateDataOutputTag;

        if (afterMatchSkipStrategy == null) {
            this.afterMatchSkipStrategy = AfterMatchSkipStrategy.noSkip();
        } else {
            this.afterMatchSkipStrategy = afterMatchSkipStrategy;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void open() throws Exception {
        super.open();

        stateStore = new SimpleKeyedStateStore();
        computationStates = stateStore.getState(new ValueStateDescriptor<>(NFA_STATE_NAME, NFAState.class));
        // MapStateDescriptor does not support generic type tokens for value class;
        // (Class) List.class is used as a raw class hint, actual generic safety is ensured by usage
        elementQueueState = stateStore.getMapState(
                new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
        partialMatches = new SharedBuffer<>(stateStore, inputSerializer, new SharedBufferCacheConfig());

        final Set<Long> registeredEventTimeTimers = new TreeSet<>();

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
                getProcessingTimeService().registerTimer(time, t -> {
                    try {
                        onProcessingTime(t);
                    } catch (Exception e) {
                        throw NopException.adapt(e);
                    }
                });
            }

            @Override
            public void deleteProcessingTimeTimer(VoidNamespace namespace, long time) {
            }

            @Override
            public void registerEventTimeTimer(VoidNamespace namespace, long time) {
                registeredEventTimeTimers.add(time);
            }

            @Override
            public void deleteEventTimeTimer(VoidNamespace namespace, long time) {
                registeredEventTimeTimers.remove(time);
            }

            @Override
            public void forEachEventTimeTimer(BiConsumer<VoidNamespace, Long> consumer) {
                for (Long time : new TreeSet<>(registeredEventTimeTimers)) {
                    consumer.accept(VoidNamespace.INSTANCE, time);
                }
            }

            @Override
            public void forEachProcessingTimeTimer(BiConsumer<VoidNamespace, Long> consumer) {
            }
        };

        nfa = nfaFactory.createNFA();

        cepRuntimeContext = new CepRuntimeContext(new io.nop.stream.core.common.functions.RuntimeContext() {}, stateStore);
        nfa.open(cepRuntimeContext, null);

        context = new ContextFunctionImpl();
        collector = new TimestampedCollector<>(output);
        cepTimerService = new TimerServiceImpl();

        this.numLateRecordsDropped = Metrics.counter(LATE_ELEMENTS_DROPPED_METRIC_NAME);
    }

    @Override
    public void close() throws Exception {
        super.close();
        if (nfa != null) {
            nfa.close();
        }
        if (partialMatches != null) {
            partialMatches.releaseCacheStatisticsTimer();
        }
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
        long newWatermark = mark.getTimestamp();
        if (newWatermark > currentWatermark) {
            currentWatermark = newWatermark;
            if (!isProcessingTime) {
                onEventTime(currentWatermark);
            }
        }
        super.processWatermark(mark);
    }

    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        if (isProcessingTime) {
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

    private void bufferEvent(IN event, long currentTime) throws Exception {
        List<IN> elementsForTimestamp = elementQueueState.get(currentTime);
        if (elementsForTimestamp == null) {
            elementsForTimestamp = new ArrayList<>();
            registerTimer(currentTime);
        }

        elementsForTimestamp.add(event);
        elementQueueState.put(currentTime, elementsForTimestamp);
    }

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
                                throw NopException.adapt(e);
                            }
                        });
            }
            elementQueueState.remove(timestamp);
        }

        // STEP 3
        advanceTime(nfaState, timerService.currentWatermark());

        // STEP 4
        updateNFA(nfaState);

        // In order to remove dangling partial matches.
        if (nfaState.getPartialMatches().size() == 1 && nfaState.getCompletedMatches().isEmpty()) {
            computationStates.clear();
        }
    }

    public void onProcessingTime(long time) throws Exception {
        // STEP 1
        PriorityQueue<Long> sortedTimestamps = getSortedTimestamps();
        NFAState nfa = getNFAState();

        // STEP 2
        while (!sortedTimestamps.isEmpty()) {
            long timestamp = sortedTimestamps.poll();
            advanceTime(nfa, timestamp);
            try (Stream<IN> elements = sort(elementQueueState.get(timestamp))) {
                elements.forEachOrdered(
                        event -> {
                            try {
                                processEvent(nfa, event, timestamp);
                            } catch (Exception e) {
                                throw NopException.adapt(e);
                            }
                        });
            }
            elementQueueState.remove(timestamp);
        }

        // STEP 3
        advanceTime(nfa, timerService.currentProcessingTime());

        // STEP 4
        updateNFA(nfa);
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

    @VisibleForTesting
    boolean hasNonEmptySharedBuffer(KEY key) throws Exception {
        setCurrentKey(key);
        return !partialMatches.isEmpty();
    }

    @VisibleForTesting
    boolean hasNonEmptyPQ(KEY key) throws Exception {
        setCurrentKey(key);
        return !elementQueueState.isEmpty();
    }

    @VisibleForTesting
    int getPQSize(KEY key) throws Exception {
        setCurrentKey(key);
        int counter = 0;
        for (List<IN> elements : elementQueueState.values()) {
            counter += elements.size();
        }
        return counter;
    }

    @VisibleForTesting
    public NFAState getNFAStateForTesting() throws IOException {
        return getNFAState();
    }

    @VisibleForTesting
    public void updateNFAStateForTesting(NFAState state) throws IOException {
        computationStates.update(state);
    }

    @VisibleForTesting
    public SimpleKeyedStateStore getStateStore() {
        return stateStore;
    }

    @VisibleForTesting
    public CepRuntimeContext getCepRuntimeContext() {
        return cepRuntimeContext;
    }

    @VisibleForTesting
    public SharedBuffer<IN> getPartialMatches() {
        return partialMatches;
    }
}
