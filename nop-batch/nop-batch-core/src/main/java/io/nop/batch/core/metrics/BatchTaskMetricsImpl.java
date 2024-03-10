/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.BatchConstants;
import io.nop.batch.core.IBatchTaskMetrics;
import io.nop.commons.util.StringHelper;

import java.util.Arrays;

public class BatchTaskMetricsImpl implements IBatchTaskMetrics {
    private final MeterRegistry registry;
    private final String prefix;

    private final Timer taskSuccessTimer;
    private final Timer taskFailureTimer;
    private final Timer chunkSuccessTimer;
    private final Timer chunkFailureTimer;
    private final Timer loadSuccessTimer;
    private final Timer loadFailureTimer;
    private final Timer processSuccessTimer;
    private final Timer processFailureTimer;
    private final Timer consumeSuccessTimer;
    private final Timer consumeFailureTimer;
    private final Counter retryCounter;
    private final Counter skipCounter;

    private final Counter loadCounter;
    private final Counter consumeCounter;

    public BatchTaskMetricsImpl(MeterRegistry registry, String prefix, String jobName, String taskName) {
        this.registry = Guard.notNull(registry, "registry");
        this.prefix = prefix;
        if (StringHelper.isEmpty(jobName))
            jobName = "default";
        if (StringHelper.isEmpty(taskName))
            taskName = "default";

        Tag jobNameTag = Tag.of("job.name", jobName);
        Tag taskNameTag = Tag.of("task.name", taskName);
        Tag statusSuccessTag = Tag.of("status", BatchConstants.STATUS_SUCCESS);
        Tag statusFailureTag = Tag.of("status", BatchConstants.STATUS_FAILURE);

        taskSuccessTimer = createTimer(BatchConstants.METER_TASK, jobNameTag, taskNameTag, statusSuccessTag);
        taskFailureTimer = createTimer(BatchConstants.METER_TASK, jobNameTag, taskNameTag, statusFailureTag);

        chunkSuccessTimer = createTimer(BatchConstants.METER_CHUNK, jobNameTag, taskNameTag, statusSuccessTag);

        chunkFailureTimer = createTimer(BatchConstants.METER_CHUNK, jobNameTag, taskNameTag, statusFailureTag);

        loadSuccessTimer = createTimer(BatchConstants.METER_LOAD, jobNameTag, taskNameTag, statusSuccessTag);

        loadFailureTimer = createTimer(BatchConstants.METER_LOAD, jobNameTag, taskNameTag, statusFailureTag);

        processSuccessTimer = createTimer(BatchConstants.METER_ITEM_PROCESS, jobNameTag, taskNameTag, statusSuccessTag);

        processFailureTimer = createTimer(BatchConstants.METER_ITEM_PROCESS, jobNameTag, taskNameTag, statusFailureTag);

        consumeSuccessTimer = createTimer(BatchConstants.METER_CONSUME, jobNameTag, taskNameTag, statusSuccessTag);

        consumeFailureTimer = createTimer(BatchConstants.METER_CONSUME, jobNameTag, taskNameTag, statusFailureTag);

        retryCounter = createCounter(BatchConstants.METER_ITEM_RETRY, jobNameTag, taskNameTag);
        skipCounter = createCounter(BatchConstants.METER_ITEM_SKIP, jobNameTag, taskNameTag);

        loadCounter = createCounter(BatchConstants.METER_ITEM_LOAD, jobNameTag, taskNameTag);
        consumeCounter = createCounter(BatchConstants.METER_ITEM_CONSUME, jobNameTag, taskNameTag);
    }

    Timer createTimer(String name, Tag... tags) {
        return registry.timer(meterName(name), Arrays.asList(tags));
    }

    Counter createCounter(String name, Tag... tags) {
        return registry.counter(meterName(name), Arrays.asList(tags));
    }

    String meterName(String name) {
        if (prefix == null)
            return BatchConstants.DEFAULT_METER_PREFIX + name;
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
    public Object beginChunk() {
        return Timer.start(registry);
    }

    @Override
    public void endChunk(Object meter, boolean success) {
        ((Timer.Sample) meter).stop(success ? chunkSuccessTimer : chunkFailureTimer);
    }

    @Override
    public Object beginLoad() {
        return Timer.start(registry);
    }

    @Override
    public void endLoad(Object meter, int count, boolean success) {
        ((Timer.Sample) meter).stop(success ? loadSuccessTimer : loadFailureTimer);
        loadCounter.increment(count);
    }

    @Override
    public Object beginConsume(int count) {
        return Timer.start(registry);
    }

    @Override
    public void endConsume(Object meter, int count, boolean success) {
        ((Timer.Sample) meter).stop(success ? consumeSuccessTimer : consumeFailureTimer);
        consumeCounter.increment(count);
    }

    @Override
    public Object beginProcess() {
        return Timer.start(registry);
    }

    @Override
    public void endProcess(Object meter, boolean success) {
        ((Timer.Sample) meter).stop(success ? processSuccessTimer : processFailureTimer);
    }

    @Override
    public void retry(int count) {
        retryCounter.increment(count);
    }

    @Override
    public void skipError(int count) {
        skipCounter.increment(count);
    }

    @Override
    public long getLoadItemCount() {
        return loadSuccessTimer.count() + loadFailureTimer.count();
    }

    @Override
    public long getProcessItemCount() {
        return processSuccessTimer.count() + processFailureTimer.count();
    }

    @Override
    public long getSkipItemCount() {
        return (long) skipCounter.count();
    }

    @Override
    public long getRetryItemCount() {
        return (long) retryCounter.count();
    }

    @Override
    public long getConsumeItemCount() {
        return (long) consumeCounter.count();
    }

}