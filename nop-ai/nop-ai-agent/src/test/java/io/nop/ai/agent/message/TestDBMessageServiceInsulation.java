package io.nop.ai.agent.message;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-07/C3-027 + C7-029 regression (plan 2496 Phase 4, 30-pt table #26/#27): a
 * Throwable (incl. Error) thrown while delivering a message must not kill the
 * single poller thread. Pre-fix the per-topic guard in {@code pollAllTopics}
 * caught only {@code Exception} — an Error escaped the JDK
 * {@code scheduleWithFixedDelay} round, the poller died permanently and every
 * topic on the consumer silently stopped being delivered (the at-least-once
 * poller heartbeat stops). Post-fix the guard catches Throwable per topic, the
 * poller keeps delivering and the failed round is observable.
 */
public class TestDBMessageServiceInsulation {

    private DataSource dataSource;
    private DBMessageService svc;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-db-msg-insulation-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;

        svc = new DBMessageService(dataSource, "insulation-test");
        svc.setPollIntervalMs(30);
        svc.setSweepIntervalMs(60_000);

        serviceLogger = (Logger) LoggerFactory.getLogger(DBMessageService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (svc != null) {
            svc.close();
        }
        if (logAppender != null && serviceLogger != null) {
            serviceLogger.detachAppender(logAppender);
            logAppender.stop();
        }
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort teardown
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

    @Test
    void testPollerSurvivesHandlerErrorAndKeepsDelivering() throws Exception {
        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch secondDelivered = new CountDownLatch(1);
        IMessageConsumer handler = (topic, message, context) -> {
            if (deliveries.incrementAndGet() == 1) {
                // Deterministic Error escape (C3-009 injection form: LinkageError-derived)
                throw new LinkageError("injected deliver Error");
            }
            secondDelivered.countDown();
            return CompletableFuture.completedFuture(null);
        };

        svc.start();
        svc.subscribe("agent.X.inbox", handler);

        svc.send("agent.X.inbox", new AgentMessageEnvelope(
                "A", "agent.X.inbox", "corr-1", AgentMessageKind.ASYNC, "boom-message"));

        // Wait for the first delivery attempt (the Error round).
        long deadline = System.currentTimeMillis() + 10_000;
        while (deliveries.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(deliveries.get() >= 1, "first message must be attempted");

        // Pre-fix the LinkageError escaped pollTopic's inner Exception catch into the
        // per-topic Exception guard → out of the poll round → the poller thread's
        // periodic task was cancelled permanently. Post-fix the Error is caught per
        // topic and the poller keeps polling.
        svc.send("agent.X.inbox", new AgentMessageEnvelope(
                "A", "agent.X.inbox", "corr-2", AgentMessageKind.ASYNC, "recovery-message"));

        assertTrue(secondDelivered.await(10, TimeUnit.SECONDS),
                "poller must survive the injected Error and deliver the next message");

        long errors = logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("db-poll-error"))
                .count();
        assertTrue(errors >= 1,
                "the Error round must be observable via the per-topic poll error log");

        // The failed round's message stays CLAIMED (its claim release belongs to the
        // stale-CLAIMED sweep, out of scope here) — only the recovery message flows on.
        assertTrue(countRows("STATUS = " + AiAgentMessageTable.STATUS_PENDING) <= 1,
                "no unexpected PENDING accumulation beyond the recovery message");
    }

    /**
     * DataSource stub: real connections until {@link #armError()}, then every
     * {@code getConnection} throws a deterministic Error — injected straight into the
     * sweep leg ({@code sweepStaleClaimedMessages} opens its own connection).
     */
    static final class SweepErrorDataSource extends SimpleDataSource {
        private final SimpleDataSource delegate;
        private final AtomicBoolean errorArmed = new AtomicBoolean(false);

        SweepErrorDataSource(SimpleDataSource delegate) {
            this.delegate = delegate;
        }

        void armError() {
            errorArmed.set(true);
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (errorArmed.get()) {
                throw new LinkageError("injected sweep Error");
            }
            return delegate.getConnection();
        }
    }

    /**
     * MA-07/C7-029 regression (plan 2496 Phase 4, second DBMessageService face): the
     * at-least-once sweep task on the shared poller thread must survive a deterministic
     * Error. Pre-fix {@code sweepStaleClaimedMessagesSafely} caught only {@code Exception}
     * — an Error escaping the wrapper killed the sweep task on the shared single-thread
     * poller permanently and silently (the at-least-once safety net died). Post-fix the
     * Error is caught and observed, and the sweep keeps firing.
     *
     * <p>The poll interval is set far outside the test window so only the sweep rounds
     * run — the observed {@code db-sweep-stale-claimed-error} rounds are attributable to
     * the sweep face alone.
     */
    @Test
    void sweepFaceSurvivesInjectedErrorAndKeepsSweeping() throws Exception {
        // The setUp instance polls for real; this test drives its own dedicated instance.
        svc.close();
        svc = null;

        SimpleDataSource real = new SimpleDataSource();
        real.setDriverClassName("org.h2.Driver");
        real.setUrl("jdbc:h2:mem:test-db-msg-sweep-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        real.setUsername("sa");
        real.setPassword("");
        SweepErrorDataSource ds = new SweepErrorDataSource(real);

        DBMessageService sweepSvc = new DBMessageService(ds, "insulation-sweep-test");
        sweepSvc.setPollIntervalMs(60_000);
        sweepSvc.setSweepIntervalMs(30);
        try {
            // initSchema runs through the un-armed leg; afterwards every sweep round
            // hits the injected Error at getConnection.
            sweepSvc.start();
            ds.armError();

            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                long errors = logAppender.list.stream()
                        .filter(e -> e.getFormattedMessage()
                                .contains("db-sweep-stale-claimed-error"))
                        .count();
                if (errors >= 2) {
                    break;
                }
                Thread.sleep(10);
            }
            long errors = logAppender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("db-sweep-stale-claimed-error"))
                    .count();
            assertTrue(errors >= 2,
                    "the sweep must survive the injected Error and keep firing (>=2 observed "
                            + "rounds prove the scheduled task was not cancelled by the first one): "
                            + "rounds=" + errors);
        } finally {
            sweepSvc.close();
        }
    }
}
