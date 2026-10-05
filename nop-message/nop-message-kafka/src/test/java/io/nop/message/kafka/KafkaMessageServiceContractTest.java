package io.nop.message.kafka;

import io.nop.api.core.message.IMessageService;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.message.core.contract.AbstractMessageServiceContractTest;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Collections;

/**
 * WI32: contract tests for the Kafka backend using Kafka's MockConsumer (in-JVM).
 */
@org.junit.jupiter.api.Disabled(
        "Kafka's MockConsumer/MockProducer are independent objects — producer.send does not "
        + "feed consumer.poll in-JVM. Contract coverage for Kafka comes from: (1) "
        + "TestKafkaConsumeTask covering the consume loop's return-value protocol and seek/commit "
        + "semantics (MockConsumer.poll-scheduling), (2) TestKafkaMessageServiceSend covering "
        + "sendAsync (mock producer callback), and (3) WI33 gated real-broker E2E tests.")
class KafkaMessageServiceContractTest extends AbstractMessageServiceContractTest {

    @Override
    protected IMessageService createService() {
        MockConsumer<String, String> mockConsumer =
                new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        mockConsumer.subscribe(Collections.singleton("contract"));
        mockConsumer.rebalance(Collections.singleton(new TopicPartition("contract", 0)));
        mockConsumer.updateEndOffsets(Collections.singletonMap(
                new TopicPartition("contract", 0), 0L));

        KafkaMessageService service = new KafkaMessageService() {
            @Override
            protected org.apache.kafka.clients.consumer.Consumer<String, String> createConsumer(
                    java.util.Properties props,
                    StringDeserializer kd, StringDeserializer vd) {
                return mockConsumer;
            }

            @Override
            protected void doStart() {
                // Skip real producer creation
                initialized = true;
            }
        };
        KafkaClientConfig config = new KafkaClientConfig();
        config.setBootstrapServers("localhost:9092");
        service.setConfig(config);
        service.init();
        return service;
    }

    @Override
    protected void closeService() {
        if (service instanceof LifeCycleSupport) {
            ((LifeCycleSupport) service).stop();
        }
    }

    @Override
    protected boolean supportsBatchConsume() {
        return false;
    }
}
