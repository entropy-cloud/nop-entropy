/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.launch;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.commons.util.StringHelper;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import com.zaxxer.hikari.HikariDataSource;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2510 C8-011 / C10-008 (plan 2510 裁定⑥): the launch poller must separate
 * control-plane and data-plane thread resources — blocked/slow data-plane
 * consumers must not starve control-plane message delivery (deploy / cancel /
 * coordination RPCs).
 *
 * <p>Red (pre-fix): one shared 2-thread scheduled pool carries every
 * subscription; two blocked data-plane subscriptions monopolize both threads
 * and the control-plane topic is never polled.
 */
class TestPollerControlPlaneFaceting {

    private static HikariDataSource dataSource;

    private PollingJdbcMessageService service;

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
    }

    @AfterAll
    static void destroyAll() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    private PollingJdbcMessageService newService() {
        JdbcFactory factory = new JdbcFactory();
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        PollingJdbcMessageService s = new PollingJdbcMessageService(jdbcTemplate, 50L);
        s.initialize();
        return s;
    }

    @Test
    void blockedDataPlaneConsumersMustNotStarveControlPlane() throws Exception {
        service = newService();

        List<Object> controlReceived = new CopyOnWriteArrayList<>();
        IMessageSubscription controlSub = service.subscribe("nop-stream.rpc.control.probe",
                (topic, message, ctx) -> {
            controlReceived.add(message);
            return null;
        },
                new MessageSubscribeOptions());

        // Two data-plane subscriptions whose consumers block on the first message —
        // pre-fix these occupy both threads of the shared 2-thread poller pool.
        CountDownLatch block1 = new CountDownLatch(1);
        CountDownLatch block2 = new CountDownLatch(1);
        CountDownLatch entered1 = new CountDownLatch(1);
        CountDownLatch entered2 = new CountDownLatch(1);

        service.send("nop-stream.data.faceting.edge1", "row-1", null);
        service.send("nop-stream.data.faceting.edge2", "row-2", null);

        service.subscribe("nop-stream.data.faceting.edge1", (topic, message, ctx) -> {
            entered1.countDown();
            await(block1);
            return null;
        }, new MessageSubscribeOptions());
        service.subscribe("nop-stream.data.faceting.edge2", (topic, message, ctx) -> {
            entered2.countDown();
            await(block2);
            return null;
        }, new MessageSubscribeOptions());

        assertTrue(entered1.await(10, TimeUnit.SECONDS), "data consumer 1 must be entered");
        assertTrue(entered2.await(10, TimeUnit.SECONDS), "data consumer 2 must be entered");

        // Both poller threads are now wedged on data-plane dispatch. A control-plane
        // message must still be delivered promptly (faceted pools), not starved.
        long start = System.currentTimeMillis();
        service.send("nop-stream.rpc.control.probe", "control-payload", null);

        long deadline = start + 5_000;
        while (controlReceived.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        long latency = System.currentTimeMillis() - start;

        assertTrue(!controlReceived.isEmpty(),
                "control-plane message must be delivered while data-plane consumers are blocked; "
                        + "latency was " + latency + "ms (C8-011 control-plane starvation)");
        assertTrue(latency < 3_000,
                "control-plane latency " + latency + "ms exceeds the bounded window (C8-011)");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
