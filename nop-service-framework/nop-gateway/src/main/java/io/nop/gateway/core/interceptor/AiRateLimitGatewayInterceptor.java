package io.nop.gateway.core.interceptor;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;
import io.nop.gateway.core.context.IGatewayContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AiRateLimitGatewayInterceptor implements IGatewayInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AiRateLimitGatewayInterceptor.class);

    /** header key 必须小写读取：生产链路上 Vertx/Servlet 实现已把 header key 统一小写化 */
    static final String HEADER_X_FORWARDED_FOR = "x-forwarded-for";
    static final String DEFAULT_KEY = "default";
    /** XFF 条目应为 IP 文本；IPv6 文本最长 45 字符，超出视为非法值 */
    private static final int MAX_KEY_LENGTH = 45;

    private int capacity = 10;
    private double refillRate = 1;
    private long refillIntervalMs = 1000;
    /** 最多跟踪的独立限流 key 数（防伪造 XFF 轮换导致的桶 Map 无界增长） */
    private int maxTrackedKeys = 100_000;

    private volatile LocalCache<String, TokenBucket> buckets;

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public void setRefillRate(double refillRate) {
        this.refillRate = refillRate;
    }

    public void setRefillIntervalMs(long refillIntervalMs) {
        this.refillIntervalMs = refillIntervalMs;
    }

    public void setMaxTrackedKeys(int maxTrackedKeys) {
        this.maxTrackedKeys = maxTrackedKeys;
    }

    @Override
    public ApiResponse<?> onResponse(ApiResponse<?> response, IGatewayContext svcCtx) {
        return response;
    }

    @Override
    public ApiRequest<?> onRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
        String key = resolveKey(svcCtx);
        TokenBucket bucket = buckets().computeIfAbsent(key,
                k -> new TokenBucket(capacity, refillRate, refillIntervalMs));
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
        Object value = svcCtx.getRequest().getHeaders() != null
                ? svcCtx.getRequest().getHeaders().get(HEADER_X_FORWARDED_FOR)
                : null;
        String clientIp = normalizeKey(value != null ? value.toString() : null);
        if (clientIp == null) {
            clientIp = DEFAULT_KEY;
        }
        return clientIp;
    }

    /**
     * XFF 形如 "client, proxy1, proxy2"，取第一项（客户端侧地址）。
     * 仅接受 IP 形态文本（限定长度与字符集：数字/十六进制/./:/），否则归并为 default——
     * 防止任意字符串进入桶 key 空间撑爆内存。
     * <p>注意：XFF 本身可被客户端伪造，基于可信代理链的真实客户端 IP 解析需独立的部署级配置，
     * 见审计 check2 P1 标注；此处仅收敛 key 空间保证内存有界。
     */
    static String normalizeKey(String xff) {
        if (xff == null) {
            return null;
        }
        int comma = xff.indexOf(',');
        String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
        if (first.isEmpty() || first.length() > MAX_KEY_LENGTH) {
            return null;
        }
        for (int i = 0; i < first.length(); i++) {
            char c = first.charAt(i);
            boolean ipChar = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':' || c == '%'; // % 允许 IPv6 zone-id
            if (!ipChar) {
                return null;
            }
        }
        return first.toLowerCase(Locale.ROOT);
    }

    /**
     * 桶缓存：{@link LocalCache}（Caffeine）带 expireAfterAccess 淘汰 + maximumSize 上限，
     * 空闲桶自动回收，内存有界。TTL 取"桶静置满 refill 周期"——空闲超过该时长的桶令牌已回满，
     * 驱逐后重建（新桶满令牌）语义无损；refillRate<=0（不回填）时退化为固定 1 小时，
     * 避免驱逐重置造成限流绕过。
     */
    private LocalCache<String, TokenBucket> buckets() {
        LocalCache<String, TokenBucket> c = buckets;
        if (c == null) {
            synchronized (this) {
                c = buckets;
                if (c == null) {
                    long ttlMs = refillRate > 0
                            ? Math.max((long) Math.ceil(capacity / refillRate), 1)
                                    * Math.max(refillIntervalMs, 1)
                            : TimeUnit.HOURS.toMillis(1);
                    c = LocalCache.newCache("ai-gateway-rate-limit-buckets",
                            CacheConfig.newConfig(maxTrackedKeys)
                                    .expireAfterAccess(java.time.Duration.ofMillis(ttlMs)));
                    buckets = c;
                }
            }
        }
        return c;
    }

    // ---- package-private test helpers ----

    /** 当前跟踪的桶数量（含已过期未清扫的惰性条目，测试断言上界用）。 */
    int trackedKeyCount() {
        return (int) buckets().estimatedSize();
    }

    /** 供测试触发缓存维护（清扫过期条目）：LocalCache.destroy() 即 Caffeine cleanUp，可重复调用。 */
    void cleanUpBuckets() {
        buckets().destroy();
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
            this.lastRefillTime = CoreMetrics.currentTimeMillis();
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
            long now = CoreMetrics.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed >= refillIntervalMs) {
                long intervals = elapsed / refillIntervalMs;
                tokens = Math.min(capacity, tokens + intervals * refillRate);
                lastRefillTime = now;
            }
        }
    }
}
