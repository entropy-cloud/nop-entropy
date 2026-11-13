/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C4-011）死旋钮删除结构钉。
 */
public class TestThreadPoolConfigFields {

    /** pin4011：threadPriority / allowCoreThreadTimeOut 已删除（反射，含 getter/setter）。 */
    @Test
    public void pin4011_deadKnobsRemoved() {
        for (java.lang.reflect.Field f : ThreadPoolConfig.class.getDeclaredFields()) {
            assertFalse(f.getName().equals("threadPriority") || f.getName().equals("allowCoreThreadTimeOut"),
                    "缺陷态：死旋钮字段仍存在: " + f.getName());
        }
        for (java.lang.reflect.Method m : ThreadPoolConfig.class.getDeclaredMethods()) {
            assertFalse(m.getName().contains("ThreadPriority")
                            || m.getName().toLowerCase().contains("allowcorethreadtimeout"),
                    "缺陷态：死旋钮访问器仍存在: " + m.getName());
        }
        // 存活字段仍可正常使用
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setQueueCapacity(5);
        assertTrue(config.getQueueCapacity() == 5);
    }
}
