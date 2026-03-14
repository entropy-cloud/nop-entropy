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

/**
 * Internal interface for list state that supports namespace-based partitioning.
 * <p>
 * This is a simplified version of Flink's InternalListState, designed for
 * the nop-stream single-key context.
 *
 * @param <K> The type of the key
 * @param <N> The type of the namespace
 * @param <T> The type of elements in the list
 */
public interface InternalListState<K, N, T> extends ListState<T> {

    /**
     * Sets the current namespace for state operations.
     *
     * @param namespace The namespace to use for subsequent state operations
     */
    void setCurrentNamespace(N namespace);

    /**
     * Returns the current namespace.
     *
     * @return The current namespace
     */
    N getCurrentNamespace();
}
