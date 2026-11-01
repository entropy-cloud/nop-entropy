package io.nop.integration.feishu.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1-009 / C6-021 / C3-015 (plan 2522): feishu stream lifecycle concurrency pins.
 *
 * <ul>
 *   <li>红① (C3-015): a heartbeat send blocked in the transport must not occupy the
 *       single scheduler thread indefinitely — send is bounded and the failure path
 *       hands back to reconnect scheduling.</li>
 *   <li>红② (C1-009): onClose/onError double-terminal sequence must schedule at most
 *       one reconnect (single-flight), and a stale callback after stop must not
 *       reconnect (generation guard).</li>
 *   <li>红③ (C6-021): a connect completing after stop must be torn down by the
 *       generation guard — no orphan live connection (connectCount - closeCount ≤ 1).</li>
 * </ul>
 */
@Timeout(60)
public class TestFeishuClientLifecycleConcurrency {

    /** Programmable transport: blocking send / blocking connect / counters. */
    static class ProgrammableTransport implements IStreamTransport {
        final AtomicInteger connectCount = new AtomicInteger();
        final AtomicInteger closeCount = new AtomicInteger();
        volatile CountDownLatch sendGate;
        volatile CountDownLatch connectGate;
        volatile boolean failSend;
        final List<byte[]> sentFrames = new CopyOnWriteArrayList<>();
        private volatile IStreamListener listener;

        @Override
        public void connect(URI uri, IStreamListener listener) {
            connectCount.incrementAndGet();
            this.listener = listener;
            CountDownLatch gate = connectGate;
            if (gate != null) {
                try {
                    gate.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            listener.onOpen();
        }

        @Override
        public void send(byte[] frameBytes) {
            CountDownLatch gate = sendGate;
            if (gate != null) {
                try {
                    gate.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failSend)
                throw new NopTestTransportException("injected send failure");
            sentFrames.add(frameBytes);
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
            listener = null;
        }

        void fireClose() {
            IStreamListener l = listener;
            if (l != null)
                l.onClose();
        }

        void fireError(Throwable t) {
            IStreamListener l = listener;
            if (l != null)
                l.onError(t);
        }
    }

    static final class NopTestTransportException extends RuntimeException {
        NopTestTransportException(String msg) {
            super(msg);
        }
    }

    /** Manual scheduler: commands are queued for the test to execute deterministically. */
    static class ManualScheduler implements ScheduledExecutorService {
        final List<Runnable> queued = new ArrayList<>();
        final AtomicInteger executions = new AtomicInteger();
        private final ScheduledExecutorService delegate = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

        int drainCount() {
            int n = queued.size();
            List<Runnable> batch = new ArrayList<>(queued);
            queued.clear();
            for (Runnable r : batch) {
                executions.incrementAndGet();
                r.run();
            }
            return n;
        }

        int pending() {
            return queued.size();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            queued.add(command);
            return delegate.schedule(() -> {
            }, 0, TimeUnit.MILLISECONDS);
        }

        // --- unused decorative methods delegate to a discarded executor ---
        @Override
        public void shutdown() {
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.concurrent.Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(
                java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(
                java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks,
                               long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay,
                                               TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                                      TimeUnit unit) {
            queued.add(command);
            return delegate.schedule(() -> {
            }, 0, TimeUnit.MILLISECONDS);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                                         TimeUnit unit) {
            throw new UnsupportedOperationException();
        }


        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }
    }

    private ProgrammableTransport transport;
    private TestFeishuClient.FakeFeishuHttpApi httpApi;
    private ManualScheduler scheduler;
    private FeishuClient client;

    @BeforeEach
    void setUp() {
        transport = new ProgrammableTransport();
        httpApi = new TestFeishuClient.FakeFeishuHttpApi();
        scheduler = new ManualScheduler();
        client = new FeishuClient(transport, httpApi, scheduler);
    }

    @AfterEach
    void tearDown() {
        transport.sendGate = null;
        transport.connectGate = null;
        client.stop();
        scheduler.delegate.shutdownNow();
    }

    /** start 落入 ManualScheduler 队列的 heartbeat 任务是无害 no-op——先排干再精确计数。 */
    private void drainHeartbeatTask() {
        scheduler.drainCount();
    }

    private FeishuCredentials creds() {
        FeishuCredentials c = new FeishuCredentials();
        c.setAppId("cli_app");
        c.setAppSecret("secret");
        return c;
    }

    /**
     * 红① (C3-015) failure-single-point pin: a send failure on any call site must hit
     * the unified failure handler — connected=false, transport closed, reconnect
     * scheduled. Defect state: sendHeartbeat swallows the exception (LOG.debug) and the
     * client stays "connected" with a dead transport (no reconnect).
     * Companion source pin: JdkStreamTransport.send must carry the bounded budget —
     * the production bound lives inside the transport (fake sends cannot observe it).
     */
    @Test
    public void sendFailureTriggersReconnectNotSilentSwallow() throws Exception {
        client.start(creds(), new IMessageHandler() {
            @Override
            public void onMessage(FeishuInboundMessage message) {
            }

            @Override
            public void onError(Throwable error) {
            }
        });
        assertTrue(client.isConnected(), "start must reach connected");
        drainHeartbeatTask();
        int closesBefore = transport.closeCount.get();

        transport.failSend = true;
        client.sendHeartbeat();

        assertFalse(client.isConnected(),
                "C3-015: heartbeat send failure must mark the client disconnected (defect:"
                        + " swallowed with LOG.debug, connected stays true)");
        assertTrue(transport.closeCount.get() > closesBefore,
                "C3-015: send failure must close the dead transport (MA-04 failure single point)");
        assertEquals(1, scheduler.pending(),
                "C3-015: send failure must schedule exactly one reconnect via the single-flight path");

        // reconnect executes and restores the connection (the injected failure applied
        // to the heartbeat hop only)
        transport.failSend = false;
        scheduler.drainCount();
        assertTrue(client.isConnected(), "reconnect must restore connected state");
        assertEquals(2, transport.connectCount.get(), "exactly one reconnect connect");

        // companion source pin: the production send budget line exists in the transport
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/io/nop/integration/feishu/client/JdkStreamTransport.java"));
        assertTrue(source.contains("get(sendTimeoutMillis"),
                "C3-015: JdkStreamTransport.send must apply the send timeout budget"
                        + " (bounded get) — unbounded get() is the half-open deadlock");
    }

    /**
     * 红② (C1-009): onClose+onError double-terminal must produce exactly ONE reconnect
     * connect (single-flight), and a stale reconnect command after stop must not connect
     * (generation guard). Defect: double schedule → connectCount==2; stale command runs
     * post-stop → connectCount increments after stop.
     */
    @Test
    public void doubleTerminalSchedulesSingleReconnectAndStaleCommandDropped() {
        client.start(creds(), new IMessageHandler() {
            @Override
            public void onMessage(FeishuInboundMessage message) {
            }

            @Override
            public void onError(Throwable error) {
            }
        });
        int connectsAfterStart = transport.connectCount.get();
        assertEquals(1, connectsAfterStart, "start performs exactly one connect");
        drainHeartbeatTask();

        // double-terminal sequence
        transport.fireClose();
        transport.fireError(new RuntimeException("injected transport error"));
        int queued = scheduler.pending();
        assertEquals(1, queued,
                "C1-009: double-terminal onClose/onError must be deduplicated to a single"
                        + " in-flight reconnect (single-flight CAS)");

        scheduler.drainCount();
        int afterReconnect = transport.connectCount.get();
        assertEquals(connectsAfterStart + 1, afterReconnect,
                "C1-009: double-terminal must not double-connect (defect: connectCount==2)");
        assertTrue(client.isConnected(), "the single reconnect must restore the connection");
        assertEquals(0, scheduler.pending(),
                "a successful reconnect closes the single-flight window without successors");

        // stale command: a reconnect scheduled BEFORE stop executing AFTER stop
        transport.fireClose();
        assertEquals(1, scheduler.pending(), "one reconnect queued");
        client.stop();
        client.stop();
        int connectsAtStop = transport.connectCount.get();
        scheduler.drainCount();
        assertEquals(connectsAtStop, transport.connectCount.get(),
                "C6-021/C1-009: a stale reconnect command executing after stop must not"
                        + " open a new connection (generation guard)");
        assertFalse(client.isConnected(), "post-stop execution must not restore connected");
    }

    /**
     * 红③ (C6-021): a reconnect connect still in flight when stop() returns must be
     * torn down by the generation guard — the stale connection is closed after the
     * gate release and the late onOpen does not mark the client connected.
     * Defect: the in-flight connect completes post-stop, onOpen marks connected=true,
     * and nothing closes the connection (closeCount frozen at the stop value).
     */
    @Test
    public void connectCompletingAfterStopIsTornDown() throws Exception {
        client.start(creds(), new IMessageHandler() {
            @Override
            public void onMessage(FeishuInboundMessage message) {
            }

            @Override
            public void onError(Throwable error) {
            }
        });
        assertTrue(client.isConnected(), "start must reach connected");
        drainHeartbeatTask();

        // force a reconnect round whose connect blocks on the gate
        transport.connectGate = new CountDownLatch(1);
        transport.fireClose();
        assertEquals(1, scheduler.pending(), "close schedules exactly one reconnect");
        Thread attempt = new Thread(scheduler::drainCount);
        attempt.start();
        for (int i = 0; i < 100 && transport.connectCount.get() < 2; i++)
            Thread.sleep(10);
        assertEquals(2, transport.connectCount.get(), "reconnect connect issued");

        // stop while the connect is still in flight
        client.stop();
        int closeCountAtStop = transport.closeCount.get();
        assertFalse(client.isConnected(), "stop must disconnect");

        transport.connectGate.countDown();
        attempt.join(10_000);
        assertFalse(attempt.isAlive(), "reconnect attempt must finish after gate release");

        // the post-stop connection must have been closed by the generation guard
        assertTrue(transport.closeCount.get() > closeCountAtStop,
                "C6-021: connect completing after stop must be closed by the generation guard"
                        + " — closeCount frozen at " + closeCountAtStop
                        + " with connectCount=" + transport.connectCount.get());
        assertFalse(client.isConnected(),
                "C6-021: late onOpen of a stale-generation connection must not mark connected");
    }
}
