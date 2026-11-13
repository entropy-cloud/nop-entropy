package io.nop.auth.sso.jwk;

import io.nop.auth.sso.SsoConfig;
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
 * pin-b：拉取失败不占速率窗（lastRequestTime 不推进）且有旧钥 grace 返回、无旧钥返回 null；
 * pin-c：fetch 带显式 timeout（键值断言，缺省 5s）。
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
    public void pin_b_failureKeepsRateWindowAndGracesStaleKey() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        client.fail = true;
        JWKPublicKeyLocator locator = newLocator(client, newConfig());

        // 无旧钥：返回 null（不抛——grace 语义下 kid 缺失即 null）
        assertNull(locator.getPublicKey("missing-kid"));
        int fetchesAfterFirst = client.fetches.get();

        // 失败不占速率窗：立即可重试（fetch 计数增长）
        locator.getPublicKey("missing-kid");
        assertTrue(client.fetches.get() > fetchesAfterFirst,
                "failed fetch must not consume the rate window (immediate retry allowed)");

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
}
