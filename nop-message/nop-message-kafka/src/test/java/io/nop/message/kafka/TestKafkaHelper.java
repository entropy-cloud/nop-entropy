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
}
