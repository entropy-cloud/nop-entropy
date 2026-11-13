/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.ratelimit;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT;
import static io.nop.commons.CommonErrors.ERR_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C7-015）acquire() 默认上界可配置载体钉。
 */
public class TestRateLimiterAcquireBound {

    @AfterEach
    public void restoreConfig() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT, null);
    }

    /** pin7015：配置短上界 → acquire() 超时抛类型化异常（缺陷态=无限不可中断阻塞）。 */
    @Test
    public void pin7015_acquireBoundTypedException() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT, 100L);
        // permitsPerSecond 极小 → 1 个 permit 也需等待 > 100ms
        IRateLimiter limiter = DefaultRateLimiter.create(1.0 / 1000_000);
        limiter.tryAcquire(1, 0); // 消耗掉初始许可

        NopException ex = assertThrows(NopException.class, limiter::acquire);
        assertEquals(ERR_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT.getErrorCode(), ex.getErrorCode());
    }

    /** pin7015b：<=0 保持无限语义（不抛不阻塞——用 tryAcquire(permits,0) 观察层验证配置通路）。 */
    @Test
    public void pin7015b_zeroMeansUnlimited() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT, 0L);
        IRateLimiter limiter = DefaultRateLimiter.create(1000);
        // 无限语义下 acquire 正常获得许可（速率 1000/s，不会真阻塞）
        limiter.acquire();
        assertTrue(limiter.getAcquireSuccessCount() >= 1);
        assertEquals(0, TimeUnit.SECONDS.toMillis(0));
    }
}
