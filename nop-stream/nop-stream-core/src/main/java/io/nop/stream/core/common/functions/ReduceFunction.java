/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
 * A ReduceFunction that reduces a group of values to a single value.
 * The reduce function takes two values and returns a new value.
 *
 * @param <T> The type of the values that are reduced.
 */
public interface ReduceFunction<T> extends Serializable {

    /**
     * Combines two values into one value. The reduce function is consecutively applied to pairs
     * of values until only a single value remains.
     *
     * @param value1 The first value to combine.
     * @param value2 The second value to combine.
     * @return The combined value of the two input values.
     * @throws Exception The function may throw exceptions to fail the program and trigger recovery.
     */
    T reduce(T value1, T value2) throws Exception;
}