/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce;

import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.DigestedText;
import io.nop.nosql.core.INosqlHashOperations;
import io.nop.nosql.core.INosqlSessionStore;
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-08 写-TTL 原子化载体（plan 2554 裁定 B）：真 Redis 上四个写入口（hash putEx /
 * putIfAbsentEx / getAndSetEx + session setAsync）操作后立即断言 PTTL &gt; 0——
 * 缺陷态两步 hset→pexpire 存在 TTL-less 窗口（且任一腿失败即永不过期），原子脚本后
 * 写完即带 TTL。另含 Lua 脚本装配结构钉（RedisScripts digest + .lua classpath 资源）。
 * 携带历史 D03-02 收口义务（session 入口原子化）。
 */
@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
public class TestLettuceHashTtlAtomic {

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

    private long pttlOf(String key) throws Exception {
        Long pttl = service.getTimeoutAsync(key).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(pttl);
        return pttl;
    }

    @Test
    void testHashPutEx_TtlAppliedAtomically() throws Exception {
        String key = "test:hash:putex";
        INosqlHashOperations hashOps = service.hashOps(key);

        hashOps.putExAsync("field", "value", 60000).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals("value", String.valueOf(hashOps.get("field")));
        // the TTL must be visible immediately after the write, not only after a second round trip
        assertTrue(pttlOf(key) > 0, "putEx must leave the hash key with a TTL right after the write");
    }

    @Test
    void testHashPutIfAbsentEx_TtlAppliedAtomicallyAndSemanticsPreserved() throws Exception {
        String key = "test:hash:putnxex";
        INosqlHashOperations hashOps = service.hashOps(key);

        // absent -> set, return true, TTL applied
        assertTrue(hashOps.putIfAbsentExAsync("field", "v1", 60000)
                .toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals("v1", String.valueOf(hashOps.get("field")));
        assertTrue(pttlOf(key) > 0, "putIfAbsentEx must leave the hash key with a TTL right after the write");

        // present -> return false, value and TTL untouched
        assertFalse(hashOps.putIfAbsentExAsync("field", "v2", 60000)
                .toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals("v1", String.valueOf(hashOps.get("field")));
    }

    @Test
    void testHashGetAndSetEx_ReturnsOldValueAndTtlAppliedAtomically() throws Exception {
        String key = "test:hash:gasetex";
        INosqlHashOperations hashOps = service.hashOps(key);
        hashOps.put("field", "old");

        Object old = hashOps.getAndSetExAsync("field", "new", 60000)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals("old", String.valueOf(old));
        assertEquals("new", String.valueOf(hashOps.get("field")));
        assertTrue(pttlOf(key) > 0, "getAndSetEx must leave the hash key with a TTL right after the write");
    }

    @Test
    void testHashGetAndSetEx_AbsentFieldReturnsNull() throws Exception {
        String key = "test:hash:gasetex-absent";
        INosqlHashOperations hashOps = service.hashOps(key);

        assertNull(hashOps.getAndSetExAsync("field", "new", 60000)
                .toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals("new", String.valueOf(hashOps.get("field")));
        assertTrue(pttlOf(key) > 0);
    }

    @Test
    void testSessionSet_TtlAppliedAtomically() throws Exception {
        // D03-02 closure face: the session entry must never appear as a TTL-less hash
        String prefix = "test:sess:atomic";
        INosqlSessionStore store = service.sessionStore(prefix);

        Map<String, Object> data = new HashMap<>();
        data.put("user", "alice");
        data.put("role", "admin");
        store.setAsync("s1", data, 60000).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(store.exists("s1"));
        assertEquals("alice", String.valueOf(store.getField("s1", "user")));
        assertEquals("admin", String.valueOf(store.getField("s1", "role")));
        assertTrue(pttlOf(prefix + ":s1") > 0, "setAsync must leave the session key with a TTL right after the write");
    }

    @Test
    void testSessionSet_ZeroTtlDoesNotExpire() throws Exception {
        String prefix = "test:sess:atomic-ttl0";
        INosqlSessionStore store = service.sessionStore(prefix);

        Map<String, Object> data = new HashMap<>();
        data.put("user", "bob");
        store.set("s1", data, 0);

        assertTrue(store.exists("s1"));
        Long pttl = service.getTimeoutAsync(prefix + ":s1").toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(-1L, pttl, "ttlMs=0 must keep the session key persistent");
    }

    // ===== structural pins: Lua script assembly =====

    private static String scriptText(String path) {
        DigestedText script = RedisScripts.loadScript(path);
        assertNotNull(script);
        return script.getText();
    }

    @Test
    void testRedisScriptsContainAtomicDigests() {
        assertNotNull(RedisScripts.HASH_PUT_EX.getDigestString());
        assertNotNull(RedisScripts.HASH_PUT_IF_ABSENT_EX.getDigestString());
        assertNotNull(RedisScripts.HASH_GETSET_EX.getDigestString());
        assertNotNull(RedisScripts.SESSION_SET.getDigestString());
    }

    @Test
    void testAtomicLuaResourcesExistAndCarryTtlLeg() {
        String putEx = scriptText("classpath:/nop/redis/hash_put_ex.lua");
        assertTrue(putEx.contains("hset") && putEx.contains("pexpire"),
                "hash_put_ex.lua must carry both hset and pexpire legs");

        String putNxEx = scriptText("classpath:/nop/redis/hash_put_if_absent_ex.lua");
        assertTrue(putNxEx.contains("hsetnx") && putNxEx.contains("pexpire"),
                "hash_put_if_absent_ex.lua must carry both hsetnx and pexpire legs");

        String getSetEx = scriptText("classpath:/nop/redis/hash_getset_ex.lua");
        assertTrue(getSetEx.contains("hget") && getSetEx.contains("hset") && getSetEx.contains("pexpire"),
                "hash_getset_ex.lua must carry hget, hset and pexpire legs");

        String sessionSet = scriptText("classpath:/nop/redis/session_set.lua");
        assertTrue(sessionSet.contains("hset") && sessionSet.contains("pexpire"),
                "session_set.lua must carry both hset and pexpire legs");
    }

    @Test
    void testComputeIfAbsentScriptsOnClasspath() {
        String kvScript = scriptText("classpath:/nop/redis/kv_compute_if_absent.lua");
        assertTrue(kvScript.contains("nx"), "kv_compute_if_absent.lua must use SET NX semantics");
        assertTrue(kvScript.contains("get"));

        String hashScript = scriptText("classpath:/nop/redis/hash_compute_if_absent.lua");
        assertTrue(hashScript.contains("hsetnx"));
        assertTrue(scriptText("classpath:/nop/redis/hash_get_set.lua").contains("hget"));
        assertFalse(StringHelper.isEmpty(kvScript));
    }
}
