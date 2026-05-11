/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.commons.metrics.GlobalMeterRegistry;

public class JobPlannerMetricsImpl implements IJobPlannerMetrics {
    private final Counter dueCountCounter;
    private final Counter lockConflictCounter;

    public JobPlannerMetricsImpl() {
        this(GlobalMeterRegistry.instance());
    }

    public JobPlannerMetricsImpl(MeterRegistry registry) {
        this.dueCountCounter = registry.counter("nop.job.planner.due-count");
        this.lockConflictCounter = registry.counter("nop.job.planner.lock-conflict");
    }

    @Override
    public void onDueSchedules(int count) {
        dueCountCounter.increment(count);
    }

    @Override
    public void onLockConflicts(int count) {
        lockConflictCounter.increment(count);
    }
}
