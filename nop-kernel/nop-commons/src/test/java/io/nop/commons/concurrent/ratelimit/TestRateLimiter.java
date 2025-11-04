package io.nop.commons.concurrent.ratelimit;

import io.nop.api.core.time.CoreMetrics;
import org.junit.jupiter.api.Test;

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
}
