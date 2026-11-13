package io.nop.graphql.core.ws;

import io.nop.api.core.beans.ApiResponse;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.jsonrpc.JsonRpcRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * plan 2498 Phase 3, finding 2.3-C8-001 + matrix A5:
 *
 * <ul>
 * <li>keepalive fan-out convergence: instead of one globalTimer periodic task per connection
 * (N connections = N timer tasks), a single shared O(1) scan is armed once per JVM on the
 * globalTimer via {@code executeOn(globalWorker())} — timer triggers only;</li>
 * <li>blocking sends (keepalive ping, subscription data/complete push) are enqueued into a
 * per-session bounded send pump drained on the shared worker pool — no timer thread ever
 * executes a send, and one slow client cannot block other sessions;</li>
 * <li>the R1-01 insulation coverage of the keepalive chain is preserved
 * (see {@code TestJsonRpcKeepAliveInsulation}, coexisting).</li>
 * </ul>
 *
 * <p>Tests marked "pre-fix compatible" use only pre-existing API surface and ran red on the
 * defect baseline (fan-out = N timer tasks; sends executed on the caller/timer thread).
 */
@Timeout(90)
public class TestJsonRpcWsSendOffload_C8_001 {

    @BeforeAll
    public static void init() {
        // ping/data payloads go through JSON.stringify; the JSON provider registers on platform init
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
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
                fail(message);
            }
        }
        fail(message);
    }

    static class RecordingSession implements IWebSocketSession {
        final List<String> sentMessages = new CopyOnWriteArrayList<>();
        final AtomicReference<String> sendThread = new AtomicReference<>();
        volatile boolean closed;

        @Override
        public void sendMessage(String message) {
            sendThread.set(Thread.currentThread().getName());
            sentMessages.add(message);
        }

        @Override
        public void close(short statusCode, String reason) {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    /**
     * Simulates a slow client: the first send blocks until released (bounded), then fails
     * with IOException like a dead TCP peer would eventually surface.
     */
    static class SlowThenFailingSession implements IWebSocketSession {
        final CountDownLatch sendEntered = new CountDownLatch(1);
        final CountDownLatch releaseSend = new CountDownLatch(1);
        final AtomicBoolean sendDone = new AtomicBoolean();
        volatile boolean closed;

        @Override
        public void sendMessage(String message) throws IOException {
            sendEntered.countDown();
            try {
                releaseSend.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendDone.set(true);
            throw new IOException("simulated slow client send");
        }

        @Override
        public void close(short statusCode, String reason) {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    /**
     * rule 23 wiring evidence for the A5 convergence: the globalTimer's periodic-task
     * registry must not grow per connection. Pre-fix compatible — on the defect baseline
     * each handler armed its own timer task and this assertion failed red (delta == 5).
     */
    @Test
    public void keepAliveFanOutConvergesToConstantOnGlobalTimer_C8_001() throws Exception {
        int before = periodicTaskCountOnGlobalTimer();
        List<JsonRpcWebSocketHandler> handlers = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                handlers.add(new JsonRpcWebSocketHandler((request, headers) -> null, new RecordingSession()));
            }
            int delta = periodicTaskCountOnGlobalTimer() - before;
            assertTrue(delta <= 1,
                    "N connections must add at most ONE shared keepalive task on the globalTimer "
                            + "(A5 fan-out convergence), but 5 connections added " + delta
                            + " — pre-fix (2.3-C8-001) every connection armed its own timer task");
        } finally {
            handlers.forEach(JsonRpcWebSocketHandler::onClose);
        }
    }

    private static int periodicTaskCountOnGlobalTimer() throws Exception {
        DefaultScheduledExecutor timer = (DefaultScheduledExecutor) GlobalExecutors.globalTimer();
        Field field = DefaultScheduledExecutor.class.getDeclaredField("periodicTasks");
        field.setAccessible(true);
        Set<?> tasks = (Set<?>) field.get(timer);
        return tasks.size();
    }

    /**
     * ② keepalive send thread attribution (pre-fix compatible): the ping must be written on
     * a shared worker thread, never on a timer thread and never on the caller's thread.
     * Pre-fix the send executed synchronously on the calling thread and this failed red.
     */
    @Test
    public void keepaliveSendRunsOnWorkerNotTimerThread_C8_001() {
        RecordingSession session = new RecordingSession();
        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler((request, headers) -> null, session);
        try {
            handler.sendKeepAlive();
            awaitUntil("keepalive ping must be delivered through the send pump",
                    () -> !session.sentMessages.isEmpty());
            assertTrue(session.sentMessages.get(0).contains("\"method\":\"ping\""),
                    "the delivered message must be the ping keepalive notification");
            String threadName = session.sendThread.get();
            assertTrue(threadName.startsWith("nop-global-worker"),
                    "keepalive send must run on the shared worker pool but ran on: " + threadName);
            assertFalse(threadName.startsWith("nop-global-timer"),
                    "keepalive send must never run on the globalTimer thread, got: " + threadName);
        } finally {
            handler.onClose();
        }
    }

    /**
     * ③ slow-client scenario: a session whose send blocks must not prevent other sessions'
     * keepalives from being delivered. At the moment the healthy session's ping is delivered,
     * the slow session's send must still be blocked in flight (not sequentially completed
     * before the healthy send started, as pre-fix).
     */
    @Test
    public void slowSessionDoesNotBlockOtherSessionsKeepalive_C8_001() throws Exception {
        SlowThenFailingSession slow = new SlowThenFailingSession();
        RecordingSession healthy = new RecordingSession();
        JsonRpcWebSocketHandler slowHandler = new JsonRpcWebSocketHandler((request, headers) -> null, slow);
        JsonRpcWebSocketHandler healthyHandler = new JsonRpcWebSocketHandler((request, headers) -> null, healthy);
        try {
            slowHandler.sendKeepAlive();
            awaitUntil("slow session's send must be in flight",
                    () -> slow.sendEntered.getCount() == 0);

            healthyHandler.sendKeepAlive();
            awaitUntil("healthy session's ping must be delivered while the slow session's send is blocked",
                    () -> !healthy.sentMessages.isEmpty());
            assertFalse(slow.sendDone.get(),
                    "slow session's send must still be blocked when the healthy session's ping was "
                            + "delivered — pre-fix (2.3-C8-001) sends were sequential and blocking on the "
                            + "caller thread, so the healthy ping could only be sent after the slow send finished");
        } finally {
            slow.releaseSend.countDown();
            slowHandler.onClose();
            healthyHandler.onClose();
        }
    }

    /**
     * ④ subscription data push offload (pre-fix compatible): the data message must be
     * written on a shared worker thread; the publisher thread that calls onNext must not
     * perform the blocking send itself. Pre-fix the send ran synchronously inside onNext on
     * the publisher/caller thread and this failed red.
     */
    @Test
    public void subscriptionDataPushOffloadsSendToWorker_C8_001() {
        RecordingSession session = new RecordingSession();
        AtomicReference<String> publisherThread = new AtomicReference<>();

        JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler((request, headers) -> subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    publisherThread.set(Thread.currentThread().getName());
                    subscriber.onNext(ApiResponse.wrap("payload-C8-001"));
                }

                @Override
                public void cancel() {
                }
            });
        }, session);

        try {
            JsonRpcRequest request = new JsonRpcRequest();
            request.setId("op-offload");
            request.setMethod("queryData");
            handler.onMessage(request);

            awaitUntil("data message must be delivered through the send pump",
                    () -> !session.sentMessages.isEmpty());
            assertTrue(session.sentMessages.get(0).contains("payload-C8-001"),
                    "the delivered message must carry the subscription payload");
            String sendThreadName = session.sendThread.get();
            assertTrue(sendThreadName.startsWith("nop-global-worker"),
                    "subscription data send must run on the shared worker pool but ran on: " + sendThreadName);
            assertFalse(sendThreadName.startsWith("nop-global-timer"),
                    "subscription data send must never run on the globalTimer thread");
        } finally {
            handler.onClose();
        }
    }
}
