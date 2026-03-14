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

import io.nop.stream.core.util.Collector;

/**
 * A flat map function that takes an element and outputs zero, one, or more elements.
 *
 * <p>The basic syntax for using a FlatMapFunction is as follows:
 *
 * <pre>{@code
 * DataSet<X> input = ...;
 *
 * DataSet<Y> result = input.flatMap(new MyFlatMapFunction());
 * }</pre>
 *
 * <p><strong>IMPORTANT:</strong> The system assumes that the function does not modify the elements
 * on which it is applied. Violating this assumption can lead to incorrect results.
 *
 * @param <T> Type of the input elements.
 * @param <R> Type of the returned elements.
 */
@FunctionalInterface
public interface FlatMapFunction<T, R> extends StreamFunction, java.io.Serializable {

    /**
     * The flat mapping method that takes one value and outputs zero, one, or more values through
     * the Collector.
     *
     * <p><strong>IMPORTANT:</strong> The system assumes that the function does not modify the
     * elements on which it is applied. Violating this assumption can lead to incorrect
     * results.
     *
     * @param value The input value to be flat mapped.
     * @param out The collector for returning result values.
     * @throws Exception This method may throw exceptions. Throwing an exception will cause the
     *                   operation to fail and may trigger recovery.
     */
    void flatMap(T value, Collector<R> out) throws Exception;
}
