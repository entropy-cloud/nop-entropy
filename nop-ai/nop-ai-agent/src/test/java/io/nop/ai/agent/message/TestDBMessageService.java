package io.nop.ai.agent.message;

import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 tests for {@link DBMessageService}.
 *
 * <p>Each test uses a real H2 in-memory database — no mocks — so the full
 * persistence + polling + delivery chain is exercised end-to-end.
 */
public class TestDBMessageService {

    private DataSource dataSource;
    private String dbUrl;

    @BeforeEach
    void setUp() {
        dbUrl = "jdbc:h2:mem:test-db-msg-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    private int countRows(String statusCondition) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentMessageTable.TABLE_NAME + " WHERE " + statusCondition)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countAllRows() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentMessageTable.TABLE_NAME)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void sendPersistsMessageToDbTable() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "test-sender");
        svc.start();

        try {
            AgentMessageEnvelope env = new AgentMessageEnvelope(
                    "A", "agent.B.inbox", "corr-1", AgentMessageKind.ASYNC, "hello-db");
            svc.send("agent.B.inbox", env);

            Thread.sleep(200);

            assertEquals(1, countAllRows(), "one row should exist in the table after send");
            assertEquals(1, countRows("STATUS = " + AiAgentMessageTable.STATUS_PENDING),
                    "the row should be in PENDING status");
        } finally {
            svc.close();
        }
    }

    @Test
    void subscribeAndPollDeliversMessageToConsumer() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "test-deliver");
        svc.setPollIntervalMs(20);
        svc.start();

        try {
            AtomicReference<Object> received = new AtomicReference<>();
            svc.subscribe("agent.B.inbox", new IMessageConsumer() {
                @Override
                public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                    received.set(message);
                    return null;
                }
            });

            AgentMessageEnvelope env = new AgentMessageEnvelope(
                    "A", "agent.B.inbox", "corr-1", AgentMessageKind.ASYNC, "hello-payload");
            svc.send("agent.B.inbox", env);

            waitForCondition(() -> received.get() != null, 3, TimeUnit.SECONDS);

            assertNotNull(received.get(), "consumer should have received the message");
            assertTrue(received.get() instanceof AgentMessageEnvelope,
                    "consumer should receive an AgentMessageEnvelope, got: " + received.get().getClass());
            AgentMessageEnvelope receivedEnv = (AgentMessageEnvelope) received.get();
            assertEquals("A", receivedEnv.getSenderId());
            assertEquals("agent.B.inbox", receivedEnv.getTargetTopic());
            assertEquals("corr-1", receivedEnv.getCorrelationId());
            assertEquals("hello-payload", receivedEnv.getPayload());
        } finally {
            svc.close();
        }
    }

    @Test
    void competingConsumersOnlyOneInstanceConsumes() throws Exception {
        DBMessageService svcA = new DBMessageService(dataSource, "instance-A");
        DBMessageService svcB = new DBMessageService(dataSource, "instance-B");
        svcA.setPollIntervalMs(20);
        svcB.setPollIntervalMs(20);
        svcA.start();
        svcB.start();

        try {
            AtomicInteger aReceived = new AtomicInteger(0);
            AtomicInteger bReceived = new AtomicInteger(0);

            svcA.subscribe("competing.topic", new IMessageConsumer() {
                @Override
                public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                    aReceived.incrementAndGet();
                    return null;
                }
            });
            svcB.subscribe("competing.topic", new IMessageConsumer() {
                @Override
                public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                    bReceived.incrementAndGet();
                    return null;
                }
            });

            for (int i = 0; i < 5; i++) {
                svcA.send("competing.topic", "msg-" + i);
            }

            waitForCondition(() -> (aReceived.get() + bReceived.get()) == 5, 5, TimeUnit.SECONDS);

            assertEquals(5, aReceived.get() + bReceived.get(),
                    "all 5 messages should be consumed across both instances");
            assertTrue(aReceived.get() + bReceived.get() == 5,
                    "no duplicate delivery: a=" + aReceived.get() + ", b=" + bReceived.get());
        } finally {
            svcA.close();
            svcB.close();
        }
    }

    @Test
    void messageSurvivesRestart() throws Exception {
        DBMessageService sender = new DBMessageService(dataSource, "sender-only");
        sender.start();

        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "persistence.topic", "corr-1", AgentMessageKind.ASYNC, "survive-restart");
        sender.send("persistence.topic", env);

        Thread.sleep(200);
        sender.close();

        assertEquals(1, countRows("STATUS = " + AiAgentMessageTable.STATUS_PENDING),
                "message should still be PENDING in DB after sender closed");

        DBMessageService receiver = new DBMessageService(dataSource, "receiver-only");
        receiver.setPollIntervalMs(20);
        receiver.start();

        try {
            AtomicReference<Object> received = new AtomicReference<>();
            receiver.subscribe("persistence.topic", new IMessageConsumer() {
                @Override
                public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                    received.set(message);
                    return null;
                }
            });

            waitForCondition(() -> received.get() != null, 5, TimeUnit.SECONDS);

            assertNotNull(received.get(), "message should be delivered after restart");
            assertTrue(received.get() instanceof AgentMessageEnvelope);
            AgentMessageEnvelope receivedEnv = (AgentMessageEnvelope) received.get();
            assertEquals("survive-restart", receivedEnv.getPayload());
        } finally {
            receiver.close();
        }
    }

    @Test
    void consumeLaterRetriesMessage() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "test-retry");
        svc.setPollIntervalMs(20);
        svc.start();

        try {
            AtomicInteger attempts = new AtomicInteger(0);

            svc.subscribe("retry.topic", new IMessageConsumer() {
                @Override
                public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 2) {
                        return new ConsumeLater(0);
                    }
                    return null;
                }
            });

            svc.send("retry.topic", "retry-me");

            waitForCondition(() -> attempts.get() >= 2, 5, TimeUnit.SECONDS);

            assertTrue(attempts.get() >= 2, "message should be redelivered at least once");
        } finally {
            svc.close();
        }
    }

    @Test
    void subscriptionCancelStopsDelivery() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "test-cancel");
        svc.setPollIntervalMs(20);
        svc.start();

        try {
            AtomicInteger received = new AtomicInteger(0);
            IMessageSubscription sub = svc.subscribe("cancel.topic", new IMessageConsumer() {
                @Override
                public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                    received.incrementAndGet();
                    return null;
                }
            });

            svc.send("cancel.topic", "before-cancel");
            waitForCondition(() -> received.get() >= 1, 3, TimeUnit.SECONDS);
            assertEquals(1, received.get());

            sub.cancel();
            assertTrue(sub.isCancelled());

            svc.send("cancel.topic", "after-cancel");
            Thread.sleep(500);

            assertEquals(1, received.get(), "no more messages should be delivered after cancel");
        } finally {
            svc.close();
        }
    }

    @Test
    void closeStopsPollingAndReleasesThread() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "test-close");
        svc.setPollIntervalMs(20);
        svc.start();

        svc.subscribe("close.topic", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                return null;
            }
        });

        svc.send("close.topic", "test");
        Thread.sleep(200);

        svc.close();

        svc.send("close.topic", "after-close");
        Thread.sleep(500);

        assertEquals(1, countRows("STATUS = " + AiAgentMessageTable.STATUS_PENDING),
                "after close, new messages should remain PENDING (no polling)");
    }

    @Test
    void consumerCanSendResponseViaContext() throws Exception {
        DBMessageService svc = new DBMessageService(dataSource, "test-context");
        svc.setPollIntervalMs(20);
        svc.start();

        try {
            svc.subscribe("inbox.topic", new IMessageConsumer() {
                @Override
                public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                    context.send("reply.topic", "acknowledged");
                    return null;
                }
            });

            AtomicReference<Object> replyReceived = new AtomicReference<>();
            svc.subscribe("reply.topic", new IMessageConsumer() {
                @Override
                public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                    replyReceived.set(message);
                    return null;
                }
            });

            svc.send("inbox.topic", "trigger");
            waitForCondition(() -> replyReceived.get() != null, 5, TimeUnit.SECONDS);

            assertEquals("acknowledged", replyReceived.get());
        } finally {
            svc.close();
        }
    }

    private void waitForCondition(java.util.function.Supplier<Boolean> condition,
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
