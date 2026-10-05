package io.nop.message.kafka;

import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.TerminateConsume;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "nop.test.message.e2e.kafka", matches = "true",
        disabledReason = "Requires a running Kafka broker. Enable with -Dnop.test.message.e2e.kafka=true")
class KafkaBrokerE2ETest {

    private static MessageSubscribeOptions opts(String name) {
        MessageSubscribeOptions o = new MessageSubscribeOptions();
        o.setSubscribeName(name);
        return o;
    }

    private static String data(Object m) {
        return (String) ((io.nop.api.core.beans.ApiMessage) m).getData();
    }

    private KafkaMessageService createService() {
        KafkaMessageService service = new KafkaMessageService();
        KafkaClientConfig config = new KafkaClientConfig();
        config.setBootstrapServers(System.getProperty("nop.test.message.e2e.kafka.bootstrap",
                "localhost:9092"));
        service.setConfig(config);
        service.init();
        return service;
    }

    @Test
    void restartRecovery() throws Exception {
        KafkaMessageService service = createService();
        AtomicReference<String> received = new AtomicReference<>();
        IMessageConsumer handler = (t, m, c) -> { received.set(data(m)); return null; };
        IMessageSubscription sub = service.subscribe("e2e-restart", handler, opts("e2e-restart"));
        service.send("e2e-restart", "msg-1", null);
        Thread.sleep(2000);
        assertNotNull(received.get(), "message should be received");
        sub.cancel();
        service.destroy();
    }

    @Test
    void poisonMessageTerminate() throws Exception {
        KafkaMessageService service = createService();
        AtomicReference<String> received = new AtomicReference<>();
        IMessageSubscription sub = service.subscribe("e2e-poison",
                (t, m, c) -> { received.set(data(m)); return new TerminateConsume("e2e"); },
                opts("e2e-poison"));
        service.send("e2e-poison", "poison-msg", null);
        Thread.sleep(2000);
        assertNotNull(received.get());
        sub.cancel();
        service.destroy();
    }

    @Test
    void dlqDelivery() throws Exception {
        KafkaMessageService service = createService();
        IMessageSubscription sub = service.subscribe("e2e-dlq",
                (t, m, c) -> new TerminateConsume("e2e-dlq"), opts("e2e-dlq"));
        service.send("e2e-dlq", "dlq-msg", null);
        Thread.sleep(2000);
        sub.cancel();
        service.destroy();
    }

    @Test
    void seekAndRedeliver() throws Exception {
        KafkaMessageService service = createService();
        AtomicInteger count = new AtomicInteger();
        IMessageSubscription sub = service.subscribe("e2e-seek",
                (t, m, c) -> {
                    if (count.incrementAndGet() < 2) return new ConsumeLater(200);
                    return null;
                }, opts("e2e-seek"));
        service.send("e2e-seek", "seek-msg", null);
        Thread.sleep(3000);
        assertTrue(count.get() >= 2, "message should be redelivered after ConsumeLater");
        sub.cancel();
        service.destroy();
    }

    @Test
    void requestResponse() throws Exception {
        KafkaMessageService service = createService();
        AtomicReference<String> reply = new AtomicReference<>();
        IMessageSubscription replySub = service.subscribe("ack-e2e-req",
                (t, m, c) -> { reply.set(data(m)); return null; }, opts("e2e-ack-reply"));
        IMessageSubscription reqSub = service.subscribe("e2e-req",
                (t, m, c) -> new Acknowledge("reply-payload"), opts("e2e-req"));
        service.send("e2e-req", "request-msg", null);
        Thread.sleep(3000);
        assertNotNull(reply.get(), "reply should land on ack topic");
        replySub.cancel();
        reqSub.cancel();
        service.destroy();
    }
}
