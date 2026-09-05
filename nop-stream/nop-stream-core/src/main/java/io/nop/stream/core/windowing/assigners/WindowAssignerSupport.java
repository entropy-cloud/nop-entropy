/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing.assigners;

import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * item 21 D-4 convergence: single point for the window-assigner family's
 * shared structure — the size/slide/offset constructor validation (previously
 * 17/17 verbatim lines in the sliding event/processing twins), the overflow
 * guard for {@code start + size} (previously 4 copies across the tumbling and
 * sliding assigners), and the sliding window expansion loop (previously one
 * copy per sliding assigner). Package-private: no public API addition.
 */
final class WindowAssignerSupport {

    private WindowAssignerSupport() {
    }

    /** Shared ctor validation for sliding assigners (size/slide/offset invariants). */
    static void validateSliding(long size, long slide, long offset) {
        if (size <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "size").param(ARG_DETAIL, "must be positive");
        }
        if (slide <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "slide").param(ARG_DETAIL, "must be positive");
        }
        if (offset < 0 || offset >= slide) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "offset").param(ARG_DETAIL, "must be in [0, slide)");
        }
        if (size / slide > 10000) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "size/slide")
                    .param(ARG_DETAIL, "size/slide ratio exceeds 10000, which would generate too many windows");
        }
    }

    /** Shared ctor validation for tumbling assigners (size invariants). */
    static void validateTumbling(long size) {
        if (size <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "size").param(ARG_DETAIL, "must be positive");
        }
    }

    /**
     * Single point for the window end overflow guard: an addition that wraps
     * past {@code Long.MAX_VALUE} clamps the end to {@code Long.MAX_VALUE}.
     */
    static TimeWindow windowOf(long start, long size) {
        long end = start + size;
        if (end < start) {
            end = Long.MAX_VALUE;
        }
        return new TimeWindow(start, end);
    }

    /**
     * Single point for the sliding expansion: all windows of the given
     * size/slide that contain {@code time} (newest first, matching the
     * historical iteration order).
     */
    static List<TimeWindow> slidingWindowsFor(long time, long size, long slide, long offset) {
        List<TimeWindow> windows = new ArrayList<>();
        long lastStart = TimeWindow.getWindowStartWithOffset(time, offset, slide);
        for (long start = lastStart; start > time - size; start -= slide) {
            windows.add(windowOf(start, size));
        }
        return windows;
    }
}
