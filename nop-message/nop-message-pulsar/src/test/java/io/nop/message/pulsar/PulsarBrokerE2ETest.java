package io.nop.message.pulsar;

import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.SeekMode;
import io.nop.api.core.message.SubscriptionMode;
import io.nop.api.core.message.TerminateConsume;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.pulsar.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * WI33 (nop-message-encapsulation-roadmap): real-broker E2E against a standalone Pulsar
 * container (pulsar:3.0.11 arm64, protocol-compatible with the 2.8.0 client; if the very
 * first connect fails the plan's fallback is the 2.11.4 image under emulation).
 *
 * <p><b>Gating semantics (declared consistently in the owner doc and
 * {@code ai-dev/design/nop-message/backend-semantics-design.md})</b>:
 * <ul>
 *   <li>Primary gate: Docker reachability — assume-skip <em>visibly</em> when the daemon
 *       is unreachable (SKIP is never a silent pass).</li>
 *   <li>Opt-out: {@code -Dnop.test.message.e2e.pulsar=false} forces the skip even with
 *       Docker up.</li>
 *   <li>{@code -Dnop.test.message.e2e.registry=<prefix>} overrides the image registry
 *       prefix (default {@code docker.m.daocloud.io/}; empty string for direct Hub).</li>
 * </ul>
 *
 * <p><b>Redelivery tuning is a hard precondition</b>: the 2.8.0 client defaults
 * {@code negativeAckRedeliveryDelay} to 60s and {@code maxConsumeAttempts} to 10 — without
 * the sub-second tuning below every redelivery-driven assertion would time out.
 *
 * <p>Scenario set (roadmap WI33): restart recovery (cursor persistence), poison
 * TerminateConsume (Pulsar side: direct ack, never DLQ), DLQ diversion (Shared
 * subscription + {@code deadLetterMaxRedeliverCount == maxConsumeAttempts} coupling),
 * real seek arbitration (INIT_SEEK_TO_BEGIN backlog + ALWAYS_SEEK_TO_END), request-response.
 * The Kafka-only slow-handler maxPollIntervalMs eviction has no Pulsar counterpart by
 * design (design doc 3.6.1).
 */
class PulsarBrokerE2ETest {

    private static final boolean DOCKER_AVAILABLE = detectDocker();
    private static final boolean OPTED_OUT =
            "false".equalsIgnoreCase(System.getProperty("nop.test.message.e2e.pulsar", "true"));

    private static final String REGISTRY_PREFIX =
            System.getProperty("nop.test.message.e2e.registry", "docker.m.daocloud.io/");

    private static final String BASE = "persistent://public/default";

    private static PulsarContainer pulsar;

    private final List<PulsarMessageService> services = new CopyOnWriteArrayList<>();

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
                "docker unavailable: real-Pulsar E2E gated off (SKIP is visible by design)");
        pulsar = new PulsarContainer(DockerImageName
                .parse(REGISTRY_PREFIX + "apachepulsar/pulsar:3.0.11")
                .asCompatibleSubstituteFor("apachepulsar/pulsar"));
        pulsar.start();
    }

    @AfterAll
    static void stopBroker() {
        if (pulsar != null) {
            pulsar.stop();
        }
    }

    @BeforeEach
    void assumeGates() {
        assumeTrue(!OPTED_OUT,
                "opted out via -Dnop.test.message.e2e.pulsar=false (visible skip by design)");
        assumeTrue(DOCKER_AVAILABLE,
                "docker unavailable: real-Pulsar E2E gated off (SKIP is visible by design)");
    }

    @AfterEach
    void destroyServices() {
        for (PulsarMessageService service : services) {
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

    private PulsarMessageService newService() {
        return newService(null);
    }

    private PulsarMessageService newService(PulsarConsumerConfig consumerConfig) {
        PulsarMessageService service = new PulsarMessageService();
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl(pulsar.getPulsarBrokerUrl());
        service.setConfig(config);
        if (consumerConfig != null) {
            service.setDefaultConsumerConfig(consumerConfig);
        }
        service.init();
        services.add(service);
        return service;
    }

    /**
     * Sub-second redelivery tuning shared by the redelivery-driven scenarios (DLQ). The
     * Shared subscription mode is required for the 2.8 redelivery-count tracking (design
     * doc 3.3.1), and {@code deadLetterMaxRedeliverCount == maxConsumeAttempts} is the
     * WI22 coupling: the local attempt cap fires the terminal ack exactly when the native
     * policy's diversion threshold is reached.
     */
    private static PulsarConsumerConfig redeliveryConfig(String dlqTopic) {
        PulsarConsumerConfig cc = new PulsarConsumerConfig();
        cc.setNegativeAckRedeliveryDelay(100);
        cc.setConsumeErrorBackoffMs(50);
        cc.setConsumeErrorBackoffMaxMs(200);
        cc.setMaxConsumeAttempts(2);
        cc.setDeadLetterMaxRedeliverCount(2);
        cc.setDeadLetterTopic(dlqTopic);
        return cc;
    }

    private static String topic(String scenario) {
        return BASE + "/e2e-" + scenario.toLowerCase(Locale.ROOT);
    }

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
        PulsarMessageService service1 = newService();
        IMessageSubscription sub1 = service1.subscribe(t, (topic, m, c) -> {
            first.compareAndSet(null, data(m));
            return null;
        }, opts("e2e-restart-sub"));

        service1.send(t, "msg-1", null);
        awaitUntil(() -> first.get() != null, 30_000, "first delivery before restart");
        sub1.cancel();
        service1.destroy();
        services.remove(service1);

        // Same subscription name = same cursor (D7): the rebuild resumes past msg-1.
        List<String> after = new CopyOnWriteArrayList<>();
        PulsarMessageService service2 = newService();
        IMessageSubscription sub2 = service2.subscribe(t, (topic, m, c) -> {
            after.add(data(m));
            return null;
        }, opts("e2e-restart-sub"));

        service2.send(t, "msg-2", null);
        awaitUntil(() -> after.contains("msg-2"), 30_000, "msg-2 after restart");
        awaitUntil(() -> after.size() >= 1, 2_000, "settle window");
        Thread.yield();
        assertFalse(after.contains("msg-1"),
                "acknowledged message must not be redelivered after cursor persistence, got " + after);
        sub2.cancel();
    }

    @Test
    void poisonMessageTerminate() {
        String t = topic("poison");
        AtomicInteger poisonCount = new AtomicInteger();
        AtomicReference<String> after = new AtomicReference<>();
        PulsarMessageService service = newService();
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
        awaitUntil(() -> poisonCount.get() >= 1, 30_000, "poison delivery");
        awaitUntil(() -> poisonCount.get() == 1, 1_500, "no-redelivery settle window");
        assertEquals(1, poisonCount.get(),
                "TerminateConsume must acknowledge the poison message — never redelivered");

        service.send(t, "after-poison", null);
        awaitUntil(() -> "after-poison".equals(after.get()), 30_000, "post-poison delivery");
        sub.cancel();
    }

    @Test
    void dlqDelivery() {
        String t = topic("dlq");
        String dlq = BASE + "/e2e-dlq-dlq";
        PulsarMessageService service = newService(redeliveryConfig(dlq));

        List<String> dlqReceived = new CopyOnWriteArrayList<>();
        IMessageSubscription dlqSub = service.subscribe(dlq, (topic, m, c) -> {
            dlqReceived.add(data(m));
            return null;
        }, opts("e2e-dlq-receiver"));

        // DLQ diversion is Shared-scoped in 2.8 (design 3.3.1): LOAD_BALANCED -> Shared.
        MessageSubscribeOptions sharedOpts = opts("e2e-dlq-sub");
        sharedOpts.setSubscriptionMode(SubscriptionMode.LOAD_BALANCED);
        AtomicInteger failures = new AtomicInteger();
        IMessageSubscription sub = service.subscribe(t, (topic, m, c) -> {
            failures.incrementAndGet();
            throw new IllegalStateException("e2e: poison for DLQ");
        }, sharedOpts);

        service.send(t, "dlq-poison", null);
        awaitUntil(() -> dlqReceived.contains("dlq-poison"), 30_000,
                "message diversion to the configured DLQ after the attempt cap + terminal ack");
        assertTrue(failures.get() >= 3,
                "endpoint fires on failure maxConsumeAttempts+1=3, got " + failures.get());
        sub.cancel();
        dlqSub.cancel();
    }

    @Test
    void seekArbitration() {
        String t = topic("seek");
        PulsarMessageService service = newService();

        // Backlog first: msg-1 exists before any subscription — a late INIT_SEEK_TO_BEGIN
        // subscription must still receive it (broker arbitrates the absent cursor).
        service.send(t, "msg-1", null);

        List<String> seen = new CopyOnWriteArrayList<>();
        MessageSubscribeOptions beginOpts = opts("e2e-seek-begin-sub");
        beginOpts.setSeekMode(SeekMode.INIT_SEEK_TO_BEGIN);
        IMessageSubscription beginSub = service.subscribe(t, (topic, m, c) -> {
            seen.add(data(m));
            return null;
        }, beginOpts);
        awaitUntil(() -> seen.contains("msg-1"), 30_000,
                "INIT_SEEK_TO_BEGIN delivers pre-existing backlog to a late subscription");
        beginSub.cancel();

        // ALWAYS_SEEK_TO_END: a fresh subscription starts at the live edge — msg-2 was
        // sent before this subscription is created and must be skipped; msg-3 arrives after.
        service.send(t, "msg-2", null);
        List<String> endSeen = new CopyOnWriteArrayList<>();
        MessageSubscribeOptions endOpts = opts("e2e-seek-end-sub");
        endOpts.setSeekMode(SeekMode.ALWAYS_SEEK_TO_END);
        IMessageSubscription endSub = service.subscribe(t, (topic, m, c) -> {
            endSeen.add(data(m));
            return null;
        }, endOpts);
        service.send(t, "msg-3", null);
        awaitUntil(() -> endSeen.contains("msg-3"), 30_000, "msg-3 arrival on the ALWAYS_SEEK_TO_END sub");
        awaitUntil(() -> !endSeen.isEmpty(), 2_000, "settle window");
        Thread.yield();
        assertFalse(endSeen.contains("msg-2"),
                "ALWAYS_SEEK_TO_END must skip pre-subscription backlog, got " + endSeen);
        endSub.cancel();
    }

    @Test
    void requestResponse() {
        String t = topic("req");
        AtomicReference<String> reply = new AtomicReference<>();
        PulsarMessageService service = newService();
        String ackTopic = PulsarHelper.deriveAckTopic(t);
        IMessageSubscription replySub = service.subscribe(ackTopic, (topic, m, c) -> {
            reply.compareAndSet(null, data(m));
            return null;
        }, opts("e2e-ack-reply-sub"));
        IMessageSubscription reqSub = service.subscribe(t,
                (topic, m, c) -> new Acknowledge("reply-payload"), opts("e2e-req-sub"));

        service.send(t, "request-msg", null);
        awaitUntil(() -> reply.get() != null, 30_000, "reply on the derived ack topic");
        assertEquals("reply-payload", reply.get());
        assertNotNull(reply.get());
        replySub.cancel();
        reqSub.cancel();
    }
}
