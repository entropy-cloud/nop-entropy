package io.nop.autotest.core.util;

import io.nop.api.core.time.IClock;

/**
 * 单元测试执行时所采用的时钟，确保时间永不重复且向前执行
 */
public class TestClock implements IClock {
    private long lastTime;

    @Override
    public synchronized long currentTimeMillis() {
        long now = System.currentTimeMillis();
        if (now <= lastTime) {
            lastTime++;
            return lastTime;
        }
        lastTime = now;
        return lastTime;
    }
}
