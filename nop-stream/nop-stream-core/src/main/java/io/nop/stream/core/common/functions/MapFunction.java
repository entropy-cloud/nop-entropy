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


/**
 * A map function that maps one value to another.
 *
 * <p>The basic syntax for using a MapFunction is as follows:
 *
 * <pre>{@code
 * DataSet<X> input = ...;
 *
 * DataSet<Y> result = input.map(new MyMapFunction());
 * }</pre>
 *
 * <p><strong>IMPORTANT:</strong> The system assumes that the function does not modify the elements
 * on which it is applied. Violating this assumption can lead to incorrect results.
 *
 * @param <T> Type of the input elements.
 * @param <R> Type of the returned elements.
 */
@FunctionalInterface
public interface MapFunction<T, R> extends StreamFunction, java.io.Serializable {

    /**
     * The mapping method that takes an input and transforms it into an output.
     *
     * <p><strong>IMPORTANT:</strong> The system assumes that the function does not modify the
     * elements on which it is applied. Violating this assumption can lead to incorrect
     * results.
     *
     * @param value The input value to be mapped.
     * @return The mapped output value.
     * @throws Exception This method may throw exceptions. Throwing an exception will cause the
     *                   operation to fail and may trigger recovery.
     */
    R map(T value) throws Exception;
}
