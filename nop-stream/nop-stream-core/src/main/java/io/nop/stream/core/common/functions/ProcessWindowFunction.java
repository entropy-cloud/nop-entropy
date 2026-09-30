/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions;

import java.io.Serializable;

import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.windows.Window;

public interface ProcessWindowFunction<IN, OUT, KEY, W extends Window> extends Serializable {
    void process(KEY key, W window, Iterable<IN> input, Context context, Collector<OUT> out) throws Exception;

    /**
     * Releases any per-window state that this function has written via
     * {@link Context#windowState()}. G-3 (plan 369 Phase 2): invoked by the window
     * operator whenever a window's state is discarded — on trigger purge, on
     * cleanup-time expiry, and when a merging assigner retires the window in a
     * merge. The default implementation is a no-op; functions that keep
     * per-window state MUST clear it here, otherwise the state leaks into every
     * subsequent checkpoint (Flink {@code ProcessWindowFunction#clear} semantics).
     *
     * @param context the context of the window being cleared
     * @throws Exception the function may throw exceptions
     */
    default void clear(Context context) throws Exception {
    }

    abstract class Context implements Serializable {
        public abstract long currentProcessingTime();

        public abstract long currentWatermark();

        public abstract KeyedStateStore windowState();

        public abstract KeyedStateStore globalState();

        public abstract <X> void output(OutputTag<X> outputTag, X value);
    }
}
