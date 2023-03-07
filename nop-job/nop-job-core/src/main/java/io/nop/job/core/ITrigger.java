/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.job.core;

/**
 * 定时触发器
 *
 * @author canonical_entropy@163.com
 */
public interface ITrigger {
    /**
     * 返回下一次调度执行时间。返回-1表示已经到达终点，不再需要触发
     *
     * @param afterTime      返回的nextExecutionTime一般情况下大于afterTime。只有HandleMisfireTrigger可能返回较小的值。
     * @param triggerContext 当前定时器的状态
     * @return 从January 1, 1970, 00:00:00 GMT开始的毫秒数
     */
    long nextScheduleTime(long afterTime, ITriggerContext triggerContext);
}