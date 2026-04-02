/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing.cep;

import com.google.common.annotations.VisibleForTesting;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.tuple.Tuple2;
import io.nop.stream.cep.EventComparator;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.time.TimerService;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.VoidNamespace;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.TimestampedCollector;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.windows.Window;
import io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * An operator that combines CEP pattern matching with window semantics.
 * 
 * <p>This operator processes events within window boundaries and detecting CEP patterns
 * only within each window. When a window fires, it:</p>
 * <ul>
 *   <li>Collects all events in the window</li>
 *   <li>Applies CEP pattern matching to the collected events</li>
 *   <li>Emits pattern matches as results</li>
 * </ul>
 * 
 * <p>This enables use cases like:</p>
 * <ul>
 *   <li>Detect fraud patterns within sliding/tumbling windows</li>
 *   <li>Find event sequences within session boundaries</li>
 *   <li>Apply time-constrained pattern matching with window aggregation</li>
 * </ul>
 *
 * @param <IN>  Type of the input elements
 * @param <KEY> Type of the key on which the input stream is keyed
 * @param <OUT> Type of the output elements
 * @param <W>   Type of {@link Window} that the window assigner assigns
 */
public class CepWindowOperator<IN, KEY, OUT, W extends Window>
        extends AbstractUdfStreamOperator<OUT, PatternProcessFunction<IN, OUT>>
        implements OneInputStreamOperator<IN, OUT> {

    private static final long serialVersionUID = -4166778210774160758L;

    private static final String NFA_STATE_NAME = "nfaStateName";
    private static final String EVENT_QUEUE_STATE_NAME = "eventQueuesStateName";

    private final boolean isProcessingTime;
    private final TypeSerializer<IN> inputSerializer;
    private final NFACompiler.NFAFactory<IN> nfaFactory;
    private final long windowTimeMs;

    private transient ValueState<NFAState> computationStates;
    private transient MapState<Long, List<IN>> elementQueueState;
    private transient SharedBuffer<IN> partialMatches;
    private transient InternalTimerService<VoidNamespace> timerService;
    private transient NFA<IN> nfa;
    private final EventComparator<IN> comparator;
    private final OutputTag<IN> lateDataOutputTag;
    private final AfterMatchSkipStrategy afterMatchSkipStrategy;

    private transient ContextFunctionImpl context;
    private transient TimestampedCollector<OUT> collector;
    private transient TimerService cepTimerService;

    public CepWindowOperator(
            final TypeSerializer<IN> inputSerializer,
            final boolean isProcessingTime,
            final NFACompiler.NFAFactory<IN> nfaFactory,
            final long windowTimeMs,
            @Nullable final EventComparator<IN> comparator,
            @Nullable final AfterMatchSkipStrategy afterMatchSkipStrategy,
            final PatternProcessFunction<IN, OUT> function,
            @Nullable final OutputTag<IN> lateDataOutputTag) {
        super(function);

        this.inputSerializer = com.google.common.base.Preconditions.checkNotNull(inputSerializer);
        this.nfaFactory = com.google.common.base.Preconditions.checkNotNull(nfaFactory);
        this.isProcessingTime = isProcessingTime;
        this.windowTimeMs = windowTimeMs;
        this.comparator = comparator;
        this.lateDataOutputTag = lateDataOutputTag;
        this.afterMatchSkipStrategy = afterMatchSkipStrategy != null 
                ? afterMatchSkipStrategy 
                : AfterMatchSkipStrategy.noSkip();
    }

    @Override
    public void open() throws Exception {
        super.open();

        // Initialize keyed state stores
        SimpleKeyedStateStore stateStore = new SimpleKeyedStateStore();
        computationStates = stateStore.getState(new ValueStateDescriptor<>(NFA_STATE_NAME, NFAState.class));
        elementQueueState = stateStore.getMapState(
                new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
        partialMatches = new SharedBuffer<>(stateStore, inputSerializer, new SharedBufferCacheConfig());

        // Initialize a minimal InternalTimerService backed by the ProcessingTimeService.
        final io.nop.stream.core.operators.ProcessingTimeService pts = getProcessingTimeService();
        timerService = new InternalTimerService<VoidNamespace>() {
            @Override
            public long currentProcessingTime() {
                return pts.getCurrentProcessingTime();
            }

            @Override
            public long currentWatermark() {
                return Long.MIN_VALUE;
            }

            @Override
            public void registerProcessingTimeTimer(VoidNamespace namespace, long time) {
                pts.registerTimer(time, t -> onProcessingTime(t));
            }

            @Override
            public void deleteProcessingTimeTimer(VoidNamespace namespace, long time) {
                // not supported in simple implementation
            }

            @Override
            public void registerEventTimeTimer(VoidNamespace namespace, long time) {
                // event-time timers are driven by watermarks at the task level
            }

            @Override
            public void deleteEventTimeTimer(VoidNamespace namespace, long time) {
                // not supported in simple implementation
            }

            @Override
            public void forEachEventTimeTimer(BiConsumer<VoidNamespace, Long> consumer) {
                // no-op
            }

            @Override
            public void forEachProcessingTimeTimer(BiConsumer<VoidNamespace, Long> consumer) {
                // no-op
            }
        };

        nfa = nfaFactory.createNFA();
        context = new ContextFunctionImpl();
        collector = new TimestampedCollector<>(output);
        cepTimerService = new TimerServiceImpl();
    }

    @Override
    public void close() throws Exception {
        if (nfa != null) {
            nfa.close();
        }
        if (partialMatches != null) {
            partialMatches.releaseCacheStatisticsTimer();
        }
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
        PriorityQueue<Long> sortedTimestamps = getSortedTimestamps();
        NFAState nfaState = getNFAState();

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

        advanceTime(nfaState, timerService.currentWatermark());
        updateNFA(nfaState);

        if (nfaState.getPartialMatches().size() == 1 && nfaState.getCompletedMatches().isEmpty()) {
            computationStates.clear();
        }
    }

    public void onProcessingTime(long time) throws Exception {
        PriorityQueue<Long> sortedTimestamps = getSortedTimestamps();
        NFAState nfa = getNFAState();

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

        advanceTime(nfa, timerService.currentProcessingTime());
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
        if (function instanceof io.nop.stream.cep.functions.TimedOutPartialMatchHandler) {
            @SuppressWarnings("unchecked")
            io.nop.stream.cep.functions.TimedOutPartialMatchHandler<IN> timeoutHandler =
                    (io.nop.stream.cep.functions.TimedOutPartialMatchHandler<IN>) function;

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
     * Gets the window time in milliseconds.
     *
     * @return The window time in milliseconds
     */
    public long getWindowTimeMs() {
        return windowTimeMs;
    }

    /**
     * Gets the NFA used by this operator.
     *
     * @return The NFA
     */
    @VisibleForTesting
    public NFA<IN> getNFA() {
        return nfa;
    }

    private class TimerServiceImpl implements TimerService {

        @Override
        public long currentProcessingTime() {
            return timerService.currentProcessingTime();
        }
    }

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
}
