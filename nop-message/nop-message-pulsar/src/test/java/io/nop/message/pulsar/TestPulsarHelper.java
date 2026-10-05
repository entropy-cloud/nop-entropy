package io.nop.message.pulsar;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@NopTestConfig(testBeansFile = "/nop/message/pulsar/beans/test.beans.xml")
class TestPulsarHelper extends JunitBaseTestCase {

    @Test
    void testBuildApiMessage_withStringMessage() {
        Message<?> pulsarMsg = mock(Message.class);
        given(pulsarMsg.getValue()).willReturn("hello");
        given(pulsarMsg.getKey()).willReturn("biz-key-1");
        given(pulsarMsg.getMessageId()).willReturn(mock(MessageId.class));
        given(pulsarMsg.getTopicName()).willReturn("persistent://public/default/test-topic");
        given(pulsarMsg.getEventTime()).willReturn(1000L);
        given(pulsarMsg.getPublishTime()).willReturn(2000L);
        given(pulsarMsg.getSequenceId()).willReturn(42L);

        Map<String, String> props = new HashMap<>();
        props.put("custom-header", "custom-value");
        given(pulsarMsg.getProperties()).willReturn(props);

        var apiMessage = PulsarHelper.buildApiMessage(pulsarMsg);

        assertNotNull(apiMessage);
        assertEquals("hello", apiMessage.getData());
        assertEquals("biz-key-1", apiMessage.getHeader("nop-biz-key"));
        assertEquals("persistent://public/default/test-topic", apiMessage.getHeader("nop-topic"));
        assertEquals(1000L, apiMessage.getHeader("nop-event-time"));
        assertEquals(2000L, apiMessage.getHeader("nop-publish-time"));
        assertEquals(42L, apiMessage.getHeader("nop-sequence-id"));
        assertEquals("custom-value", apiMessage.getHeader("custom-header"));
    }

    @Test
    void testBuildApiMessage_withNullKeyAndNoProperties() {
        Message<?> pulsarMsg = mock(Message.class);
        given(pulsarMsg.getValue()).willReturn(123);
        given(pulsarMsg.getKey()).willReturn(null);
        given(pulsarMsg.getMessageId()).willReturn(mock(MessageId.class));
        given(pulsarMsg.getTopicName()).willReturn(null);
        given(pulsarMsg.getEventTime()).willReturn(0L);
        given(pulsarMsg.getPublishTime()).willReturn(0L);
        given(pulsarMsg.getSequenceId()).willReturn(0L);
        given(pulsarMsg.getProperties()).willReturn(null);

        var apiMessage = PulsarHelper.buildApiMessage(pulsarMsg);

        assertNotNull(apiMessage);
        assertEquals(123, apiMessage.getData());
    }

    @Test
    void testEncodeValue_null() {
        assertNull(PulsarHelper.encodeValue(null));
    }

    @Test
    void testEncodeValue_string() {
        assertEquals("hello", PulsarHelper.encodeValue("hello"));
    }

    @Test
    void testEncodeValue_number() {
        assertEquals("42", PulsarHelper.encodeValue(42));
        assertEquals("3.14", PulsarHelper.encodeValue(3.14));
    }

    @Test
    void testEncodeValue_boolean() {
        assertEquals("true", PulsarHelper.encodeValue(true));
        assertEquals("false", PulsarHelper.encodeValue(false));
    }

    @Test
    void testEncodeValue_complexObject_serializedToJson() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("key", "value");
        String result = PulsarHelper.encodeValue(obj);
        assertNotNull(result);
        assertEquals("{\"key\":\"value\"}", result);
    }

    @Test
    void deriveAckTopicKeepsDomainAndNamespaceForFqn() {
        assertEquals("persistent://public/default/ack-orders",
                PulsarHelper.deriveAckTopic("persistent://public/default/orders"));
        assertEquals("non-persistent://tenant/ns/ack-orders",
                PulsarHelper.deriveAckTopic("non-persistent://tenant/ns/orders"));
    }

    @Test
    void deriveAckTopicPrefixesShortNames() {
        assertEquals("ack-orders", PulsarHelper.deriveAckTopic("orders"));
    }

    @Test
    void deriveAckTopicContractHoldsForRealPulsarTopicNameParsing() {
        // The derived topic must be parseable by the broker-side TopicName grammar —
        // the exact failure mode of MSG-F-04.
        org.apache.pulsar.common.naming.TopicName.get(
                PulsarHelper.deriveAckTopic("persistent://public/default/orders"));
    }

    // ------------------------------------------------------------------
    // WI24: header protection (F-33/F-48) and wire-value encoding (F-31)
    // ------------------------------------------------------------------

    @Test
    void buildApiMessageFrameworkHeadersBeatUpstreamProperties() {
        org.apache.pulsar.client.api.Message<Object> message =
                mock(org.apache.pulsar.client.api.Message.class);
        when(message.getValue()).thenReturn("payload");
        when(message.getMessageId()).thenReturn(mock(org.apache.pulsar.client.api.MessageId.class));
        when(message.getTopicName()).thenReturn("persistent://public/default/real-topic");
        when(message.getKey()).thenReturn("real-key");
        when(message.getEventTime()).thenReturn(12345L);
        java.util.Map<String, String> properties = new java.util.HashMap<>();
        properties.put("nop-id", "spoofed-id");
        properties.put(io.nop.api.core.ApiConstants.HEADER_TOPIC, "spoofed-topic");
        properties.put("nop-biz-key", "spoofed-key");
        when(message.getProperties()).thenReturn(properties);

        io.nop.api.core.beans.ApiMessage apiMessage = PulsarHelper.buildApiMessage(message);

        // F-33: framework metadata wins over upstream same-name properties.
        org.junit.jupiter.api.Assertions.assertEquals("persistent://public/default/real-topic",
                apiMessage.getHeader(io.nop.api.core.ApiConstants.HEADER_TOPIC));
        org.junit.jupiter.api.Assertions.assertEquals("real-key",
                io.nop.api.core.util.ApiHeaders.getBizKey(apiMessage));
        // F-48/B1: the producer-supplied nop-id SURVIVES fill-if-absent.
        org.junit.jupiter.api.Assertions.assertEquals("spoofed-id",
                apiMessage.getHeader(io.nop.api.core.ApiConstants.HEADER_ID));

        // F-48 (audit condition): with NO upstream nop-id, the Pulsar-native messageId
        // fills in (symmetric with the Kafka derived location).
        org.apache.pulsar.client.api.Message<Object> noIdMessage =
                mock(org.apache.pulsar.client.api.Message.class);
        org.apache.pulsar.client.api.MessageId msgId =
                mock(org.apache.pulsar.client.api.MessageId.class);
        when(noIdMessage.getValue()).thenReturn("payload");
        when(noIdMessage.getMessageId()).thenReturn(msgId);
        when(noIdMessage.getTopicName()).thenReturn("persistent://public/default/real-topic");
        when(noIdMessage.getProperties()).thenReturn(java.util.Collections.emptyMap());
        io.nop.api.core.beans.ApiMessage noId = PulsarHelper.buildApiMessage(noIdMessage);
        org.junit.jupiter.api.Assertions.assertEquals(String.valueOf(msgId),
                noId.getHeader(io.nop.api.core.ApiConstants.HEADER_ID));
    }

    @Test
    void wireValueEncodesNonStringPayloadsLikeKafka() {
        org.apache.pulsar.client.api.TypedMessageBuilder<Object> builder =
                mock(org.apache.pulsar.client.api.TypedMessageBuilder.class);
        io.nop.api.core.beans.ApiRequest<Object> message = new io.nop.api.core.beans.ApiRequest<>();
        Point point = new Point();
        point.x = 1;
        point.y = 2;
        message.setData(point);

        PulsarHelper.buildPulsarMessage(builder, message, null);

        org.mockito.ArgumentCaptor<Object> value = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(builder).value(value.capture());
        String encoded = (String) value.getValue();
        org.junit.jupiter.api.Assertions.assertTrue(
                encoded.contains("\"x\"") && encoded.contains("\"y\""),
                "non-String payload must be JSON-encoded (same codec as Kafka), got: " + encoded);
        // Parity anchor: identical output from the shared codec.
        org.junit.jupiter.api.Assertions.assertEquals(
                io.nop.api.core.message.MessageValueCodec.encodeValue(point), encoded);
    }

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
}
