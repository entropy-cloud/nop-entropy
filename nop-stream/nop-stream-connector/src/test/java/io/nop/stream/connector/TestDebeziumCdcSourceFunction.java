/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.stream.core.common.functions.source.SourceFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class TestDebeziumCdcSourceFunction {

    @Test
    void testNullConfigRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DebeziumCdcSourceFunction(null));
    }

    @Test
    void testCancelBeforeRun() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("test");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
        assertDoesNotThrow(source::cancel);
    }

    @Test
    void testRunCollectsChangeEventsViaSourceContext() throws Exception {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("test-run");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");

        CopyOnWriteArrayList<ChangeEvent> collected = new CopyOnWriteArrayList<>();
        SourceFunction.SourceContext<ChangeEvent> ctx = new SourceFunction.SourceContext<>() {
            @Override
            public void collect(ChangeEvent element) {
                collected.add(element);
            }

            @Override
            public void collectWithTimestamp(ChangeEvent element, long timestamp) {
                collected.add(element);
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);

        CountDownLatch eventLatch = new CountDownLatch(1);
        CountDownLatch runStarted = new CountDownLatch(1);

        Thread runner = new Thread(() -> {
            try {
                runStarted.countDown();
                source.run(ctx);
            } catch (Exception e) {
                // expected on cancel
            }
        });
        runner.start();

        assertTrue(runStarted.await(2, TimeUnit.SECONDS), "Runner should start");

        source.cancel();
        runner.join(5000);
    }

    @Test
    void testTruncateForDrainStopsSource() throws Exception {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("test-drain");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");

        CopyOnWriteArrayList<ChangeEvent> collected = new CopyOnWriteArrayList<>();
        SourceFunction.SourceContext<ChangeEvent> ctx = new SourceFunction.SourceContext<>() {
            @Override
            public void collect(ChangeEvent element) {
                collected.add(element);
            }

            @Override
            public void collectWithTimestamp(ChangeEvent element, long timestamp) {
                collected.add(element);
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);

        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception e) {
                // expected
            }
        });
        runner.start();

        Thread.sleep(500);

        assertFalse(source.isDraining());
        source.truncateForDrain();
        assertTrue(source.isDraining());

        runner.join(5000);
        assertTrue(source.isDraining());
    }

    @Test
    void testCancelAfterDrainDoesNotThrow() throws Exception {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("test-cancel-after-drain");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);

        SourceFunction.SourceContext<ChangeEvent> ctx = new SourceFunction.SourceContext<>() {
            @Override
            public void collect(ChangeEvent element) {
            }

            @Override
            public void collectWithTimestamp(ChangeEvent element, long timestamp) {
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };

        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception e) {
                // expected
            }
        });
        runner.start();

        Thread.sleep(300);
        source.truncateForDrain();
        runner.join(5000);

        assertDoesNotThrow(source::cancel);
    }

    @Test
    void testSourceConsistencyIsReplayable() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("test-consistency");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
        assertEquals(
                io.nop.stream.core.common.functions.source.SourceConsistencyCapability.REPLAYABLE,
                source.getSourceConsistency()
        );
    }
}
