package io.nop.cluster.naming;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.cluster.discovery.ServiceInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C4-004 + CL-011/CL-014 regression (plan 2496 Phase 4, 30-pt table #10 module-side
 * obligation): the AutoRegistration refresh chain must report its failures with the
 * instance business identifier instead of only the executor's generic error log.
 *
 * <ul>
 *   <li>the site guard in {@code refreshRegistration} covers Throwable — an Error from
 *       the naming client is observed at the site (business identifier) and no longer
 *       escapes into the periodic facility;</li>
 *   <li>the chain future carries a {@code whenComplete} alarm: a chain-level terminal
 *       (facility rejection — the audited kill path) is reported via
 *       {@code nop.err.cluster.refresh-registration-chain-broken} with the instance
 *       identity.</li>
 * </ul>
 */
public class TestAutoRegistrationInsulation {

    /** Naming stub whose updateInstance throws a deterministic Error. */
    static class ErrorNamingService implements INamingService {
        @Override
        public void registerInstance(ServiceInstance instance) {
        }

        @Override
        public void unregisterInstance(ServiceInstance instance) {
        }

        @Override
        public void updateInstance(ServiceInstance instance) {
            throw new LinkageError("injected naming-client Error");
        }

        @Override
        public List<String> getServices() {
            return List.of();
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            return List.of();
        }
    }

    /** Recording naming stub for the alarm test (register only). */
    static class RecordingNamingService implements INamingService {
        @Override
        public void registerInstance(ServiceInstance instance) {
        }

        @Override
        public void unregisterInstance(ServiceInstance instance) {
        }

        @Override
        public List<String> getServices() {
            return List.of();
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            return List.of();
        }
    }

    private AutoRegistration registration;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger registrationLogger;

    @BeforeEach
    void setUp() {
        registration = new AutoRegistration();
        registrationLogger = (Logger) LoggerFactory.getLogger(AutoRegistration.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        registrationLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (registration != null) {
            registration.stop();
        }
        if (logAppender != null && registrationLogger != null) {
            registrationLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    private void seedInstance() {
        registration.setNamingService(new RecordingNamingService());
        registration.setServiceName("test-registration-svc");
        registration.setAddr("127.0.0.1");
        registration.setPort(1);
        registration.setAutoUpdate(false);
        // start() seeds the instance field via getServiceInstance() (mock register only).
        registration.start();
    }

    @Test
    void refreshRegistrationThrowableIsContainedAndObserved() {
        seedInstance();
        registration.setNamingService(new ErrorNamingService());

        assertDoesNotThrow(() -> registration.refreshRegistration(),
                "the site guard must contain a Throwable from the naming client");

        long warns = logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("nop.err.cluster.refresh-registration-fail"))
                .count();
        assertTrue(warns >= 1,
                "the Error must be observable via the site's business-identified warn log");
    }

    @Test
    void chainTerminalFiresBusinessIdentifiedAlarm() throws Exception {
        registration.setNamingService(new RecordingNamingService());
        registration.setServiceName("test-registration-svc");
        registration.setAddr("127.0.0.1");
        registration.setPort(1);
        registration.setAutoUpdate(true);
        registration.setAutoUpdateInterval(Duration.ofMillis(60_000));
        registration.start();

        Future<?> chain = readChainFuture();
        assertTrue(chain instanceof CompletableFuture,
                "the insulated chain future must be completable for the alarm test");

        // Simulate the audited kill path: the facility rejects the chain (shared worker
        // pool Abort) — the chain future completes exceptionally with only the executor's
        // generic log. The whenComplete watcher must raise the business-identified alarm.
        ((CompletableFuture<?>) chain).completeExceptionally(
                new RejectedExecutionException("injected facility rejection"));

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (logAppender.list.stream().anyMatch(
                    e -> e.getFormattedMessage().contains("nop.err.cluster.refresh-registration-chain-broken"))) {
                break;
            }
            Thread.sleep(10);
        }
        boolean alarmed = logAppender.list.stream().anyMatch(
                e -> e.getFormattedMessage().contains("nop.err.cluster.refresh-registration-chain-broken"));
        assertTrue(alarmed, "a chain-level terminal must fire the business-identified alarm");
    }

    private Future<?> readChainFuture() throws Exception {
        Field field = AutoRegistration.class.getDeclaredField("autoUpdateTimerFuture");
        field.setAccessible(true);
        return (Future<?>) field.get(registration);
    }
}
