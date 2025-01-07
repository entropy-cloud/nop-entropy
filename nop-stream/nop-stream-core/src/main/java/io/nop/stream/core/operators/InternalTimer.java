/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.core.operators;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.state.KeyExtractorFunction;
import io.nop.stream.core.state.Keyed;
import io.nop.stream.core.state.PriorityComparable;
import io.nop.stream.core.state.PriorityComparator;
import jakarta.annotation.Nonnull;

/**
 * Internal interface for in-flight timers.
 *
 * @param <K> Type of the keys to which timers are scoped.
 * @param <N> Type of the namespace to which timers are scoped.
 */
@Internal
public interface InternalTimer<K, N> extends PriorityComparable<InternalTimer<?, ?>>, Keyed<K> {

    /** Function to extract the key from a {@link InternalTimer}. */
    KeyExtractorFunction<InternalTimer<?, ?>> KEY_EXTRACTOR_FUNCTION = InternalTimer::getKey;

    /** Function to compare instances of {@link InternalTimer}. */
    PriorityComparator<InternalTimer<?, ?>> TIMER_COMPARATOR =
            (left, right) -> Long.compare(left.getTimestamp(), right.getTimestamp());
    /**
     * Returns the timestamp of the timer. This value determines the point in time when the timer
     * will fire.
     */
    long getTimestamp();

    /** Returns the key that is bound to this timer. */
    @Nonnull
    @Override
    K getKey();

    /** Returns the namespace that is bound to this timer. */
    @Nonnull
    N getNamespace();
}
