/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.core.windowing.triggers;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import java.time.Duration;

import io.nop.stream.core.common.accumulators.LongMinimum;
import io.nop.stream.core.windowing.windows.Window;

/**
 * item 21 D-4 convergence: shared base of the two continuous-fire interval
 * triggers. Holds the interval, the shared fire-time state descriptor, the
 * S-8a fail-fast interval validation and the next-fire-timestamp computation.
 * The event-time and processing-time subclasses keep their timer-kind
 * differences (registration/deletion surface, watermark precondition) — those
 * are real semantic differences, not clones.
 */
abstract class ContinuousIntervalTrigger<W extends Window> extends Trigger<Object, W> {

    private static final long serialVersionUID = 1L;

    protected final long interval;

    /**
     * When merging we take the lowest of all fire timestamps as the new fire timestamp.
     */
    protected final io.nop.stream.core.common.state.ReducingStateDescriptor<Long> stateDesc =
            new io.nop.stream.core.common.state.ReducingStateDescriptor<>("fire-time", Long.class, LongMinimum.class);

    protected ContinuousIntervalTrigger(long interval) {
        this.interval = interval;
    }

    /**
     * S-8a (2026-09-01 core audit): fail fast on non-positive intervals — a zero
     * interval would later surface as a bare ArithmeticException (modulo by
     * zero) and a negative one schedules timers in the past.
     */
    protected static long validatedIntervalMillis(Duration interval, String triggerName) {
        if (interval == null || interval.toMillis() <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, triggerName + " interval must be a positive duration in milliseconds, but was: " + interval);
        }
        return interval.toMillis();
    }

    /** Next fire timestamp: capped at the window end so late windows fire once. */
    protected long nextFireTimestamp(long time, W window) {
        return Math.min(time + interval, window.maxTimestamp());
    }

    public long getInterval() {
        return interval;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + interval + ")";
    }
}
