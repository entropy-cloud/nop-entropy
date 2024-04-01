/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.task.TaskConstants;

import java.util.Arrays;

public class TaskFlowMetricsImpl implements ITaskFlowMetrics {
    private final MeterRegistry registry;
    private final String prefix;
    private final Timer taskSuccessTimer;
    private final Timer taskFailureTimer;
    private final Timer stepSuccessTimer;
    private final Timer stepFailureTimer;

    public TaskFlowMetricsImpl(MeterRegistry registry, String prefix, String taskName, long taskVersion) {
        this.registry = Guard.notNull(registry, "registry");
        this.prefix = prefix;
        if (StringHelper.isEmpty(taskName))
            taskName = "default";

        Tag taskNameTag = Tag.of("task.name", taskName);
        Tag taskVersionTag = Tag.of("task.version", String.valueOf(taskVersion));
        Tag statusSuccessTag = Tag.of("status", TaskConstants.STATUS_SUCCESS);
        Tag statusFailureTag = Tag.of("status", TaskConstants.STATUS_FAILURE);

        taskSuccessTimer = createTimer(TaskConstants.METER_TASK, taskNameTag, taskVersionTag, statusSuccessTag);
        taskFailureTimer = createTimer(TaskConstants.METER_TASK, taskNameTag, taskVersionTag, statusFailureTag);

        stepSuccessTimer = createTimer(TaskConstants.METER_STEP, taskNameTag, taskVersionTag, statusSuccessTag);
        stepFailureTimer = createTimer(TaskConstants.METER_STEP, taskNameTag, taskVersionTag, statusSuccessTag);
    }

    Timer createTimer(String name, Tag... tags) {
        return registry.timer(meterName(name), Arrays.asList(tags));
    }

    Counter createCounter(String name, Tag... tags) {
        return registry.counter(meterName(name), Arrays.asList(tags));
    }

    String meterName(String name) {
        if (prefix == null)
            return TaskConstants.DEFAULT_METER_PREFIX + name;
        return prefix + name;
    }

    @Override
    public Object beginTask() {
        return Timer.start(registry);
    }

    @Override
    public void endTask(Object meter, boolean success) {
        ((Timer.Sample) meter).stop(success ? taskSuccessTimer : taskFailureTimer);
    }

    @Override
    public Object beginStep(String stepId, String stepType) {
        return Timer.start(registry);
    }

    @Override
    public void endStep(Object meter, boolean success) {
        ((Timer.Sample) meter).stop(success ? stepSuccessTimer : stepFailureTimer);
    }
}