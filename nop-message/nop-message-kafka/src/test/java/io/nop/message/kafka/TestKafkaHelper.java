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
import io.nop.api.core.util.ApiHeaders;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestKafkaHelper extends JunitBaseTestCase {

    @Test
    void buildApiMessageMapsRecordComponentsToHeaders() {
        Headers headers = new RecordHeaders();
        headers.add("custom-header", "custom-value".getBytes(StandardCharsets.UTF_8));

        ApiMessage msg = KafkaHelper.buildApiMessage("test-topic", 2, 42L,
                1000L, "biz-key-1", "hello", headers);

        assertNotNull(msg);
        assertEquals("hello", msg.getData());
        assertEquals("biz-key-1", ApiHeaders.getBizKey(msg));
        assertEquals("test-topic", msg.getHeader(ApiConstants.HEADER_TOPIC));
        assertEquals(2, msg.getHeader(KafkaHelper.HEADER_KAFKA_PARTITION));
        assertEquals(42L, msg.getHeader(KafkaHelper.HEADER_KAFKA_OFFSET));
        assertEquals(1000L, msg.getHeader(ApiConstants.HEADER_EVENT_TIME));
        assertEquals("custom-value", msg.getHeader("custom-header"));
    }

    @Test
    void buildApiMessageWithNullKeyAndEmptyHeadersProducesMinimalMessage() {
        ApiMessage msg = KafkaHelper.buildApiMessage("t", 0, 0L, 0L, null, 123, null);

        assertNotNull(msg);
        assertEquals(123, msg.getData());
        assertNull(ApiHeaders.getBizKey(msg));
    }

    @Test
    void extractKeyReturnsBizKeyHeader() {
        ApiMessage msg = new io.nop.api.core.beans.ApiRequest<>();
        ApiHeaders.setBizKey(msg, "the-key");
        assertEquals("the-key", KafkaHelper.extractKey(msg));
    }

    @Test
    void extractKeyReturnsNullForNullMessage() {
        assertNull(KafkaHelper.extractKey(null));
    }

    @Test
    void copyHeadersSkipsBizKeyAndEncodesValues() {
        io.nop.api.core.beans.ApiRequest<Object> msg = new io.nop.api.core.beans.ApiRequest<>();
        ApiHeaders.setBizKey(msg, "k1");
        msg.setHeader("h1", "v1");
        msg.setHeader("h2", 42);

        Headers sink = new RecordHeaders();
        KafkaHelper.copyHeaders(sink, msg);

        assertNull(sink.lastHeader(io.nop.api.core.ApiConstants.HEADER_BIZ_KEY));
        assertEquals("v1", KafkaHelper.decodeValue(sink.lastHeader("h1").value()));
        assertEquals("42", KafkaHelper.decodeValue(sink.lastHeader("h2").value()));
    }

    @Test
    void copyHeadersHandlesNullSource() {
        Headers sink = new RecordHeaders();
        KafkaHelper.copyHeaders(sink, null);
        // No headers added, no exception.
        assertNull(sink.lastHeader("anything"));
    }

    @Test
    void encodeValueHandlesStringNumberBooleanAndComplex() {
        assertNull(KafkaHelper.encodeValue(null));
        assertEquals("hello", KafkaHelper.encodeValue("hello"));
        assertEquals("42", KafkaHelper.encodeValue(42));
        assertEquals("3.14", KafkaHelper.encodeValue(3.14));
        assertEquals("true", KafkaHelper.encodeValue(true));

        Map<String, Object> obj = new HashMap<>();
        obj.put("key", "value");
        assertEquals("{\"key\":\"value\"}", KafkaHelper.encodeValue(obj));
    }

    @Test
    void decodeValueHandlesNullAndEmpty() {
        assertNull(KafkaHelper.decodeValue(null));
        assertNull(KafkaHelper.decodeValue(new byte[0]));
        assertEquals("abc", KafkaHelper.decodeValue("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void resolveDelayReturnsZeroForNullOrZero() {
        assertEquals(0L, KafkaHelper.resolveDelay(null));
        io.nop.api.core.message.MessageSendOptions opts = new io.nop.api.core.message.MessageSendOptions();
        opts.setDelay(5000L);
        assertEquals(5000L, KafkaHelper.resolveDelay(opts));
        opts.setDelay(-1L);
        assertEquals(0L, KafkaHelper.resolveDelay(opts));
    }

    // ------------------------------------------------------------------
    // WI24: shared codec delegation (F-31) and header protection (F-33/F-48)
    // ------------------------------------------------------------------

    @io.nop.api.core.annotations.data.DataBean
    static class Point {
        public int x;
        public int y;

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

    @Test
    void encodeValueDelegatesToSharedCodecIncludingPojoJson() {
        assertEquals("text", KafkaHelper.encodeValue("text"));
        assertEquals("42", KafkaHelper.encodeValue(42));
        // nop-core's JSON provider is initialized under JunitBaseTestCase.
        Point point = new Point();
        point.x = 1;
        point.y = 2;
        String encoded = KafkaHelper.encodeValue(point);
        org.junit.jupiter.api.Assertions.assertTrue(
                encoded.contains("\"x\"") && encoded.contains("\"y\""),
                "POJO must be JSON-stringified, got: " + encoded);
    }

    @Test
    void buildApiMessageFrameworkHeadersBeatUpstreamHeaders() {
        org.apache.kafka.common.header.internals.RecordHeaders upstream =
                new org.apache.kafka.common.header.internals.RecordHeaders();
        upstream.add("nop-id", "spoofed-id".getBytes());
        upstream.add(io.nop.api.core.ApiConstants.HEADER_TOPIC, "spoofed-topic".getBytes());
        upstream.add(io.nop.api.core.ApiConstants.HEADER_EVENT_TIME, "-1".getBytes());
        upstream.add("nop-biz-key", "spoofed-key".getBytes());

        io.nop.api.core.beans.ApiMessage message = KafkaHelper.buildApiMessage(
                "real-topic", 2, 7L, 12345L, "real-key", "payload", upstream);

        // F-33: framework metadata wins over upstream same-name headers.
        org.junit.jupiter.api.Assertions.assertEquals("real-topic",
                message.getHeader(io.nop.api.core.ApiConstants.HEADER_TOPIC));
        org.junit.jupiter.api.Assertions.assertEquals("real-key",
                io.nop.api.core.util.ApiHeaders.getBizKey(message));
        org.junit.jupiter.api.Assertions.assertEquals(12345L,
                message.getHeader(io.nop.api.core.ApiConstants.HEADER_EVENT_TIME));
        // Type guards (auditor m5): numeric framework values, not upstream strings.
        org.junit.jupiter.api.Assertions.assertEquals(Integer.class,
                message.getHeader(KafkaHelper.HEADER_KAFKA_PARTITION).getClass());
        org.junit.jupiter.api.Assertions.assertEquals(Long.class,
                message.getHeader(KafkaHelper.HEADER_KAFKA_OFFSET).getClass());
        // F-48/B1: the producer-supplied nop-id (RPC correlation) SURVIVES fill-if-absent.
        org.junit.jupiter.api.Assertions.assertEquals("spoofed-id",
                message.getHeader(io.nop.api.core.ApiConstants.HEADER_ID));

        // F-48: with NO upstream nop-id, the derived location fills in.
        io.nop.api.core.beans.ApiMessage noId = KafkaHelper.buildApiMessage(
                "real-topic", 2, 7L, 12345L, "real-key", "payload", null);
        org.junit.jupiter.api.Assertions.assertEquals("real-topic-2@7",
                noId.getHeader(io.nop.api.core.ApiConstants.HEADER_ID));
    }

}
