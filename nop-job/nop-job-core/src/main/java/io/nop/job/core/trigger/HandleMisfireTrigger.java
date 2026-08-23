/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.trigger;

import io.nop.api.core.util.Guard;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;

/**
 * @author canonical_entropy@163.com
 */
public class HandleMisfireTrigger implements ITrigger {

    private final ITrigger trigger;
    private final long misfireThreshold;
    /**
     * 包装链的once语义触发时刻（0=非once）。HandleMisfireTrigger处于包装链最外层，内层总是
     * CheckActiveTrigger等包装器，instanceof OnceTrigger永不成立（原判定为死代码，misfire对once
     * 完全失效：迟到的一次性任务被立即补跑而非丢弃）。
     */
    private final long onceScheduleTime;

    public HandleMisfireTrigger(long misfireThreshold, ITrigger trigger) {
        this(misfireThreshold, trigger, 0L);
    }

    public HandleMisfireTrigger(long misfireThreshold, ITrigger trigger, long onceScheduleTime) {
        this.misfireThreshold = Guard.positiveLong(misfireThreshold, "misfireThreshold");
        this.trigger = trigger;
        this.onceScheduleTime = onceScheduleTime;
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerEvalContext evalContext) {
        // 双路判定：包装链场景用构造时捕获的onceScheduleTime（instanceof对包装器恒不成立）；
        // 直接以raw OnceTrigger构造的场景（既有用法）保留instanceof判定
        long onceTime = onceScheduleTime;
        if (onceTime <= 0 && trigger instanceof OnceTrigger) {
            onceTime = ((OnceTrigger) trigger).getScheduleTime();
        }
        if (onceTime > 0 && onceTime < afterTime - misfireThreshold) {
            // 超过misfire阈值的迟到一次性任务：丢弃而非补跑
            return -1;
        }

        long startTime = evalContext.getLastScheduledTime();
        if (startTime <= 0) {
            startTime = evalContext.getMinScheduleTime();
            if (startTime <= 0)
                return trigger.nextScheduleTime(afterTime, evalContext);
        }

        if (startTime < afterTime - misfireThreshold)
            startTime = afterTime - misfireThreshold;

        long next = trigger.nextScheduleTime(startTime, evalContext);
        return next;
    }
}