package io.nop.message.pulsar;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(testBeansFile = "/nop/message/pulsar/beans/test.beans.xml")
class TestPulsarIoC extends JunitBaseTestCase {

    @Inject
    PulsarClientConfig clientConfig;

    @Inject
    PulsarProducerConfig producerConfig;

    @Inject
    PulsarConsumerConfig consumerConfig;

    @Test
    void testConfigBeansInjected() {
        assertNotNull(clientConfig);
        assertEquals("pulsar://localhost:6650", clientConfig.getServiceUrl());
        assertFalse(clientConfig.isEnableTransaction());
        assertNotNull(producerConfig);
        assertNotNull(consumerConfig);
    }

    @Test
    void testProducerConfigDefaults() {
        assertTrue(producerConfig.isBatchingEnabled());
        assertEquals(1000, producerConfig.getBatchMaxMessages());
        assertEquals(30000, producerConfig.getSendTimeout());
    }

    @Test
    void testServiceInitFailsWithoutServiceUrl() {
        PulsarMessageService service = new PulsarMessageService();
        service.setConfig(new PulsarClientConfig());
        assertThrows(NopException.class, service::init);
    }

    @Test
    void testServiceInitFailsWithNullConfig() {
        PulsarMessageService service = new PulsarMessageService();
        assertThrows(NopException.class, service::init);
    }
}
