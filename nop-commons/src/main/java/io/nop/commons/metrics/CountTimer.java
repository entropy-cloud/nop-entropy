/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.metrics;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class CountTimer {
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong totalTime = new AtomicLong();

    public void record(int count, long time, TimeUnit unit) {
        this.count.getAndAdd(count);
        long nanoAmount = (long) TimeUtils.convert(time, unit, TimeUnit.NANOSECONDS);
        this.totalTime.getAndAdd(nanoAmount);
    }

    public long getCount() {
        return count.get();
    }

    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(totalTime.get(), unit);
    }

    public long getTotalTime() {
        return totalTime.get();
    }

    public FunctionTimer register(MeterRegistry registry, String name, Iterable<Tag> tags) {
        return registry.more().timer(name, tags, this, m -> m.getCount(), m -> m.getTotalTime(), TimeUnit.NANOSECONDS);
    }
}
