/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce;

import io.nop.nosql.core.INosqlCounter;
import io.nop.nosql.core.INosqlHashOperations;
import io.nop.nosql.core.INosqlListOperations;
import io.nop.nosql.core.INosqlLock;
import io.nop.nosql.core.INosqlQueue;
import io.nop.nosql.core.INosqlRanking;
import io.nop.nosql.core.INosqlRateLimiter;
import io.nop.nosql.core.INosqlSessionStore;
import io.nop.nosql.core.INosqlSetOperations;
import io.nop.nosql.core.INosqlZSetOperations;
import io.nop.nosql.core.RankingEntry;
import io.nop.nosql.core.RateLimitResult;
import io.nop.nosql.core.RateLimiterConfig;
import io.nop.nosql.core.config.RedisConfig;
import io.nop.nosql.lettuce.impl.LettuceMessageService;
import io.nop.nosql.lettuce.impl.LettuceRedisConnectionProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
public class TestLettuceNosqlService {

    @Container
    public GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceMessageService service;
    private LettuceRedisConnectionProvider provider;

    @BeforeEach
    void setUp() {
        String host = redis.getHost();
        int port = redis.getMappedPort(6379);

        RedisConfig config = new RedisConfig();
        config.setHost(host);
        config.setPort(port);

        provider = new LettuceRedisConnectionProvider();
        provider.setConfig(config);
        provider.start();

        service = new LettuceMessageService(provider);
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.clear();
        }
        if (provider != null) {
            provider.stop();
        }
    }

    // ===== Lock Tests =====

    @Test
    void testLock_AcquireAndRelease() {
        INosqlLock lock = service.lock("test:lock:basic");

        assertTrue(lock.tryLock(5000));
        assertTrue(lock.isHeld());

        lock.unlock();
        assertFalse(lock.isHeld());
    }

    @Test
    void testLock_TimeoutExpires() throws InterruptedException {
        INosqlLock lock1 = service.lock("test:lock:timeout");

        assertTrue(lock1.tryLock(100));
        assertTrue(lock1.isHeld());

        Thread.sleep(150);

        INosqlLock lock2 = service.lock("test:lock:timeout");
        assertTrue(lock2.tryLock(5000));
        assertTrue(lock2.isHeld());
    }

    @Test
    void testLock_CASPreventMisunlock() {
        INosqlLock lock1 = service.lock("test:lock:cas");
        INosqlLock lock2 = service.lock("test:lock:cas");

        assertTrue(lock1.tryLock(5000));
        assertTrue(lock1.isHeld());

        lock2.unlock();
        assertTrue(lock1.isHeld());

        // Verify lock is still held in Redis
        INosqlLock lock3 = service.lock("test:lock:cas");
        assertFalse(lock3.tryLock(100));
    }

    // ===== Counter Tests =====

    @Test
    void testCounter_IncrementAndGet() {
        INosqlCounter counter = service.counter("test:counter:incr");

        counter.increment(5);
        assertEquals(5, counter.get());

        counter.increment(3);
        assertEquals(8, counter.get());
    }

    @Test
    void testCounter_GetAndReset() {
        INosqlCounter counter = service.counter("test:counter:reset");

        counter.increment(10);
        long oldValue = counter.getAndReset();
        assertEquals(10, oldValue);
        assertEquals(0, counter.get());
    }

    @Test
    void testCounter_Decrement() {
        INosqlCounter counter = service.counter("test:counter:decr");

        counter.increment(10);
        counter.increment(-3);
        assertEquals(7, counter.get());
    }

    // ===== Queue Tests =====

    @Test
    void testQueue_EnqueueAndDequeue() {
        INosqlQueue queue = service.queue("test:queue:fifo");

        queue.enqueue("a");
        queue.enqueue("b");
        queue.enqueue("c");

        assertEquals("a", String.valueOf(queue.dequeue()));
        assertEquals("b", String.valueOf(queue.dequeue()));
        assertEquals("c", String.valueOf(queue.dequeue()));
    }

    @Test
    void testQueue_EmptyDequeue() {
        INosqlQueue queue = service.queue("test:queue:empty");
        assertNull(queue.dequeue());
    }

    @Test
    void testQueue_Peek() {
        INosqlQueue queue = service.queue("test:queue:peek");

        queue.enqueue("x");
        queue.enqueue("y");

        assertEquals("x", String.valueOf(queue.peek()));
        assertEquals(2, queue.size());
    }

    @Test
    void testQueue_Batch() {
        INosqlQueue queue = service.queue("test:queue:batch");

        queue.enqueueBatch(Arrays.asList("d", "e", "f"));

        List<Object> batch = queue.dequeueBatch(2);
        assertEquals(2, batch.size());
        assertEquals("d", String.valueOf(batch.get(0)));
        assertEquals("e", String.valueOf(batch.get(1)));

        assertEquals("f", String.valueOf(queue.dequeue()));
    }

    @Test
    void testQueue_Size() {
        INosqlQueue queue = service.queue("test:queue:size");

        queue.enqueue("a");
        queue.enqueue("b");
        queue.enqueue("c");

        assertEquals(3, queue.size());
    }

    // ===== RateLimiter Tests =====

    @Test
    void testRateLimiter_AllowWhenTokensAvailable() {
        RateLimiterConfig config = new RateLimiterConfig(1, 10);
        INosqlRateLimiter limiter = service.rateLimiter("test:rl:allow", config);

        RateLimitResult result = limiter.tryAcquire(5);
        assertTrue(result.isAllowed());
        assertEquals(5, result.getRemainingTokens());
    }

    @Test
    void testRateLimiter_RejectWhenEmpty() {
        RateLimiterConfig config = new RateLimiterConfig(1, 2);
        INosqlRateLimiter limiter = service.rateLimiter("test:rl:reject", config);

        RateLimitResult r1 = limiter.tryAcquire(2);
        assertTrue(r1.isAllowed());

        RateLimitResult r2 = limiter.tryAcquire(1);
        assertFalse(r2.isAllowed());
    }

    @Test
    void testRateLimiter_GetAvailableTokens() {
        RateLimiterConfig config = new RateLimiterConfig(1, 10);
        INosqlRateLimiter limiter = service.rateLimiter("test:rl:tokens", config);

        assertEquals(10, limiter.getAvailableTokens());

        limiter.tryAcquire(3);
        assertEquals(7, limiter.getAvailableTokens());
    }

    // ===== Ranking Tests =====

    @Test
    void testRanking_AddAndGetScore() {
        INosqlRanking ranking = service.ranking("test:ranking:score");

        ranking.add("player1", 100);
        ranking.add("player2", 200);

        assertEquals(100.0, ranking.getScore("player1"), 0.001);
        assertEquals(200.0, ranking.getScore("player2"), 0.001);
    }

    @Test
    void testRanking_GetRank() {
        INosqlRanking ranking = service.ranking("test:ranking:rank");

        ranking.add("player1", 100);
        ranking.add("player2", 200);
        ranking.add("player3", 150);

        // ZREVRANK: highest score = rank 0
        assertEquals(0, ranking.getRank("player2")); // 200
        assertEquals(1, ranking.getRank("player3")); // 150
        assertEquals(2, ranking.getRank("player1")); // 100
    }

    @Test
    void testRanking_GetTopN() {
        INosqlRanking ranking = service.ranking("test:ranking:topn");

        ranking.add("p1", 100);
        ranking.add("p2", 300);
        ranking.add("p3", 200);
        ranking.add("p4", 500);
        ranking.add("p5", 400);

        List<RankingEntry> top3 = ranking.getTopN(3);
        assertEquals(3, top3.size());

        // Descending: p4(500), p5(400), p2(300)
        assertEquals("p4", top3.get(0).getMember());
        assertEquals(500.0, top3.get(0).getScore(), 0.001);
        assertEquals(0, top3.get(0).getRank());

        assertEquals("p5", top3.get(1).getMember());
        assertEquals(400.0, top3.get(1).getScore(), 0.001);
        assertEquals(1, top3.get(1).getRank());

        assertEquals("p2", top3.get(2).getMember());
        assertEquals(300.0, top3.get(2).getScore(), 0.001);
        assertEquals(2, top3.get(2).getRank());
    }

    @Test
    void testRanking_IncrementScore() {
        INosqlRanking ranking = service.ranking("test:ranking:incr");

        ranking.add("player1", 100);

        double newScore = ranking.incrementScore("player1", 50);
        assertEquals(150.0, newScore, 0.001);

        assertEquals(150.0, ranking.getScore("player1"), 0.001);
    }

    @Test
    void testRanking_Remove() {
        INosqlRanking ranking = service.ranking("test:ranking:remove");

        ranking.add("player1", 100);
        assertTrue(ranking.remove("player1"));
        assertEquals(-1, ranking.getRank("player1"));
    }

    @Test
    void testRanking_GetAround() {
        INosqlRanking ranking = service.ranking("test:ranking:around");

        ranking.add("p1", 100);
        ranking.add("p2", 200);
        ranking.add("p3", 300);
        ranking.add("p4", 400);
        ranking.add("p5", 500);

        // Descending order: p5(500)=0, p4(400)=1, p3(300)=2, p2(200)=3, p1(100)=4
        // getAround("p3", 1): rank=2, start=max(0,1)=1, end=3
        List<RankingEntry> around = ranking.getAround("p3", 1);
        assertEquals(3, around.size());

        assertEquals("p4", around.get(0).getMember());
        assertEquals(400.0, around.get(0).getScore(), 0.001);
        assertEquals(1, around.get(0).getRank());

        assertEquals("p3", around.get(1).getMember());
        assertEquals(300.0, around.get(1).getScore(), 0.001);
        assertEquals(2, around.get(1).getRank());

        assertEquals("p2", around.get(2).getMember());
        assertEquals(200.0, around.get(2).getScore(), 0.001);
        assertEquals(3, around.get(2).getRank());
    }

    // ===== SessionStore Tests =====

    @Test
    void testSessionStore_SetAndGet() {
        INosqlSessionStore store = service.sessionStore("test:sess:basic");

        Map<String, Object> data = new HashMap<>();
        data.put("user", "alice");
        data.put("role", "admin");

        store.set("s1", data, 5000);

        Map<String, Object> result = store.get("s1");
        assertEquals(2, result.size());
        assertEquals("alice", String.valueOf(result.get("user")));
        assertEquals("admin", String.valueOf(result.get("role")));
    }

    @Test
    void testSessionStore_GetField() {
        INosqlSessionStore store = service.sessionStore("test:sess:field");

        Map<String, Object> data = new HashMap<>();
        data.put("user", "bob");
        data.put("role", "user");
        store.set("s1", data, 5000);

        assertEquals("bob", String.valueOf(store.getField("s1", "user")));
        assertEquals("user", String.valueOf(store.getField("s1", "role")));
    }

    @Test
    void testSessionStore_SetField() {
        INosqlSessionStore store = service.sessionStore("test:sess:setfield");

        Map<String, Object> data = new HashMap<>();
        data.put("user", "alice");
        store.set("s1", data, 5000);

        store.setField("s1", "email", "alice@example.com");

        assertEquals("alice@example.com", String.valueOf(store.getField("s1", "email")));
        assertEquals("alice", String.valueOf(store.getField("s1", "user")));
    }

    @Test
    void testSessionStore_TouchRefreshesTTL() throws InterruptedException {
        INosqlSessionStore store = service.sessionStore("test:sess:touch");

        Map<String, Object> data = new HashMap<>();
        data.put("user", "alice");

        store.set("s1", data, 200);

        Thread.sleep(100);
        assertTrue(store.touch("s1", 5000));

        Thread.sleep(150);
        assertTrue(store.exists("s1"));
    }

    @Test
    void testSessionStore_Remove() {
        INosqlSessionStore store = service.sessionStore("test:sess:remove");

        Map<String, Object> data = new HashMap<>();
        data.put("user", "bob");
        store.set("s1", data, 5000);

        assertTrue(store.exists("s1"));
        store.remove("s1");
        assertFalse(store.exists("s1"));
    }

    @Test
    void testSessionStore_Exists() {
        INosqlSessionStore store = service.sessionStore("test:sess:exists");

        assertFalse(store.exists("nonexistent"));

        Map<String, Object> data = new HashMap<>();
        data.put("user", "charlie");
        store.set("s1", data, 5000);

        assertTrue(store.exists("s1"));
    }

    // ===== Primitive Tests =====

    @Test
    void testHashOps_PutAndGet() {
        INosqlHashOperations hashOps = service.hashOps("test:hash:basic");

        hashOps.put("field", "value");
        assertEquals("value", String.valueOf(hashOps.get("field")));
    }

    @Test
    void testListOps_AddAndPop() {
        INosqlListOperations listOps = service.listOps("test:list:basic");

        listOps.addAsync("a").join();
        Object popped = listOps.leftPopAsync().join();
        assertNotNull(popped);
        assertEquals("a", String.valueOf(popped));
    }

    @Test
    void testSetOps_AddAndMembers() {
        INosqlSetOperations setOps = service.setOps("test:set:basic");

        setOps.add("x");
        Set<Object> members = setOps.members();
        assertEquals(1, members.size());
        assertTrue(members.contains("x"));
    }

    @Test
    void testZSetOps_AddAndScore() {
        INosqlZSetOperations zsetOps = service.zSetOps("test:zset:basic");

        assertTrue(zsetOps.add("m1", 1.0));
        assertEquals(1.0, zsetOps.score("m1"), 0.001);
    }

    // ===== Edge Case Tests =====

    @Test
    void testCounterGetNonExistent() {
        INosqlCounter counter = service.counter("test:counter:missing");
        assertEquals(0L, counter.get());
    }

    @Test
    void testQueueDequeueBatchPartial() {
        INosqlQueue queue = service.queue("test:queue:partial");
        queue.enqueue("item1");
        List<Object> items = queue.dequeueBatch(10);
        assertEquals(1, items.size());
        assertEquals("item1", items.get(0));
        // Second batch should be empty
        List<Object> empty = queue.dequeueBatch(10);
        assertTrue(empty.isEmpty());
    }

    @Test
    void testRankingGetRankMissing() {
        INosqlRanking ranking = service.ranking("test:ranking:missing");
        assertEquals(-1L, ranking.getRank("nonexistent"));
    }

    @Test
    void testRankingGetScoreMissing() {
        INosqlRanking ranking = service.ranking("test:ranking:scoremissing");
        assertEquals(0.0, ranking.getScore("nonexistent"), 0.001);
    }

    @Test
    void testRankingGetTopNEmpty() {
        INosqlRanking ranking = service.ranking("test:ranking:empty");
        List<RankingEntry> top = ranking.getTopN(10);
        assertTrue(top.isEmpty());
    }

    @Test
    void testLockDoubleUnlock() {
        INosqlLock lock = service.lock("test:lock:double");
        assertTrue(lock.tryLock(5000));
        lock.unlock();
        // Second unlock should not throw
        lock.unlock();
        assertFalse(lock.isHeld());
    }

    @Test
    void testSessionStoreGetMissing() {
        INosqlSessionStore store = service.sessionStore("test:session:missing:");
        Map<String, Object> data = store.get("nonexistent");
        assertTrue(data == null || data.isEmpty());
    }
}
