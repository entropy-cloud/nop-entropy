package io.nop.job.core.trigger;

import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITrigger;
import io.nop.job.core.ITriggerEvalContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check 审计 nop-job [P1]：HandleMisfireTrigger 的 once 任务 misfire 跳过分支为死代码——
 * 包装链上 instanceof OnceTrigger 恒不成立（传入的总是 CheckActiveTrigger 等包装器），
 * 迟到超过 misfireThreshold 的一次性任务被立即补跑而非丢弃。
 * 修复：TriggerBuilder 在包装前捕获 once 语义传入 HandleMisfireTrigger。
 */
public class TestOnceMisfireAndLocalContract {

    static class FixedEvalContext implements ITriggerEvalContext {
        @Override
        public long getFireCount() {
            return 0;
        }

        @Override
        public long getLastScheduledTime() {
            return 0;
        }

        @Override
        public long getLastEndTime() {
            return 0;
        }

        @Override
        public long getMinScheduleTime() {
            return 0;
        }

        @Override
        public boolean isScheduleCompleted() {
            return false;
        }

        @Override
        public long getMaxExecutionCount() {
            return 0;
        }

        @Override
        public long getMaxScheduleTime() {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 超过misfire阈值的迟到once任务：包装链整体返回-1（丢弃），不返回过去的触发时刻。
     */
    @Test
    public void testLateOnceTaskBeyondMisfireThresholdIsDiscarded() {
        long now = System.currentTimeMillis();
        TriggerSpec spec = new TriggerSpec();
        spec.setMinScheduleTime(now - 3600_000L); // 一小时前的once触发时刻
        spec.setMisfireThreshold(60_000L);

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        long next = trigger.nextScheduleTime(now, new FixedEvalContext());
        assertEquals(-1L, next, "once task late beyond misfireThreshold must be discarded (-1)");
    }

    /**
     * 对照组：迟到但未超阈值的once任务正常补跑（返回过去时刻，调度器立即执行）。
     */
    @Test
    public void testSlightlyLateOnceTaskStillFires() {
        long now = System.currentTimeMillis();
        TriggerSpec spec = new TriggerSpec();
        spec.setMinScheduleTime(now - 10_000L); // 迟到10s < 阈值60s
        spec.setMisfireThreshold(60_000L);

        ITrigger trigger = TriggerBuilder.buildTrigger(spec, null);
        long next = trigger.nextScheduleTime(now, new FixedEvalContext());
        assertTrue(next > 0 && next <= now, "slightly late once task should fire immediately, got: " + next);
    }
}
