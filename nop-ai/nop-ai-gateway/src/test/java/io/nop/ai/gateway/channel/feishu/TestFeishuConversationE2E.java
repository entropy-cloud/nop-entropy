package io.nop.ai.gateway.channel.feishu;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.IAgentEventPublisher;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.gateway.channel.ChannelConfig;
import io.nop.ai.gateway.channel.ChannelConnectorContext;
import io.nop.ai.gateway.channel.ChannelConnectorManager;
import io.nop.ai.gateway.channel.ChannelSession;
import io.nop.ai.gateway.channel.ChannelSessionStoreImpl;
import io.nop.ai.gateway.channel.ChannelSession;
import io.nop.ai.gateway.channel.IChannelConnector;
import io.nop.ai.gateway.channel.IChannelSessionStore;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.integration.feishu.client.FeishuClient;
import io.nop.integration.feishu.client.FeishuCredentials;
import io.nop.integration.feishu.client.FeishuInboundMessage;
import io.nop.integration.feishu.client.IMessageHandler;
import io.nop.ioc.loader.AppBeanContainerLoader;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6-1 inbound-conversation E2E (real IoC container). Assembles the REAL
 * transport components in a Nop IoC container started by
 * {@link AppBeanContainerLoader} and verifies the full inbound -> agent ->
 * outbound round-trip, with the external boundaries (LLM + network) stubbed
 * and EVERYTHING else real:
 *
 * <ul>
 *   <li>{@link FeishuConnector} — real (constructed by the container; both
 *       {@code @Inject} fields feishuClient + sessionStore resolved by the
 *       container from the test beans.xml).</li>
 *   <li>{@link ChannelConnectorManager} — real, collects FeishuConnector via
 *       {@code <ioc:collect-beans>} (real collect-beans wiring, not a
 *       programmatic register).</li>
 *   <li>{@link ChannelSessionStoreImpl} — real, via the {@link E2EH2SessionStore}
 *       subclass which builds an in-memory H2 + OrmSessionFactoryBean (creating
 *       the {@code nop_ai_channel_session} table from the live ORM model) and
 *       sets its own daoProvider/ormTemplate. All store methods are inherited
 *       from ChannelSessionStoreImpl and read/write the real H2 database.</li>
 *   <li>{@link FeishuClient} — a {@link RecordingFeishuClient} test subclass
 *       (network boundary: records sendMessage, never opens a real socket).</li>
 *   <li>{@link IAgentEngine} — a {@link CannedAgentEngine} test impl (LLM
 *       boundary: returns {@code CompletableFuture}s the test completes).</li>
 * </ul>
 *
 * <p><b>Anti-Hollow / wiring</b> (Minimum Rules #22, #23): the round-trip
 * test proves the end-to-end path (FeishuClient.onMessage -> engine.execute
 * -> future -> FeishuClient.sendMessage) is really connected inside the real
 * container; call-count assertions prove execute + sendMessage are really
 * invoked; the session-reuse test proves the store really reads/writes H2.
 */
public class TestFeishuConversationE2E {

    private IBeanContainer container;
    private FeishuConnector connector;
    private ChannelConnectorManager manager;
    private RecordingFeishuClient feishuClient;
    private CannedAgentEngine engine;
    private E2EH2SessionStore h2Store;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        // real IoC container: wires FeishuConnector + manager + the H2-backed
        // store (self-initialized) + the recording FeishuClient stub
        IResource resource = VirtualFileSystem.instance()
                .getResource("/test/beans/test-channel-e2e-messaging.beans.xml");
        container = new AppBeanContainerLoader().loadFromResource("e2e-feishu", resource);
        container.start();

        connector = (FeishuConnector) container.getBean("nopFeishuConnector");
        manager = (ChannelConnectorManager) container.getBean("nopChannelConnectorManager");
        feishuClient = (RecordingFeishuClient) container.getBean("nopFeishuClient");
        h2Store = (E2EH2SessionStore) container.getBean("nopChannelSessionStore");
        engine = new CannedAgentEngine();

        // verify the container REALLY injected both @Inject fields (anti-hollow)
        assertNotNull(connector.feishuClient, "container must inject feishuClient");
        assertNotNull(connector.sessionStore, "container must inject the H2-backed sessionStore");

        // REAL lifecycle: manager.startAll really calls FeishuConnector.start
        manager.startAll(ctx());
    }

    @AfterEach
    void tearDown() {
        try {
            if (manager != null) {
                manager.stopAll();
            }
        } catch (Exception ignored) {
            // best-effort teardown
        }
        try {
            if (h2Store != null) {
                h2Store.close();
            }
        } catch (Exception ignored) {
            // best-effort teardown
        }
        try {
            if (container != null) {
                container.stop();
            }
        } catch (Exception ignored) {
            // best-effort teardown
        }
    }

    private ChannelConnectorContext ctx() {
        ChannelConfig config = new ChannelConfig("test-agent");
        FeishuCredentials creds = new FeishuCredentials();
        creds.setAppId("cli_app");
        creds.setAppSecret("secret");
        config.setOption("feishu.credentials", creds);
        return new ChannelConnectorContext(engine, new NoopPublisher(), config);
    }

    private FeishuInboundMessage dmMessage(String chatId, String senderId, String text) {
        return message(chatId, senderId, text, "p2p", null);
    }

    private FeishuInboundMessage groupMessage(String chatId, String senderId, String text,
                                              String mentionsJson) {
        return message(chatId, senderId, text, "group", mentionsJson);
    }

    private FeishuInboundMessage message(String chatId, String senderId, String text,
                                         String chatType, String mentionsJson) {
        FeishuInboundMessage m = new FeishuInboundMessage();
        m.setReceiveIdType("chat_id");
        m.setReceiveId(chatId);
        m.setMsgType("text");
        m.setContent("{\"text\":\"" + text + "\"}");
        m.setSenderId(senderId);
        m.setChatType(chatType);
        String raw = "{\"event\":{\"message\":{\"chat_id\":\"" + chatId + "\""
                + (mentionsJson != null ? ",\"mentions\":" + mentionsJson : "")
                + "}}}";
        m.setRawPayload(raw.getBytes(StandardCharsets.UTF_8));
        return m;
    }

    private static AgentExecutionResult resultWith(String text, String sessionId) {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(new ChatAssistantMessage(text));
        return new AgentExecutionResult(AgentExecStatus.completed, null, msgs,
                1, 10, 100, null, sessionId, null);
    }

    // =================== E2E tests ===================

    @Test
    void e2eRoundTripInboundToReply() {
        // the manager collected the connector and startAll really started it
        assertEquals(1, manager.getConnectors().size(), "manager must collect the FeishuConnector");
        IChannelConnector lookedUp = manager.lookup("feishu");
        assertTrue(lookedUp == connector, "manager.lookup must return the same real connector");
        assertEquals(1, feishuClient.startCount, "startAll really invoked FeishuConnector.start (handler registered)");

        // inbound DM arrives
        connector.onMessage(dmMessage("oc_e2e_1", "ou_s", "你好"));

        // execute really called
        assertEquals(1, engine.executeCount, "inbound must really call engine.execute");
        // complete the future the engine returned
        engine.lastFuture.complete(resultWith("你好！我是助手。", "sess-e2e-1"));

        // future callback really called FeishuClient.sendMessage
        assertEquals(1, feishuClient.sendMessageCount, "future callback must really call FeishuClient.sendMessage");
        assertTrue(feishuClient.lastContent.contains("你好！我是助手。"),
                "reply must contain the assistant text: " + feishuClient.lastContent);
    }

    @Test
    void e2eSessionReuseAcrossTwoMessages() {
        // message 1: new session — execute + saveMapping
        connector.onMessage(dmMessage("oc_e2e_2", "ou_s", "第一句"));
        assertEquals(1, engine.executeCount);
        engine.lastFuture.complete(resultWith("reply-1", "sess-reuse-1"));

        // the store REALLY persisted the mapping in H2
        ChannelSession saved = h2Store.findByChannel("feishu", "oc_e2e_2");
        assertNotNull(saved, "session mapping must be persisted in H2");
        assertEquals("sess-reuse-1", saved.getSessionId());

        // message 2 (same chatId): store hit -> reuse sessionId
        connector.onMessage(dmMessage("oc_e2e_2", "ou_s", "第二句"));
        assertEquals(2, engine.executeCount, "second message must also call execute");
        assertEquals("sess-reuse-1", engine.lastRequest.getSessionId(),
                "second message must reuse the sessionId from the H2-mapped session");
    }

    @Test
    void e2eGroupAtBotFilter() {
        int before = engine.executeCount;

        // group message WITH a documented-shape @bot mention -> processed
        String documented = "[{\"key\":\"@_user_1\",\"id\":{\"open_id\":\"ou_bot\","
                + "\"union_id\":\"on_x\",\"name\":\"Bot\"}}]";
        connector.onMessage(groupMessage("oc_e2e_g1", "ou_s", "hi", documented));
        assertEquals(before + 1, engine.executeCount,
                "group message with documented @bot mention must be processed");

        // group message WITHOUT @bot -> skipped (correct semantics, not silent)
        connector.onMessage(groupMessage("oc_e2e_g2", "ou_s", "hi", null));
        assertEquals(before + 1, engine.executeCount,
                "group message without @bot must NOT call execute");
    }

    @Test
    void e2eErrorPathRepliesWithError() {
        connector.onMessage(dmMessage("oc_e2e_err", "ou_s", "q"));
        // future completes exceptionally -> error reply
        engine.lastFuture.completeExceptionally(new RuntimeException("LLM unreachable"));

        assertTrue(feishuClient.sendMessageCount > 0, "error future must still trigger a reply");
        assertTrue(feishuClient.lastContent.contains("执行出错"),
                "error reply must mention failure: " + feishuClient.lastContent);
        assertTrue(feishuClient.lastContent.contains("LLM unreachable"));
    }

    @Test
    void e2eLongTextSegmented() {
        connector.onMessage(dmMessage("oc_e2e_long", "ou_s", "q"));
        // 5000-char response -> Phase 1 segmentation kicks in (visible at E2E layer)
        StringBuilder sb = new StringBuilder(5000);
        for (int i = 0; i < 500; i++) {
            sb.append("0123456789");
        }
        String longText = sb.toString();
        engine.lastFuture.complete(resultWith(longText, "sess-long"));

        assertTrue(feishuClient.sendMessageCount > 1,
                "long assistant text must be segmented into multiple sendMessage calls");
        // segments concatenate back to the original (no character loss)
        StringBuilder rejoined = new StringBuilder(longText.length());
        for (String envelope : feishuClient.sentContents) {
            rejoined.append(FeishuConnector.extractJsonTextField(envelope, "text"));
        }
        assertEquals(longText, rejoined.toString(),
                "E2E segmented reply must concatenate back to the original text");
    }

    // =================== stubs (external boundaries only) ===================

    /**
     * Network-boundary stub: records sendMessage without any real socket.
     * Public static + no-arg c'tor so the Nop IoC container can instantiate it.
     */
    public static class RecordingFeishuClient extends FeishuClient {
        public int startCount;
        public int sendMessageCount;
        public String lastContent;
        public String lastReceiveId;
        public final List<String> sentContents = Collections.synchronizedList(new ArrayList<>());
        public IMessageHandler registeredHandler;

        @Override
        public void start(FeishuCredentials credentials, IMessageHandler handler) {
            startCount++;
            this.registeredHandler = handler;
        }

        @Override
        public void stop() {
            // no-op
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void sendMessage(String receiveIdType, String receiveId, String msgType, String content) {
            sendMessageCount++;
            this.lastReceiveId = receiveId;
            this.lastContent = content;
            this.sentContents.add(content);
        }
    }

    /**
     * LLM-boundary stub: returns CompletableFutures the test completes.
     */
    static class CannedAgentEngine implements IAgentEngine {
        int executeCount;
        AgentMessageRequest lastRequest;
        CompletableFuture<AgentExecutionResult> lastFuture;

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            return new AgentMessageAck(null, "accepted");
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount++;
            lastRequest = request;
            lastFuture = new CompletableFuture<>();
            return lastFuture;
        }
    }

    static class NoopPublisher implements IAgentEventPublisher {
        @Override
        public void publish(io.nop.ai.agent.engine.AgentEvent event) {
        }

        @Override
        public void addSubscriber(io.nop.ai.agent.engine.IAgentEventSubscriber subscriber) {
        }

        @Override
        public void removeSubscriber(io.nop.ai.agent.engine.IAgentEventSubscriber subscriber) {
        }
    }

    /**
     * An {@link IChannelSessionStore} backed by a real
     * {@link ChannelSessionStoreImpl} reading an in-memory H2 database. This
     * holder builds its own H2 + OrmSessionFactoryBean in its constructor
     * (creating the {@code nop_ai_channel_session} table from the live ORM
     * model), constructs a real {@link ChannelSessionStoreImpl}, and sets the
     * store's daoProvider/ormTemplate. It implements {@link IChannelSessionStore}
     * directly (rather than extending ChannelSessionStoreImpl) so the Nop IoC
     * container does not process the inherited {@code @Inject IDaoProvider} /
     * {@code @Inject IOrmTemplate} fields — those are satisfied manually on
     * the delegate. Instantiated by the container as the
     * {@code channelSessionStore} bean; every method delegates to the real
     * store, so the call chain reads/writes a real database.
     * {@link #close()} tears the H2 stack down between tests.
     */
    public static class E2EH2SessionStore implements IChannelSessionStore {
        private final ChannelSessionStoreImpl delegate;
        private final OrmSessionFactoryBean factoryBean;
        private final IDaoProvider daoProvider;

        public E2EH2SessionStore() {
            SimpleDataSource dataSource = new SimpleDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:e2e-store-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");

            JdbcFactory factory = new JdbcFactory();
            ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
            IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

            factoryBean = new OrmSessionFactoryBean();
            factoryBean.setJdbcTemplate(jdbcTemplate);
            factoryBean.setBeanProvider(new io.nop.api.core.ioc.IBeanProvider() {
                @Override
                public boolean containsBean(String name) {
                    return false;
                }

                @Override
                public <T> T getBeanByType(Class<T> clazz) {
                    try {
                        return clazz.getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("cannot instantiate " + clazz.getName(), e);
                    }
                }

                @Override
                public Object getBean(String name) {
                    return null;
                }

                @Override
                public String getBeanScope(String name) {
                    return null;
                }
            });
            factoryBean.setGlobalCache(new io.nop.commons.cache.LocalCacheProvider("e2e-store",
                    io.nop.commons.cache.CacheConfig.newConfig(100)));
            factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
            factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
            factoryBean.init();

            IOrmSessionFactory sessionFactory = factoryBean.getObject();
            IOrmTemplate orm = new OrmTemplateImpl(sessionFactory);
            // create ALL tables (incl. nop_ai_channel_session AND, when
            // nop-auth-dao is on the test classpath, nop_auth_ext_login) from
            // the live ORM model
            Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
            String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                    .createTables(tables, false);
            jdbcTemplate.executeMultiSql(new SQL(createSql));

            daoProvider = new OrmDaoProvider(orm);
            delegate = new ChannelSessionStoreImpl();
            delegate.setDaoProvider(daoProvider);
            delegate.setOrmTemplate(orm);
        }

        /**
         * The H2-backed IDaoProvider (shared with the store). Exposed so the
         * W6-3 proactive-notify E2E can build a resolver that reads
         * {@code NopAuthExtLogin} from the same database and seed binding rows.
         */
        public IDaoProvider getDaoProvider() {
            return daoProvider;
        }

        @Override
        public ChannelSession findByChannel(String channelType, String channelId) {
            return delegate.findByChannel(channelType, channelId);
        }

        @Override
        public void saveMapping(String channelType, String channelId, String sessionId, String agentName) {
            delegate.saveMapping(channelType, channelId, sessionId, agentName);
        }

        @Override
        public void updateLastActive(String channelType, String channelId) {
            delegate.updateLastActive(channelType, channelId);
        }

        public void close() {
            if (factoryBean != null) {
                try {
                    factoryBean.destroy();
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        }
    }
}
