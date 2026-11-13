package io.nop.graphql.core.subscription;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.ws.IWebSocketSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * plan 2498 Phase 3, finding 2.3-C8-001 (pushToClient leg): the subscription manager's
 * push must not execute the blocking {@code session.sendMessage} on the message-distribution
 * / publisher thread. It is enqueued into a per-session bounded send pump drained on the
 * shared worker pool; overflow disconnects the session instead of pinning platform carriers.
 *
 * <p>Pre-fix compatible: on the defect baseline the send executed synchronously on the
 * caller thread and the thread assertion failed red.
 */
@Timeout(60)
public class TestSubscriptionPushOffload_C8_001 {

    @BeforeAll
    public static void init() {
        // pushToClient serializes the payload via JsonTool; the JSON provider registers on platform init
        CoreInitialization.initialize();
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

    @Test
    public void subscriptionManagerPushOffloadsSendToWorker_C8_001() {
        GraphQLSubscriptionManager manager = new GraphQLSubscriptionManager();
        RecordingSession session = new RecordingSession();
        try {
            SubscriptionInfo subscription = new SubscriptionInfo();
            subscription.setOperationId("op-push-offload");
            subscription.setOperationName("Query_dataChanged");
            subscription.setSession(session);

            try {
                manager.pushToClient(subscription,
                        GraphQLSubscriptionManager.buildTopic("Query_dataChanged", "op-push-offload"),
                        Map.of("changed", true));
            } catch (Exception e) {
                // pre-fix pushToClient declared throws IOException; catch (Exception) keeps the
                // test compile-compatible with the defect baseline (red-run requirement)
                throw new AssertionError("push must not propagate a send failure", e);
            }

            awaitUntil("pushed message must be delivered through the send pump",
                    () -> !session.sentMessages.isEmpty());
            assertTrue(session.sentMessages.get(0).contains("\"result\""),
                    "the pushed message must use the JSON-RPC data format");
            String threadName = session.sendThread.get();
            assertTrue(threadName.startsWith("nop-global-worker"),
                    "subscription manager push must run on the shared worker pool but ran on: " + threadName);
            assertFalse(threadName.startsWith("nop-global-timer"),
                    "subscription manager push must never run on the globalTimer thread, got: " + threadName);
            assertFalse(threadName.equals(Thread.currentThread().getName()),
                    "push must not execute the blocking send on the caller (distribution/publisher) thread");
        } finally {
            manager.destroy();
        }
    }

    /**
     * Overflow policy (audit suggestion ①): when the session's bounded send buffer is full,
     * the session is disconnected instead of the buffer growing without bound.
     */
    @Test
    public void pushOverflowDisconnectsSession_C8_001() {
        GraphQLSubscriptionManager manager = new GraphQLSubscriptionManager();
        BlockingSession session = new BlockingSession();
        try {
            SubscriptionInfo subscription = new SubscriptionInfo();
            subscription.setOperationId("op-overflow");
            subscription.setOperationName("Query_dataChanged");
            subscription.setSession(session);

            boolean submitted = true;
            for (int i = 0; i < 200 && submitted; i++) {
                // buffer capacity 128: the 129th enqueue must overflow and disconnect the session
                try {
                    manager.pushToClient(subscription,
                            GraphQLSubscriptionManager.buildTopic("Query_dataChanged", "op-overflow"),
                            Map.of("seq", i));
                } catch (Exception e) {
                    // compile-compatibility with the pre-fix throws-clause; never expected post-fix
                    throw new AssertionError("push must not propagate a send failure", e);
                }
                submitted = !session.closed;
            }
            assertTrue(session.closed,
                    "session must be disconnected when its bounded send buffer overflows "
                            + "(audit suggestion ①: bounded queue + overflow policy, not unbounded buffering)");
        } finally {
            session.release.countDown();
            manager.destroy();
        }
    }

    static class BlockingSession implements IWebSocketSession {
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean closed;

        @Override
        public void sendMessage(String message) throws IOException {
            // simulate a stuck consumer: the drain task never finishes until released
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
}
