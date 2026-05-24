/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.stream.core.connector.DrainableSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests DRAIN support for connector sources.
 */
class TestDrainableSourceSupport {

    @Test
    void testDebeziumCdcSourceFunctionImplementsDrainable() {
        // Verify that DebeziumCdcSourceFunction implements DrainableSource
        assertTrue(DrainableSource.class.isAssignableFrom(DebeziumCdcSourceFunction.class),
                "DebeziumCdcSourceFunction should implement DrainableSource");
    }

    @Test
    void testDrainableSourceTruncateStopsConsuming() throws Exception {
        // Create a simple DrainableSource implementation for testing
        List<String> collected = new ArrayList<>();
        DrainableSource<String> source = new DrainableSource<>() {
            private static final long serialVersionUID = 1L;
            private volatile boolean running = true;
            private volatile boolean draining = false;

            @Override
            public void truncateForDrain() {
                draining = true;
            }

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                int i = 0;
                while (running && !draining) {
                    ctx.collect("item-" + i);
                    i++;
                    Thread.sleep(10);
                }
            }

            @Override
            public void cancel() {
                running = false;
            }
        };

        // Run source in a thread
        Thread sourceThread = new Thread(() -> {
            try {
                source.run(new io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<String>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void collect(String element) {
                        collected.add(element);
                    }

                    @Override
                    public void collectWithTimestamp(String element, long timestamp) {
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
                });
            } catch (Exception e) {
                // Expected on interrupt
            }
        });
        sourceThread.start();

        // Let it produce some items
        Thread.sleep(100);

        // Truncate for drain
        source.truncateForDrain();

        // Wait for the thread to finish
        sourceThread.join(2000);
        assertFalse(sourceThread.isAlive(), "Source thread should have exited after truncateForDrain");

        // Verify that items were collected
        assertFalse(collected.isEmpty(), "Should have collected some items before drain");

        source.cancel();
    }

    @Test
    void testDrainableSourceContract() {
        // Verify interface contract: DrainableSource extends SourceFunction
        assertTrue(io.nop.stream.core.common.functions.source.SourceFunction.class
                        .isAssignableFrom(DrainableSource.class),
                "DrainableSource should extend SourceFunction");
    }
}
