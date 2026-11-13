package io.nop.nosql.lettuce;

import io.nop.nosql.core.RateLimitResult;
import io.nop.nosql.core.RateLimiterConfig;
import io.nop.nosql.core.config.RedisConfig;
import io.nop.nosql.lettuce.impl.LettuceRateLimiter;
import io.nop.nosql.lettuce.impl.LettuceRedisConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4-32（2.5-C7-013，plan 2566 [R1-M6]）行为钉（testcontainers，先例 TestLettuceHashTtlAtomic）：
 * <ol>
 *   <li>真 Redis TIME 路径：连续取满 capacity 后拒绝；将 timestamp_key 预置为「服务器钟
 *       明显过去」的旧值 → 下一请求按服务器钟 delta 正常回填（本地墙钟不再参与，客户端
 *       无法影响判定）。</li>
 *   <li>旧键混存腿：timestamp_key 预置客户端 epoch 秒旧值（同域同单位）→
 *       delta=max(0,...)/min(capacity,...) 钳制下允许判定正常。</li>
 * </ol>
 * 门控：-Dnop.test.docker.enabled=true；环境不可用时跳过，结构钉（TestRedisRateLimitServerClock_C7_013）
 * 承载并诚实登记。
 */
@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
public class TestRedisRateLimitServerClockDocker_C7_013 {

    @Container
    public GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceRedisConnectionProvider provider;

    @BeforeEach
    void setUp() {
        RedisConfig config = new RedisConfig();
        config.setHost(redis.getHost());
        config.setPort(redis.getMappedPort(6379));

        provider = new LettuceRedisConnectionProvider();
        provider.setConfig(config);
        provider.start();
    }

    @AfterEach
    void tearDown() {
        if (provider != null)
            provider.stop();
    }

    LettuceRateLimiter limiter(String key, int rate, int capacity) {
        return new LettuceRateLimiter(provider, key, new RateLimiterConfig(rate, capacity));
    }

    @Test
    void testServerClockRefill_localClientCannotAffectVerdict() throws Exception {
        String key = "test:rl:server-clock";
        // rate=1 token/s, capacity=2
        LettuceRateLimiter limiter = limiter(key, 1, 2);

        assertTrue(limiter.tryAcquireAsync(1).toCompletableFuture().get(5, TimeUnit.SECONDS).isAllowed());
        assertTrue(limiter.tryAcquireAsync(1).toCompletableFuture().get(5, TimeUnit.SECONDS).isAllowed());
        assertFalse(limiter.tryAcquireAsync(1).toCompletableFuture().get(5, TimeUnit.SECONDS).isAllowed(),
                "capacity exhausted without elapsed server time must be denied");

        // 无 sleep：将 timestamp_key 预置为服务器钟 10s 前（真 TIME 路径的确定性缝）
        // Redis TIME 返回 [秒, 微秒] 列表（秒针取 [0]）
        @SuppressWarnings("unchecked")
        java.util.List<Object> serverTime = (java.util.List<Object>) provider.getSyncCommands().time();
        long serverNow = Long.parseLong(String.valueOf(serverTime.get(0)));
        String tsKey = "{" + key + "}:timestamp";
        provider.getSyncCommands().set(tsKey, String.valueOf(serverNow - 10));

        RateLimitResult result = limiter.tryAcquireAsync(1).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(result.isAllowed(),
                "server-clock based refill must restore tokens after seeded elapsed time");
        assertTrue(result.getRemainingTokens() >= 0);
    }

    @Test
    void testLegacyClientEpochSecondKey_mixingBoundedAndAllowed() throws Exception {
        String key = "test:rl:legacy-key";
        LettuceRateLimiter limiter = limiter(key, 1, 5);

        // 旧键混存腿：预置客户端 epoch 秒旧值（与服务器钟同域同单位，可能带偏斜）
        long clientEpochSec = System.currentTimeMillis() / 1000 - 10;
        provider.getSyncCommands().set("{" + key + "}:timestamp", String.valueOf(clientEpochSec));

        RateLimitResult result = limiter.tryAcquireAsync(1).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(result.isAllowed(),
                "legacy client epoch-second timestamp key must be consumed safely "
                        + "(delta=max(0,..) / min(capacity,..) clamps bound client skew)");
    }
}
