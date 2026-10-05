package io.nop.message.core;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.message.core.local.LocalMessageService;
import io.nop.message.core.reflection.ReflectionMessageSubscriptionRegistrar;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@NopTestConfig(testBeansFile = "/nop/message/beans/test.beans.xml")
public class TestLocalMessageService extends JunitBaseTestCase {
    @Inject
    IMessageService messageService;

    // 明确引入bean确保它会被初始化
    @Inject
    ReflectionMessageSubscriptionRegistrar registrar;

    @Inject
    MyMessageListener listener;

    @Test
    public void testReflectionRegistrar() {
        MyMessage message = new MyMessage();
        message.setName("aaa");

        messageService.send("test", message);
        assertEquals(1, listener.getTriggerCount());
    }

    // ------------------------------------------------------------------
    // WI23: independent subscriptions (F-15) and option dispositions (F-56 residual)
    // ------------------------------------------------------------------

    @Test
    public void testSubscribeIsIndependentPerCallAndCancelDoesNotCrossAffect() {
        LocalMessageService service = new LocalMessageService();
        AtomicInteger count = new AtomicInteger();
        IMessageConsumer listener = (topic, message, context) -> {
            count.incrementAndGet();
            return null;
        };
        IMessageSubscription sub1 = service.subscribe("t-indep", listener, null);
        IMessageSubscription sub2 = service.subscribe("t-indep", listener, null);

        // WI23: two subscriptions, two independent instances (the pre-WI23 dedupe returned
        // the SAME instance, so either cancel silently unsubscribed both holders).
        org.junit.jupiter.api.Assertions.assertNotSame(sub1, sub2);

        service.send("t-indep", "m1", null);
        org.junit.jupiter.api.Assertions.assertEquals(2, count.get(),
                "each subscription receives the broadcast once");

        sub2.cancel();
        org.junit.jupiter.api.Assertions.assertFalse(sub1.isCancelled(),
                "cancelling one holder must not cancel the other");
        java.util.List<LocalMessageService.Subscription> remaining =
                service.getConsumers().get("t-indep");
        org.junit.jupiter.api.Assertions.assertEquals(1, remaining.size());
        org.junit.jupiter.api.Assertions.assertSame(sub1, remaining.get(0),
                "cancel must remove the cancelled instance itself, not a sibling");

        service.send("t-indep", "m2", null);
        org.junit.jupiter.api.Assertions.assertEquals(3, count.get(),
                "the surviving subscription still receives after the sibling cancel");

        sub1.cancel();
        service.send("t-indep", "m3", null);
        org.junit.jupiter.api.Assertions.assertEquals(3, count.get(),
                "no subscription left — no delivery");
    }

    @Test
    public void testSubscribeOptionFieldsAreWarnOnceNotRejected() {
        LocalMessageService service = new LocalMessageService();
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        // Every optional field set: the declared disposition is warn-once — a typed
        // rejection would throw and fail this test. (Whether the WARN was logged is not
        // programmatically assertable: the validator's WARNED table is private.)
        options.setSubscribeName("sub");
        options.setConcurrency(2);
        options.setSubscriptionType(io.nop.api.core.message.SubscriptionType.Shared);
        options.setBatchReceiveCount(10);
        options.setSeekMode(io.nop.api.core.message.SeekMode.INIT_SEEK_TO_BEGIN);
        IMessageSubscription sub = service.subscribe("t-opts", (t, m, c) -> null, options);
        org.junit.jupiter.api.Assertions.assertNotNull(sub);
        sub.cancel();
    }

    @Test
    public void testRedeliveryScheduleRejectionIsWarnedNotThrown() {
        LocalMessageService service = new LocalMessageService();
        // WI26 (F-40): a shut-down scheduler rejects the redelivery task — the rejection is
        // WARNed with the typed MessageCoreErrors code and must NOT propagate.
        java.util.concurrent.ScheduledExecutorService dead =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        dead.shutdownNow();
        service.setRedeliveryExecutorForTest(dead);

        LocalMessageService.Subscription subscription =
                service.new Subscription("t-ree", (t, m, c) -> new io.nop.api.core.message.ConsumeLater(0));
        java.util.List<LocalMessageService.Subscription> list =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        list.add(subscription);
        service.getConsumers().put("t-ree", list);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                service.send("t-ree", "deferred-msg", null));
    }
}
