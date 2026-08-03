/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@NopTestConfig(testBeansFile = "/nop/message/kafka/beans/test.beans.xml")
class TestKafkaIoC extends JunitBaseTestCase {

    @Inject
    KafkaClientConfig clientConfig;

    @Inject
    KafkaProducerConfig producerConfig;

    @Inject
    KafkaConsumerConfig consumerConfig;

    @Test
    void testConfigBeansInjected() {
        assertNotNull(clientConfig);
        assertEquals("localhost:9092", clientConfig.getBootstrapServers());
        assertNotNull(producerConfig);
        assertNotNull(consumerConfig);
    }

    @Test
    void testProducerConfigDefaults() {
        assertEquals("all", producerConfig.getAcks());
        assertEquals(3, producerConfig.getRetries());
        assertEquals(16384, producerConfig.getBatchSize());
    }

    @Test
    void testConsumerConfigDefaults() {
        assertEquals("earliest", consumerConfig.getAutoOffsetReset());
        assertFalse(consumerConfig.isEnableAutoCommit());
    }

    @Test
    void testServiceInitFailsWithoutBootstrapServers() {
        KafkaMessageService service = new KafkaMessageService();
        service.setConfig(new KafkaClientConfig());
        assertThrows(NopException.class, service::init);
    }

    @Test
    void testServiceInitFailsWithNullConfig() {
        KafkaMessageService service = new KafkaMessageService();
        assertThrows(NopException.class, service::init);
    }
}
