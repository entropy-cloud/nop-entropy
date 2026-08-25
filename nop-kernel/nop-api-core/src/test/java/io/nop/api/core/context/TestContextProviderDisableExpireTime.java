/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestContextProviderDisableExpireTime {

    @AfterEach
    void cleanContext() {
        BaseContextProvider.clear();
    }

    /**
     * 回归：disableExpireTime在task执行期间必须真正禁用callExpireTime（置为-1），
     * 修复前代理只是拷贝原值，超时判断行为完全不变，与方法语义相反。
     */
    @Test
    public void testDisableExpireTime() {
        IContext context = ContextProvider.getOrCreateContext();
        long expireTime = System.currentTimeMillis() + 10_000;
        context.setCallExpireTime(expireTime);
        assertFalse(context.isCallExpired());

        Long observed = ContextProvider.disableExpireTime(() -> {
            IContext current = ContextProvider.currentContext();
            assertTrue(current.getCallExpireTime() < 0,
                    "expire time must be disabled inside task, but was " + current.getCallExpireTime());
            assertFalse(current.isCallExpired());
            return current.getCallExpireTime();
        });
        assertTrue(observed < 0, "callExpireTime inside task must be negative, but was " + observed);

        // task结束后原context的超时设置不受影响
        assertEquals(expireTime, context.getCallExpireTime());
    }

    /**
     * 无context或本就未设置超时（<0）时不包装代理，直接执行task。
     */
    @Test
    public void testDisableExpireTimeNoopCases() {
        // 无context
        BaseContextProvider.clear();
        assertEquals("done", ContextProvider.disableExpireTime(() -> "done"));

        // 已禁用（-1）
        IContext context = ContextProvider.getOrCreateContext();
        context.setCallExpireTime(-1);
        assertEquals("done", ContextProvider.disableExpireTime(() -> "done"));
    }
}
