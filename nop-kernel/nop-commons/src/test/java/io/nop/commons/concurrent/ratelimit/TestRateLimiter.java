package io.nop.commons.concurrent.ratelimit;

import io.nop.api.core.time.CoreMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRateLimiter {
    @Test
    public void testLimit(){
        IRateLimiter limiter = DefaultRateLimiter.create(1);
        long beginTime = CoreMetrics.currentTimeMillis();
        int n = 10;
        for(int i=0;i<n;i++){
            limiter.acquire();
        }
        long diffTime = CoreMetrics.currentTimeMillis() - beginTime;
        System.out.println("tps = " + (n/(diffTime*0.001)));
    }

    @Test
    public void testAcquireCounters() {
        // 成功计数
        DefaultRateLimiter successLimiter = new DefaultRateLimiter(1000);
        assertTrue(successLimiter.tryAcquire(1, 1000));
        assertEquals(1, successLimiter.getAcquireSuccessCount());
        assertEquals(0, successLimiter.getAcquireFailCount());

        // 失败计数：Guava允许首个许可立即发放，后续在超时0内必然获取失败
        DefaultRateLimiter failLimiter = new DefaultRateLimiter(0.000001);
        assertTrue(failLimiter.tryAcquire(1, 0));
        assertFalse(failLimiter.tryAcquire(1, 0));
        assertFalse(failLimiter.tryAcquire(1, 0));
        assertEquals(1, failLimiter.getAcquireSuccessCount());
        // 失败计数不应错误地返回成功计数
        assertEquals(2, failLimiter.getAcquireFailCount());
    }
}
