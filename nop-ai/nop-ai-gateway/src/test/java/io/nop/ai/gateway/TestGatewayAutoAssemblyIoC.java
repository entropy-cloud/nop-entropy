package io.nop.ai.gateway;

import io.nop.ai.gateway.channel.ChannelMessageServiceImpl;
import io.nop.ai.gateway.channel.ChannelConnectorManager;
import io.nop.ai.gateway.channel.feishu.FeishuConnector;
import io.nop.ai.gateway.failover.ChatServiceFailoverAdapter;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M7-P1 round-3 (plan 2026-09-15-0116-1, Phase 2): proves nop-ai-gateway's
 * production beans are loaded through the STANDARD auto-assembly mechanism.
 *
 * <p>Runs the full {@code CoreInitialization.initialize()} — the real
 * production path in which {@code IocCoreInitializer} builds the app
 * container from every {@code /nop/autoconfig/*.beans} entry on the
 * classpath (including the new {@code nop-ai-gateway.beans}) — and asserts
 * the gateway's 12 production beans are present and injectable:
 *
 * <ul>
 *   <li>{@code nopChannelConnectorManager} (IChannelConnector lifecycle)</li>
 *   <li>{@code nopChannelSessionStore} (DB-backed session mapping)</li>
 *   <li>{@code nopFeishuConnector} (feishu channel connector)</li>
 *   <li>{@code nopChannelMessageService} (business message layer)</li>
 *   <li>{@code io.nop.ai.gateway.login.ChannelLoginApiBizModel} (scan login)</li>
 *   <li>{@code nopChatServiceFailoverAdapter} / {@code nopAiGatewayFailoverInterceptor}
 *       (failover two forms)</li>
 *   <li>{@code nopFailoverCircuitBreaker} / {@code nopFailoverConcurrencyRegistry}
 *       / {@code nopAiFailoverMetrics} / {@code nopAiRuleBasedSelectionStrategy}
 *       / {@code nopBackendMessageConverter_AI_DIALECT}</li>
 * </ul>
 *
 * <p><b>Anti-Hollow / wiring</b>: the beans are asserted to be the REAL
 * container instances (not hand-registered test stubs), and cross-bean
 * injection edges are asserted on the same instance (connector manager
 * injected into the message service, feishu connector collected by the
 * manager, failover adapter wrapping {@code nopChatService}).
 */
class TestGatewayAutoAssemblyIoC {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void allProductionBeansAutoAssembled() {
        IBeanContainer container = BeanContainer.instance();
        assertTrue(container.isRunning(), "app container must be running");

        // 12 production beans reachable by id (M7-P1 exit: >= 3 key beans).
        assertNotNull(container.getBean("nopBackendMessageConverter_AI_DIALECT"),
                "nopBackendMessageConverter_AI_DIALECT must be auto-assembled");
        assertNotNull(container.getBean("nopChannelConnectorManager"),
                "nopChannelConnectorManager must be auto-assembled");
        assertNotNull(container.getBean("nopChannelSessionStore"),
                "nopChannelSessionStore must be auto-assembled");
        assertNotNull(container.getBean("nopFeishuConnector"),
                "nopFeishuConnector must be auto-assembled");
        assertNotNull(container.getBean("nopChannelMessageService"),
                "nopChannelMessageService must be auto-assembled");
        assertNotNull(container.getBean("io.nop.ai.gateway.login.ChannelLoginApiBizModel"),
                "ChannelLoginApiBizModel must be auto-assembled");
        assertNotNull(container.getBean("nopAiRuleBasedSelectionStrategy"),
                "nopAiRuleBasedSelectionStrategy must be auto-assembled");
        assertNotNull(container.getBean("nopAiFailoverMetrics"),
                "nopAiFailoverMetrics must be auto-assembled");
        assertNotNull(container.getBean("nopFailoverCircuitBreaker"),
                "nopFailoverCircuitBreaker must be auto-assembled");
        assertNotNull(container.getBean("nopFailoverConcurrencyRegistry"),
                "nopFailoverConcurrencyRegistry must be auto-assembled");
        assertNotNull(container.getBean("nopChatServiceFailoverAdapter"),
                "nopChatServiceFailoverAdapter must be auto-assembled");
        assertNotNull(container.getBean("nopAiGatewayFailoverInterceptor"),
                "nopAiGatewayFailoverInterceptor must be auto-assembled");
    }

    /**
     * Wiring (anti-hollow): the channel message service must hold the SAME
     * connector manager instance as the container's nopChannelConnectorManager
     * bean — the beans file ref wiring is runtime-live, not a text-only
     * property. Field read via reflection (no public getter — injection point,
     * mirroring the readMessageService pattern in TestChannelMessageServiceIoC).
     */
    @Test
    void messageServiceWiredToConnectorManagerInstance() {
        IBeanContainer container = BeanContainer.instance();
        ChannelMessageServiceImpl svc = (ChannelMessageServiceImpl)
                container.getBean("nopChannelMessageService");
        ChannelConnectorManager manager = (ChannelConnectorManager)
                container.getBean("nopChannelConnectorManager");
        assertSame(manager, readField(svc, "channelConnectorManager"),
                "nopChannelMessageService.channelConnectorManager must be the container's manager instance");
    }

    /**
     * Wiring (anti-hollow): the failover adapter must wrap the REAL
     * nopChatService bean from nop-ai-core's ai-defaults auto-assembly.
     */
    @Test
    void failoverAdapterWiredToChatService() {
        IBeanContainer container = BeanContainer.instance();
        ChatServiceFailoverAdapter adapter = (ChatServiceFailoverAdapter)
                container.getBean("nopChatServiceFailoverAdapter");
        assertNotNull(adapter.getDelegate(), "adapter delegate must be injected");
        assertSame(container.getBean("nopChatService"), adapter.getDelegate(),
                "adapter delegate must be the nopChatService bean");
    }

    /**
     * Wiring (anti-hollow): the feishu connector must be the instance
     * auto-collected by the connector manager (transport layer sees the
     * connector through the manager, not a parallel construction).
     */
    @Test
    void feishuConnectorCollectedByManager() {
        IBeanContainer container = BeanContainer.instance();
        FeishuConnector connector = (FeishuConnector) container.getBean("nopFeishuConnector");
        ChannelConnectorManager manager = (ChannelConnectorManager)
                container.getBean("nopChannelConnectorManager");
        assertSame(connector, manager.lookup("feishu"),
                "manager.lookup(\"feishu\") must be the auto-assembled nopFeishuConnector instance");
        assertSame(container.getBean("nopChannelSessionStore"), readField(connector, "sessionStore"),
                "feishu connector's session store must be the nopChannelSessionStore bean");
    }

    private static Object readField(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError("unable to read field " + name, e);
        }
    }
}