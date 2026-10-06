package io.nop.message.kafka;

import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.TerminateConsume;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * WI33 (nop-message-encapsulation-roadmap): real-broker E2E against a KRaft Kafka container.
 *
 * <p><b>Gating semantics (declared consistently in the owner doc and
 * {@code ai-dev/design/nop-message/backend-semantics-design.md})</b>:
 * <ul>
 *   <li>Primary gate: Docker reachability — when the daemon is unreachable the tests
 *       assume-skip <em>visibly</em> (SKIP is never a silent pass; the roadmap forbids
 *       marking WI33 done on skipped-state tests, so the done verdict below rests on an
 *       actual green run).</li>
 *   <li>Opt-out: {@code -Dnop.test.message.e2e.kafka=false} forces the skip even with
 *       Docker up (CI environments that must not spend container budget here).</li>
 *   <li>{@code -Dnop.test.message.e2e.registry=<prefix>} overrides the image registry
 *       prefix (default {@code docker.m.daocloud.io/} for Hub-unreachable environments;
 *       set to an empty string for direct Docker Hub access).</li>
 * </ul>
 *
 * <p>Scenario set (roadmap WI33 + WI17 charter extension): restart recovery, poison
 * TerminateConsume, DLQ delivery, seek-and-redeliver, request-response, slow-handler
 * maxPollIntervalMs eviction.
 */
class KafkaBrokerE2ETest {

    private static final boolean DOCKER_AVAILABLE = detectDocker();
    private static final boolean OPTED_OUT =
            "false".equalsIgnoreCase(System.getProperty("nop.test.message.e2e.kafka", "true"));

    private static final String REGISTRY_PREFIX =
            System.getProperty("nop.test.message.e2e.registry", "docker.m.daocloud.io/");

    private static KafkaContainer kafka;

    /** Services created by the current test; all are destroyed in @AfterEach. */
    private final List<KafkaMessageService> services = new CopyOnWriteArrayList<>();

    static boolean detectDocker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @BeforeAll
    static void startBroker() {
        if (OPTED_OUT) {
            return;
        }
        assumeTrue(DOCKER_AVAILABLE,
                "docker unavailable: real-Kafka E2E gated off (SKIP is visible by design)");
        kafka = new KafkaContainer(DockerImageName
                .parse(REGISTRY_PREFIX + "apache/kafka:3.7.0")
                .asCompatibleSubstituteFor("apache/kafka"));
        kafka.start();
    }

    @AfterAll
    static void stopBroker() {
        if (kafka != null) {
            kafka.stop();
        }
    }

    @BeforeEach
    void assumeGates() {
        assumeTrue(!OPTED_OUT,
                "opted out via -Dnop.test.message.e2e.kafka=false (visible skip by design)");
        assumeTrue(DOCKER_AVAILABLE,
                "docker unavailable: real-Kafka E2E gated off (SKIP is visible by design)");
    }

    @AfterEach
    void destroyServices() {
        for (KafkaMessageService service : services) {
            try {
                service.destroy();
            } catch (Exception e) {
                // best-effort teardown; test assertions have already run
            }
        }
        services.clear();
    }

    private static MessageSubscribeOptions opts(String name) {
        MessageSubscribeOptions o = new MessageSubscribeOptions();
        o.setSubscribeName(name);
        return o;
    }

    private static String data(Object m) {
        return (String) ((io.nop.api.core.beans.ApiMessage) m).getData();
    }

    /**
     * A non-null {@link KafkaConsumerConfig} is mandatory in the E2E: with a null config the
     * service falls back to kafka-clients defaults and {@code auto.offset.reset=latest}
     * silently drops records sent before the consumer's first partition assignment — the
     * real-deployment posture is an explicit {@code autoOffsetReset=earliest} (the config
     * default), which the restart/seek scenarios below rely on.
     */
    private KafkaMessageService newService() {
        return newService(new KafkaConsumerConfig());
    }

    private KafkaMessageService newService(KafkaConsumerConfig consumerConfig) {
        KafkaMessageService service = new KafkaMessageService();
        KafkaClientConfig config = new KafkaClientConfig();
        config.setBootstrapServers(kafka.getBootstrapServers());
        service.setConfig(config);
        if (consumerConfig != null) {
            service.setDefaultConsumerConfig(consumerConfig);
        }
        service.init();
        services.add(service);
        return service;
    }

    private static String topic(String scenario) {
        return "e2e-" + scenario.toLowerCase(Locale.ROOT);
    }

    /** Polls until the condition holds or the deadline passes (no bare Thread.sleep gates). */
    private static void awaitUntil(BooleanSupplier condition, long timeoutMs, String what) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for " + what, e);
            }
        }
        assertTrue(condition.getAsBoolean(), "timed out after " + timeoutMs + "ms waiting for " + what);
    }

    @Test
    void restartRecovery() {
        String t = topic("restart");
        AtomicReference<String> first = new AtomicReference<>();
        KafkaMessageService service1 = newService();
        IMessageSubscription sub1 = service1.subscribe(t, (topic, m, c) -> {
            first.compareAndSet(null, data(m));
            return null;
        }, opts("e2e-restart-sub"));

        service1.send(t, "msg-1", null);
        awaitUntil(() -> first.get() != null, 15_000, "first delivery before restart");
        sub1.cancel();
        service1.destroy();
        services.remove(service1);

        // Same subscribeName = same consumer group (D7): the service rebuild resumes from
        // the committed offset — msg-1 is never redelivered, msg-2 is delivered fresh.
        List<String> after = new CopyOnWriteArrayList<>();
        KafkaMessageService service2 = newService();
        IMessageSubscription sub2 = service2.subscribe(t, (topic, m, c) -> {
            after.add(data(m));
            return null;
        }, opts("e2e-restart-sub"));

        service2.send(t, "msg-2", null);
        awaitUntil(() -> after.contains("msg-2"), 15_000, "msg-2 after restart");
        awaitUntil(() -> after.size() >= 1, 2_000, "settle window");
        Thread.yield();
        assertTrue(!after.contains("msg-1"),
                "committed message must not be redelivered after service restart, got " + after);
        sub2.cancel();
    }

    @Test
    void poisonMessageTerminate() {
        String t = topic("poison");
        AtomicInteger poisonCount = new AtomicInteger();
        AtomicReference<String> after = new AtomicReference<>();
        KafkaMessageService service = newService();
        IMessageConsumer handler = (topic, m, c) -> {
            String d = data(m);
            if (d.startsWith("poison")) {
                poisonCount.incrementAndGet();
                return new TerminateConsume("e2e");
            }
            after.set(d);
            return null;
        };
        IMessageSubscription sub = service.subscribe(t, handler, opts("e2e-poison-sub"));

        service.send(t, "poison-msg", null);
        awaitUntil(() -> poisonCount.get() >= 1, 15_000, "poison delivery");
        awaitUntil(() -> poisonCount.get() == 1, 1_500, "no-redelivery settle window");
        assertEquals(1, poisonCount.get(),
                "TerminateConsume must commit the poison message — never redelivered");

        // The subscription stays live after the termination.
        service.send(t, "after-poison", null);
        awaitUntil(() -> "after-poison".equals(after.get()), 15_000, "post-poison delivery");
        sub.cancel();
    }

    @Test
    void dlqDelivery() {
        String t = topic("dlq");
        String dlq = t + "-dlq";
        KafkaConsumerConfig cc = new KafkaConsumerConfig();
        cc.setDeadLetterTopic(dlq);
        cc.setMaxConsumeAttempts(1);
        cc.setConsumeErrorBackoffMs(50);
        cc.setConsumeErrorBackoffMaxMs(200);
        KafkaMessageService service = newService(cc);

        List<String> dlqReceived = new CopyOnWriteArrayList<>();
        IMessageSubscription dlqSub = service.subscribe(dlq, (topic, m, c) -> {
            dlqReceived.add(data(m));
            return null;
        }, opts("e2e-dlq-receiver"));

        IMessageSubscription sub = service.subscribe(t, (topic, m, c) -> {
            throw new IllegalStateException("e2e: poison for DLQ");
        }, opts("e2e-dlq-sub"));

        service.send(t, "dlq-poison", null);
        awaitUntil(() -> dlqReceived.contains("dlq-poison"), 20_000,
                "message arrival on the configured DLQ topic after attempt-cap exhaustion");
        sub.cancel();
        dlqSub.cancel();
    }

    @Test
    void seekAndRedeliver() {
        String t = topic("seek");
        AtomicInteger count = new AtomicInteger();
        KafkaMessageService service = newService();
        IMessageSubscription sub = service.subscribe(t, (topic, m, c) -> {
            if (count.incrementAndGet() < 2) {
                // ConsumeLater seeks back to the record offset without committing (F-08):
                // the same record must be re-polled and re-delivered.
                return new ConsumeLater(100);
            }
            return null;
        }, opts("e2e-seek-sub"));

        service.send(t, "seek-msg", null);
        awaitUntil(() -> count.get() >= 2, 15_000,
                "redelivery of the deferred record after the ConsumeLater seek");
        sub.cancel();
    }

    @Test
    void requestResponse() {
        String t = topic("req");
        AtomicReference<String> reply = new AtomicReference<>();
        KafkaMessageService service = newService();
        IMessageSubscription replySub = service.subscribe("ack-" + t, (topic, m, c) -> {
            reply.compareAndSet(null, data(m));
            return null;
        }, opts("e2e-ack-reply-sub"));
        IMessageSubscription reqSub = service.subscribe(t,
                (topic, m, c) -> new Acknowledge("reply-payload"), opts("e2e-req-sub"));

        service.send(t, "request-msg", null);
        awaitUntil(() -> reply.get() != null, 15_000, "reply on the derived ack topic");
        assertEquals("reply-payload", reply.get());
        replySub.cancel();
        reqSub.cancel();
    }

    @Test
    void slowHandlerEviction() {
        String t = topic("slow");
        KafkaConsumerConfig cc = new KafkaConsumerConfig();
        // Must stay above the idle poll timeout (pollTimeoutMs=1000) so the eviction is
        // caused by the blocked handler, not by the poll cadence.
        cc.setMaxPollIntervalMs(2500);
        KafkaMessageService service = newService(cc);

        AtomicInteger deliveries = new AtomicInteger();
        AtomicReference<String> recovered = new AtomicReference<>();
        IMessageConsumer handler = (topic, m, c) -> {
            String d = data(m);
            if (deliveries.incrementAndGet() == 1) {
                // Block past maxPollIntervalMs: the client heartbeat thread leaves the
                // group, the post-handler commit fails (swallowed), the offset stays
                // uncommitted and auto.offset.reset=earliest forces a fresh delivery.
                try {
                    Thread.sleep(6_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }
            if ("slow-msg-2".equals(d)) {
                recovered.set(d);
            }
            return null;
        };
        IMessageSubscription sub = service.subscribe(t, handler, opts("e2e-slow-sub"));

        service.send(t, "slow-msg", null);
        awaitUntil(() -> deliveries.get() >= 2, 30_000,
                "redelivery of the uncommitted record after the maxPollIntervalMs eviction");

        // The consumer group must have healed: fresh messages still flow.
        service.send(t, "slow-msg-2", null);
        awaitUntil(() -> "slow-msg-2".equals(recovered.get()), 15_000,
                "post-eviction recovery of the consume loop");
        sub.cancel();
    }
}
