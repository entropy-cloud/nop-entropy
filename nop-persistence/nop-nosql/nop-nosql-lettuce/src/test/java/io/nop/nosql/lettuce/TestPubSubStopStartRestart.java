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
import io.nop.nosql.core.config.RedisConfig;
import io.nop.nosql.lettuce.impl.LettuceMessageService;
import io.nop.nosql.lettuce.impl.LettucePubSubService;
import io.nop.nosql.lettuce.impl.LettuceRedisConnectionProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C7-009 载体（plan 2554 裁定 M）：真 Redis 上 PubSub 服务 stop→start→重新 subscribe→
 * 收消息。缺陷态 doStop 不复位 {@code link}，重启后 subscribe 复用已关闭链路——订阅
 * 注册成功但消息永不投递（消息面死亡）。修复后 getOrCreateLink 命中 null 重建链路。
 * 回归面：{@code TestLettucePubSubConcurrency}（plan 2518）零改动。
 */
@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
public class TestPubSubStopStartRestart {

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
        if (provider != null)
            provider.stop();
    }

    @Test
    void testStopThenStartThenSubscribeReceivesMessages() throws Exception {
        IMessageService msgService = service.getMessageService();

        // phase 1: subscribe + receive works before stop
        String topic = "test:pubsub:restart";
        CountDownLatch latch1 = new CountDownLatch(1);
        List<String> received1 = new CopyOnWriteArrayList<>();
        IMessageSubscription sub1 = msgService.subscribe(topic, (t, msg, ctx) -> {
            received1.add(String.valueOf(msg));
            latch1.countDown();
            return null;
        });
        msgService.send(topic, "before-stop");
        assertTrue(latch1.await(5, TimeUnit.SECONDS), "should receive a message before stop");
        assertEquals("before-stop", received1.get(0));
        sub1.cancel();

        // phase 2: stop the service (closes the pub/sub link)
        ((LettucePubSubService) msgService).stop();

        // phase 3: start again, subscribe again and receive a message
        ((LettucePubSubService) msgService).start();
        CountDownLatch latch2 = new CountDownLatch(1);
        List<String> received2 = new CopyOnWriteArrayList<>();
        IMessageSubscription sub2 = msgService.subscribe(topic, (t, msg, ctx) -> {
            received2.add(String.valueOf(msg));
            latch2.countDown();
            return null;
        });
        msgService.send(topic, "after-restart");
        assertTrue(latch2.await(5, TimeUnit.SECONDS),
                "subscription after stop→start must receive messages (C7-009: link must be rebuilt, not reused)");
        assertEquals("after-restart", received2.get(0));
        assertFalse(sub2.isCancelled());
        sub2.cancel();
    }

    @Test
    void testStopStartRestartAcrossDifferentTopic() throws Exception {
        IMessageService msgService = service.getMessageService();
        msgService.subscribe("test:pubsub:restart-topic-a", (t, msg, ctx) -> null);
        ((LettucePubSubService) msgService).stop();
        ((LettucePubSubService) msgService).start();

        String topicB = "test:pubsub:restart-topic-b";
        CountDownLatch latch = new CountDownLatch(1);
        IMessageSubscription sub = msgService.subscribe(topicB, (t, msg, ctx) -> {
            latch.countDown();
            return null;
        });
        msgService.send(topicB, "b-after-restart");
        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "a fresh topic subscription after restart must receive messages");
        sub.cancel();
    }
}
