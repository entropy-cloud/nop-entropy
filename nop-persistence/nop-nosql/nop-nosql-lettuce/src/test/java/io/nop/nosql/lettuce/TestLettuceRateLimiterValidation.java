/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce;

import io.nop.nosql.core.RateLimiterConfig;
import io.nop.nosql.lettuce.impl.LettuceRateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestLettuceRateLimiterValidation {

    @Test
    void testNonPositiveRateRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LettuceRateLimiter(null, "test:rl:invalid", new RateLimiterConfig(0, 10)));
        assertThrows(IllegalArgumentException.class,
                () -> new LettuceRateLimiter(null, "test:rl:invalid", new RateLimiterConfig(-1, 10)));
    }

    @Test
    void testNonPositiveCapacityRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LettuceRateLimiter(null, "test:rl:invalid", new RateLimiterConfig(1, 0)));
    }

    @Test
    void testValidConfigAccepted() {
        assertDoesNotThrow(() -> new LettuceRateLimiter(null, "test:rl:valid", new RateLimiterConfig(0.5, 10)));
    }
}
