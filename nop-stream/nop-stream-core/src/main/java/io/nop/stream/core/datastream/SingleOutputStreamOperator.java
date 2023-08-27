/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
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

package io.nop.stream.core.datastream;

/**
 * {@code SingleOutputStreamOperator} represents a user defined transformation applied on a {@link
 * DataStream} with one predefined output type.
 *
 * @param <T> The type of the elements in this stream.
 */
public interface SingleOutputStreamOperator<T> extends DataStream<T> {
    /**
     * Sets the parallelism and maximum parallelism of this operator to one. And mark this operator
     * cannot set a non-1 degree of parallelism.
     *
     * @return The operator with only one parallelism.
     */
    SingleOutputStreamOperator<T> forceNonParallel();
}
