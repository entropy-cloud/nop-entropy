/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.trigger;

import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;

/**
 * 只在指定时刻执行一次
 */
public class OnceTrigger implements ITrigger {
    private boolean first = true;
    private final long scheduleTime;

    public OnceTrigger(long scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    public long getScheduleTime() {
        return scheduleTime;
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerEvalContext evalContext) {
        // check2 [P0]: once 语义必须由持久化状态（evalContext）判定，而非仅靠实例内的 first 标志。
        // 分布式 planner 路径（JobTriggerCalculator.calculateNextFireTime）每次计算都经
        // TriggerBuilder 重建全新 trigger 链，first 每次都是 true，导致 once schedule 首次触发后
        // 每个周期都返回同一个过去时刻而被无限重复执行。LocalJobScheduler 保留 trigger 实例，
        // first 标志继续作为实例级兜底。
        if (scheduleTime > 0) {
            // 已在 once 时刻（或之后）产生过 fire（schedule.lastFireTime）→ 触发器已耗尽
            if (evalContext.getLastScheduledTime() >= scheduleTime) {
                return -1;
            }
        } else if (evalContext.getFireCount() > 0) {
            // 无显式触发时刻（立即执行一次）：已产生过任何 fire → 耗尽
            return -1;
        }

        if (!first) {
            return -1;
        }
        first = false;

        if (scheduleTime > 0)
            return scheduleTime;

        return afterTime + 1;
    }
}
