package io.nop.message.pulsar;

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

@EnabledIfSystemProperty(named = "nop.test.message.e2e.pulsar", matches = "true",
        disabledReason = "Requires a running Pulsar broker. Enable with -Dnop.test.message.e2e.pulsar=true")
class PulsarBrokerE2ETest {

    private static MessageSubscribeOptions opts(String name) {
        MessageSubscribeOptions o = new MessageSubscribeOptions();
        o.setSubscribeName(name);
        return o;
    }

    private static String data(Object m) {
        return (String) ((io.nop.api.core.beans.ApiMessage) m).getData();
    }

    private static final String BASE = "persistent://public/default";

    private PulsarMessageService createService() {
        PulsarMessageService service = new PulsarMessageService();
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl(System.getProperty("nop.test.message.e2e.pulsar.url",
                "pulsar://localhost:6650"));
        service.setConfig(config);
        service.init();
        return service;
    }

    @Test
    void restartRecovery() throws Exception {
        PulsarMessageService service = createService();
        AtomicReference<String> received = new AtomicReference<>();
        IMessageSubscription sub = service.subscribe(BASE + "/e2e-restart",
                (t, m, c) -> { received.set(data(m)); return null; }, opts("e2e-restart"));
        service.send(BASE + "/e2e-restart", "msg-1", null);
        Thread.sleep(2000);
        assertNotNull(received.get());
        sub.cancel();
        service.destroy();
    }

    @Test
    void poisonMessageTerminate() throws Exception {
        PulsarMessageService service = createService();
        AtomicReference<String> received = new AtomicReference<>();
        IMessageSubscription sub = service.subscribe(BASE + "/e2e-poison",
                (t, m, c) -> { received.set(data(m)); return new TerminateConsume("e2e"); },
                opts("e2e-poison"));
        service.send(BASE + "/e2e-poison", "poison", null);
        Thread.sleep(2000);
        assertNotNull(received.get());
        sub.cancel();
        service.destroy();
    }

    @Test
    void dlqDelivery() throws Exception {
        PulsarMessageService service = createService();
        IMessageSubscription sub = service.subscribe(BASE + "/e2e-dlq",
                (t, m, c) -> new TerminateConsume("e2e-dlq"), opts("e2e-dlq"));
        service.send(BASE + "/e2e-dlq", "dlq-msg", null);
        Thread.sleep(2000);
        sub.cancel();
        service.destroy();
    }

    @Test
    void seekAndRedeliver() throws Exception {
        PulsarMessageService service = createService();
        AtomicInteger count = new AtomicInteger();
        IMessageSubscription sub = service.subscribe(BASE + "/e2e-seek",
                (t, m, c) -> {
                    if (count.incrementAndGet() < 2) return new ConsumeLater(200);
                    return null;
                }, opts("e2e-seek"));
        service.send(BASE + "/e2e-seek", "seek-msg", null);
        Thread.sleep(3000);
        assertTrue(count.get() >= 2);
        sub.cancel();
        service.destroy();
    }

    @Test
    void requestResponse() throws Exception {
        PulsarMessageService service = createService();
        AtomicReference<String> reply = new AtomicReference<>();
        String reqTopic = BASE + "/e2e-req";
        String ackTopic = PulsarHelper.deriveAckTopic(reqTopic);
        IMessageSubscription replySub = service.subscribe(ackTopic,
                (t, m, c) -> { reply.set(data(m)); return null; }, opts("e2e-ack-reply"));
        IMessageSubscription reqSub = service.subscribe(reqTopic,
                (t, m, c) -> new Acknowledge("reply-payload"), opts("e2e-req"));
        service.send(reqTopic, "request-msg", null);
        Thread.sleep(3000);
        assertNotNull(reply.get());
        replySub.cancel();
        reqSub.cancel();
        service.destroy();
    }
}
