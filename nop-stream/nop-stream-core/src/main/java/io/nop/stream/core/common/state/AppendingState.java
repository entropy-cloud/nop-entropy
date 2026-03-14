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

package io.nop.stream.core.common.state;

import java.io.IOException;

/**
 * Base interface for partitioned state that supports adding elements and retrieving an accumulated
 * result.
 *
 * @param <IN>  Type of the values that are added to the state.
 * @param <OUT> Type of the value that is returned when calling {@link #get()}.
 */
public interface AppendingState<IN, OUT> extends State {

    /**
     * Returns the current value for the state. When the state is not partitioned the returned value
     * is the same for all inputs in a given operator instance. If state partitioning is applied,
     * the value returned depends on the current operator input, as the operator maintains an
     * independent state for each partition.
     *
     * @return The operator state value corresponding to the current input.
     * @throws IOException Thrown if the system cannot access the state.
     */
    OUT get() throws IOException;

    /**
     * Updates the operator state accessible by {@link #get()} by adding the given value.
     *
     * @param value The new value for the state.
     * @throws IOException Thrown if the system cannot access the state.
     */
    void add(IN value) throws IOException;
}
