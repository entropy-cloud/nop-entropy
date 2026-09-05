package io.nop.stream.connector;

import io.nop.stream.core.connector.DrainableSource;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class TestDrainableSourceSupport {

    /** Event-driven wait: returns as soon as the condition holds; the deadline only guards against hangs. */
    private static void awaitUntil(String message, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail(message + " (condition not met within 30s)");
    }

    @Test
    void testDrainableSourceTruncateStopsConsuming() throws Exception {
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
        awaitUntil("source thread did not collect any items", () -> !collected.isEmpty());

        source.truncateForDrain();

        awaitUntil("source thread should have exited after truncateForDrain", () -> !sourceThread.isAlive());
        assertFalse(collected.isEmpty(), "Should have collected some items before drain");

        source.cancel();
    }
}
