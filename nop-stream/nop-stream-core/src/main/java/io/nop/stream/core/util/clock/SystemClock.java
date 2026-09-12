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

package io.nop.stream.core.util.clock;

import io.nop.api.core.time.CoreMetrics;


/**
 * A clock that returns the time of the system / process.
 *
 * <p>Absolute time is bridged to {@link CoreMetrics#currentTimeMillis()} (platform IClock timeline,
 * TestClock injectable in autotest); relative time delegates to {@link CoreMetrics#nanoTime()},
 * which is monotonic per the IClock contract (equivalent to {@code System.nanoTime()} today).
 *
 * <p>This SystemClock exists as a singleton instance.
 */
public final class SystemClock extends Clock {

    private static final SystemClock INSTANCE = new SystemClock();

    public static SystemClock getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------------

    @Override
    public long absoluteTimeMillis() {
        return CoreMetrics.currentTimeMillis();
    }

    @Override
    public long relativeTimeMillis() {
        return CoreMetrics.nanoTime() / 1_000_000;
    }

    @Override
    public long relativeTimeNanos() {
        return CoreMetrics.nanoTime();
    }

    // ------------------------------------------------------------------------

    private SystemClock() {}
}
