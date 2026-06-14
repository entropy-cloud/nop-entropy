package io.nop.ai.agent.message;

import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 cross-instance end-to-end tests for {@link DBMessageService}.
 *
 * <p>These tests prove that messages travel through the database (not
 * in-memory) by using two independent {@code DBMessageService} instances
 * — separate poller threads, separate consumer maps, no shared memory
 * state — that communicate only through a shared H2 database.
 */
public class TestDBMessageServiceCrossInstance {

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        String dbUrl = "jdbc:h2:mem:test-cross-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    private DBMessageService newStartedService(String consumerId) {
        DBMessageService svc = new DBMessageService(dataSource, consumerId);
        svc.setPollIntervalMs(20);
        svc.start();
        return svc;
    }

    @Test
    void crossInstanceSendAsync() throws Exception {
        DBMessageService instanceA = newStartedService("instance-A");
        DBMessageService instanceB = newStartedService("instance-B");

        try {
            AtomicReference<Object> receivedByA = new AtomicReference<>();

            instanceA.subscribe("agent.B.inbox", (topic, message, context) -> {
                receivedByA.set(message);
                return null;
            });

            AgentMessageEnvelope envelope = new AgentMessageEnvelope(
                    "sender-X", "agent.B.inbox", null, AgentMessageKind.ASYNC, "cross-instance-payload");
            instanceB.send("agent.B.inbox", envelope);

            waitFor(() -> receivedByA.get() != null, 5, TimeUnit.SECONDS);

            assertNotNull(receivedByA.get(), "instance A should receive message sent by instance B via DB");
            assertTrue(receivedByA.get() instanceof AgentMessageEnvelope);
            AgentMessageEnvelope received = (AgentMessageEnvelope) receivedByA.get();
            assertEquals("cross-instance-payload", received.getPayload());
            assertEquals("sender-X", received.getSenderId());
            assertEquals("agent.B.inbox", received.getTargetTopic());
        } finally {
            instanceA.close();
            instanceB.close();
        }
    }

    @Test
    void crossInstanceRequestResponseViaLocalAgentMessenger() throws Exception {
        DBMessageService dbA = newStartedService("db-A");
        DBMessageService dbB = newStartedService("db-B");

        try {
            LocalAgentMessenger messengerA = new LocalAgentMessenger(dbA);
            LocalAgentMessenger messengerB = new LocalAgentMessenger(dbB);

            messengerB.registerHandler("agent.B.inbox",
                    envelope -> "reply:" + envelope.getPayload());

            AgentMessageEnvelope request = new AgentMessageEnvelope(
                    "A", "agent.B.inbox", "corr-cross-1", AgentMessageKind.REQUEST, "ping-cross");

            CompletableFuture<Object> future = messengerA.request(request, Duration.ofSeconds(5));

            Object result = future.get(10, TimeUnit.SECONDS);

            assertEquals("reply:ping-cross", result);
            assertTrue(future.isDone());
        } finally {
            dbA.close();
            dbB.close();
        }
    }

    @Test
    void crossInstanceRequestResponseTimesOutWhenNoResponder() throws Exception {
        DBMessageService dbA = newStartedService("db-A");

        try {
            LocalAgentMessenger messengerA = new LocalAgentMessenger(dbA);

            AgentMessageEnvelope request = new AgentMessageEnvelope(
                    "A", "agent.B.inbox", "corr-no-resp", AgentMessageKind.REQUEST, "ping");

            CompletableFuture<Object> future = messengerA.request(request, Duration.ofMillis(300));

            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof TimeoutException,
                    "expected TimeoutException but got: " + ex.getCause());
        } finally {
            dbA.close();
        }
    }

    @Test
    void backwardCompatibilityNoOpDefaultFailFast() {
        IAgentMessenger noOp = NoOpAgentMessenger.noOp();

        AgentMessageEnvelope request = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c1", AgentMessageKind.REQUEST, "p");

        assertThrows(UnsupportedOperationException.class,
                () -> noOp.request(request, Duration.ofMillis(50)));
    }

    private void waitFor(java.util.function.Supplier<Boolean> condition,
                         long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(20);
        }
    }
}
