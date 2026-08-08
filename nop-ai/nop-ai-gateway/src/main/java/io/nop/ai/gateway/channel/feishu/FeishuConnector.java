package io.nop.ai.gateway.channel.feishu;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.gateway.channel.ChannelCapabilities;
import io.nop.ai.gateway.channel.ChannelConfig;
import io.nop.ai.gateway.channel.ChannelConnectorContext;
import io.nop.ai.gateway.channel.ChannelOutboundMessage;
import io.nop.ai.gateway.channel.ChannelSession;
import io.nop.ai.gateway.channel.IChannelConnector;
import io.nop.ai.gateway.channel.IChannelSessionStore;
import io.nop.integration.feishu.NopFeishuException;
import io.nop.integration.feishu.client.FeishuClient;
import io.nop.integration.feishu.client.FeishuCredentials;
import io.nop.integration.feishu.client.FeishuInboundMessage;
import io.nop.integration.feishu.client.IMessageHandler;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Feishu channel connector (W5-3). Bridges the Feishu Stream long-connection
 * (via {@link FeishuClient}) to the agent engine.
 *
 * <p><b>Inbound path</b> (user → agent): {@link FeishuClient} delivers a parsed
 * {@link FeishuInboundMessage} to the {@link IMessageHandler} registered in
 * {@link #start}. The connector extracts the chat id / sender / text / chat
 * type, applies the group-{@code @bot} filter, looks up (or creates) the
 * channel↔session mapping via {@link IChannelSessionStore}, and calls
 * {@link io.nop.ai.agent.engine.IAgentEngine#execute} (Phase 0 裁定 path —
 * NOT {@code sendMessage}, which is fire-and-forget and cannot return the
 * response text).
 *
 * <p><b>Outbound path</b> (agent → user): the {@code execute()} future
 * callback extracts the last assistant message text from
 * {@link AgentExecutionResult#getMessages()} and replies via
 * {@link FeishuClient#sendMessage}. The {@code chatId} is captured directly
 * in the future lambda closure — no in-memory reverse mapping needed. On
 * failure (exceptionally completed future or {@code status == failed}) an
 * error notice is sent.
 *
 * <p><b>Single-JVM constraint</b>: {@code execute()} returns a
 * {@link CompletableFuture} that completes in the same JVM. Multi-instance
 * deployment requires cross-process future passing (deferred).
 *
 * <p><b>Group {@code @bot} filter</b>: group messages that do not mention the
 * bot are ignored — this is correct semantics (do not reply to unrelated
 * messages), not a silent skip of work that should be handled.
 *
 * <p><b>Attachment degradation</b>: {@code supportsFileUpload=false} (v1
 * deferred); {@link #sendOutbound} degrades attachments to a text link /
 * notice rather than silently dropping them.
 *
 * <p><b>NopIoC injection</b>: {@code @Inject} fields are {@code protected}
 * (NopIoC does not support injecting into {@code private} fields).
 * {@link FeishuClient} and {@link IChannelSessionStore} are injected via IoC
 * (the store is NOT part of {@link ChannelConnectorContext} per its Javadoc).
 */
public class FeishuConnector implements IChannelConnector, IMessageHandler {

    static final Logger LOG = LoggerFactory.getLogger(FeishuConnector.class);

    public static final String CHANNEL_TYPE = "feishu";

    private static final String RECEIVE_ID_TYPE_CHAT = "chat_id";
    private static final String MSG_TYPE_TEXT = "text";
    private static final String CHAT_TYPE_GROUP = "group";
    private static final String CHAT_TYPE_P2P = "p2p";

    private static final int MAX_MESSAGE_LENGTH = 4000;

    @Inject
    protected FeishuClient feishuClient;

    @Inject
    protected IChannelSessionStore sessionStore;

    private volatile ChannelConnectorContext context;
    private volatile boolean started = false;
    private ChannelCapabilities capabilities;

    public void setFeishuClient(FeishuClient feishuClient) {
        this.feishuClient = feishuClient;
    }

    public void setSessionStore(IChannelSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public String getChannelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public ChannelCapabilities getCapabilities() {
        if (capabilities == null) {
            capabilities = new ChannelCapabilities();
            capabilities.setSupportsMarkdown(true);
            capabilities.setSupportsFileUpload(false);
            capabilities.setSupportsStreaming(false);
            capabilities.setSupportsGroupChat(true);
            capabilities.setSupportsMentions(true);
            capabilities.setMaxMessageLength(MAX_MESSAGE_LENGTH);
            capabilities.setRateLimitPerMinute(3000);
        }
        return capabilities;
    }

    @Override
    public synchronized void start(ChannelConnectorContext context) {
        if (started) {
            LOG.debug("feishu-connector already started, ignoring duplicate start");
            return;
        }
        if (context == null) {
            throw new NopFeishuException("FeishuConnector.start: context must not be null");
        }
        if (feishuClient == null) {
            throw new NopFeishuException("FeishuConnector.start: feishuClient must be injected");
        }
        if (sessionStore == null) {
            throw new NopFeishuException("FeishuConnector.start: sessionStore must be injected");
        }
        this.context = context;
        FeishuCredentials credentials = resolveCredentials(context.getConfig());
        // register this connector as the inbound message handler; FeishuClient
        // delivers parsed FeishuInboundMessage via onMessage / errors via onError
        feishuClient.start(credentials, this);
        this.started = true;
        LOG.info("feishu-connector started, agentName={}", context.getConfig().getAgentName());
    }

    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        try {
            feishuClient.stop();
        } catch (Exception e) {
            LOG.debug("feishu-connector stop ignored feishuClient error", e);
        }
        LOG.info("feishu-connector stopped");
    }

    @Override
    public void sendOutbound(String channelAddress, ChannelOutboundMessage outboundMessage) {
        if (!started) {
            throw new NopFeishuException(
                    "FeishuConnector.sendOutbound: connector not started");
        }
        if (channelAddress == null || channelAddress.isEmpty()) {
            throw new NopFeishuException(
                    "FeishuConnector.sendOutbound: channelAddress must not be null/empty");
        }
        if (outboundMessage == null) {
            throw new NopFeishuException(
                    "FeishuConnector.sendOutbound: outboundMessage must not be null");
        }
        String text = outboundMessage.getText();
        if (text == null || text.isEmpty()) {
            text = outboundMessage.getMarkdown();
        }
        // attachment degradation: supportsFileUpload=false → degrade to link/notice
        List<ChannelOutboundMessage.Attachment> attachments = outboundMessage.getAttachments();
        StringBuilder sb = new StringBuilder();
        if (text != null && !text.isEmpty()) {
            sb.append(text);
        }
        if (attachments != null && !attachments.isEmpty()) {
            for (ChannelOutboundMessage.Attachment att : attachments) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                if (att.getUrl() != null && !att.getUrl().isEmpty()) {
                    sb.append("[附件] ").append(att.getName() != null ? att.getName() : "file")
                            .append(": ").append(att.getUrl());
                } else {
                    sb.append("[附件不支持] ").append(att.getName() != null ? att.getName() : "file")
                            .append(" (此信道暂不支持文件上传)");
                }
            }
        }
        String reply = sb.toString();
        if (reply.isEmpty()) {
            reply = "[空消息]";
        }
        feishuClient.sendMessage(RECEIVE_ID_TYPE_CHAT, channelAddress, MSG_TYPE_TEXT,
                toJsonText(reply));
    }

    // ---- IMessageHandler (inbound path) ----------------------------------

    @Override
    public void onMessage(FeishuInboundMessage message) {
        if (!started) {
            LOG.debug("feishu-connector not started, ignoring inbound message");
            return;
        }
        if (message == null) {
            LOG.warn("feishu-connector received null inbound message");
            return;
        }
        String chatId = message.getReceiveId();
        if (chatId == null || chatId.isEmpty()) {
            chatId = message.getSenderId();
        }
        String chatType = message.getChatType();
        boolean isGroup = CHAT_TYPE_GROUP.equals(chatType);

        // group @bot filter: group messages that do not mention the bot are ignored.
        // This is correct semantics (do not reply to unrelated messages), not a
        // silent skip of work that should be handled.
        if (isGroup && !isBotMentioned(message)) {
            LOG.debug("feishu-connector ignoring group message without @bot in chat {}", chatId);
            return;
        }

        String text = extractUserText(message);
        if (text == null || text.isEmpty()) {
            LOG.debug("feishu-connector ignoring message with no extractable text in chat {}", chatId);
            return;
        }

        String senderId = message.getSenderId();
        ChannelKind channelKind = isGroup ? ChannelKind.GROUP : ChannelKind.DM;

        // session lookup: hit → reuse sessionId; miss → null (engine creates new)
        ChannelSession session = null;
        try {
            session = sessionStore.findByChannel(CHANNEL_TYPE, chatId);
        } catch (Exception e) {
            LOG.warn("feishu-connector sessionStore.findByChannel failed for chat {}", chatId, e);
        }
        String existingSessionId = session != null ? session.getSessionId() : null;
        boolean isNewSession = existingSessionId == null;
        if (!isNewSession) {
            try {
                sessionStore.updateLastActive(CHANNEL_TYPE, chatId);
            } catch (Exception e) {
                LOG.debug("feishu-connector updateLastActive failed for chat {}", chatId, e);
            }
        }

        String agentName = context.getConfig().getAgentName();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("channelType", CHANNEL_TYPE);
        metadata.put("channelId", chatId);
        metadata.put("senderId", senderId);

        AgentMessageRequest request = new AgentMessageRequest(
                agentName, text, existingSessionId, metadata, channelKind, null);

        // Phase 0 裁定: execute() + future callback (not event subscription).
        // chatId is captured directly in the lambda closure — no reverse mapping.
        final String replyTarget = chatId;
        CompletableFuture<AgentExecutionResult> future;
        try {
            future = context.getAgentEngine().execute(request);
        } catch (Exception e) {
            LOG.error("feishu-connector agentEngine.execute threw synchronously for chat {}", replyTarget, e);
            sendReply(replyTarget, "执行出错: " + errorMessage(e));
            return;
        }
        if (future == null) {
            sendReply(replyTarget, "执行出错: 引擎返回 null future");
            return;
        }
        future.whenComplete((result, error) -> onExecutionComplete(result, error, replyTarget, isNewSession, agentName));
    }

    @Override
    public void onError(Throwable error) {
        LOG.warn("feishu-connector received FeishuClient connection/decode error", error);
    }

    // ---- future callback (outbound path) ---------------------------------

    void onExecutionComplete(AgentExecutionResult result, Throwable error,
                             String chatId, boolean isNewSession, String agentName) {
        try {
            if (error != null) {
                LOG.warn("feishu-connector execute future completed exceptionally for chat {}", chatId, error);
                sendReply(chatId, "执行出错: " + error.getMessage());
                return;
            }
            if (result == null) {
                sendReply(chatId, "执行出错: 引擎返回 null 结果");
                return;
            }
            if (result.getStatus() == AgentExecStatus.failed) {
                String errMsg = result.getError() != null ? result.getError() : "unknown error";
                sendReply(chatId, "执行出错: " + errMsg);
                return;
            }
            String reply = extractLastAssistantText(result);
            if (reply != null && !reply.isEmpty()) {
                sendReply(chatId, reply);
            }
            // save mapping for a newly created session (sessionId from result)
            if (isNewSession && result.getSessionId() != null) {
                try {
                    sessionStore.saveMapping(CHANNEL_TYPE, chatId, result.getSessionId(), agentName);
                } catch (Exception e) {
                    LOG.warn("feishu-connector saveMapping failed for chat {}", chatId, e);
                }
            }
        } catch (Throwable t) {
            // never let the callback throw to the executor thread silently
            LOG.error("feishu-connector onExecutionComplete unexpected error for chat {}", chatId, t);
        }
    }

    // ---- helpers ---------------------------------------------------------

    private static String errorMessage(Throwable e) {
        String m = e.getMessage();
        return m != null ? m : e.getClass().getSimpleName();
    }

    private void sendReply(String chatId, String text) {
        try {
            feishuClient.sendMessage(RECEIVE_ID_TYPE_CHAT, chatId, MSG_TYPE_TEXT, toJsonText(text));
        } catch (Exception e) {
            LOG.error("feishu-connector failed to send reply to chat {}", chatId, e);
        }
    }

    private static String toJsonText(String text) {
        // Feishu text message content is JSON: {"text":"..."} — escape quotes/backslashes
        if (text == null) {
            text = "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 12);
        sb.append("{\"text\":\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("\"}");
        return sb.toString();
    }

    /**
     * Extract the user-facing text from a Feishu inbound message. Text-message
     * content is JSON like {@code {"text":"hello"}}; other message types
     * ({@code post}, {@code image}, ...) are not supported in v1 and return
     * null (the caller skips them).
     */
    static String extractUserText(FeishuInboundMessage message) {
        if (!MSG_TYPE_TEXT.equals(message.getMsgType())) {
            return null;
        }
        String content = message.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        // content is {"text":"..."} — extract the text field
        return extractJsonTextField(content, "text");
    }

    /**
     * Minimal JSON "text" field extractor for the Feishu content envelope
     * (avoids a runtime JSON provider dependency, mirrors FeishuJsons).
     */
    private static String extractJsonTextField(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
            j++;
        }
        if (j >= json.length() || json.charAt(j) != '"') {
            return null;
        }
        j++;
        StringBuilder sb = new StringBuilder();
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                char n = json.charAt(j + 1);
                switch (n) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    default:
                        sb.append(n);
                }
                j += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                j++;
            }
        }
        return sb.toString();
    }

    /**
     * Extract the last assistant message text from an execution result.
     */
    static String extractLastAssistantText(AgentExecutionResult result) {
        if (result == null) {
            return null;
        }
        List<ChatMessage> messages = result.getMessages();
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if (msg instanceof ChatAssistantMessage || "assistant".equals(msg.getRole())) {
                    String content = msg.getContent();
                    if (content != null && !content.isEmpty()) {
                        return content;
                    }
                }
            }
        }
        // finalMessage is passed null from fromContext, but check defensively
        return result.getFinalMessage();
    }

    /**
     * Group {@code @bot} detection: check the raw payload for a non-empty
     * {@code mentions} array. Real payload-shape calibration is W6 E2E.
     */
    private static boolean isBotMentioned(FeishuInboundMessage message) {
        byte[] raw = message.getRawPayload();
        if (raw == null || raw.length == 0) {
            return false;
        }
        String json = new String(raw, StandardCharsets.UTF_8);
        // Feishu event payload carries an "mentions" array; absence or empty → not mentioned
        int idx = json.indexOf("\"mentions\"");
        if (idx < 0) {
            return false;
        }
        // check whether the array is non-empty (contains at least one element object)
        int colon = json.indexOf(':', idx + 9);
        if (colon < 0) {
            return false;
        }
        int arrOpen = json.indexOf('[', colon);
        if (arrOpen < 0) {
            return false;
        }
        int arrClose = json.indexOf(']', arrOpen);
        if (arrClose < 0) {
            return false;
        }
        String inside = json.substring(arrOpen + 1, arrClose).trim();
        return !inside.isEmpty();
    }

    private FeishuCredentials resolveCredentials(ChannelConfig config) {
        // ChannelConfig may carry credentials in options; fall back to a plain
        // appId/appSecret pair. The injected FeishuCredentials bean (with
        // @InjectValue config resolution) is the primary source in production.
        Object appId = config != null ? config.getOption("feishu.appId") : null;
        Object appSecret = config != null ? config.getOption("feishu.appSecret") : null;
        if (appId != null && appSecret != null) {
            FeishuCredentials c = new FeishuCredentials();
            c.setAppId(appId.toString());
            c.setAppSecret(appSecret.toString());
            return c;
        }
        // rely on a FeishuCredentials bean resolved from nop.config — set
        // after construction by IoC; for unit tests the connector test injects
        // a FeishuClient that is already configured.
        Object creds = config != null ? config.getOption("feishu.credentials") : null;
        if (creds instanceof FeishuCredentials) {
            return (FeishuCredentials) creds;
        }
        // last-resort: an empty credentials object lets FeishuClient.start
        // surface a clear "appId not configured" error rather than an NPE here
        return new FeishuCredentials();
    }
}
