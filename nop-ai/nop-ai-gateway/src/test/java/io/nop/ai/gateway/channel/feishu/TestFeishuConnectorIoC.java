package io.nop.ai.gateway.channel.feishu;

import io.nop.ai.gateway.channel.ChannelConnectorManager;
import io.nop.ai.gateway.channel.ChannelSession;
import io.nop.ai.gateway.channel.IChannelConnector;
import io.nop.ai.gateway.channel.IChannelSessionStore;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.integration.api.bind.IChannelBindProvider;
import io.nop.integration.feishu.client.FeishuClient;
import io.nop.integration.feishu.client.FeishuCredentials;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 IoC wiring test: verifies the beans.xml declarations produce a
 * running container where:
 * <ul>
 *   <li>FeishuConnector is constructed and its {@code @Inject} fields
 *       ({@link FeishuClient}, {@link IChannelSessionStore}) are non-null
 *       (real injection, not hollow).</li>
 *   <li>{@link ChannelConnectorManager} auto-collects FeishuConnector via
 *       {@code <ioc:collect-beans by-type="IChannelConnector">} and
 *       {@code lookup("feishu")} resolves.</li>
 *   <li>{@link ChannelLoginApiBizModel} auto-collects FeishuBindProvider via
 *       {@code <ioc:collect-beans by-type="IChannelBindProvider">}.</li>
 *   <li>FeishuCredentials is constructed with {@code @InjectValue} config
 *       setters resolved.</li>
 * </ul>
 *
 * <p>This is a real container test (not programmatic wiring) — it starts the
 * NopIoC container with the production beans.xml + test stubs and asserts the
 * collect-beans + {@code @Inject} wiring is live.
 */
class TestFeishuConnectorIoC {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IBeanContainer startContainer() {
        IResource resource = VirtualFileSystem.instance()
                .getResource("/test/beans/test-feishu-connector-ioc.beans.xml");
        IBeanContainer container = new io.nop.ioc.loader.AppBeanContainerLoader()
                .loadFromResource("test-feishu-ioc", resource);
        container.start();
        return container;
    }

    @Test
    void feishuConnectorBeanConstructedWithInjectedFields() {
        IBeanContainer container = startContainer();
        try {
            FeishuConnector connector = (FeishuConnector) container.getBean("nopFeishuConnector");
            assertNotNull(connector, "nopFeishuConnector bean must exist");
            assertEquals("feishu", connector.getChannelType());

            // verify @Inject fields are really injected (not null = hollow)
            assertNotNull(connector.feishuClient,
                    "FeishuConnector.feishuClient must be injected by the container");
            assertNotNull(connector.sessionStore,
                    "FeishuConnector.sessionStore must be injected by the container");
            // P2-CHANNEL (plan 2026-09-14-1937-2): the platform-standard
            // nopFeishuCredentials bean must be injected and reachable —
            // resolveCredentials then uses it when the options face is empty
            assertNotNull(connector.feishuCredentials,
                    "FeishuConnector.feishuCredentials (nopFeishuCredentials bean) must be injected");
            assertEquals(connector.feishuCredentials, container.getBean("nopFeishuCredentials"),
                    "the injected credentials must be the nopFeishuCredentials bean");
        } finally {
            container.stop();
        }
    }

    @Test
    void channelConnectorManagerCollectsFeishuConnector() {
        IBeanContainer container = startContainer();
        try {
            ChannelConnectorManager manager = (ChannelConnectorManager)
                    container.getBean("testChannelConnectorManager");
            assertNotNull(manager);

            // collect-beans must have gathered FeishuConnector
            IChannelConnector feishu = manager.lookup("feishu");
            assertNotNull(feishu, "manager.lookup(\"feishu\") must resolve after collect-beans");
            assertEquals("feishu", feishu.getChannelType());
            assertTrue(manager.getConnectors().size() >= 1,
                    "at least one connector must be collected");
        } finally {
            container.stop();
        }
    }

    @Test
    void channelLoginApiCollectsFeishuBindProvider() {
        IBeanContainer container = startContainer();
        try {
            BindProviderCollector collector = (BindProviderCollector)
                    container.getBean("testBindProviderCollector");
            assertNotNull(collector);

            // collect-beans must have gathered FeishuBindProvider
            assertNotNull(collector.providers, "providers collection must not be null");
            assertTrue(collector.providers.size() >= 1,
                    "at least one IChannelBindProvider must be collected");
            boolean hasFeishu = false;
            for (IChannelBindProvider p : collector.providers) {
                if ("feishu".equals(p.getChannelType())) {
                    hasFeishu = true;
                    break;
                }
            }
            assertTrue(hasFeishu, "FeishuBindProvider (channelType=feishu) must be collected");
        } finally {
            container.stop();
        }
    }

    @Test
    void feishuCredentialsBeanConstructedWithConfig() {
        IBeanContainer container = startContainer();
        try {
            FeishuCredentials creds = (FeishuCredentials) container.getBean("nopFeishuCredentials");
            assertNotNull(creds, "nopFeishuCredentials bean must exist");
            // @InjectValue("@cfg:nop.integration.feishu.appId|") resolves to ""
            // when no config is set (the "|" default); appId is non-null (resolved)
            assertNotNull(creds.getAppId(), "appId must be resolved by @InjectValue");
        } finally {
            container.stop();
        }
    }

    /** In-memory IChannelSessionStore stub for the wiring test. */
    public static class InMemorySessionStore implements IChannelSessionStore {
        final Map<String, ChannelSession> map = new ConcurrentHashMap<>();

        @Override
        public ChannelSession findByChannel(String channelType, String channelId) {
            return map.get(channelType + "::" + channelId);
        }

        @Override
        public void saveMapping(String channelType, String channelId, String sessionId, String agentName) {
            map.put(channelType + "::" + channelId,
                    new ChannelSession(channelType, channelId, sessionId, agentName,
                            new Timestamp(System.currentTimeMillis()),
                            new Timestamp(System.currentTimeMillis())));
        }

        @Override
        public void updateLastActive(String channelType, String channelId) {
            // no-op for test
        }
    }

    /** Collector for IChannelBindProvider beans — mirrors ChannelLoginApiBizModel's collect-beans. */
    public static class BindProviderCollector {
        public Collection<IChannelBindProvider> providers;

        public void setProviders(Collection<IChannelBindProvider> providers) {
            this.providers = providers;
        }
    }
}
