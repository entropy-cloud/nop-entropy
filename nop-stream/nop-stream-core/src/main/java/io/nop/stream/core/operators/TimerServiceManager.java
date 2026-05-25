/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.streamrecord.watermark.Watermark;

/**
 * Manages multiple {@link HeapInternalTimerService} instances and advances their watermarks
 * in response to incoming watermark events.
 */
public class TimerServiceManager {

    private final List<HeapInternalTimerService<?>> timerServices = new ArrayList<>();

    public void registerTimerService(HeapInternalTimerService<?> timerService) {
        timerServices.add(timerService);
    }

    public void advanceWatermark(Watermark mark) throws Exception {
        for (HeapInternalTimerService<?> service : timerServices) {
            service.advanceWatermark(mark.getTimestamp());
        }
    }
}
