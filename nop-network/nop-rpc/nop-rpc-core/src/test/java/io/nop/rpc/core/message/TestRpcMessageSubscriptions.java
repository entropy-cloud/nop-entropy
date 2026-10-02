package io.nop.rpc.core.message;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.MessageSubscribeOptions;
import org.junit.jupiter.api.Test;

import static io.nop.rpc.core.RpcErrors.ERR_RPC_NOT_ALLOW_MULTIPLE_SUBSCRIPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 RpcMessageSubscriptions 的订阅管理语义：同 topic 不允许重复订阅、
 * suspend/resume 只改挂起标志、cancel 后订阅从注册表移除且可重新注册。
 */
public class TestRpcMessageSubscriptions {

    static final IMessageConsumer NOOP_CONSUMER = (topic, message, context) -> null;

    @Test
    public void testRegisterAndLookup() {
        RpcMessageSubscriptions subscriptions = new RpcMessageSubscriptions();
        RpcMessageSubscriptions.Subscription sub =
                subscriptions.register("topic-a", NOOP_CONSUMER, new MessageSubscribeOptions());

        assertSame(sub, subscriptions.getSubscription("topic-a"));
        assertEquals("topic-a", sub.getTopic());
        assertSame(NOOP_CONSUMER, sub.getConsumer());
        assertNotNull(sub.getOptions());
    }

    @Test
    public void testDuplicateSubscriptionRejected() {
        RpcMessageSubscriptions subscriptions = new RpcMessageSubscriptions();
        subscriptions.register("topic-a", NOOP_CONSUMER, new MessageSubscribeOptions());

        NopException e = assertThrows(NopException.class,
                () -> subscriptions.register("topic-a", NOOP_CONSUMER, new MessageSubscribeOptions()));
        assertEquals(ERR_RPC_NOT_ALLOW_MULTIPLE_SUBSCRIPTION.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testSuspendResumeTogglesFlag() {
        RpcMessageSubscriptions subscriptions = new RpcMessageSubscriptions();
        RpcMessageSubscriptions.Subscription sub =
                subscriptions.register("topic-a", NOOP_CONSUMER, new MessageSubscribeOptions());

        assertFalse(sub.isSuspended());
        sub.suspend();
        assertTrue(sub.isSuspended(), "suspend must mark the subscription suspended");
        sub.resume();
        assertFalse(sub.isSuspended(), "resume must clear the suspended flag");
    }

    @Test
    public void testCancelRemovesSubscriptionAndAllowsReregister() {
        RpcMessageSubscriptions subscriptions = new RpcMessageSubscriptions();
        RpcMessageSubscriptions.Subscription sub =
                subscriptions.register("topic-a", NOOP_CONSUMER, new MessageSubscribeOptions());

        sub.cancel();

        assertNull(subscriptions.getSubscription("topic-a"), "cancelled subscription must be removed");
        // 取消后同 topic 可重新注册
        RpcMessageSubscriptions.Subscription again =
                subscriptions.register("topic-a", NOOP_CONSUMER, new MessageSubscribeOptions());
        assertSame(again, subscriptions.getSubscription("topic-a"));
    }

    @Test
    public void testUnknownTopicYieldsNull() {
        RpcMessageSubscriptions subscriptions = new RpcMessageSubscriptions();
        assertNull(subscriptions.getSubscription("no-such-topic"));
    }
}
