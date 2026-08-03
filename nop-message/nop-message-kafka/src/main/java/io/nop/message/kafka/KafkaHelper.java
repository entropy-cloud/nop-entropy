/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.util.ApiHeaders;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared conversion helpers between Kafka records and {@link ApiMessage}, mirroring
 * {@code PulsarHelper.buildApiMessage} / {@code buildPulsarMessage}.
 *
 * <p>Kafka carries message metadata as: record {@code key} (string), {@code value}
 * (bytes), {@code timestamp}, {@code partition}, {@code offset}, plus a
 * {@link Headers} collection (byte[] values). {@code ApiMessage} headers are
 * string-typed, so header values are encoded with {@link #encodeValue} on send and
 * decoded from UTF-8 bytes on receive.
 *
 * <p>The Kafka record key maps to the {@code ApiMessage} biz-key header (mirrors
 * PulsarHelper mapping the Pulsar message key to {@code HEADER_BIZ_KEY}).
 */
public final class KafkaHelper {
    private KafkaHelper() {
    }

    /**
     * Builds an {@link ApiRequest} from a delivered Kafka consumer record's components
     * (mirrors {@code PulsarHelper.buildApiMessage}). The record {@code value} becomes
     * the {@code ApiRequest.data}; key/headers/timestamp/offset/partition are mapped to
     * headers so the consumer can round-trip them on a reply if needed.
     */
    public static ApiMessage buildApiMessage(String topic, int partition, long offset,
                                             long timestamp, String key, Object value,
                                             Headers headers) {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(value);

        if (key != null) {
            ApiHeaders.setBizKey(request, key);
        }

        request.setHeader(ApiConstants.HEADER_TOPIC, topic);
        request.setHeader(HEADER_KAFKA_PARTITION, partition);
        request.setHeader(HEADER_KAFKA_OFFSET, offset);

        if (timestamp > 0) {
            request.setHeader(ApiConstants.HEADER_EVENT_TIME, timestamp);
        }

        if (headers != null) {
            for (Header header : headers) {
                String decoded = decodeValue(header.value());
                if (decoded != null) {
                    request.setHeader(header.key(), decoded);
                }
            }
        }
        return request;
    }

    /**
     * Extracts the Kafka record key from an {@link ApiMessage}'s biz-key header
     * (inverse of {@link #buildApiMessage}).
     */
    public static String extractKey(ApiMessage message) {
        if (message == null) {
            return null;
        }
        return ApiHeaders.getBizKey(message);
    }

    /**
     * Copies an {@link ApiMessage}'s headers into a Kafka {@link Headers} sink (encoded
     * to UTF-8 bytes), skipping the biz-key header (Kafka carries it as the record key,
     * not a header — mirrors {@code PulsarHelper._buildPulsarMessage} skipping
     * {@code HEADER_BIZ_KEY}).
     */
    public static void copyHeaders(Headers sink, ApiMessage source) {
        if (source == null || !source.hasHeaders()) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.getHeaders().entrySet()) {
            String name = entry.getKey();
            if (ApiConstants.HEADER_BIZ_KEY.equals(name)) {
                continue;
            }
            String encoded = encodeValue(entry.getValue());
            if (encoded == null) {
                continue;
            }
            sink.remove(name);
            sink.add(name, encoded.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Resolves the effective send delay (milliseconds) from {@link MessageSendOptions},
     * returning {@code <= 0} when no delay was requested. Kafka has no native delayed
     * delivery (unlike Pulsar's {@code deliverAfter}); the producer ignores a positive
     * delay and a warn is logged at the call-site. Kept as a helper so the policy is
     * single-sourced.
     */
    public static long resolveDelay(MessageSendOptions options) {
        if (options == null) {
            return 0L;
        }
        return Math.max(options.getDelay(), 0L);
    }

    /**
     * Encodes an arbitrary header value to a String (mirrors
     * {@code PulsarHelper.encodeValue}). Strings / numbers / booleans are passed through
     * literally; complex objects are JSON-serialized.
     */
    public static String encodeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return JSON.stringify(value);
    }

    /**
     * Decodes a raw header byte[] (Kafka native) to a String for the {@code ApiMessage}
     * header map. Returns {@code null} for null/empty input.
     */
    public static String decodeValue(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    /**
     * Adapts a checked Kafka exception (producer send callback) into a
     * {@link NopException}, preserving the cause. Mirrors
     * {@code NopException.adapt(e)} usage in PulsarMessageService.
     */
    public static RuntimeException adapt(Exception e) {
        return NopException.adapt(e);
    }

    static final String HEADER_KAFKA_PARTITION = "nop-kafka-partition";
    static final String HEADER_KAFKA_OFFSET = "nop-kafka-offset";
}
