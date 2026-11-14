package io.nop.graphql.core.subscription;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * A3（plan 2573，2026-11-14 独立评审）：pushToClient 的 {@code session == null} 分支不得把
 * null 键传给 {@code sendPumps.remove(...)}（ConcurrentHashMap null 键 NPE）。
 *
 * <p>缺陷基线（修复前）红：pushToClient 对 null-session 订阅推送时抛 NullPointerException，
 * 异常沿消息分发线程上抛并中断该 topic 的后续订阅推送。修复后：不抛异常、订阅被注销、
 * 后续对同 opId 的推送幂等 no-op。
 */
@Timeout(60)
public class TestPushToClientNullSession_A3 {

    @BeforeAll
    public static void init() {
        // pushToClient 的正常路径经 JsonTool 序列化；JSON provider 随平台初始化注册
        CoreInitialization.initialize();
    }

    @Test
    public void nullSessionPushDoesNotThrowAndUnregisters_A3() {
        GraphQLSubscriptionManager manager = new GraphQLSubscriptionManager();
        try {
            SubscriptionInfo subscription = new SubscriptionInfo();
            subscription.setOperationId("op-null-session");
            subscription.setOperationName("Query_dataChanged");
            // session 缺省为 null：推送必须走注销路径而不是 NPE
            assertNull(subscription.getSession(), "test precondition: session must be null");

            manager.registerSubscription(subscription);

            // 修复前：此调用抛 NullPointerException（CHM.remove(null)）
            assertDoesNotThrow(() -> manager.pushToClient(subscription,
                    GraphQLSubscriptionManager.buildTopic("Query_dataChanged", "op-null-session"),
                    Map.of("changed", true)),
                    "push to a null-session subscription must not throw");

            assertNull(manager.getSubscription("op-null-session"),
                    "the null-session subscription must be unregistered by the push");

            // 注销后重复推送：幂等 no-op（unregisterSubscription 对缺失 opId 无害）
            assertDoesNotThrow(() -> manager.pushToClient(subscription,
                    GraphQLSubscriptionManager.buildTopic("Query_dataChanged", "op-null-session"),
                    Map.of("changed", false)),
                    "repeat push after unregister must stay a no-op");
        } finally {
            manager.destroy();
        }
    }
}
