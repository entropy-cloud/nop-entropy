/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.message.debezium.engine;

import io.debezium.engine.DebeziumEngine;
import io.nop.message.debezium.DebeziumConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2498 Phase 1, finding 1.2-C4-004: the embedded Debezium engine body
 * ({@code engine.run()}) is an unbounded long-running task (it blocks for the whole engine
 * lifetime, day/month scale). It must run on a dedicated per-engine carrier thread
 * ({@code debezium-engine-<connector>-<pool>-<n>}, daemon {@link io.nop.commons.concurrent.thread.NamedThreadFactory}),
 * never on the platform-shared {@code nop-global-worker} pool where one CDC subtask used to
 * permanently pin one shared thread. stop() must apply a bounded termination budget to that
 * carrier (shutdown + bounded awaitTermination, aligned with the reader close chain's
 * join(5s)) instead of returning fire-and-forget.
 *
 * <p>Boundary (plan 2498 baseline errata 4): the stop completion-latch contract and the
 * startup guard belong to 1.2-C3-014 (R1-08) and are NOT covered here; this test only pins
 * the dedicated carrier and its bounded termination.
 *
 * <p>The probe subclass captures the thread that runs the engine body without actually
 * running the engine, so both assertions observe the real submission seam of
 * {@code start()} (rule 23 wiring evidence) using only pre-existing API surface.
 */
@Timeout(60)
public class TestDebeziumEngineDedicatedCarrier_C4_004 {

    private static DebeziumConfig newConfig(String name) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(name);
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        config.setDatabasePort(3306);
        config.setDatabaseUser("repl");
        config.setDatabasePassword("secret");
        config.setSchemaHistoryStore("file");
        config.setSchemaHistoryPath("_tmp/cdc-" + name + "/schema-history.dat");
        return config;
    }

    private static void awaitUntil(String message, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean())
                return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                assertTrue(false, message);
            }
        }
        assertTrue(false, message);
    }

    /**
     * Probe wrapper: records the thread that runs the engine body and returns without
     * running the real engine.
     */
    static class ProbeWrapper extends DebeziumEngineWrapper {
        final CountDownLatch bodyStarted = new CountDownLatch(1);
        final AtomicReference<String> carrierThreadName = new AtomicReference<>();
        volatile Thread carrierThread;

        ProbeWrapper(DebeziumConfig config) {
            super(config, event -> {
            });
        }

        @Override
        protected void runEngineWorker(
                DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef) {
            carrierThread = Thread.currentThread();
            carrierThreadName.set(Thread.currentThread().getName());
            bodyStarted.countDown();
        }
    }

    /**
     * rule 23 wiring evidence: {@code wrapper.start()} must execute the engine body on the
     * dedicated {@code debezium-engine-} carrier thread. Pre-fix the body ran on
     * {@code nop-global-worker-N} (finding 1.2-C4-004) and this assertion failed red.
     */
    @Test
    public void engineBodyRunsOnDedicatedCarrierThreadNotGlobalWorker() throws Exception {
        ProbeWrapper wrapper = new ProbeWrapper(newConfig("carrier-probe"));
        try {
            wrapper.start();

            assertTrue(wrapper.bodyStarted.await(10, TimeUnit.SECONDS),
                    "engine body must start after wrapper.start()");
            String threadName = wrapper.carrierThreadName.get();
            assertTrue(threadName.startsWith("debezium-engine-carrier-probe"),
                    "engine body must run on the dedicated carrier thread but ran on: " + threadName
                            + ". Pre-fix (1.2-C4-004) the unbounded engine body was submitted to the "
                            + "shared nop-global-worker pool and permanently pinned one shared thread.");
            assertFalse(threadName.startsWith("nop-global-worker"),
                    "engine body must never run on the shared nop-global-worker pool, got: " + threadName);
        } finally {
            wrapper.stop();
        }
    }

    /**
     * stop() must apply a bounded termination budget to the dedicated carrier: it waits for
     * the running engine body to finish (here a fixed 800ms body), and the carrier thread
     * actually terminates afterwards. Pre-fix stop() returned fire-and-forget (elapsed ~0ms)
     * and the host thread was a pooled nop-global-worker that never terminates — both
     * assertions failed red.
     */
    @Test
    public void stopTerminatesCarrierWithinBoundedBudget() throws Exception {
        ProbeWrapper wrapper = new ProbeWrapper(newConfig("stop-budget")) {
            @Override
            protected void runEngineWorker(
                    DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef) {
                carrierThread = Thread.currentThread();
                carrierThreadName.set(Thread.currentThread().getName());
                bodyStarted.countDown();
                try {
                    // bounded engine-body work in progress while stop() arrives
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        wrapper.start();
        assertTrue(wrapper.bodyStarted.await(10, TimeUnit.SECONDS),
                "engine body must start after wrapper.start()");
        awaitUntil("engine body thread must be alive before stop",
                () -> wrapper.carrierThread != null && wrapper.carrierThread.isAlive());

        // bounded termination budget of the dedicated carrier = 5s (STOP_TERMINATION_BUDGET_SECONDS),
        // aligned with the reader close chain's join(5s)
        long t0 = System.nanoTime();
        wrapper.stop();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        assertTrue(elapsedMs >= 300,
                "stop must await the running engine body (bounded termination budget), but returned after "
                        + elapsedMs + "ms — pre-fix this was a fire-and-forget signal");
        assertTrue(elapsedMs < 5000,
                "stop must not wait unboundedly for the carrier, took " + elapsedMs + "ms");

        awaitUntil("dedicated carrier thread must terminate after stop",
                () -> wrapper.carrierThread.getState() == Thread.State.TERMINATED);
        assertFalse(wrapper.isRunning(), "engine must not be running after stop");
    }
}
