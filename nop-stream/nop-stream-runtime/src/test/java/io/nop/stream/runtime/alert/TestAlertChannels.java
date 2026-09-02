/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.alert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.nop.stream.runtime.event.StreamJobEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-12): AlertChannel abstraction + built-in channels.
 */
class TestAlertChannels {

    /** Recording channel (also the fault-injection assertion device). */
    static final class RecordingChannel implements IAlertChannel {
        final List<AlertEvent> received = new CopyOnWriteArrayList<>();
        final boolean throwOnSend;

        RecordingChannel(boolean throwOnSend) {
            this.throwOnSend = throwOnSend;
        }

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public void send(AlertEvent event) {
            if (throwOnSend) {
                throw new IllegalStateException("channel broken (test)");
            }
            received.add(event);
        }
    }

    private HttpServer receiver;
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresRemaining = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws IOException {
        receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/alert", (HttpExchange exchange) -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (failuresRemaining.getAndDecrement() > 0) {
                send(exchange, 500, "injected receiver failure");
                return;
            }
            receivedBodies.add(body);
            send(exchange, 200, "ok");
        });
        receiver.start();
    }

    @AfterEach
    void tearDown() {
        if (receiver != null) {
            receiver.stop(0);
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String receiverUrl() {
        return "http://127.0.0.1:" + receiver.getAddress().getPort() + "/alert";
    }

    @Test
    void routingTableRoutesFaultEventsOnly() {
        assertEquals(AlertEvent.Severity.ERROR, AlertService.route(StreamJobEvent.EventType.JOB_FAILED));
        assertEquals(AlertEvent.Severity.WARN, AlertService.route(StreamJobEvent.EventType.RECOVERY_STARTED));
        assertEquals(AlertEvent.Severity.WARN, AlertService.route(StreamJobEvent.EventType.JOB_DEGRADED));
        // progress events are not faults — not routed
        assertNull(AlertService.route(StreamJobEvent.EventType.JOB_STARTED));
        assertNull(AlertService.route(StreamJobEvent.EventType.CHECKPOINT_COMPLETED));
        assertNull(AlertService.route(StreamJobEvent.EventType.JOB_FINISHED));
        assertNull(AlertService.route(StreamJobEvent.EventType.JOB_CANCELED));
    }

    @Test
    void alertServiceDeliversRoutedEventsAndContainsChannelFailures() {
        RecordingChannel broken = new RecordingChannel(true);
        RecordingChannel healthy = new RecordingChannel(false);
        AlertService service = new AlertService(List.of(broken, healthy));

        // routed: JOB_FAILED
        service.onEvent(StreamJobEvent.simple("job-a", StreamJobEvent.EventType.JOB_FAILED, "disk full"));
        // non-routed: JOB_STARTED must NOT alert
        service.onEvent(StreamJobEvent.simple("job-a", StreamJobEvent.EventType.JOB_STARTED, null));

        assertEquals(1, healthy.received.size());
        AlertEvent alert = healthy.received.get(0);
        assertEquals("job-a", alert.getJobId());
        assertEquals(AlertEvent.Severity.ERROR, alert.getSeverity());
        assertEquals("JOB_FAILED", alert.getEventType());
        assertTrue(alert.getMessage().contains("disk full"));
        // the broken channel did not break dispatch (contained + observable via WARN log)
        assertTrue(broken.received.isEmpty());
    }

    @Test
    void webhookChannelDeliversJsonAndRetriesOnServerFailure() throws Exception {
        WebhookAlertChannel channel = new WebhookAlertChannel(receiverUrl(), 2_000L, 2);
        try {
            // first delivery: receiver fails twice (500) then succeeds — retry path
            failuresRemaining.set(2);
            channel.send(new AlertEvent("job-w", AlertEvent.Severity.ERROR,
                    "JOB_FAILED", "JOB_FAILED job=job-w cause=boom", System.currentTimeMillis()));

            await(() -> !channel.getDelivered().isEmpty());
            await(() -> receivedBodies.size() >= 1);

            String body = receivedBodies.get(0);
            assertTrue(body.contains("\"jobId\":\"job-w\""), body);
            assertTrue(body.contains("\"severity\":\"ERROR\""), body);
            assertTrue(body.contains("\"eventType\":\"JOB_FAILED\""), body);
            assertTrue(body.contains("\"message\":"));
            assertTrue(body.contains("\"timestamp\":"), body);
        } finally {
            channel.close();
        }
    }

    @Test
    void webhookChannelValidatesConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new WebhookAlertChannel(""));
        assertThrows(IllegalArgumentException.class, () -> new WebhookAlertChannel(null));
        assertThrows(IllegalArgumentException.class, () -> new WebhookAlertChannel("ftp://x/y"));
        assertThrows(IllegalArgumentException.class,
                () -> new WebhookAlertChannel("http://127.0.0.1:1/x", 0L, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new WebhookAlertChannel("http://127.0.0.1:1/x", 1_000L, -1));
    }

    @Test
    void fromPropertiesBuildsChannelsFromConfigKeys() {
        java.util.Map<String, String> props = new java.util.HashMap<>();
        AlertService defaults = AlertService.fromProperties(props::get);
        assertEquals(1, defaults.getChannels().size());
        assertTrue(defaults.getChannels().get(0) instanceof LoggingAlertChannel);

        props.put(AlertService.KEY_WEBHOOK_ENABLED, "true");
        props.put(AlertService.KEY_WEBHOOK_URL, receiverUrl());
        AlertService withWebhook = AlertService.fromProperties(props::get);
        assertEquals(2, withWebhook.getChannels().size());

        // enabled webhook without url fails fast (no silent alert dropping)
        props.put(AlertService.KEY_WEBHOOK_URL, "");
        assertThrows(IllegalArgumentException.class, () -> AlertService.fromProperties(props::get));

        // logging disabled + no webhook = empty service (observable via WARN)
        java.util.Map<String, String> off = new java.util.HashMap<>();
        off.put(AlertService.KEY_LOGGING_ENABLED, "false");
        AlertService empty = AlertService.fromProperties(off::get);
        assertTrue(empty.getChannels().isEmpty());
    }

    @Test
    void loggingChannelSendsWithoutError() {
        LoggingAlertChannel channel = new LoggingAlertChannel();
        assertEquals("logging", channel.getName());
        channel.send(new AlertEvent("job-l", AlertEvent.Severity.WARN,
                "JOB_DEGRADED", "JOB_DEGRADED job=job-l", System.currentTimeMillis()));
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50L);
        }
    }
}
