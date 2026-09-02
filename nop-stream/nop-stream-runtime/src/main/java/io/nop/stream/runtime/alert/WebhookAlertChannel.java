/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.alert;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item 16 (P-REQ-12): webhook alert channel — HTTP POST of the alert as JSON
 * (JDK HttpClient, zero new framework dependency). URL / timeout / retry
 * count are configurable.
 *
 * <p>Delivery is asynchronous on a bounded single-thread executor: the alert
 * path is invoked synchronously from the job event bus (recovery / failure
 * events), so a slow or unreachable webhook endpoint must never stall job
 * control flow. When the bounded queue is full, alerts are dropped with a
 * WARN (alerting is best-effort by contract; the logging channel remains the
 * always-on record). Delivery results (including final failure after all
 * retries) are logged with the channel identity — never silently swallowed.
 */
public class WebhookAlertChannel implements IAlertChannel {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookAlertChannel.class);

    /** Fixed backoff between retries (ms) — conservative, the channel is best-effort. */
    private static final long RETRY_BACKOFF_MS = 200L;

    /** Bounded async queue capacity (overflow = drop with WARN). */
    private static final int QUEUE_CAPACITY = 256;

    public static final long DEFAULT_TIMEOUT_MS = 5_000L;
    public static final int DEFAULT_RETRIES = 2;

    private final URI url;
    private final long timeoutMs;
    private final int retries;
    private final HttpClient client;
    private final BlockingQueue<AlertEvent> pending;
    private final java.util.concurrent.ExecutorService deliveryExecutor;
    private final List<AlertEvent> delivered = new CopyOnWriteArrayList<>();

    public WebhookAlertChannel(String url) {
        this(url, DEFAULT_TIMEOUT_MS, DEFAULT_RETRIES);
    }

    public WebhookAlertChannel(String url, long timeoutMs, int retries) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("webhook alert channel requires a non-blank url");
        }
        try {
            this.url = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("webhook alert channel url is not a valid URI: " + url, e);
        }
        if (!"http".equalsIgnoreCase(this.url.getScheme()) && !"https".equalsIgnoreCase(this.url.getScheme())) {
            throw new IllegalArgumentException(
                    "webhook alert channel url must be http(s): " + url);
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("webhook timeout must be positive: " + timeoutMs);
        }
        if (retries < 0) {
            throw new IllegalArgumentException("webhook retries must be >= 0: " + retries);
        }
        this.timeoutMs = timeoutMs;
        this.retries = retries;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.pending = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.deliveryExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "stream-alert-webhook");
            t.setDaemon(true);
            return t;
        });
        this.deliveryExecutor.execute(this::drainLoop);
    }

    @Override
    public String getName() {
        return "webhook";
    }

    @Override
    public void send(AlertEvent event) {
        if (!pending.offer(event)) {
            LOG.warn("webhook alert queue full (capacity {}) — dropping alert {} for job {} "
                    + "(best-effort contract; the logging channel retains the record)",
                    QUEUE_CAPACITY, event.getEventType(), event.getJobId());
        }
    }

    /** Delivered alerts (test observability). */
    public List<AlertEvent> getDelivered() {
        return delivered;
    }

    public void close() {
        deliveryExecutor.shutdownNow();
    }

    private void drainLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                AlertEvent event = pending.poll(1, TimeUnit.SECONDS);
                if (event == null) {
                    continue;
                }
                deliverWithRetries(event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void deliverWithRetries(AlertEvent event) {
        String body = "{\"jobId\":" + json(event.getJobId())
                + ",\"severity\":\"" + event.getSeverity() + "\""
                + ",\"eventType\":" + json(event.getEventType())
                + ",\"message\":" + json(event.getMessage())
                + ",\"timestamp\":" + event.getTimestamp() + "}";

        for (int attempt = 0; attempt <= retries; attempt++) {
            if (attempt > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_BACKOFF_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(url)
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    delivered.add(event);
                    return;
                }
                LOG.warn("webhook alert delivery attempt {}/{} returned {} for job {} (type={})",
                        attempt + 1, retries + 1, response.statusCode(),
                        event.getJobId(), event.getEventType());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.warn("webhook alert delivery attempt {}/{} failed for job {} (type={}): {}",
                        attempt + 1, retries + 1, event.getJobId(), event.getEventType(), e.toString());
            }
        }
        LOG.error("webhook alert delivery gave up after {} attempts for job {} (type={}, message={})",
                retries + 1, event.getJobId(), event.getEventType(), event.getMessage());
    }

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        // minimal JSON string escaping
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
