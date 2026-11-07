package io.nop.stream.connector;

import io.nop.stream.core.connector.DrainableSource;
import io.nop.stream.core.testsupport.TestAwait;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TestDrainableSourceSupport {

    @Test
    void testDrainableSourceTruncateStopsConsuming() throws Exception {
        List<String> collected = new ArrayList<>();
        // 显式交接信号：truncateForDrain/cancel 释放源线程的逐条等待（上限仅挂死保护，
        // 正常路径由 countDown 即刻唤醒）——不再用墙钟节流制造发射节奏
        CountDownLatch stepGate = new CountDownLatch(1);
        DrainableSource<String> source = new DrainableSource<>() {
            private static final long serialVersionUID = 1L;
            private volatile boolean running = true;
            private volatile boolean draining = false;

            @Override
            public void truncateForDrain() {
                draining = true;
                stepGate.countDown();
            }

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                int i = 0;
                while (running && !draining) {
                    ctx.collect("item-" + i);
                    i++;
                    stepGate.await(60, TimeUnit.SECONDS);
                }
            }

            @Override
            public void cancel() {
                running = false;
                stepGate.countDown();
            }
        };

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
                // intentionally ignored: source thread collection failure surfaces via the await assertions below
            }
        });
        sourceThread.start();

        // Wait until the source thread has actually started collecting items
        TestAwait.until("source thread collected at least one item", () -> !collected.isEmpty());

        source.truncateForDrain();

        TestAwait.until("source thread exited after truncateForDrain", () -> !sourceThread.isAlive());
        assertFalse(collected.isEmpty(), "Should have collected some items before drain");

        source.cancel();
    }
}
