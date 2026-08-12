package io.nop.ai.gateway.channel;

import io.nop.ai.gateway.channel.feishu.FeishuConnector;
import io.nop.ai.gateway.channel.feishu.TestFeishuConversationE2E;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.auth.dao.entity.NopAuthExtLogin;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.integration.api.channel.ChannelBinding;
import io.nop.integration.api.channel.ChannelTypeCodes;
import io.nop.integration.api.channel.OutboundChannelMessage;
import io.nop.integration.api.channel.SendResult;
import io.nop.integration.api.channel.UserChannelResolver;
import io.nop.integration.feishu.client.FeishuCredentials;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6-3 proactive-notification E2E (real IoC container + real H2). Verifies the
 * outbound business path: {@code IChannelMessageService.sendToUser} ->
 * {@link UserChannelResolver} (reads {@code NopAuthExtLogin} from H2) ->
 * {@link ChannelConnectorManager#lookup} -> {@link FeishuConnector#sendOutbound}
 * -> FeishuClient.sendMessage — all with real production classes, the only
 * stub being the network boundary (RecordingFeishuClient).
 *
 * <p>The container (test-channel-e2e-messaging.beans.xml) provides the real
 * {@link FeishuConnector}, real {@link ChannelConnectorManager}, real H2-backed
 * {@link io.nop.ai.gateway.channel.ChannelSessionStoreImpl} (via
 * {@link TestFeishuConversationE2E.E2EH2SessionStore}) and the
 * RecordingFeishuClient stub. On top of that the test wires the REAL
 * {@link ChannelMessageServiceImpl} with a resolver that genuinely reads
 * {@code NopAuthExtLogin} from the same H2.
 *
 * <p><b>Resolver note (Phase 0 Decision: Candidate B)</b>: the genuine
 * {@code UserChannelResolverImpl} class lives in nop-auth-service, which
 * cannot be pulled into the gateway test classpath (its
 * auth-service.beans.xml hard-imports nop-biz resources and breaks
 * AiDialectBackendMessageConverterTest — verified in Phase 0). This test
 * therefore uses {@link E2EUserChannelResolver}, a FAITHFUL REPLICA of
 * UserChannelResolverImpl's query logic (same example: userId + verified=TRUE
 * + delFlag=0, orderBy lastLoginTime DESC, same ChannelTypeCodes
 * loginType&lt;-&gt;channelType mapping) that really reads NopAuthExtLogin from
 * H2 via IDaoProvider — real DB reading, not a canned stub. Production
 * deployments use the genuine UserChannelResolverImpl (in nop-auth-service,
 * injected by IoC when deployed alongside the gateway).
 *
 * <p><b>Anti-Hollow / wiring</b> (Minimum Rules #22, #23): sendToUser really
 * invokes resolver.resolve (H2 hit), manager.lookup (real FeishuConnector),
 * connector.sendOutbound (real), FeishuClient.sendMessage (count &gt; 0); the
 * no-binding case really returns NO_BINDING with FeishuClient untouched.
 */
class TestChannelProactiveNotifyE2E {

    private IBeanContainer container;
    private ChannelConnectorManager manager;
    private TestFeishuConversationE2E.RecordingFeishuClient feishuClient;
    private TestFeishuConversationE2E.E2EH2SessionStore h2Store;
    private ChannelMessageServiceImpl messageService;
    private IDaoProvider daoProvider;

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
        // reuse the W6-1 container (real FeishuConnector + manager + H2 store +
        // RecordingFeishuClient), all wired by the Nop IoC container
        IResource resource = VirtualFileSystem.instance()
                .getResource("/test/beans/test-channel-e2e-messaging.beans.xml");
        container = new AppBeanContainerLoader().loadFromResource("e2e-notify", resource);
        container.start();

        manager = (ChannelConnectorManager) container.getBean("nopChannelConnectorManager");
        feishuClient = (TestFeishuConversationE2E.RecordingFeishuClient) container.getBean("nopFeishuClient");
        h2Store = (TestFeishuConversationE2E.E2EH2SessionStore) container.getBean("nopChannelSessionStore");
        daoProvider = h2Store.getDaoProvider();

        // REAL ChannelMessageServiceImpl with a REAL-reading resolver + manager
        messageService = new ChannelMessageServiceImpl();
        messageService.setUserChannelResolver(new E2EUserChannelResolver(daoProvider));
        messageService.setChannelConnectorManager(manager);

        // start the connector so sendOutbound is allowed (started==true)
        manager.startAll(ctx());
    }

    @AfterEach
    void tearDown() {
        try {
            if (manager != null) {
                manager.stopAll();
            }
        } catch (Exception ignored) {
            // best-effort
        }
        try {
            if (h2Store != null) {
                h2Store.close();
            }
        } catch (Exception ignored) {
            // best-effort
        }
        try {
            if (container != null) {
                container.stop();
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private ChannelConnectorContext ctx() {
        ChannelConfig config = new ChannelConfig("test-agent");
        FeishuCredentials creds = new FeishuCredentials();
        creds.setAppId("cli_app");
        creds.setAppSecret("secret");
        config.setOption("feishu.credentials", creds);
        // the proactive-notify path never calls engine.execute (only
        // sendOutbound is exercised), but ChannelConnectorContext rejects null
        // agentEngine/eventPublisher, so minimal never-invoked stubs are passed.
        return new ChannelConnectorContext(new NoCallEngine(), new NoopPublisher(), config);
    }

    private void seedBinding(String sid, String userId, int loginType, String extId,
                             boolean verified, int delFlag, long lastLoginMillis) {
        IEntityDao<NopAuthExtLogin> dao = daoProvider.daoFor(NopAuthExtLogin.class);
        NopAuthExtLogin row = dao.newEntity();
        row.setSid(sid);
        row.setUserId(userId);
        row.setLoginType(loginType);
        row.setExtId(extId);
        row.setVerified(verified);
        row.setDelFlag((byte) delFlag);
        row.setVersion(0);
        row.setCreatedBy("e2e");
        row.setCreateTime(new Timestamp(System.currentTimeMillis()));
        if (lastLoginMillis > 0) {
            row.setLastLoginTime(new Timestamp(lastLoginMillis));
        }
        dao.saveEntity(row);
    }

    private OutboundChannelMessage textMessage(String text) {
        OutboundChannelMessage m = new OutboundChannelMessage();
        m.setText(text);
        return m;
    }

    // =================== E2E tests ===================

    @Test
    void e2eSendToUserWithBindingSent() {
        // seed a verified, active feishu binding (loginType=20) for user "u_bound"
        seedBinding("sid-feishu-1", "u_bound", ChannelTypeCodes.loginType("feishu"),
                "oc_bound_chat", true, 0, System.currentTimeMillis());

        OutboundChannelMessage msg = textMessage("您的报告已生成");

        SendResult result = messageService.sendToUser("u_bound", msg);

        assertEquals(SendResult.SENT, result, "bound user -> message must be SENT");
        // sendOutbound really invoked FeishuClient.sendMessage
        assertEquals(1, feishuClient.sendMessageCount,
                "sendToUser must really reach FeishuClient.sendMessage");
        // the reply went to the binding's channel address (extId)
        assertEquals("oc_bound_chat", feishuClient.lastReceiveId,
                "reply must target the binding's channel address");
        assertTrue(feishuClient.lastContent.contains("您的报告已生成"),
                "reply must contain the notification text: " + feishuClient.lastContent);
    }

    @Test
    void e2eSendToUserNoBindingReturnsNoBinding() {
        // no seeded binding for "u_none"
        int before = feishuClient.sendMessageCount;

        OutboundChannelMessage msg = textMessage("hello");

        SendResult result = messageService.sendToUser("u_none", msg);

        assertEquals(SendResult.NO_BINDING, result,
                "no binding -> explicit NO_BINDING, not an exception");
        assertEquals(before, feishuClient.sendMessageCount,
                "NO_BINDING must NOT reach FeishuClient.sendMessage");
    }

    @Test
    void e2eUnverifiedBindingIsNotResolved() {
        // a binding exists but verified=false -> resolver excludes it -> NO_BINDING
        seedBinding("sid-unverified", "u_unverified", ChannelTypeCodes.loginType("feishu"),
                "oc_unverified", false, 0, System.currentTimeMillis());

        SendResult result = messageService.sendToUser("u_unverified", textMessage("hi"));

        assertEquals(SendResult.NO_BINDING, result,
                "unverified binding must NOT be resolved -> NO_BINDING");
        assertEquals(0, feishuClient.sendMessageCount,
                "unverified binding must NOT reach sendMessage");
    }

    @Test
    void e2eAttachmentDegradesToTextLink() {
        seedBinding("sid-att", "u_att", ChannelTypeCodes.loginType("feishu"),
                "oc_att_chat", true, 0, System.currentTimeMillis());

        OutboundChannelMessage msg = new OutboundChannelMessage();
        msg.setText("see report");
        OutboundChannelMessage.Attachment att = new OutboundChannelMessage.Attachment();
        att.setName("report.pdf");
        att.setUrl("https://example.com/report.pdf");
        msg.setAttachments(Collections.singletonList(att));

        SendResult result = messageService.sendToUser("u_att", msg);

        assertEquals(SendResult.SENT, result);
        // supportsFileUpload=false -> attachment degraded to a text link/notice
        assertTrue(feishuClient.lastContent.contains("report.pdf"),
                "attachment name must appear in the degraded text: " + feishuClient.lastContent);
        assertTrue(feishuClient.lastContent.contains("https://example.com/report.pdf"),
                "attachment url must appear in the degraded text: " + feishuClient.lastContent);
    }

    /**
     * Faithful replica of {@code io.nop.auth.service.channel.UserChannelResolverImpl}
     * (Phase 0 Decision: Candidate B). Really reads {@code NopAuthExtLogin}
     * from H2 via {@link IDaoProvider} using the same example query
     * (userId + verified=TRUE + delFlag=0), the same lastLoginTime-DESC
     * ordering, and the same {@link ChannelTypeCodes} loginType&lt;-&gt;channelType
     * mapping. Non-channel login types (password/SSO) are filtered out, never
     * silently included.
     */
    static class E2EUserChannelResolver implements UserChannelResolver {
        private static final Byte DEL_FLAG_ACTIVE = (byte) 0;
        private final IDaoProvider daoProvider;

        E2EUserChannelResolver(IDaoProvider daoProvider) {
            this.daoProvider = daoProvider;
        }

        @Override
        public List<ChannelBinding> resolve(String userId) {
            if (userId == null || userId.isEmpty()) {
                return Collections.emptyList();
            }
            NopAuthExtLogin example = newExample(userId);
            List<OrderFieldBean> orderBy = Collections.singletonList(
                    OrderFieldBean.desc(NopAuthExtLogin.PROP_NAME_lastLoginTime));
            List<NopAuthExtLogin> rows = dao().findAllByExample(example, orderBy);
            if (rows.isEmpty()) {
                return Collections.emptyList();
            }
            List<ChannelBinding> bindings = new ArrayList<>(rows.size());
            for (NopAuthExtLogin row : rows) {
                ChannelBinding b = toBinding(row);
                if (b != null) {
                    bindings.add(b);
                }
            }
            return bindings;
        }

        @Override
        public ChannelBinding resolve(String userId, String channelType) {
            if (userId == null || userId.isEmpty() || channelType == null) {
                return null;
            }
            int loginType = ChannelTypeCodes.loginType(channelType);
            if (loginType < 0) {
                return null;
            }
            NopAuthExtLogin example = newExample(userId);
            example.setLoginType(loginType);
            NopAuthExtLogin row = dao().findFirstByExample(example);
            return row == null ? null : toBinding(row);
        }

        private IEntityDao<NopAuthExtLogin> dao() {
            return daoProvider.daoFor(NopAuthExtLogin.class);
        }

        private NopAuthExtLogin newExample(String userId) {
            NopAuthExtLogin example = new NopAuthExtLogin();
            example.setUserId(userId);
            example.setVerified(Boolean.TRUE);
            example.setDelFlag(DEL_FLAG_ACTIVE);
            return example;
        }

        private ChannelBinding toBinding(NopAuthExtLogin row) {
            Integer loginType = row.getLoginType();
            if (loginType == null) {
                return null;
            }
            String channelType = ChannelTypeCodes.channelType(loginType);
            if (channelType == null) {
                return null;
            }
            return new ChannelBinding(row.getUserId(), channelType, row.getExtId());
        }
    }

    /**
     * Minimal {@link io.nop.ai.agent.engine.IAgentEngine} stub for the context
     * constructor. The proactive-notify path never invokes it; if it ever is,
     * it fails explicitly rather than silently returning null.
     */
    static class NoCallEngine implements io.nop.ai.agent.engine.IAgentEngine {
        @Override
        public io.nop.ai.agent.engine.AgentMessageAck sendMessage(io.nop.ai.agent.engine.AgentMessageRequest request) {
            throw new UnsupportedOperationException("NoCallEngine.sendMessage must not be invoked by sendToUser");
        }

        @Override
        public java.util.concurrent.CompletableFuture<io.nop.ai.agent.engine.AgentExecutionResult>
                execute(io.nop.ai.agent.engine.AgentMessageRequest request) {
            throw new UnsupportedOperationException("NoCallEngine.execute must not be invoked by sendToUser");
        }
    }

    static class NoopPublisher implements io.nop.ai.agent.engine.IAgentEventPublisher {
        @Override
        public void publish(io.nop.ai.agent.engine.AgentEvent event) {
            // no-op
        }

        @Override
        public void addSubscriber(io.nop.ai.agent.engine.IAgentEventSubscriber subscriber) {
            // no-op
        }

        @Override
        public void removeSubscriber(io.nop.ai.agent.engine.IAgentEventSubscriber subscriber) {
            // no-op
        }
    }
}
