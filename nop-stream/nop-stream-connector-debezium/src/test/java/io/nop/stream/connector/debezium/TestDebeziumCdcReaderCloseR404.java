/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.44 R4-04 (plan 2537) red⑤ face② acceptance pin — W-05 on the debezium
 * CDC push→pull bridge (finding 1.2-C3-012; adjudication C.1 / seam S5): same
 * clear-first + interrupt restructure as the message bridge, dual-bridge
 * symmetry. The Debezium engine's dispatch thread blocks in {@code queue.put}
 * when the queue is full; the pre-fix close() threw on the join timeout BEFORE
 * the clear, so that thread stayed blocked on the abandoned queue forever (and
 * the parked runner was never interrupted).
 *
 * <p>K2 boundary (red line 9): the 2503
 * {@code verifyEmbeddedEngineTerminated("reader-close", true)} call stays on
 * the join-SUCCESS path — the timeout path types out before reaching it, which
 * loses no signal (a timed-out runner means the verify would ghost-fail
 * anyway). {@code TestDebeziumCdcStopVerification_C3_014} drives the
 * join-success path and is untouched.
 *
 * <p>Drive form: {@code createEmbeddedFunction} seam injects a stub engine host
 * that publishes the reader's real QueueSourceContext and parks (runner stuck
 * inside engine stop); the test's sender thread fills the queue to capacity and
 * then parks in the blocking put.
 */
class TestDebeziumCdcReaderCloseR404 {

    private static DebeziumConfig newConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("r404-close-engine");
        return config;
    }

    private static ChangeEvent smallEvent(String value) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("v", value);
        ChangeEventMetadata metadata = new ChangeEventMetadata(
                "mysql", "r404", "db", "schema", "t", null, null, null, null, null);
        return new ChangeEvent(metadata, "c", null, after,
                Map.of("id", 1), System.currentTimeMillis());
    }

    private static final class ParkedEngineHost extends DebeziumCdcSourceFunction {
        final AtomicReference<SourceFunction.SourceContext<ChangeEvent>> publishedContext =
                new AtomicReference<>();
        final CountDownLatch contextPublished = new CountDownLatch(1);
        final CountDownLatch runnerTerminated = new CountDownLatch(1);

        ParkedEngineHost(DebeziumConfig config) {
            super(config);
        }

        @Override
        public void run(SourceContext<ChangeEvent> ctx) throws Exception {
            publishedContext.set(ctx);
            contextPublished.countDown();
            try {
                // parked forever = the runner is stuck inside the engine stop chain
                new CountDownLatch(1).await();
            } finally {
                runnerTerminated.countDown();
            }
        }
    }

    @Test
    void closeJoinTimeoutPreservesTypedSignalAndReleasesTheBlockedDispatcher()
            throws Exception {
        AtomicReference<ParkedEngineHost> hostRef = new AtomicReference<>();
        DebeziumCdcSourceReader reader = new DebeziumCdcSourceReader(
                newConfig(), null, null, DebeziumCdcSourceReader.DEFAULT_MAX_RECORD_BYTES) {
            @Override
            protected DebeziumCdcSourceFunction createEmbeddedFunction(DebeziumConfig engineConfig) {
                ParkedEngineHost host = new ParkedEngineHost(engineConfig);
                hostRef.set(host);
                return host;
            }
        };
        reader.addSplits(List.of(new DebeziumTableSplit("db.t1")));
        reader.start();
        ParkedEngineHost host = hostRef.get();
        assertTrue(host != null && host.contextPublished.await(5, TimeUnit.SECONDS),
                "the engine runner must publish the reader's source context");

        // ---- drive the queue to full and then into the blocking put ----
        AtomicBoolean putReturned = new AtomicBoolean(false);
        AtomicBoolean senderInPut = new AtomicBoolean(false);
        SourceFunction.SourceContext<ChangeEvent> ctx = host.publishedContext.get();
        Thread sender = new Thread(() -> {
            for (int i = 0; i < DebeziumCdcSourceReader.QUEUE_CAPACITY; i++) {
                ctx.collect(smallEvent("filler-" + i));
            }
            senderInPut.set(true);
            ctx.collect(smallEvent("the-record-that-blocks"));
            putReturned.set(true);
        });
        sender.setDaemon(true);
        sender.start();
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!senderInPut.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(senderInPut.get(), "the dispatcher must reach the blocking put");
        Thread.sleep(200);
        assertTrue(sender.isAlive(), "the dispatcher must be parked inside queue.put");

        // ---- close(): join times out (5s) inside the reader ----
        StreamException closeFailure = assertThrows(StreamException.class, reader::close,
                "the join timeout must keep failing typed (signal preserved)");
        assertTrue(String.valueOf(closeFailure).contains("did not terminate within"),
                "the timeout message must be unchanged, got: " + closeFailure);

        // ---- leg (b): the blocked dispatcher must be released by the clear ----
        long releaseAt = System.currentTimeMillis();
        deadline = System.currentTimeMillis() + 5_000L;
        while (!putReturned.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(putReturned.get(),
                "the dispatcher's in-flight put must return after close() — pre-fix the typed "
                        + "throw skipped queue.clear() and the engine dispatch thread stayed "
                        + "blocked forever (CRA 8.44 R4-04 W-05 red⑤ face② leg b, released in "
                        + (System.currentTimeMillis() - releaseAt) + "ms)");

        // ---- leg (c): the parked runner was interrupted and terminated ----
        assertTrue(host.runnerTerminated.await(5, TimeUnit.SECONDS),
                "the parked runner must be interrupted and terminate on the close path "
                        + "(pre-fix: never interrupted — red⑤ face② leg c)");
    }
}
