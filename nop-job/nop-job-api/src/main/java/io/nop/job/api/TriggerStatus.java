/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.api;

public enum TriggerStatus {
    /**
     * 处于暂停状态，没有被调度
     */
    PAUSED,

    /**
     * 处于调度状态, 等待执行
     */
    SCHEDULING,

    /**
     * 到达调度时刻，正在执行任务
     */
    EXECUTING,

    /**
     * 整体执行完毕，不再调度
     */
    COMPLETED,

    /**
     * 主动调用cancel操作取消了任务执行，不再被调度
     */
    CANCELLED,

    /**
     * 执行过程出现异常，执行中断，不再被调度
     */
    ERROR;

    public boolean isDone() {
        return this.ordinal() >= COMPLETED.ordinal();
    }

    public boolean isActive() {
        return this == SCHEDULING || this == EXECUTING;
    }
}