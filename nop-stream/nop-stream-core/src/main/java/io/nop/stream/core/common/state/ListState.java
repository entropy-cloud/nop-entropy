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
import java.util.Iterator;

/**
 * {@link State} interface for partitioned list state. Elements can be added to the list.
 *
 * @param <T> Type of the elements in the list state.
 */
public interface ListState<T> extends State {

    /**
     * Returns the current elements in the state as an Iterable.
     *
     * @return An iterable view of the state elements.
     * @throws IOException Thrown if the system cannot access the state.
     */
    Iterable<T> get() throws IOException;

    /**
     * Adds the given element to the state.
     *
     * @param value The element to add to the state.
     * @throws IOException Thrown if the system cannot access the state.
     */
    void add(T value) throws IOException;

    /**
     * Adds all elements from the given iterable to the state.
     *
     * @param values The elements to add to the state.
     * @throws IOException Thrown if the system cannot access the state.
     */
    void addAll(Iterable<T> values) throws IOException;

    /**
     * Updates the operator state accessible by {@link #get()} to the given list.
     * The next time {@link #get()} is called (for the same state partition)
     * the returned state will represent the updated list.
     *
     * @param values The new values for the state.
     * @throws IOException Thrown if the system cannot access the state.
     */
    void update(Iterable<T> values) throws IOException;
}
