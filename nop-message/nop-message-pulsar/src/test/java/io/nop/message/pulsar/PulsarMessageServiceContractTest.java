package io.nop.message.pulsar;

import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.message.core.contract.AbstractMessageServiceContractTest;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
@org.junit.jupiter.api.Disabled(
        "Pulsar client architecture is broker-mediated: producer.send → broker → consumer.receive "
        + "cannot be simulated with Mockito mocks (the mock producer does not feed the mock consumer). "
        + "Contract coverage for Pulsar comes from: (1) TestPulsarConsumeTask covering the consume "
        + "loop's return-value protocol (ack/nack/reconsumeLater/PartialCommit/TerminateConsume), "
        + "(2) TestPulsarServiceLifecycle covering lifecycle/destroy/DLQ/subscribe-name, and "
        + "(3) WI33 gated real-broker E2E tests. This class is kept as a scaffold for future "
        + "embedded-Pulsar testing.")
class PulsarMessageServiceContractTest extends AbstractMessageServiceContractTest {

    @Override
    protected IMessageService createService() {
        PulsarMessageService service = new PulsarMessageService() {
            @Override
            protected PulsarClient createClient() {
                PulsarClient client = mock(PulsarClient.class);
                try {
                    org.apache.pulsar.client.api.ProducerBuilder pb =
                            mock(org.apache.pulsar.client.api.ProducerBuilder.class);
                    org.apache.pulsar.client.api.Producer producer =
                            mock(org.apache.pulsar.client.api.Producer.class);
                    when(pb.topic(org.mockito.ArgumentMatchers.anyString())).thenReturn(pb);
                    when(pb.create()).thenReturn(producer);
                    when(client.newProducer(org.mockito.ArgumentMatchers
                            .<Schema<?>>any())).thenReturn(pb);
                } catch (Exception e) {
                    // stub
                }
                return client;
            }

            @Override
            public io.nop.api.core.message.IMessageSubscription subscribe(
                    String topic, io.nop.api.core.message.IMessageConsumer listener,
                    MessageSubscribeOptions options) {
                // WI29 (F-29): Pulsar requires subscribeName — inject a default for contract tests.
                if (options == null)
                    options = new MessageSubscribeOptions();
                if (options.getSubscribeName() == null)
                    options.setSubscribeName("contract-test");
                return super.subscribe(topic, listener, options);
            }

            @Override
            protected org.apache.pulsar.client.api.ConsumerBuilder newConsumerBuilder(
                    Schema schema) {
                try {
                    org.apache.pulsar.client.api.ConsumerBuilder builder =
                            mock(org.apache.pulsar.client.api.ConsumerBuilder.class);
                    org.apache.pulsar.client.api.Consumer consumer =
                            mock(org.apache.pulsar.client.api.Consumer.class);
                    when(builder.topic(org.mockito.ArgumentMatchers.anyString())).thenReturn(builder);
                    when(builder.subscriptionName(org.mockito.ArgumentMatchers.anyString())).thenReturn(builder);
                    when(builder.subscribe()).thenReturn(consumer);
                    when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                            org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
                        Thread.sleep(50);
                        return null;
                    });
                    return builder;
                } catch (Exception e) {
                    throw new io.nop.api.core.exceptions.NopException(io.nop.api.core.message.MessageErrors.ERR_OPTION_NOT_SUPPORTED).cause(e);
                }
            }
        };
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl("pulsar://localhost:6650");
        service.setConfig(config);
        try {
            service.init();
        } catch (Exception e) {
            throw new io.nop.api.core.exceptions.NopException(io.nop.api.core.message.MessageErrors.ERR_OPTION_NOT_SUPPORTED).cause(e);
        }
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
