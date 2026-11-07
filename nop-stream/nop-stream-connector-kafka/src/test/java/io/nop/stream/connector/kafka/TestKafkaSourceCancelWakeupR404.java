/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.44 R4-04 (plan 2537) red① acceptance pin — W-01 (finding 1.2-C3-005;
 * adjudication A.1 / seam S1): {@code cancel()} must deliver a
 * {@code consumer.wakeup()} so a startup-phase metadata fetch
 * ({@code partitionsFor}, bounded by {@code max.block.ms}) can no longer hold
 * the cancellation hostage for up to max.block.ms.
 *
 * <p>Zero broker: all probe legs run against a probe {@link KafkaConsumer}
 * subclass injected through the {@code createConsumer()} protected seam.
 * The reachability leg (behavioral carrier) runs against an unreachable
 * localhost:9092 (no broker in the unit-test environment — same
 * symbolic-host assumption family as the existing kafka module tests).
 *
 * <p>Legs: (1) cancel during the startup fetch terminates the run well within
 * the pin budget; (2) cancel during the poll loop delivers wakeup (&ge;1) to
 * the held consumer and exits gracefully; (3) the armed self-protection closes
 * the early-cancel window (cancel landing before the consumer reference was
 * published wakes the first blocking call and still exits gracefully —
 * cancellation = stop, never failure, per adjudication A.1 decision 4);
 * (4) an unexpected wakeup while still running surfaces typed, never silently
 * swallowed.
 */
class TestKafkaSourceCancelWakeupR404 {

    /** red① behavioral carrier (compiles and runs pre-fix): cancel must terminate
     *  a run blocked in the startup metadata fetch well within the pin budget —
     *  pre-fix the thread stays alive (blocked up to max.block.ms = 60s). */
    @Test
    void cancelTerminatesRunBlockedInStartupMetadataFetchWithinBudget() throws Exception {
        // explicit 60s max.block.ms = the documented pre-fix cancellation latency
        // ceiling; the pin budget (8s) must be far below it
        KafkaSourceFunction<String> f = new KafkaSourceFunction<>("localhost:9092", "in-topic",
                java.util.Map.of("max.block.ms", 60_000L), KafkaSourceFunction.utf8String(),
                KafkaSourceFunction.START_EARLIEST, 1, 0);
        AtomicReference<Throwable> runFailure = new AtomicReference<>();
        Thread runThread = new Thread(() -> {
            try {
                f.run(emptySourceContext());
            } catch (Throwable t) {
                runFailure.set(t);
            }
        });
        runThread.start();
        assertTrue(awaitThreadParked(runThread, 10_000L),
                "run thread must reach the blocking metadata fetch");

        long cancelAt = System.currentTimeMillis();
        f.cancel();
        // join bounded by the pin latch band (<= 10s): post-fix the wakeup breaks
        // partitionsFor immediately; pre-fix the thread stays blocked (red)
        runThread.join(8_000L);
        assertFalse(runThread.isAlive(),
                "cancel() must unblock the startup metadata fetch (partitionsFor) within "
                        + (System.currentTimeMillis() - cancelAt) + "ms — pre-fix it stays "
                        + "blocked up to max.block.ms (CRA 8.44 R4-04 W-01 red①)");
    }

    // ==================== probe seam legs (S1) ====================

    /** red① probe leg: cancel() during the poll loop must invoke wakeup() on the
     *  held consumer (count >= 1) and the run must exit gracefully. */
    @Test
    void cancelDuringPollDeliversWakeupToHeldConsumer() throws Exception {
        ProbeConsumer probe = new ProbeConsumer();
        KafkaSourceFunction<String> f = sourceWithProbe(probe);
        AtomicReference<Throwable> runFailure = new AtomicReference<>();
        Thread runThread = new Thread(() -> {
            try {
                f.run(emptySourceContext());
            } catch (Throwable t) {
                runFailure.set(t);
            }
        });
        runThread.start();
        assertTrue(probe.inPoll.await(5, TimeUnit.SECONDS), "run must reach the poll loop");

        f.cancel();
        runThread.join(5_000L);
        assertFalse(runThread.isAlive(), "cancel must terminate the run within budget");
        assertTrue(probe.wakeupCount.get() >= 1,
                "cancel() must call consumer.wakeup() at least once, got "
                        + probe.wakeupCount.get() + " (CRA 8.44 R4-04 W-01 red①)");
        assertNull(runFailure.get(),
                "a cancel-initiated wakeup must exit gracefully (stop, not failure), got "
                        + runFailure.get());
    }

    /** armed leg: cancel() landing inside the early-cancel window (while the consumer
     *  is still being created, before the reference is published) must arm the first
     *  blocking call — the run-side wakeup converts into an immediate WakeupException
     *  at partitionsFor instead of blocking up to max.block.ms, and the run exits
     *  gracefully (running == false branch). */
    @Test
    void cancelDuringConsumerCreationArmsFirstBlockingCallAndExitsGracefully() throws Exception {
        ProbeConsumer probe = new ProbeConsumer();
        // hold the constructor open: while it blocks, the run's consumer reference
        // is not yet published, so cancel()'s own wakeup has no target
        probe.holdConstructor = new CountDownLatch(1);
        KafkaSourceFunction<String> f = sourceWithProbe(probe);
        AtomicReference<Throwable> runFailure = new AtomicReference<>();
        Thread runThread = new Thread(() -> {
            try {
                f.run(emptySourceContext());
            } catch (Throwable t) {
                runFailure.set(t);
            }
        });
        runThread.start();
        assertTrue(awaitThreadParked(runThread, 5_000L),
                "run must be parked inside createConsumer() (consumer not yet published)");

        f.cancel(); // lands while this.consumer == null: wakeup has no target
        probe.holdConstructor.countDown(); // let the consumer finish publishing
        runThread.join(5_000L);
        assertFalse(runThread.isAlive(),
                "the armed self-protection must end the run immediately instead of blocking");
        assertTrue(probe.wakeupCount.get() >= 1,
                "run-side armed wakeup must fire, got " + probe.wakeupCount.get());
        assertNull(runFailure.get(),
                "the armed wakeup (cancel in flight) must exit gracefully, got "
                        + runFailure.get());
    }

    /** WakeupException while-running typed leg: an unexpected wakeup (no cancel in
     *  flight) must surface typed — never silently swallowed. */
    @Test
    void unexpectedWakeupWhileRunningSurfacesTyped() throws Exception {
        ProbeConsumer probe = new ProbeConsumer();
        probe.failFirstPoll.set(true);
        KafkaSourceFunction<String> f = sourceWithProbe(probe);
        AtomicReference<Throwable> runFailure = new AtomicReference<>();
        Thread runThread = new Thread(() -> {
            try {
                f.run(emptySourceContext());
            } catch (Throwable t) {
                runFailure.set(t);
            }
        });
        runThread.start();
        runThread.join(5_000L);
        assertFalse(runThread.isAlive());
        assertInstanceOf(io.nop.stream.core.exceptions.StreamException.class, runFailure.get(),
                "a wakeup while still running must surface typed, got " + runFailure.get());
    }

    private static KafkaSourceFunction<String> sourceWithProbe(ProbeConsumer probe) {
        return new KafkaSourceFunction<>("localhost:9092", "in-topic", KafkaSourceFunction.utf8String()) {
            @Override
            protected KafkaConsumer<String, byte[]> createConsumer() {
                return probe;
            }
        };
    }

    private static io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<String>
            emptySourceContext() {
        return new io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<>() {
            @Override
            public void collect(String element) {
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
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

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }

    /** Probe consumer: fake metadata, no network; wakeup() counts and poisons the
     *  next blocking call (partitionsFor / poll) with a WakeupException. */
    private static class ProbeConsumer extends KafkaConsumer<String, byte[]> {
        final AtomicInteger wakeupCount = new AtomicInteger();
        final AtomicLong pollCalls = new AtomicLong();
        final java.util.concurrent.atomic.AtomicBoolean failFirstPoll =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final CountDownLatch inPoll = new CountDownLatch(1);
        /** when non-null, the constructor parks until it is counted down (armed leg). */
        volatile CountDownLatch holdConstructor;

        ProbeConsumer() {
            super(fakeConsumerConfig());
            CountDownLatch gate = holdConstructor;
            if (gate != null) {
                try {
                    gate.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private static java.util.Map<String, Object> fakeConsumerConfig() {
            java.util.Map<String, Object> props = new java.util.HashMap<>();
            props.put("bootstrap.servers", "localhost:9092");
            props.put("key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getName());
            props.put("value.deserializer",
                    org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName());
            return props;
        }

        @Override
        public void wakeup() {
            wakeupCount.incrementAndGet();
            failFirstPoll.set(true);
        }

        private WakeupException poisoned() {
            return failFirstPoll.compareAndSet(true, false) ? new WakeupException() : null;
        }

        @Override
        public List<PartitionInfo> partitionsFor(String topic) {
            WakeupException poison = poisoned();
            if (poison != null) {
                throw poison;
            }
            List<PartitionInfo> partitions = new ArrayList<>();
            partitions.add(new PartitionInfo(topic, 0, null, null, null));
            return partitions;
        }

        @Override
        public void assign(Collection<TopicPartition> partitions) {
            // no-op probe
        }

        @Override
        public void seekToBeginning(Collection<TopicPartition> partitions) {
            // no-op probe
        }

        @Override
        public void seekToEnd(Collection<TopicPartition> partitions) {
            // no-op probe
        }

        @Override
        public ConsumerRecords<String, byte[]> poll(Duration timeout) {
            pollCalls.incrementAndGet();
            inPoll.countDown();
            WakeupException poison = poisoned();
            if (poison != null) {
                throw poison;
            }
            // block until poisoned (wakeup) — bounded wait as the dead-man switch
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                if (failFirstPoll.get()) {
                    throw new WakeupException();
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ConsumerRecords.empty();
                }
            }
            return ConsumerRecords.empty();
        }
    }

    private static boolean awaitThreadParked(Thread t, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (t.getState() == Thread.State.WAITING || t.getState() == Thread.State.TIMED_WAITING) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }
}
