/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api;

import io.nop.api.core.util.ICancellable;

import java.util.function.Consumer;

/**
 * 任务调度器支持持久化状态保存。在高可用部署时，每次Trigger触发前后都会持久化状态到数据库中。
 */
public interface IJobScheduleStore {

    ICancellable fetchPersistJobs(Consumer<JobDetail> processor);

    ITriggerState loadTriggerState(String jobName);

    void saveTriggerState(ITriggerState state);

    void removeTriggerState(ITriggerState state);
}
