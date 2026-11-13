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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 锚点周期过滤装饰器（cron 轨道 C3，plan 537）：自锚点日起每 N 个 day/week 取模过滤内层触发时刻。
 * 语义契约见黄金向量 repeat 节（nop-chaos-flux docs/components/cron-editor/golden-vectors.json meta.conventions）：
 * fire 的引擎默认时区日历日 &gt;= 锚点日，且 day 单位整日差 mod every == 0 / week 单位 floor(整日差/7) mod every == 0。
 * 永不匹配时扫描达到上限即返回 -1（触发层终止约定，同 LimitTimeTrigger）。
 */
public class AnchorRepeatTrigger implements ITrigger {
    private static final int MAX_SCAN = 36600;

    private final ITrigger trigger;
    private final int every;
    private final boolean weekUnit;
    private final LocalDate anchorDate;

    public AnchorRepeatTrigger(ITrigger trigger, int every, String unit, String anchorDate) {
        // Minor-4 构造期 Guard：every<1 会在 matchesAnchor 取模处抛 ArithmeticException；
        // 非法 unit 原先静默按 day 语义执行。均在构造期 fail-fast（typed）。
        if (every < 1) {
            throw new IllegalArgumentException("AnchorRepeatTrigger: every must be >= 1, got: " + every);
        }
        if (!"day".equals(unit) && !"week".equals(unit)) {
            throw new IllegalArgumentException("AnchorRepeatTrigger: unit must be 'day' or 'week', got: " + unit);
        }
        this.trigger = trigger;
        this.every = every;
        this.weekUnit = "week".equals(unit);
        this.anchorDate = LocalDate.parse(anchorDate);
    }

    @Override
    public long nextScheduleTime(long afterTime, ITriggerEvalContext evalContext) {
        long t = trigger.nextScheduleTime(afterTime, evalContext);
        int scan = 0;
        while (t >= 0 && !matchesAnchor(t)) {
            if (scan++ >= MAX_SCAN)
                return -1L;
            t = trigger.nextScheduleTime(t, evalContext);
        }
        return t;
    }

    private boolean matchesAnchor(long time) {
        LocalDate fireDate = LocalDate.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
        if (fireDate.isBefore(anchorDate))
            return false;
        long diff = ChronoUnit.DAYS.between(anchorDate, fireDate);
        // week 单位按周奇偶锚定（同 Windows 任务计划程序「每 N 周内多天」）：第 floor(diff/7) 周须 ≡0 (mod every)；
        // day 单位按整日差取模。RP-04 向量判别两者。
        if (weekUnit)
            return (diff / 7) % every == 0;
        return diff % every == 0;
    }
}
