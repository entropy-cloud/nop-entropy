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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.streamrecord.watermark.Watermark;

/**
 * Manages multiple {@link HeapInternalTimerService} instances and advances their watermarks
 * in response to incoming watermark events.
 */
public class TimerServiceManager {

    private static final Logger LOG = LoggerFactory.getLogger(TimerServiceManager.class);

    private final List<HeapInternalTimerService<?>> timerServices = new ArrayList<>();

    public void registerTimerService(HeapInternalTimerService<?> timerService) {
        timerServices.add(timerService);
    }

    public void advanceWatermark(Watermark mark) throws Exception {
        for (HeapInternalTimerService<?> service : timerServices) {
            try {
                service.advanceWatermark(mark.getTimestamp());
            } catch (Exception e) {
                LOG.error("Failed to advance watermark for timer service: {}", service, e);
            }
        }
    }
}
