/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.launch;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2510 C5-010 / C10-006 (plan 2510 裁定⑨): a single poll must materialize at
 * most {@code nop.stream.launch.poll-batch-size} rows and drain a larger
 * backlog through cursor-advanced batching — never materialize the whole
 * backlog in one query/dispatch pass.
 *
 * <p>Red (pre-fix): the poll query has no LIMIT, so a backlog larger than the
 * batch limit is materialized and dispatched in one pass.
 */
class TestPollBatchLimitAndBoundedMaterialization {

    private static final String DATA_TOPIC = "nop-stream.data.batchlimit.edge";
    private static final int BACKLOG = 20;
    private static final int BATCH_LIMIT = 4;

    private static HikariDataSource dataSource;
    private static String previousLimit;

    private PollingJdbcMessageService service;

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        previousLimit = System.getProperty("nop.stream.launch.poll-batch-size");
        System.setProperty("nop.stream.launch.poll-batch-size", String.valueOf(BATCH_LIMIT));
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
    }

    @AfterAll
    static void destroyAll() {
        if (previousLimit == null) {
            System.clearProperty("nop.stream.launch.poll-batch-size");
        } else {
            System.setProperty("nop.stream.launch.poll-batch-size", previousLimit);
        }
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

    @Test
    void singlePollMaterializesAtMostBatchLimitRowsAndDrainsInBatches() throws Exception {
        JdbcFactory factory = new JdbcFactory();
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        long pollIntervalMs = 200L;
        service = new PollingJdbcMessageService(jdbcTemplate, pollIntervalMs);
        service.initialize();

        for (int i = 0; i < BACKLOG; i++) {
            service.send(DATA_TOPIC, "row-" + i, null);
        }

        List<Long> arrivalTimestamps = new CopyOnWriteArrayList<>();
        AtomicLong received = new AtomicLong();
        IMessageSubscription sub = service.subscribe(DATA_TOPIC, (topic, message, ctx) -> {
            received.incrementAndGet();
            arrivalTimestamps.add(System.currentTimeMillis());
            return null;
        }, new MessageSubscribeOptions());

        long deadline = System.currentTimeMillis() + 15_000;
        while (received.get() < BACKLOG && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(BACKLOG, received.get(), "cursor batching must drain the full backlog");

        // Group arrivals into poll passes separated by gaps >= pollInterval/2.
        int maxCluster = 0;
        int currentCluster = 0;
        long prev = -1;
        for (long ts : arrivalTimestamps) {
            if (prev < 0 || ts - prev < pollIntervalMs / 2) {
                currentCluster++;
            } else {
                maxCluster = Math.max(maxCluster, currentCluster);
                currentCluster = 1;
            }
            prev = ts;
        }
        maxCluster = Math.max(maxCluster, currentCluster);

        assertTrue(maxCluster <= BATCH_LIMIT,
                "a single poll must materialize at most " + BATCH_LIMIT + " rows, observed "
                        + maxCluster + " rows dispatched in one pass (C5-010 unbounded materialization)");
    }
}
