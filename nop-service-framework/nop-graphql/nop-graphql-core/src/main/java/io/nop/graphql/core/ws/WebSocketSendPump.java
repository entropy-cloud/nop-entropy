/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.graphql.core.ws;

import io.nop.commons.concurrent.QueueOverflowPolicy;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.SequentialTaskExecutor;
import io.nop.commons.concurrent.impl.OverflowBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-session sequential WebSocket send pump (plan 2498 Phase 3, finding 2.3-C8-001 /
 * design matrix A5).
 *
 * <p>Outbound messages are enqueued into a bounded, order-preserving queue and drained on
 * the shared {@code nop-global-worker} pool. This decouples the blocking
 * {@code session.sendMessage} (which can block for seconds on a slow TCP peer) from the
 * publishing thread, the message-distribution thread and the shared globalTimer — the
 * {@code ρ = N·L/40} timer pinning face is eliminated because no timer thread ever executes
 * a send.
 *
 * <p>Overflow policy (audit suggestion ①, platform {@code IBlockingQueue} +
 * {@code QueueOverflowPolicy}): when a slow consumer's bounded buffer is full, the session
 * is disconnected instead of back-pressuring shared platform carriers.
 *
 * <p>No dedicated per-connection thread is created: {@link SequentialTaskExecutor} only
 * schedules a drain task onto the shared pool while tasks are pending, and preserves
 * per-session message order.
 */
public class WebSocketSendPump {
    static final Logger LOG = LoggerFactory.getLogger(WebSocketSendPump.class);

    /**
     * Default per-session send buffer capacity. A backlog beyond this means the consumer
     * cannot keep up; the session is disconnected rather than growing the buffer without
     * bound or blocking shared platform threads.
     */
    public static final int DEFAULT_CAPACITY = 128;

    private static final short CLOSE_OVERFLOW = 1011;

    private final IWebSocketSession session;
    private final int capacity;
    private final OverflowBlockingQueue<Runnable> queue;
    private final SequentialTaskExecutor pump;
    private volatile boolean overflowClosed;

    public WebSocketSendPump(IWebSocketSession session, int capacity) {
        this.session = session;
        this.capacity = capacity;
        this.queue = new OverflowBlockingQueue<>(capacity, QueueOverflowPolicy.DROP_NEWEST);
        // drain sequentially on the shared worker pool: order-preserving, no per-connection thread
        this.pump = new SequentialTaskExecutor(GlobalExecutors.globalWorker(), queue, -1);
    }

    /**
     * Enqueues a send action; returns false when the session is closed or the bounded
     * buffer overflowed (in which case the session has been disconnected). The action
     * itself runs later on a shared worker thread and must never be executed on the
     * caller's thread.
     */
    public boolean submit(Runnable sendAction) {
        if (overflowClosed || session.isClosed())
            return false;

        if (!queue.offer(sendAction)) {
            // bounded buffer full = slow consumer: disconnect this session instead of
            // back-pressuring the publishing thread or shared platform carriers
            overflowClosed = true;
            LOG.warn("nop.websocket.send-buffer-overflow:session={},capacity={}; disconnecting session",
                    session, capacity);
            try {
                session.close(CLOSE_OVERFLOW, "send buffer overflow");
            } catch (Throwable e) {
                LOG.warn("nop.websocket.overflow-close-fail:session={}", session, e);
            }
            return false;
        }

        pump.onMessageQueued();
        return true;
    }

    /**
     * Drops all pending sends (session teardown). The drain task exits on its own once it
     * observes the empty queue.
     */
    public void close() {
        queue.clear();
    }
}
