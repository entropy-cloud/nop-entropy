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

package io.nop.stream.core.common.functions;

import java.io.Serializable;

/**
 * The {@code AggregateFunction} is a flexible aggregation function, characterized by the following
 * features:
 *
 * <ul>
 *   <li>The aggregates may use different types for input values, aggregate aggregates, and result
 *       type, to support a wide range of aggregation use cases.
 *   <li>Lazily combining aggregates: The aggregate function may lazily aggregate elements into the
 *       accumulator, which is very useful when the accumulator is a complex object.
 * </ul>
 *
 * <p>Compared to the {@code ReduceFunction}, the {@code AggregateFunction} is more flexible. It can
 * have different types for input, accumulator and result, and the accumulator can be a complex
 * object.
 *
 * <p>For example, it is not straightforward to use {@code ReduceFunction} to implement the average
 * of a data stream, because you need to keep the count and the sum of the elements as the
 * intermediate state. However, it is easy to implement the average using the {@code
 * AggregateFunction} as follows:
 *
 * <pre>{@code
 * DataStream<Tuple2<String, Long>> input = ...;
 *
 * DataStream<Tuple2<String, Double>> avgResult = input
 *     .keyBy(t -> t.f0)
 *     .window(TumblingEventTimeWindows.of(Time.seconds(5)))
 *     .aggregate(new AverageAggregate());
 *
 * public class AverageAggregate implements AggregateFunction<Tuple2<String, Long>, Tuple2<Long, Long>, Tuple2<String, Double>> {
 *     @Override
 *     public Tuple2<Long, Long> createAccumulator() {
 *         return Tuple2.of(0L, 0L);
 *     }
 *
 *     @Override
 *     public Tuple2<Long, Long> add(Tuple2<String, Long> value, Tuple2<Long, Long> accumulator) {
 *         return Tuple2.of(accumulator.f0 + value.f1, accumulator.f1 + 1L);
 *     }
 *
 *     @Override
 *     public Tuple2<String, Double> getResult(Tuple2<Long, Long> accumulator) {
 *         return Tuple2.of(key, ((double) accumulator.f0) / accumulator.f1);
 *     }
 *
 *     @Override
 *     public Tuple2<Long, Long> merge(Tuple2<Long, Long> a, Tuple2<Long, Long> b) {
 *         return Tuple2.of(a.f0 + b.f0, a.f1 + b.f1);
 *     }
 * }
 * }</pre>
 *
 * @param <IN> The type of the values that are aggregated (input values)
 * @param <ACC> The type of the accumulator (intermediate aggregate state)
 * @param <OUT> The type of the aggregated result
 */
public interface AggregateFunction<IN, ACC, OUT> extends Serializable {

    /**
     * Creates a new accumulator, starting a new aggregate.
     *
     * <p>The new accumulator is typically the identity element of the aggregate function, or if
     * not, it should be an empty accumulator that can be combined with the others.
     *
     * @return A new accumulator, corresponding to an empty aggregate.
     */
    ACC createAccumulator();

    /**
     * Adds the given input value to the given accumulator, returning the new accumulator value.
     *
     * <p>For efficiency, the input accumulator may be modified and returned.
     *
     * @param value The value to add
     * @param accumulator The accumulator to add the value to
     * @return The new accumulator value
     */
    ACC add(IN value, ACC accumulator);

    /**
     * Gets the result of the aggregation from the accumulator.
     *
     * @param accumulator The accumulator of the aggregation
     * @return The final aggregation result.
     */
    OUT getResult(ACC accumulator);

    /**
     * Merges two accumulators, returning an accumulator with the merged state.
     *
     * <p>This function may reuse any of the given accumulators as the target for the merge. Any of
     * the given accumulators may be returned as the result.
     *
     * @param a An accumulator to merge
     * @param b Another accumulator to merge
     * @return The accumulator with the merged state
     */
    ACC merge(ACC a, ACC b);
}
