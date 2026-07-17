package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.core.context.IGatewayContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AiRateLimitGatewayInterceptor implements IGatewayInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AiRateLimitGatewayInterceptor.class);

    private int capacity = 10;
    private double refillRate = 1;
    private long refillIntervalMs = 1000;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public void setRefillRate(double refillRate) {
        this.refillRate = refillRate;
    }

    public void setRefillIntervalMs(long refillIntervalMs) {
        this.refillIntervalMs = refillIntervalMs;
    }

    @Override
    public ApiResponse<?> onResponse(ApiResponse<?> response, IGatewayContext svcCtx) {
        return response;
    }

    @Override
    public ApiRequest<?> onRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
        String key = resolveKey(svcCtx);
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillRate, refillIntervalMs));
        if (!bucket.tryConsume()) {
            LOG.warn("Rate limit exceeded for key={}", key);
            ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
            rejected.setHttpStatus(429);
            rejected.setHeader("Retry-After", String.valueOf(refillIntervalMs / 1000));
            throw new io.nop.gateway.GatewayRejectException(rejected);
        }
        return request;
    }

    protected String resolveKey(IGatewayContext svcCtx) {
        String clientIp = svcCtx.getRequest().getHeaders() != null
                ? (String) svcCtx.getRequest().getHeaders().get("X-Forwarded-For")
                : null;
        if (clientIp == null) {
            clientIp = "default";
        }
        return clientIp;
    }

    static class TokenBucket {
        private final int capacity;
        private final double refillRate;
        private final long refillIntervalMs;
        private double tokens;
        private long lastRefillTime;

        TokenBucket(int capacity, double refillRate, long refillIntervalMs) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed >= refillIntervalMs) {
                long intervals = elapsed / refillIntervalMs;
                tokens = Math.min(capacity, tokens + intervals * refillRate);
                lastRefillTime = now;
            }
        }
    }
}
