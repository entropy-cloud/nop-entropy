/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.util;

import io.nop.api.core.time.IClock;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Override
    public LocalDate currentDate() {
        return new Timestamp(currentTimeMillis()).toLocalDateTime().toLocalDate();
    }

    @Override
    public LocalDateTime currentDateTime() {
        return new Timestamp(currentTimeMillis()).toLocalDateTime();
    }
}
