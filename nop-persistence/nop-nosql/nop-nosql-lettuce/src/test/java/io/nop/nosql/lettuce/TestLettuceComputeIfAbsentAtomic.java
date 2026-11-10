/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce;

import io.nop.nosql.core.INosqlHashOperations;
import io.nop.nosql.core.config.RedisConfig;
import io.nop.nosql.core.script.RedisScripts;
import io.nop.nosql.lettuce.impl.LettuceMessageService;
import io.nop.nosql.lettuce.impl.LettuceRedisConnectionProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * MA-09 computeIfAbsent 原子化载体（plan 2554 裁定 C）：真 Redis 上 N 线程并发同 key
 * computeIfAbsent——ICache「仅有一个结果生效」契约（最终存储值 = 恰好一个 loader 的计算值，
 * 后续 get 与之一致）。20 轮循环放大竞态窗口。另含结构钉：KV 脚本文本含 SET NX 语义。
 * 缺陷态（GET→loader→无条件 SET）下后写覆盖先写，最终值可混入多个 loader 的组合。
 */
@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
public class TestLettuceComputeIfAbsentAtomic {

    private static final int THREADS = 8;
    private static final int ROUNDS = 20;

    @Container
    public GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceMessageService service;
    private LettuceRedisConnectionProvider provider;

    @BeforeEach
    void setUp() {
        RedisConfig config = new RedisConfig();
        config.setHost(redis.getHost());
        config.setPort(redis.getMappedPort(6379));

        provider = new LettuceRedisConnectionProvider();
        provider.setConfig(config);
        provider.start();

        service = new LettuceMessageService(provider);
    }

    @AfterEach
    void tearDown() {
        if (service != null)
            service.clear();
        if (provider != null)
            provider.stop();
    }

    /** 每线程计算唯一值，loader 计入本线程集合；返回计算值。 */
    private static String mappedValue(String key, int threadIdx, Set<String> computedBy) {
        String value = "v-" + threadIdx;
        computedBy.add(value);
        return value;
    }

    private void runConcurrent(int threads, Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            CyclicBarrier barrier = new CyclicBarrier(threads);
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    action.run();
                    return null;
                }));
            }
            for (Future<?> f : futures)
                f.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void testConcurrentComputeIfAbsent_OnlyOneResultWins() throws Exception {
        String key = "test:cia:kv";
        for (int round = 0; round < ROUNDS; round++) {
            service.remove(key);
            Set<String> computedBy = ConcurrentHashMap.newKeySet();

            runConcurrent(THREADS, () -> service.computeIfAbsent(key,
                    k -> mappedValue(k, (int) Thread.currentThread().getId(), computedBy)));

            Object stored = service.get(key);
            assertNotNull(stored, "round " + round + ": stored value missing");
            assertTrue(computedBy.contains(String.valueOf(stored)),
                    "round " + round + ": stored value '" + stored + "' must equal exactly one computed value, computed="
                            + computedBy);

            // subsequent reads are consistent with the stored value
            Object again = service.computeIfAbsent(key, k -> {
                fail("loader must not run again once the value exists");
                return null;
            });
            assertEquals(stored, again, "round " + round + ": subsequent get must be consistent");
        }
    }

    @Test
    void testConcurrentComputeIfAbsentAsync_OnlyOneResultWins() throws Exception {
        String key = "test:cia:kv-async";
        for (int round = 0; round < ROUNDS; round++) {
            service.remove(key);
            Set<String> computedBy = ConcurrentHashMap.newKeySet();

            runConcurrent(THREADS, () -> service.computeIfAbsentAsync(key,
                    k -> mappedValue(k, (int) Thread.currentThread().getId(), computedBy))
                    .toCompletableFuture().join());

            Object stored = service.get(key);
            assertNotNull(stored, "round " + round + ": stored value missing");
            assertTrue(computedBy.contains(String.valueOf(stored)),
                    "round " + round + ": stored value '" + stored + "' must equal exactly one computed value");
        }
    }

    @Test
    void testConcurrentHashComputeIfAbsent_OnlyOneResultWins() throws Exception {
        String key = "test:cia:hash";
        INosqlHashOperations hashOps = service.hashOps(key);
        for (int round = 0; round < ROUNDS; round++) {
            hashOps.remove("field");
            Set<String> computedBy = ConcurrentHashMap.newKeySet();

            runConcurrent(THREADS, () -> hashOps.computeIfAbsent("field",
                    f -> mappedValue(f, (int) Thread.currentThread().getId(), computedBy)));

            Object stored = hashOps.get("field");
            assertNotNull(stored, "round " + round + ": stored value missing");
            assertTrue(computedBy.contains(String.valueOf(stored)),
                    "round " + round + ": stored hash value '" + stored + "' must equal exactly one computed value");
        }
    }

    @Test
    void testConcurrentHashGetAndSet_FinalValueIsOneWrite() throws Exception {
        String key = "test:cia:hash-gaset";
        INosqlHashOperations hashOps = service.hashOps(key);
        Set<String> writtenBy = ConcurrentHashMap.newKeySet();

        runConcurrent(THREADS, () -> {
            String value = "w-" + Thread.currentThread().getId();
            writtenBy.add(value);
            hashOps.getAndSet("field", value);
        });

        Object stored = hashOps.get("field");
        assertNotNull(stored);
        assertTrue(writtenBy.contains(String.valueOf(stored)),
                "final hash value '" + stored + "' must equal exactly one writer's value, written=" + writtenBy);
    }

    // ===== structural pin: SET NX semantics in the KV write-back script =====

    @Test
    void testKvComputeIfAbsentScriptCarriesNxSemantics() {
        String script = RedisScripts.KV_COMPUTE_IF_ABSENT.getText();
        assertNotNull(script);
        assertTrue(script.contains("nx"), "kv_compute_if_absent.lua must use SET NX (only-one-writer) semantics");
        assertTrue(script.contains("get"), "kv_compute_if_absent.lua must return the existing value on conflict");
    }

    @Test
    void testHashComputeIfAbsentScriptCarriesHsetnxSemantics() {
        String script = RedisScripts.HASH_COMPUTE_IF_ABSENT.getText();
        assertNotNull(script);
        assertTrue(script.contains("hsetnx"), "hash_compute_if_absent.lua must use HSETNX semantics");
    }
}
