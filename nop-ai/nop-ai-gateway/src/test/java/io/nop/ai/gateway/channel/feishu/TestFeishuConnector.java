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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        // M6-P1: the bot's own identity for exact @bot matching in groups.
        config.setOption("feishu.botOpenId", "ou_bot");
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

    // ---- W6-1 Phase 1: long-text segmentation / rate limit / @bot shape ----

    @Test
    void longTextReplyIsSegmentedByMaxMessageLength() {
        connector.start(ctx());
        connector.onMessage(dmMessage("oc_long", "ou_s", "q"));

        // 5000-char response (no newlines) — must split into 2 segments of <=4000
        StringBuilder sb = new StringBuilder(5000);
        for (int i = 0; i < 500; i++) {
            sb.append("0123456789");
        }
        String longText = sb.toString();
        engine.lastFuture.complete(resultWithAssistant(longText));

        assertTrue(feishuClient.sendMessageCount > 1,
                "long assistant text must be segmented into multiple sendMessage calls");
        // each segment envelope is {"text":"..."}; recover and rejoin
        StringBuilder rejoined = new StringBuilder(longText.length());
        for (String envelope : feishuClient.sentContents) {
            rejoined.append(FeishuConnector.extractJsonTextField(envelope, "text"));
        }
        assertEquals(longText, rejoined.toString(),
                "segments must concatenate back to the original text without loss");
    }

    @Test
    void longTextWithNewlinesPrefersNewlineBoundary() {
        connector.start(ctx());
        connector.onMessage(dmMessage("oc_nl", "ou_s", "q"));

        // 3 lines each ~2000 chars: total > 4000 but each newline window allows
        // a clean cut at the first newline boundary rather than mid-line.
        String line = repeat('a', 2000);
        String longText = line + "\n" + line + "\n" + line;
        engine.lastFuture.complete(resultWithAssistant(longText));

        assertTrue(feishuClient.sendMessageCount >= 2, "must segment across newlines");
        StringBuilder rejoined = new StringBuilder(longText.length());
        for (String envelope : feishuClient.sentContents) {
            rejoined.append(FeishuConnector.extractJsonTextField(envelope, "text"));
        }
        assertEquals(longText, rejoined.toString(),
                "newline-boundary segmentation must still concatenate to the original");
    }

    @Test
    void rateLimitGuardSuppressesEngineExecuteWhenExceeded() {
        // lower the limit so the guard triggers after 2 messages (3000 is impractical)
        connector.rateLimitPerMinute = 2;
        connector.start(ctx());

        // within limit: both messages reach the engine
        connector.onMessage(dmMessage("oc_r1", "ou_s", "q1"));
        connector.onMessage(dmMessage("oc_r2", "ou_s", "q2"));
        assertEquals(2, engine.executeCount, "within limit: execute called for both");

        // over limit: 3rd message must NOT reach the engine; explicit reply instead
        connector.onMessage(dmMessage("oc_r3", "ou_s", "q3"));
        assertEquals(2, engine.executeCount, "over limit: 3rd message must NOT call execute");
        assertTrue(feishuClient.sendMessageCount > 0, "rate-limit reply must be sent");
        assertTrue(feishuClient.lastContent.contains("请求过于频繁"),
                "reply must mention rate limit: " + feishuClient.lastContent);
    }

    // ==================== W16-impl-ext：ChannelConfig options 面不引入 credentialId ====================

    @Test
    void channelConfigOptionsCredentialIdIsIgnored() {
        // 设计 §4.1 结论 7 负向语义钉死：options 面维持字面值语义，不读取 feishu.credentialId——
        // credentialId 唯一入口为配置键 nop.integration.feishu.credentialId（FeishuCredentials bean）
        ChannelConfig config = new ChannelConfig("test-agent");
        config.setOption("feishu.appId", "cli_options");
        config.setOption("feishu.appSecret", "sec_options");
        config.setOption("feishu.credentialId", "cred-must-be-ignored");
        connector.start(new ChannelConnectorContext(engine, new NoopPublisher(), config));

        assertEquals(1, feishuClient.startCount);
        assertNotNull(feishuClient.lastCredentials, "connector.start must pass credentials to the client");
        assertNull(feishuClient.lastCredentials.getCredentialId(),
                "options-face credentialId must be ignored (no credential-reference semantics on ChannelConfig)");
        assertEquals("cli_options", feishuClient.lastCredentials.getAppId(),
                "options-face literal appId/appSecret semantics unchanged");
    }

    // ==================== P2-CHANNEL：注入的 FeishuCredentials bean 可达且被使用（plan 2026-09-14-1937-2） ====================

    @Test
    void injectedCredentialsBeanUsedWhenNoOptionsCredentials() {
        // no credentials in ChannelConfig options → the injected
        // nopFeishuCredentials bean (nop.integration.feishu.* config) is used
        FeishuCredentials injected = new FeishuCredentials();
        injected.setAppId("cli_injected");
        injected.setAppSecret("sec_injected");
        injected.setCredentialId("cred-ref-1");
        connector.setFeishuCredentials(injected);

        ChannelConfig config = new ChannelConfig("test-agent");
        connector.start(new ChannelConnectorContext(engine, new NoopPublisher(), config));

        assertEquals(1, feishuClient.startCount);
        assertNotNull(feishuClient.lastCredentials, "connector.start must pass resolved credentials to the client");
        assertEquals("cli_injected", feishuClient.lastCredentials.getAppId(),
                "injected bean appId must reach FeishuClient.start");
        assertEquals("sec_injected", feishuClient.lastCredentials.getAppSecret(),
                "injected bean appSecret must reach FeishuClient.start");
        assertEquals("cred-ref-1", feishuClient.lastCredentials.getCredentialId(),
                "injected bean credentialId must pass through (credential-store reference "
                        + "resolved by FeishuClient.start)");
    }

    @Test
    void optionsCredentialsStillTakePriorityOverInjectedBean() {
        // ChannelConfig options face (literal pair) wins over the injected bean
        FeishuCredentials injected = new FeishuCredentials();
        injected.setAppId("cli_injected");
        injected.setAppSecret("sec_injected");
        connector.setFeishuCredentials(injected);

        ChannelConfig config = new ChannelConfig("test-agent");
        config.setOption("feishu.appId", "cli_options");
        config.setOption("feishu.appSecret", "sec_options");
        connector.start(new ChannelConnectorContext(engine, new NoopPublisher(), config));

        assertEquals("cli_options", feishuClient.lastCredentials.getAppId(),
                "options-face literal appId must take priority over the injected bean");
        assertEquals("sec_options", feishuClient.lastCredentials.getAppSecret(),
                "options-face literal appSecret must take priority over the injected bean");
    }

    @Test
    void emptyCredentialsFallbackWhenBothMissingLetsClientFailFast() {
        // no options credentials AND no injected bean → an empty credentials
        // object is passed so FeishuClient.start surfaces "appId not
        // configured" (fail-fast in the real client, never an NPE here)
        connector.start(new ChannelConnectorContext(engine, new NoopPublisher(), new ChannelConfig("test-agent")));

        assertEquals(1, feishuClient.startCount);
        assertNotNull(feishuClient.lastCredentials,
                "a credentials object must always reach FeishuClient.start");
        assertNull(feishuClient.lastCredentials.getAppId(),
                "absent credentials must not be fabricated — FeishuClient.start fail-fast semantics unchanged");
        assertNull(feishuClient.lastCredentials.getAppSecret(),
                "absent secret must not be fabricated");
    }

    @Test
    void botMentionParsedFromDocumentedPayloadShape() {
        connector.start(ctx());

        // positive: a mention carrying the bot's own open_id → processed
        String documented = "[{\"key\":\"@_user_1\",\"id\":{\"open_id\":\"ou_bot\","
                + "\"union_id\":\"on_x\",\"name\":\"Bot\"}}]";
        connector.onMessage(groupMessage("oc_gp", "ou_s", "hi", documented));
        assertEquals(1, engine.executeCount,
                "group message with a mention of the bot's own open_id must be processed");

        // negative: mentions array has content but NOT a bot mention →
        // treated as "未 @" → skipped
        connector.onMessage(groupMessage("oc_gn", "ou_s", "hi", "[\"bare_string\"]"));
        assertEquals(1, engine.executeCount,
                "non-mention mention shape must NOT be processed (treated as not-@)");
    }

    /**
     * M6-P1 (round-2 audit): a group message that @-mentions ANY OTHER
     * member (mentions array present, documented shape, but open_id differs
     * from the bot's) must NOT trigger the agent. Pre-fix, the substring
     * check ("key" + "open_id" present) passed for any mention element.
     */
    @Test
    void groupMentionOfOtherMemberDoesNotTrigger() {
        connector.start(ctx());

        // @another-member: same documented shape, but open_id is a person
        String otherMention = "[{\"key\":\"@_user_7\",\"id\":{\"open_id\":\"ou_human_1\","
                + "\"union_id\":\"on_h\",\"name\":\"Alice\"}},"
                + "{\"key\":\"@_user_8\",\"id\":{\"open_id\":\"ou_human_2\","
                + "\"union_id\":\"on_h2\",\"name\":\"Bob\"}}]";
        connector.onMessage(groupMessage("oc_other", "ou_s", "hi", otherMention));
        assertEquals(0, engine.executeCount,
                "group message @-mentioning other members must NOT trigger the agent");

        // even when a mention element carries a "key" marker plus some open_id
        connector.onMessage(groupMessage("oc_other2", "ou_s", "hi",
                "[{\"key\":\"@_user_9\",\"id\":{\"open_id\":\"ou_human_3\"}}]"));
        assertEquals(0, engine.executeCount,
                "documented-shaped mention of another member must NOT trigger");

        // control: a message that ALSO mentions the bot alongside others
        // still triggers exactly once.
        String mixed = "[{\"key\":\"@_user_1\",\"id\":{\"open_id\":\"ou_bot\"}},"
                + "{\"key\":\"@_user_2\",\"id\":{\"open_id\":\"ou_human_4\"}}]";
        connector.onMessage(groupMessage("oc_mixed", "ou_s", "hi", mixed));
        assertEquals(1, engine.executeCount,
                "mention set containing the bot's open_id must trigger");
    }

    /**
     * M6-P1 (round-2 audit): when the bot identity is NOT configured
     * (no {@code feishu.botOpenId}), the group filter is fail-closed — even
     * a documented-shaped @bot mention does NOT trigger (never guess whose
     * mention it is).
     */
    @Test
    void groupMentionWithoutConfiguredBotIdentityIsFailClosed() {
        ChannelConfig config = new ChannelConfig("test-agent");
        FeishuCredentials creds = new FeishuCredentials();
        creds.setAppId("cli_app");
        creds.setAppSecret("secret");
        config.setOption("feishu.credentials", creds);
        // NOTE: no feishu.botOpenId — the fail-closed path
        connector.start(new ChannelConnectorContext(engine, new NoopPublisher(), config));

        String documented = "[{\"key\":\"@_user_1\",\"id\":{\"open_id\":\"ou_bot\","
                + "\"union_id\":\"on_x\",\"name\":\"Bot\"}}]";
        connector.onMessage(groupMessage("oc_noid", "ou_s", "hi", documented));
        assertEquals(0, engine.executeCount,
                "group @bot mention must NOT trigger when the bot identity is unconfigured (fail-closed)");

        // single chats remain unaffected (non-group path never enters isBotMentioned)
        connector.onMessage(dmMessage("oc_noid_dm", "ou_s", "hello"));
        assertEquals(1, engine.executeCount,
                "DM must still trigger without a configured bot identity");
    }

    private static String repeat(char c, int n) {
        char[] a = new char[n];
        java.util.Arrays.fill(a, c);
        return new String(a);
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
        FeishuCredentials lastCredentials;
        final List<String> sentContents = new ArrayList<>();
        IMessageHandler registeredHandler;

        @Override
        public synchronized void start(FeishuCredentials credentials, IMessageHandler handler) {
            startCount++;
            this.lastCredentials = credentials;
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
        public synchronized void sendMessage(String receiveIdType, String receiveId, String msgType, String content) {
            sendMessageCount++;
            this.lastReceiveId = receiveId;
            this.lastContent = content;
            this.sentContents.add(content);
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
