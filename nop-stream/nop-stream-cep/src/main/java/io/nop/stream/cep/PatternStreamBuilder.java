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

package io.nop.stream.cep;


import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.functions.TimedOutPartialMatchHandler;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.operator.CepOperator;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.impl.NullByteKeySelector;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.util.OutputTag;

import jakarta.annotation.Nullable;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Utility method for creating {@link PatternStream}.
 */
final class PatternStreamBuilder<IN> {

    private final DataStream<IN> inputStream;

    private final Pattern<IN, ?> pattern;

    private final EventComparator<IN> comparator;

    /**
     * Side output {@code OutputTag} for late data. If no tag is set late data will be simply
     * dropped.
     */
    private final OutputTag<IN> lateDataOutputTag;

    /**
     * The time behaviour to specify processing time or event time. Default time behaviour is {@link
     * TimeBehaviour#EventTime}.
     */
    private final TimeBehaviour timeBehaviour;

    /**
     * The time behaviour enum defines how the system determines time for time-dependent order and
     * operations that depend on time.
     */
    enum TimeBehaviour {
        ProcessingTime,
        EventTime
    }

    private PatternStreamBuilder(
            final DataStream<IN> inputStream,
            final Pattern<IN, ?> pattern,
            final TimeBehaviour timeBehaviour,
            @Nullable final EventComparator<IN> comparator,
            @Nullable final OutputTag<IN> lateDataOutputTag) {
        this.inputStream = checkNotNull(inputStream);
        this.pattern = checkNotNull(pattern);
        this.timeBehaviour = checkNotNull(timeBehaviour);
        this.comparator = comparator;
        this.lateDataOutputTag = lateDataOutputTag;
    }

    /**
     * Invokes the {@link org.apache.flink.api.java.ClosureCleaner} on the given function if closure
     * cleaning is enabled in the {@link ExecutionConfig}.
     *
     * @return The cleaned Function
     */
    <F> F clean(F f) {
        return f;
    }


    TypeInformation<IN> getInputType() {
        return inputStream.getType();
    }

    PatternStreamBuilder<IN> withComparator(final EventComparator<IN> comparator) {
        return new PatternStreamBuilder<>(
                inputStream, pattern, timeBehaviour, checkNotNull(comparator), lateDataOutputTag);
    }

    PatternStreamBuilder<IN> withLateDataOutputTag(final OutputTag<IN> lateDataOutputTag) {
        return new PatternStreamBuilder<>(
                inputStream, pattern, timeBehaviour, comparator, checkNotNull(lateDataOutputTag));
    }

    PatternStreamBuilder<IN> inProcessingTime() {
        return new PatternStreamBuilder<>(
                inputStream, pattern, TimeBehaviour.ProcessingTime, comparator, lateDataOutputTag);
    }

    PatternStreamBuilder<IN> inEventTime() {
        return new PatternStreamBuilder<>(
                inputStream, pattern, TimeBehaviour.EventTime, comparator, lateDataOutputTag);
    }

    /**
     * Creates a data stream containing results of {@link PatternProcessFunction} to fully matching
     * event patterns.
     *
     * @param processFunction function to be applied to matching event sequences
     * @param outTypeInfo     output TypeInformation of {@link PatternProcessFunction#processMatch(Map,
     *                        PatternProcessFunction.Context, Collector)}
     * @param <OUT>           type of output events
     * @return Data stream containing fully matched event sequence with applied {@link
     * PatternProcessFunction}
     */
    <OUT, K> SingleOutputStreamOperator<OUT> build(
            final TypeInformation<OUT> outTypeInfo,
            final PatternProcessFunction<IN, OUT> processFunction) {

        checkNotNull(outTypeInfo);
        checkNotNull(processFunction);

        final TypeSerializer<IN> inputSerializer = null;
//                inputStream.getType().createSerializer(inputStream.getExecutionConfig());
        final boolean isProcessingTime = timeBehaviour == TimeBehaviour.ProcessingTime;

        final boolean timeoutHandling = processFunction instanceof TimedOutPartialMatchHandler;
        final NFACompiler.NFAFactory<IN> nfaFactory =
                NFACompiler.compileFactory(pattern, timeoutHandling);

        final CepOperator<IN, K, OUT> operator =
                new CepOperator<>(
                        inputSerializer,
                        isProcessingTime,
                        nfaFactory,
                        comparator,
                        pattern.getAfterMatchSkipStrategy(),
                        processFunction,
                        lateDataOutputTag);

        final SingleOutputStreamOperator<OUT> patternStream;
        if (inputStream instanceof KeyedStream) {
            KeyedStream<IN, K> keyedStream = (KeyedStream<IN, K>) inputStream;

            patternStream = keyedStream.transform("CepOperator", outTypeInfo, operator);
        } else {
            KeySelector<IN, Byte> keySelector = new NullByteKeySelector<>();

            patternStream =
                    inputStream
                            .keyBy(keySelector)
                            .transform("GlobalCepOperator", outTypeInfo, operator)
                            .forceNonParallel();
        }

        return patternStream;
    }

    // ---------------------------------------- factory-like methods
    // ---------------------------------------- //

    static <IN> PatternStreamBuilder<IN> forStreamAndPattern(
            final DataStream<IN> inputStream, final Pattern<IN, ?> pattern) {
        return new PatternStreamBuilder<>(
                inputStream, pattern, TimeBehaviour.EventTime, null, null);
    }
}
