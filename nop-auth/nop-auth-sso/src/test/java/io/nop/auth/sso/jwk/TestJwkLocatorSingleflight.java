package io.nop.auth.sso.jwk;

import io.nop.auth.sso.SsoConfig;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C4-008（plan 2551 裁定 F）钉：JWK 定位器单飞 + grace。
 * pin-a：并发 kid 未命中共享同一次 IdP 拉取（fetch 计数=1）；
 * pin-b：拉取失败进入短退避窗（A1/plan 2573 修订原裁定 F④「失败后立即可重试」——该语义在
 *        JWKS 故障期间退化为逐请求拉取放大）：窗内不重试；有旧钥 grace 返回、无旧钥返回 null；
 * pin-c：fetch 带显式 timeout（键值断言，缺省 5s）；
 * pin-d（A1）：ManualClock 推进验证退避窗外可重试（不占满 60s 速率窗）+ 成功后恢复
 *        速率窗语义并清零退避。
 */
public class TestJwkLocatorSingleflight {

    /** 测试局部类型化异常（规避 no-raw-Exception lint） */
    static final class IdpDownException extends RuntimeException {
        IdpDownException() {
            super("idp down");
        }
    }

    static class FakeHttpClient implements IHttpClient {
        final AtomicInteger fetches = new AtomicInteger();
        volatile boolean fail = true;
        final CountDownLatch firstArrived = new CountDownLatch(1);
        volatile long seenTimeout = -1;

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest req, ICancelToken ct) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IHttpResponse fetch(HttpRequest req, ICancelToken ct) {
            fetches.incrementAndGet();
            seenTimeout = req.getTimeout();
            firstArrived.countDown();
            if (fail) {
                throw new IdpDownException();
            }
            // 极简 JSON：JWKS 格式（真实解析走 getBodyAsBean）——直接返回合法空 jwks
            return fakeResponse("{\"keys\":[]}");
        }

        @Override
        public CompletionStage<IHttpResponse> fetchStreamAsync(HttpRequest req,
                io.nop.http.api.client.IServerEventAggregator a, ICancelToken ct) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest req,
                io.nop.http.api.client.IHttpOutputFile t, io.nop.http.api.client.DownloadOptions o, ICancelToken ct) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest req,
                io.nop.http.api.client.IHttpInputFile f, io.nop.http.api.client.UploadOptions o, ICancelToken ct) {
            throw new UnsupportedOperationException();
        }

        private IHttpResponse fakeResponse(String body) {
            return new IHttpResponse() {
                @Override
                public int getHttpStatus() {
                    return 200;
                }

                @Override
                public Map<String, String> getHeaders() {
                    return Map.of();
                }

                @Override
                public String getBody() {
                    return body;
                }

                @Override
                public <T> T getBodyAsBean(Class<T> cl) {
                    io.nop.core.lang.json.JsonTool.parseBeanFromText(body, JSONWebKeySet.class);
                    return (T) io.nop.core.lang.json.JsonTool.parseBeanFromText(body, JSONWebKeySet.class);
                }

                @Override
                public String getBodyAsString() {
                    return body;
                }

                @Override
                public byte[] getBodyAsBytes() {
                    return body.getBytes();
                }

                @Override
                public String getContentType() {
                    return "application/json";
                }

                @Override
                public String getCharset() {
                    return "UTF-8";
                }
            };
        }
    }

    private static JWKPublicKeyLocator newLocator(FakeHttpClient client, SsoConfig config) throws Exception {
        JWKPublicKeyLocator locator = new JWKPublicKeyLocator();
        locator.setHttpClient(client);
        locator.setConfig(config);
        return locator;
    }

    private static SsoConfig newConfig() {
        SsoConfig config = new SsoConfig();
        config.setClientId("test-client");
        config.setRealm("test-realm");
        config.setAuthServerUrl("http://idp.test");
        config.setJwksUrl("/protocol/openid-connect/certs");
        config.setMinTimeBetweenJwksRequests(60);
        config.setPublicKeyCacheTtl(300);
        return config;
    }

    @Test
    public void pin_a_concurrentMissSharesSingleFetch() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        client.fail = false;
        JWKPublicKeyLocator locator = newLocator(client, newConfig());

        int racers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < racers; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    locator.getPublicKey("test-kid"); // 未命中（空 jwks 无此 kid）→ 触发拉取
                } catch (Throwable t) {
                    failure.set(t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        if (failure.get() != null) {
            throw new AssertionError("concurrent getPublicKey failed", failure.get());
        }
        // 单飞：并发未命中共享同一次拉取（fetch 计数 = 1；原形态 synchronized 串行逐次拉取 = N）
        assertEquals(1, client.fetches.get(), "concurrent misses must share one in-flight fetch");
    }

    @Test
    public void pin_b_failureBackoffSuppressesRefetchAndGracesStaleKey() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        client.fail = true;
        JWKPublicKeyLocator locator = newLocator(client, newConfig());

        // 无旧钥：返回 null（不抛——grace 语义下 kid 缺失即 null）
        assertNull(locator.getPublicKey("missing-kid"));
        assertEquals(1, client.fetches.get());

        // A1（plan 2573，修订原裁定 F④「失败后立即可重试」）：失败进入短退避窗
        // （minTimeBetweenRequests/10 = 60s/10 = 6s，下限 1s）——窗内立即重试不得再发起拉取。
        // 修复前（缺陷基线）：此处 fetch 计数增长 = JWKS 故障期间逐请求拉取放大面
        locator.getPublicKey("missing-kid");
        assertEquals(1, client.fetches.get(),
                "failure backoff window must suppress an immediate refetch (A1)");

        // 有旧钥：grace 返回（先成功一次注入旧钥，再失败）
        FakeHttpClient goodClient = new FakeHttpClient();
        JWKPublicKeyLocator locator2 = newLocator(goodClient, newConfig());
        Field keysField = JWKPublicKeyLocator.class.getDeclaredField("currentKeys");
        keysField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<java.lang.String, PublicKey> keys =
                (ConcurrentHashMap<java.lang.String, PublicKey>) keysField.get(locator2);
        java.security.PublicKey fakeKey = new java.security.PublicKey() {
            @Override
            public String getAlgorithm() {
                return "RSA";
            }

            @Override
            public String getFormat() {
                return "X.509";
            }

            @Override
            public byte[] getEncoded() {
                return new byte[0];
            }
        };
        keys.put("known-kid", fakeKey);

        goodClient.fail = true;
        PublicKey served = locator2.getPublicKey("known-kid");
        assertNotNull(served, "stale key must be served under failure grace");
    }

    @Test
    public void pin_c_fetchCarriesTimeout() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        client.fail = true;
        JWKPublicKeyLocator locator = newLocator(client, newConfig());
        locator.getPublicKey("kid-timeout");
        assertEquals(JWKPublicKeyLocator.DEFAULT_FETCH_TIMEOUT_MILLIS, client.seenTimeout,
                "fetch must carry the explicit jwks fetch timeout");
    }

    /**
     * A1（plan 2573）完整钉：失败退避窗语义——窗内抑制重试、窗外可重试（不占满 60s 速率窗）、
     * 成功后清零退避并恢复成功速率窗（60s 内不重拉）。
     */
    @Test
    public void pin_d_failureBackoffWindowExpiryAndRecovery_A1() throws Exception {
        ManualClock clock = ManualClock.startingNow();
        CoreMetrics.registerClock(clock);
        try {
            FakeHttpClient client = new FakeHttpClient();
            client.fail = true;
            JWKPublicKeyLocator locator = newLocator(client, newConfig());
            // minTimeBetweenJwksRequests=60 → 退避窗 = max(1s, 6s) = 6s

            // T0：拉取 #1 失败（退避锚点 = T0）
            assertNull(locator.getPublicKey("missing-kid"));
            assertEquals(1, client.fetches.get());

            // 退避窗内（T0+5s）：不重试
            clock.advance(5_000);
            locator.getPublicKey("missing-kid");
            assertEquals(1, client.fetches.get(), "within backoff window refetch must be suppressed");

            // 退避窗外（T0+6.001s，仍远小于 60s 速率窗）：可重试——失败不占满速率窗
            clock.advance(1_001);
            client.fail = false; // IdP 恢复
            assertNull(locator.getPublicKey("missing-kid"));
            assertEquals(2, client.fetches.get(),
                    "past the backoff window a retry must be allowed without waiting the full rate window");

            // 成功后：清零退避 + 恢复 60s 速率窗——窗内未知 kid 不再拉取
            locator.getPublicKey("another-kid");
            assertEquals(2, client.fetches.get(),
                    "post-success rate window (60s) must suppress refetch");

            // 越过 60s 速率窗后可再次拉取
            clock.advance(60_001);
            client.fail = false;
            assertNull(locator.getPublicKey("another-kid"));
            assertEquals(3, client.fetches.get(),
                    "past the success rate window a new fetch must be allowed");
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }
    /**
     * X-1（plan 2573 收口审计追加，Phase 1 范围扩展）：成功速率窗不得被旧 future 滑动。
     *
     * <p>缺陷形态（修复前红）：{@code !allowed} 分支仍把 fetch 指向上次**已完成**的成功拉取，
     * 等待段以其成功结果把 lastRequestTime 刷新到本次请求时刻——未知 kid 请求到达间隔 < 60s
     * 时窗口无限滑动，JWKS 永不重拉（IdP 密钥轮换后新 kid 持续到达 = 认证自锁死；
     * lookupCachedKey TTL 同源滑动，旧钥永不过期）。
     *
     * <p>修复后：窗内且无在飞拉取 → 不消费旧 future、不滑动窗口；越过距**上次真实拉取**
     * 60s 后必然重拉。
     */
    @Test
    public void pin_e_successWindowNotSlidByStaleFuture_KeyRotationRefetch_X1() throws Exception {
        ManualClock clock = ManualClock.startingNow();
        CoreMetrics.registerClock(clock);
        try {
            FakeHttpClient client = new FakeHttpClient();
            client.fail = false; // 拉取成功（空 jwks——未知 kid 仍触发拉取路径）
            JWKPublicKeyLocator locator = newLocator(client, newConfig());

            // T0：拉取 #1 成功（空钥表）→ lastRequestTime = T0
            assertNull(locator.getPublicKey("rotated-kid"));
            assertEquals(1, client.fetches.get());

            // T0+30s（成功速率窗内，无在飞）：不重拉、且不得把窗口基准滑动到本请求时刻
            clock.advance(30_000);
            assertNull(locator.getPublicKey("rotated-kid"));
            assertEquals(1, client.fetches.get(), "in-window call must not refetch nor slide the rate window");

            // T0+61.001s（距上次真实拉取 > 60s）：必须重拉——修复前窗口已被上一步滑动到
            // T0+30s，此处仍在窗内（fetches 停在 1 = 密钥轮换自锁死形态）
            clock.advance(31_001);
            assertNull(locator.getPublicKey("rotated-kid"));
            assertEquals(2, client.fetches.get(),
                    "past 60s from the last REAL fetch a refetch must happen (key rotation must not self-lock)");
        } finally {
            CoreMetrics.registerClock(CoreMetrics.defaultClock());
        }
    }
}
