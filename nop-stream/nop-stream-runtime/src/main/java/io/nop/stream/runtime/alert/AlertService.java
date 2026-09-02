/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.runtime.event.StreamJobEvent;
import io.nop.stream.runtime.event.StreamJobEventListener;

/**
 * Item 16 (P-REQ-12): alert service — a job event listener that routes
 * fault-semantic events to every configured {@link IAlertChannel}. Channel
 * failures are logged with the channel identity and never rethrown (an alert
 * channel must not break the job control path).
 *
 * <p>Routing table (the documented contract — non-routed events are progress
 * noise, not faults):
 * <ul>
 *   <li>{@code JOB_FAILED} → severity ERROR</li>
 *   <li>{@code RECOVERY_STARTED} → severity WARN</li>
 *   <li>{@code JOB_DEGRADED} → severity WARN</li>
 * </ul>
 *
 * <p>Config keys (user-visible contract, documented in the owner doc):
 * <ul>
 *   <li>{@code nop.stream.alert.logging.enabled} (default true)</li>
 *   <li>{@code nop.stream.alert.webhook.enabled} (default false)</li>
 *   <li>{@code nop.stream.alert.webhook.url} (required when webhook enabled)</li>
 *   <li>{@code nop.stream.alert.webhook.timeout-ms} (default 5000)</li>
 *   <li>{@code nop.stream.alert.webhook.retries} (default 2)</li>
 * </ul>
 */
public class AlertService implements StreamJobEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(AlertService.class);

    public static final String KEY_LOGGING_ENABLED = "nop.stream.alert.logging.enabled";
    public static final String KEY_WEBHOOK_ENABLED = "nop.stream.alert.webhook.enabled";
    public static final String KEY_WEBHOOK_URL = "nop.stream.alert.webhook.url";
    public static final String KEY_WEBHOOK_TIMEOUT_MS = "nop.stream.alert.webhook.timeout-ms";
    public static final String KEY_WEBHOOK_RETRIES = "nop.stream.alert.webhook.retries";

    private final List<IAlertChannel> channels;

    public AlertService(List<IAlertChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    /**
     * Builds the service from config properties: logging channel on by
     * default, webhook channel only when explicitly enabled (url required —
     * an enabled webhook without a url fails fast instead of silently
     * dropping alerts).
     */
    public static AlertService fromProperties(Function<String, String> props) {
        List<IAlertChannel> channels = new ArrayList<>();
        if (parseBoolean(props.apply(KEY_LOGGING_ENABLED), true)) {
            channels.add(new LoggingAlertChannel());
        }
        if (parseBoolean(props.apply(KEY_WEBHOOK_ENABLED), false)) {
            String url = props.apply(KEY_WEBHOOK_URL);
            long timeoutMs = parseLong(props.apply(KEY_WEBHOOK_TIMEOUT_MS),
                    WebhookAlertChannel.DEFAULT_TIMEOUT_MS);
            int retries = (int) parseLong(props.apply(KEY_WEBHOOK_RETRIES),
                    WebhookAlertChannel.DEFAULT_RETRIES);
            channels.add(new WebhookAlertChannel(url, timeoutMs, retries));
        }
        if (channels.isEmpty()) {
            LOG.warn("AlertService built with NO channels ({}=false and webhook disabled) — "
                    + "fault events will not be delivered anywhere", KEY_LOGGING_ENABLED);
        }
        return new AlertService(channels);
    }

    public List<IAlertChannel> getChannels() {
        return channels;
    }

    /** Severity routing: null = event is not fault-semantic, not alerted. */
    public static AlertEvent.Severity route(StreamJobEvent.EventType type) {
        switch (type) {
            case JOB_FAILED:
                return AlertEvent.Severity.ERROR;
            case RECOVERY_STARTED:
            case JOB_DEGRADED:
                return AlertEvent.Severity.WARN;
            default:
                return null;
        }
    }

    @Override
    public void onEvent(StreamJobEvent event) {
        AlertEvent.Severity severity = route(event.getType());
        if (severity == null || channels.isEmpty()) {
            return;
        }
        String message = buildMessage(event);
        AlertEvent alert = new AlertEvent(event.getJobId(), severity,
                event.getType().name(), message, event.getTimestamp());
        for (IAlertChannel channel : channels) {
            try {
                channel.send(alert);
            } catch (Exception e) {
                LOG.warn("alert channel '{}' failed to deliver {} for job {}: {}",
                        channel.getName(), alert.getEventType(), alert.getJobId(), e.toString(), e);
            }
        }
    }

    private static String buildMessage(StreamJobEvent event) {
        StringBuilder sb = new StringBuilder(event.getType().name()).append(" job=").append(event.getJobId());
        if (event.getCheckpointId() != null) {
            sb.append(" checkpointId=").append(event.getCheckpointId());
        }
        if (event.getCause() != null) {
            sb.append(" cause=").append(event.getCause());
        }
        return sb.toString();
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOG.warn("invalid numeric config value '{}' — using default {}", value, defaultValue);
            return defaultValue;
        }
    }
}
