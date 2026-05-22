/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.common.functions.source.SourceFunction;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Bug N54: latch only counted down by cancel. Fix should pass.")
public class TestDebeziumCdcSourceCompletion {

    @Test
    void testSourceCompletesOnNaturalEnd() throws Exception {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("test-completion");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);

        List<ChangeEvent> collected = new CopyOnWriteArrayList<>();
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

        CountDownLatch runFinished = new CountDownLatch(1);

        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Exception e) {
                // expected for test scenarios
            } finally {
                runFinished.countDown();
            }
        });
        runner.start();

        boolean completed = runFinished.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "Source should complete naturally without cancel() being called");
        assertFalse(runner.isAlive(), "Runner thread should have terminated");
    }
}
