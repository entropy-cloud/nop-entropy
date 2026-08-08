package io.nop.ai.gateway.channel.feishu;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.IAgentEventPublisher;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.gateway.channel.ChannelConfig;
import io.nop.ai.gateway.channel.ChannelConnectorContext;
import io.nop.ai.gateway.channel.ChannelOutboundMessage;
import io.nop.ai.gateway.channel.ChannelSession;
import io.nop.ai.gateway.channel.IChannelSessionStore;
import io.nop.integration.feishu.client.FeishuClient;
import io.nop.integration.feishu.client.FeishuCredentials;
import io.nop.integration.feishu.client.FeishuInboundMessage;
import io.nop.integration.feishu.client.IMessageHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FeishuConnector}. Verifies the full inbound→execute→outbound
 * round-trip with stub {@link FeishuClient} + stub {@link IAgentEngine} — the
 * call chain is really connected (inbound really calls execute; future
 * callback really calls FeishuClient.sendMessage), not just type-compiled.
 *
 * <p><b>Anti-Hollow / wiring</b> (Minimum Rules #22, #23, #24):
 * <ul>
 *   <li>{@code fullInboundToOutboundRoundTrip} proves the end-to-end path:
 *       FeishuClient.onMessage → engine.execute → future completes →
 *       FeishuClient.sendMessage replies.</li>
 *   <li>call-count assertions prove execute + sendMessage are really invoked.</li>
 *   <li>group-without-@bot is ignored (correct semantics); DM is processed.</li>
 *   <li>stop → sendOutbound fails explicitly (no silent no-op).</li>
 * </ul>
 */
class TestFeishuConnector {

    private RecordingFeishuClient feishuClient;
    private RecordingAgentEngine engine;
    private InMemorySessionStore sessionStore;
    private FeishuConnector connector;

    @BeforeEach
    void setUp() {
        feishuClient = new RecordingFeishuClient();
        engine = new RecordingAgentEngine();
        sessionStore = new InMemorySessionStore();
        connector = new FeishuConnector();
        connector.setFeishuClient(feishuClient);
        connector.setSessionStore(sessionStore);
    }

    @AfterEach
    void tearDown() {
        connector.stop();
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

    @Test
    void fullInboundToOutboundRoundTrip() {
        connector.start(ctx());

        // inbound: a DM arrives
        connector.onMessage(dmMessage("oc_chat1", "ou_sender", "你好"));

        // execute was really called
        assertEquals(1, engine.executeCount, "inbound must trigger engine.execute");
        assertEquals(0, engine.sendMessageCount, "sendMessage (fire-and-forget) must NOT be used");

        // complete the future the engine returned
        engine.lastFuture.complete(resultWithAssistant("你好！有什么可以帮你的吗？"));

        // future callback really called FeishuClient.sendMessage
        assertEquals(1, feishuClient.sendMessageCount, "future callback must call FeishuClient.sendMessage");
        String sent = feishuClient.lastContent;
        assertTrue(sent.contains("你好！有什么可以帮你的吗？"),
                "reply must contain the assistant text: " + sent);

        // new session mapping was saved
        assertEquals(1, sessionStore.map.size(), "new session must be saved in the store");
    }

    @Test
    void inboundMessageTriggersEngineExecuteAndSessionMapping() {
        connector.start(ctx());
        connector.onMessage(dmMessage("oc_a", "ou_s", "hi"));

        assertTrue(engine.executeCount > 0, "execute must be called");
        engine.lastFuture.complete(resultWithAssistantAndSession("hello", "sess-1"));

        // mapping persisted with the result's sessionId
        ChannelSession saved = sessionStore.findByChannel("feishu", "oc_a");
        assertNotNull(saved, "session mapping must be saved");
        assertEquals("sess-1", saved.getSessionId());
    }

    @Test
    void executeResultTriggersFeishuReply() {
        connector.start(ctx());
        connector.onMessage(dmMessage("oc_b", "ou_s", "q"));
        engine.lastFuture.complete(resultWithAssistant("answer-42"));

        assertTrue(feishuClient.sendMessageCount > 0);
        assertTrue(feishuClient.lastContent.contains("answer-42"));
    }

    @Test
    void executeFailureTriggersErrorReply() {
        connector.start(ctx());
        connector.onMessage(dmMessage("oc_c", "ou_s", "q"));
        engine.lastFuture.completeExceptionally(new RuntimeException("LLM timeout"));

        assertTrue(feishuClient.sendMessageCount > 0, "error future must still trigger a reply");
        assertTrue(feishuClient.lastContent.contains("执行出错"),
                "error reply must mention failure: " + feishuClient.lastContent);
        assertTrue(feishuClient.lastContent.contains("LLM timeout"));
    }

    @Test
    void executeResultStatusFailedTriggersErrorReply() {
        connector.start(ctx());
        connector.onMessage(dmMessage("oc_d", "ou_s", "q"));
        engine.lastFuture.complete(new AgentExecutionResult(
                AgentExecStatus.failed, null, Collections.emptyList(),
                0, 0, 0, "tool-execution-failure", null, null));

        assertTrue(feishuClient.sendMessageCount > 0);
        assertTrue(feishuClient.lastContent.contains("tool-execution-failure"),
                "failed status must surface the error: " + feishuClient.lastContent);
    }

    @Test
    void groupMessageWithoutBotMentionIsIgnored() {
        connector.start(ctx());
        // group message, no mentions array
        connector.onMessage(groupMessage("oc_g1", "ou_s", "hello", null));

        assertEquals(0, engine.executeCount,
                "group message without @bot must NOT trigger execute");
        assertEquals(0, feishuClient.sendMessageCount);
    }

    @Test
    void groupMessageWithEmptyMentionsIsIgnored() {
        connector.start(ctx());
        connector.onMessage(groupMessage("oc_g2", "ou_s", "hello", "[]"));

        assertEquals(0, engine.executeCount,
                "group message with empty mentions must NOT trigger execute");
    }

    @Test
    void dmMessageIsProcessed() {
        connector.start(ctx());
        connector.onMessage(dmMessage("oc_dm", "ou_s", "hello"));

        assertTrue(engine.executeCount > 0, "DM must trigger execute");
    }

    @Test
    void sendOutboundWithAttachmentDegradesToTextLink() {
        connector.start(ctx());

        ChannelOutboundMessage msg = new ChannelOutboundMessage();
        msg.setText("see report");
        ChannelOutboundMessage.Attachment att = new ChannelOutboundMessage.Attachment();
        att.setName("report.pdf");
        att.setUrl("https://example.com/report.pdf");
        msg.setAttachments(Collections.singletonList(att));

        connector.sendOutbound("oc_out", msg);

        assertEquals(1, feishuClient.sendMessageCount);
        assertNotNull(feishuClient.lastContent);
        // degradation: attachment turned into a link notice (supportsFileUpload=false)
        assertTrue(feishuClient.lastContent.contains("report.pdf"),
                "attachment name must appear in degraded text: " + feishuClient.lastContent);
        assertTrue(feishuClient.lastContent.contains("https://example.com/report.pdf"),
                "attachment url must appear in degraded text: " + feishuClient.lastContent);
    }

    @Test
    void sendOutboundAfterStopFailsExplicitly() {
        connector.start(ctx());
        connector.stop();

        assertThrows(Exception.class, () -> {
            ChannelOutboundMessage msg = new ChannelOutboundMessage();
            msg.setText("x");
            connector.sendOutbound("oc_x", msg);
        }, "sendOutbound after stop must fail explicitly, not silently no-op");
    }

    @Test
    void startRegistersInboundHandlerWithFeishuClient() {
        connector.start(ctx());
        assertEquals(1, feishuClient.startCount, "start must register handler with FeishuClient");
        assertNotNull(feishuClient.registeredHandler, "IMessageHandler must be registered");
        // the registered handler IS the connector (wiring verification)
        assertSame(connector, feishuClient.registeredHandler);
    }

    @Test
    void existingSessionIsReused() {
        sessionStore.map.put("feishu::oc_existing",
                new ChannelSession("feishu", "oc_existing", "sess-existing", "test-agent",
                        new Timestamp(0), new Timestamp(0)));
        connector.start(ctx());
        connector.onMessage(dmMessage("oc_existing", "ou_s", "continue"));

        assertTrue(engine.executeCount > 0);
        // the request carried the existing sessionId
        assertEquals("sess-existing", engine.lastRequest.getSessionId(),
                "existing sessionId must be reused in the execute request");
    }

    // ---- stubs ------------------------------------------------------------

    private static void assertSame(Object expected, Object actual) {
        // minimal assertSame without JUnit5 import ambiguity
        if (expected != actual) {
            throw new AssertionError("expected same instance but was different");
        }
    }

    private static AgentExecutionResult resultWithAssistant(String text) {
        return resultWithAssistantAndSession(text, "sess-auto");
    }

    private static AgentExecutionResult resultWithAssistantAndSession(String text, String sessionId) {
        List<io.nop.ai.api.chat.messages.ChatMessage> msgs = new ArrayList<>();
        msgs.add(new ChatAssistantMessage(text));
        return new AgentExecutionResult(
                AgentExecStatus.completed, null, msgs,
                1, 10, 100, null, sessionId, null);
    }

    /** FeishuClient subclass: records calls without real network. */
    static class RecordingFeishuClient extends FeishuClient {
        int startCount;
        int stopCount;
        int sendMessageCount;
        String lastContent;
        String lastReceiveId;
        IMessageHandler registeredHandler;

        @Override
        public synchronized void start(FeishuCredentials credentials, IMessageHandler handler) {
            startCount++;
            this.registeredHandler = handler;
        }

        @Override
        public synchronized void stop() {
            stopCount++;
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
        }
    }

    static class RecordingAgentEngine implements IAgentEngine {
        int executeCount;
        int sendMessageCount;
        AgentMessageRequest lastRequest;
        CompletableFuture<AgentExecutionResult> lastFuture;

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            sendMessageCount++;
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

    static class InMemorySessionStore implements IChannelSessionStore {
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
            ChannelSession s = map.get(channelType + "::" + channelId);
            if (s == null) {
                throw new IllegalStateException("updateLastActive: no mapping for " + channelId);
            }
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
}
