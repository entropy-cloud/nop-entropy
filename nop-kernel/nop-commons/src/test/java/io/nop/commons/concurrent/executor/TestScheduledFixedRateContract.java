/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C7-002 Decision）fixed-rate 补偿语义行为观测。
 */
public class TestScheduledFixedRateContract {

    /** pin7002：任务体超周期 → 后续轮次立即补跑（JDK fixed-rate 补偿语义，不漂移到「每 200ms+周期」）。 */
    @Test
    public void pin7002_fixedRateCompensatesAfterLongTask() throws Exception {
        DefaultScheduledExecutor timer = DefaultScheduledExecutor.newSingleThreadTimer("probe-fx7002");
        try {
            List<Long> startTimes = new CopyOnWriteArrayList<>();
            java.util.concurrent.Future<?> handle = timer.scheduleAtFixedRate(() -> {
                startTimes.add(System.currentTimeMillis());
                try {
                    Thread.sleep(150); // 3x 周期：故意超时
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 0, 50, TimeUnit.MILLISECONDS);

            Thread.sleep(800);
            handle.cancel(false);

            assertTrue(startTimes.size() >= 3, "应观察到至少 3 轮，实际 " + startTimes.size());
            // 补偿语义：第 2 轮起点不晚于第 1 轮结束 + 一个周期（立即补跑，而非再等一个整周期）
            for (int i = 1; i < Math.min(3, startTimes.size()); i++) {
                long gap = startTimes.get(i) - startTimes.get(i - 1);
                assertTrue(gap <= 200,
                        "fixed-rate 补偿：轮间隔应 ≈ 任务时长（立即补跑），轮 " + i + " gap=" + gap + "ms");
            }
        } finally {
            timer.destroy();
        }
    }
}
