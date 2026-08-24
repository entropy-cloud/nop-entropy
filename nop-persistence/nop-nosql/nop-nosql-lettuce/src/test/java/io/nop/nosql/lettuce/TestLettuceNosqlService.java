/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
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

    // ===== forEachEntry Tests =====

    @Test
    void testForEachEntry_Basic() {
        service.put("k1", "v1");
        service.put("k2", "v2");
        service.put("k3", "v3");

        Map<String, Object> collected = new HashMap<>();
        service.forEachEntry(collected::put);

        assertEquals(3, collected.size());
        assertEquals("v1", String.valueOf(collected.get("k1")));
        assertEquals("v2", String.valueOf(collected.get("k2")));
        assertEquals("v3", String.valueOf(collected.get("k3")));
    }

    @Test
    void testForEachEntry_EmptyDatabase() {
        service.clear();
        Map<String, Object> collected = new HashMap<>();
        service.forEachEntry(collected::put);
        assertTrue(collected.isEmpty());
    }

    @Test
    void testForEachEntryAsync_Basic() throws Exception {
        service.put("a1", "x");
        service.put("a2", "y");
        service.put("a3", "z");

        Map<String, Object> collected = new HashMap<>();
        CompletionStage<Void> stage = service.forEachEntryAsync(collected::put);
        stage.toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(3, collected.size());
        assertEquals("x", String.valueOf(collected.get("a1")));
        assertEquals("y", String.valueOf(collected.get("a2")));
        assertEquals("z", String.valueOf(collected.get("a3")));
    }

    @Test
    void testForEachEntryAsync_EmptyDatabase() throws Exception {
        service.clear();
        Map<String, Object> collected = new HashMap<>();
        CompletionStage<Void> stage = service.forEachEntryAsync(collected::put);
        stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(collected.isEmpty());
    }

    @Test
    void testForEachEntry_LargeBatch() {
        int count = 50;
        for (int i = 0; i < count; i++) {
            service.put("batch:" + i, "val:" + i);
        }

        AtomicInteger counter = new AtomicInteger(0);
        service.forEachEntry((k, v) -> {
            if (k instanceof String && ((String) k).startsWith("batch:")) {
                counter.incrementAndGet();
            }
        });
        assertEquals(count, counter.get());
    }

    // ===== Pub/Sub Tests =====

    @Test
    void testPubSub_SendAndReceive() throws Exception {
        IMessageService msgService = service.getMessageService();
        assertNotNull(msgService);

        String topic = "test:pubsub:basic";
        CountDownLatch latch = new CountDownLatch(1);
        List<String> received = new ArrayList<>();

        IMessageSubscription sub = msgService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext ctx) {
                received.add(String.valueOf(msg));
                latch.countDown();
                return null;
            }
        });

        msgService.send(topic, "hello");

        boolean ok = latch.await(5, TimeUnit.SECONDS);
        assertTrue(ok, "Should receive message within timeout");
        assertEquals(1, received.size());
        assertEquals("hello", received.get(0));

        sub.cancel();
    }

    @Test
    void testPubSub_UnsubscribeStopsReceiving() throws Exception {
        IMessageService msgService = service.getMessageService();
        String topic = "test:pubsub:unsub";
        AtomicInteger counter = new AtomicInteger(0);

        IMessageSubscription sub = msgService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext ctx) {
                counter.incrementAndGet();
                return null;
            }
        });

        msgService.send(topic, "first");
        Thread.sleep(200);
        int afterFirst = counter.get();

        sub.cancel();

        msgService.send(topic, "second");
        Thread.sleep(200);
        int afterSecond = counter.get();

        assertEquals(afterFirst, afterSecond, "Should not receive after cancel");
        assertTrue(afterFirst >= 1, "Should receive at least one before cancel");
    }

    @Test
    void testPubSub_MultipleSubscribers() throws Exception {
        IMessageService msgService = service.getMessageService();
        String topic = "test:pubsub:multi";
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        IMessageSubscription sub1 = msgService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext ctx) {
                count1.incrementAndGet();
                return null;
            }
        });

        IMessageSubscription sub2 = msgService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext ctx) {
                count2.incrementAndGet();
                return null;
            }
        });

        msgService.send(topic, "msg");
        Thread.sleep(500);

        assertTrue(count1.get() >= 1, "Subscriber 1 should receive");
        assertTrue(count2.get() >= 1, "Subscriber 2 should receive");

        sub1.cancel();
        sub2.cancel();
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

    // ===== check2 audit regression tests =====

    @Test
    void testPubSub_NoCrossTopicDelivery() throws Exception {
        IMessageService msgService = service.getMessageService();
        String topicA = "test:pubsub:topic-a";
        String topicB = "test:pubsub:topic-b";
        CountDownLatch latchA = new CountDownLatch(1);
        List<String> receivedB = new ArrayList<>();

        IMessageSubscription subA = msgService.subscribe(topicA, new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object msg, IMessageConsumeContext ctx) {
                latchA.countDown();
                return null;
            }
        });
        IMessageSubscription subB = msgService.subscribe(topicB, new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object msg, IMessageConsumeContext ctx) {
                receivedB.add(String.valueOf(msg));
                return null;
            }
        });

        msgService.send(topicA, "hello-a");

        assertTrue(latchA.await(5, TimeUnit.SECONDS), "topic A subscriber should receive its own message");
        Thread.sleep(500);
        assertTrue(receivedB.isEmpty(),
                "topic B subscriber must not receive messages published to topic A, but got: " + receivedB);

        subA.cancel();
        subB.cancel();
    }

    @Test
    void testPubSub_CancelDoesNotAffectOtherSubscribers() throws Exception {
        IMessageService msgService = service.getMessageService();
        String topic = "test:pubsub:cancel-share";
        CountDownLatch latch2 = new CountDownLatch(1);

        IMessageSubscription sub1 = msgService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext ctx) {
                return null;
            }
        });
        IMessageSubscription sub2 = msgService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext ctx) {
                latch2.countDown();
                return null;
            }
        });

        sub1.cancel();
        assertFalse(sub2.isCancelled(), "cancelling one subscription must not mark the other as cancelled");

        msgService.send(topic, "msg");
        assertTrue(latch2.await(5, TimeUnit.SECONDS), "remaining subscriber should still receive messages");

        sub2.cancel();
    }

    @Test
    void testPubSub_ResumeAfterCancelDoesNotResurrect() throws Exception {
        IMessageService msgService = service.getMessageService();
        String topic = "test:pubsub:cancel-resume";
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger count1 = new AtomicInteger(0);

        IMessageSubscription sub2 = msgService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext ctx) {
                latch2.countDown();
                return null;
            }
        });
        IMessageSubscription sub1 = msgService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext ctx) {
                count1.incrementAndGet();
                return null;
            }
        });

        sub1.suspend();
        sub1.cancel();
        sub1.resume();

        assertTrue(sub1.isCancelled(), "subscription must stay cancelled after resume()");
        msgService.send(topic, "msg");
        assertTrue(latch2.await(5, TimeUnit.SECONDS), "other subscriber should receive messages");
        Thread.sleep(300);
        assertEquals(0, count1.get(), "cancelled subscription must not receive messages after resume()");

        sub2.cancel();
    }

    @Test
    void testPubSub_ConsumeContextSendAsyncPublishes() throws Exception {
        IMessageService msgService = service.getMessageService();
        CountDownLatch replyLatch = new CountDownLatch(1);

        IMessageSubscription replySub = msgService.subscribe("test:pubsub:reply", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object msg, IMessageConsumeContext ctx) {
                if ("pong:ping".equals(String.valueOf(msg)))
                    replyLatch.countDown();
                return null;
            }
        });
        IMessageSubscription reqSub = msgService.subscribe("test:pubsub:req", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object msg, IMessageConsumeContext ctx) {
                ctx.sendAsync("test:pubsub:reply", "pong:" + msg, null);
                return null;
            }
        });

        msgService.send("test:pubsub:req", "ping");

        assertTrue(replyLatch.await(5, TimeUnit.SECONDS),
                "message sent via IMessageConsumeContext.sendAsync must actually be published");
        replySub.cancel();
        reqSub.cancel();
    }

    @Test
    void testGetAll_SkipsMissingKeys() {
        service.put("test:getall:present", "v");
        Map<String, Object> map = service.getAll(Arrays.asList("test:getall:present", "test:getall:missing"));
        assertEquals(1, map.size(), "missing keys must not appear as null entries: " + map);
        assertEquals("v", String.valueOf(map.get("test:getall:present")));
    }

    @Test
    void testGetAllAsync_SkipsMissingKeys() throws Exception {
        service.put("test:getall:present-async", "v");
        Map<String, Object> map = service.getAllAsync(Arrays.asList("test:getall:present-async", "test:getall:missing-async"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, map.size(), "missing keys must not appear as null entries: " + map);
    }

    @Test
    void testEmptyCollectionArguments_NoError() throws Exception {
        service.getAll(new ArrayList<>());
        service.getAllAsync(new ArrayList<>()).toCompletableFuture().get(5, TimeUnit.SECONDS);
        service.removeAll(new ArrayList<>());
        service.removeAllAsync(new ArrayList<>()).toCompletableFuture().get(5, TimeUnit.SECONDS);
        service.putAllAsync(new HashMap<>()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        INosqlHashOperations hashOps = service.hashOps("test:hash:empty-args");
        hashOps.getAll(new ArrayList<>());
        hashOps.getAllAsync(new ArrayList<>()).toCompletableFuture().get(5, TimeUnit.SECONDS);
        hashOps.removeAll(new ArrayList<>());

        service.setOps("test:set:empty-args").removeAll(new ArrayList<>());
        service.listOps("test:list:empty-args").addAll(new ArrayList<>());
        service.queue("test:queue:empty-args").enqueueBatch(new ArrayList<>());
    }

    @Test
    void testRanking_GetTopNZeroReturnsEmpty() {
        INosqlRanking ranking = service.ranking("test:ranking:topn-zero");
        ranking.add("p1", 100);
        ranking.add("p2", 200);

        List<RankingEntry> top = ranking.getTopN(0);
        assertTrue(top.isEmpty(), "getTopN(0) must return an empty list, got " + top.size() + " entries");
    }

    @Test
    void testRateLimiter_FractionalTokens() {
        // the rate limit script stores fractional token counts as text (e.g. "9.5") when rate is fractional
        service.put("test:rl:frac:tokens", 9.5);
        INosqlRateLimiter limiter = service.rateLimiter("test:rl:frac", new RateLimiterConfig(0.5, 10));

        assertEquals(9, limiter.getAvailableTokens());
    }

    @Test
    void testRateLimiter_FractionalRateConfig() {
        INosqlRateLimiter limiter = service.rateLimiter("test:rl:frac-rate", new RateLimiterConfig(0.5, 10));

        RateLimitResult result = limiter.tryAcquire(1);
        assertTrue(result.isAllowed());
        assertEquals(9, result.getRemainingTokens());
    }

    @Test
    void testHashOps_RemoveIfMatch() {
        INosqlHashOperations hashOps = service.hashOps("test:hash:rim");
        hashOps.put("field", "value");

        assertFalse(hashOps.removeIfMatch("field", "other"));
        assertEquals("value", String.valueOf(hashOps.get("field")));

        assertTrue(hashOps.removeIfMatch("field", "value"));
        assertNull(hashOps.get("field"));

        // numeric values are stored as bare text; removeIfMatch must still match the same numeric argument
        INosqlHashOperations numOps = service.hashOps("test:hash:rim-num");
        numOps.put("count", 5L);
        assertTrue(numOps.removeIfMatch("count", 5L));
        assertNull(numOps.get("count"));
    }

    @Test
    void testHashOps_PutIfAbsentOrMatchEx() throws Exception {
        INosqlHashOperations hashOps = service.hashOps("test:hash:piam");

        // absent -> set and return null
        assertNull(hashOps.putIfAbsentOrMatchExAsync("field", "a", 60000)
                .toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals("a", String.valueOf(hashOps.get("field")));

        // value matches -> keep value and return old value
        assertEquals("a", hashOps.putIfAbsentOrMatchExAsync("field", "a", 60000)
                .toCompletableFuture().get(5, TimeUnit.SECONDS));

        // value differs -> no update, return current value
        assertEquals("a", hashOps.putIfAbsentOrMatchExAsync("field", "b", 60000)
                .toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals("a", String.valueOf(hashOps.get("field")));

        // expiry is applied to the hash key
        assertTrue(service.getTimeoutAsync("test:hash:piam")
                .toCompletableFuture().get(5, TimeUnit.SECONDS) > 0);
    }

    @Test
    void testContainsKey_EmptyStringValue() throws Exception {
        service.put("test:contains:empty", "");
        assertTrue(service.containsKey("test:contains:empty"),
                "key holding an empty-string value must be reported as present");
        assertTrue(service.containsKeyAsync("test:contains:empty")
                .toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    @Test
    void testSessionStore_SetFieldDoesNotResurrectExpiredSession() throws Exception {
        INosqlSessionStore store = service.sessionStore("test:sess:zombie");

        Map<String, Object> data = new HashMap<>();
        data.put("user", "alice");
        store.set("s1", data, 200);

        Thread.sleep(400); // wait for the session TTL to expire

        store.setField("s1", "email", "alice@example.com");
        assertFalse(store.exists("s1"), "setField on an expired session must not resurrect a TTL-less key");
    }

    @Test
    void testClientResourcesNotLeakedAcrossStartStop() throws Exception {
        String host = redis.getHost();
        int port = redis.getMappedPort(6379);

        long base = lettuceThreadCount();
        for (int i = 0; i < 4; i++) {
            RedisConfig config = new RedisConfig();
            config.setHost(host);
            config.setPort(port);
            LettuceRedisConnectionProvider leakProvider = new LettuceRedisConnectionProvider();
            leakProvider.setConfig(config);
            leakProvider.start();
            leakProvider.stop();
        }
        Thread.sleep(300);
        long after = lettuceThreadCount();
        assertTrue(after <= base,
                "DefaultClientResources threads must be shut down on stop, leaked threads: base=" + base + ", after=" + after);
    }

    private static long lettuceThreadCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("lettuce-"))
                .count();
    }
}
