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
}
