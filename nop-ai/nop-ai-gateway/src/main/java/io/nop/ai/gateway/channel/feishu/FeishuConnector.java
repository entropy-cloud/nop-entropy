package io.nop.ai.gateway.channel.feishu;

import io.nop.api.core.time.CoreMetrics;
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
import java.util.ArrayList;
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
 * {@link FeishuClient}, {@link IChannelSessionStore} and the platform-standard
 * {@link FeishuCredentials} bean ({@code nopFeishuCredentials}) are injected
 * via IoC (the store is NOT part of {@link ChannelConnectorContext} per its
 * Javadoc). Credential precedence: ChannelConfig options face →
 * injected {@code nopFeishuCredentials} → empty-credentials fail-fast
 * (see {@link #resolveCredentials}).
 */
public class FeishuConnector implements IChannelConnector, IMessageHandler {

    static final Logger LOG = LoggerFactory.getLogger(FeishuConnector.class);

    public static final String CHANNEL_TYPE = "feishu";

    private static final String RECEIVE_ID_TYPE_CHAT = "chat_id";
    private static final String MSG_TYPE_TEXT = "text";
    private static final String CHAT_TYPE_GROUP = "group";
    private static final String CHAT_TYPE_P2P = "p2p";

    private static final int MAX_MESSAGE_LENGTH = 4000;

    /**
     * Default inbound rate limit (messages per rolling 60s window). Mirrors
     * {@link ChannelCapabilities#getRateLimitPerMinute()}. Kept as a mutable
     * package-private field so focused tests can lower it to trigger the
     * guard quickly (sending 3000+ real messages is impractical); production
     * leaves the default.
     */
    static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 3000;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;

    @Inject
    protected FeishuClient feishuClient;

    @Inject
    protected IChannelSessionStore sessionStore;

    /**
     * Platform-standard Feishu credentials bean ({@code nopFeishuCredentials},
     * from nop-integration-feishu's feishu-defaults.beans.xml, resolved via
     * {@code @InjectValue("@cfg:nop.integration.feishu.*|")}). Consumed by
     * {@link #resolveCredentials} as the fallback tier when the ChannelConfig
     * options face carries no credentials (plan 2026-09-14-1937-2 P2-CHANNEL).
     * Optional: a null value simply skips this tier.
     */
    @Inject
    protected FeishuCredentials feishuCredentials;

    private volatile ChannelConnectorContext context;
    private volatile boolean started = false;
    private ChannelCapabilities capabilities;

    /**
     * The bot's own Feishu {@code open_id}, resolved at {@link #start} from
     * the {@code feishu.botOpenId} ChannelConfig option. Used by
     * {@link #isBotMentioned} for exact {@code @bot} matching in group chat.
     * Null/empty → fail-closed: group messages never trigger the agent (a
     * group message mentioning some OTHER member must not be mistaken for a
     * bot mention), with a one-time warning logged at start.
     */
    private volatile String botOpenId;

    /**
     * Inbound rate-limit window: timestamps of accepted inbound messages
     * within the last {@link #RATE_LIMIT_WINDOW_MS}. Guarded by
     * {@link #rateLimitLock} so the evict+count+add sequence is atomic under
     * concurrent {@code onMessage} invocations.
     */
    private final List<Long> inboundWindow = new ArrayList<>();
    private final Object rateLimitLock = new Object();

    int rateLimitPerMinute = DEFAULT_RATE_LIMIT_PER_MINUTE;

    public void setFeishuClient(FeishuClient feishuClient) {
        this.feishuClient = feishuClient;
    }

    public void setSessionStore(IChannelSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public void setFeishuCredentials(FeishuCredentials feishuCredentials) {
        this.feishuCredentials = feishuCredentials;
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
            capabilities.setRateLimitPerMinute(rateLimitPerMinute);
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
        // M6-P1 (round-2 audit): resolve the bot's own identity once at start.
        // Group @bot detection is exact-match against this open_id; when it is
        // not configured the filter is fail-closed (see isBotMentioned).
        this.botOpenId = resolveBotOpenId(context.getConfig());
        if (botOpenId == null || botOpenId.isEmpty()) {
            LOG.warn("feishu-connector: no bot open_id configured (ChannelConfig option "
                    + "'feishu.botOpenId'); group @bot detection is fail-closed — group "
                    + "messages will NOT trigger the agent");
        }
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
        deliverSegmented(channelAddress, reply);
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

        // inbound rate-limit guard (W6-1): a rolling 60s window caps the
        // inbound messages forwarded to IAgentEngine. Over the limit the
        // connector replies explicitly ("请求过于频繁") and does NOT call
        // engine.execute — never a silent drop (Minimum Rules #24).
        if (!tryAcquireInbound()) {
            LOG.warn("feishu-connector inbound rate limit exceeded for chat {}; replying and not forwarding", chatId);
            deliverSegmented(chatId, "请求过于频繁，请稍后再试");
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
        deliverSegmented(chatId, text);
    }

    /**
     * Send {@code text} to {@code chatId}, splitting it into ordered segments
     * when its length exceeds {@link #MAX_MESSAGE_LENGTH} (W6-1 §7.2.1).
     * Splitting prefers newline boundaries (so semantic units are not cut),
     * falls back to a hard cut when no newline falls inside a window, and
     * never loses characters — the concatenation of all segments equals the
     * original text. Each segment is sent as its own
     * {@code feishuClient.sendMessage} call. A send failure is logged and the
     * remaining segments are still attempted (no silent swallow).
     */
    void deliverSegmented(String chatId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<String> segments = splitIntoSegments(text, MAX_MESSAGE_LENGTH);
        for (String seg : segments) {
            try {
                feishuClient.sendMessage(RECEIVE_ID_TYPE_CHAT, chatId, MSG_TYPE_TEXT, toJsonText(seg));
            } catch (Exception e) {
                // explicit log, then continue with the remaining segments
                LOG.error("feishu-connector failed to send a segment to chat {}", chatId, e);
            }
        }
    }

    /**
     * Rolling-window inbound admission (W6-1 §7.2.2). Atomically evicts
     * timestamps older than the 60s window, then admits the current message
     * only if the window is below {@link #rateLimitPerMinute}. The accepted
     * message's timestamp is recorded; a rejected message is NOT recorded
     * (so the window drains naturally once load subsides).
     *
     * @return {@code true} if admitted (caller proceeds to engine.execute);
     *         {@code false} if over the limit (caller replies and returns)
     */
    private boolean tryAcquireInbound() {
        long now = CoreMetrics.currentTimeMillis();
        long cutoff = now - RATE_LIMIT_WINDOW_MS;
        synchronized (rateLimitLock) {
            inboundWindow.removeIf(ts -> ts < cutoff);
            if (inboundWindow.size() >= rateLimitPerMinute) {
                return false;
            }
            inboundWindow.add(now);
            return true;
        }
    }

    static List<String> splitIntoSegments(String text, int maxLen) {
        if (text.length() <= maxLen) {
            List<String> single = new ArrayList<>(1);
            single.add(text);
            return single;
        }
        List<String> out = new ArrayList<>();
        int start = 0;
        int n = text.length();
        while (start < n) {
            int end = Math.min(start + maxLen, n);
            if (end < n) {
                // prefer to cut just past the last newline inside the window
                // (keeps a line intact within one segment)
                int nl = text.lastIndexOf('\n', end - 1);
                if (nl > start) {
                    end = nl + 1;
                }
            }
            out.add(text.substring(start, end));
            start = end;
        }
        return out;
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
     * Package-private so focused tests can recover the segment text from the
     * {@code {"text":"..."}} envelope that {@link #toJsonText} produces.
     */
    static String extractJsonTextField(String json, String key) {
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
     * Group {@code @bot} detection (W6-1 §7.2.3, fork (c)). Parses the Feishu
     * {@code im.message.receive_v1} event {@code mentions} array and checks
     * whether the bot's own {@code open_id} appears as a mention's
     * {@code id.open_id}. Absent / empty array, elements lacking an
     * {@code open_id}, and elements mentioning a DIFFERENT member are all
     * treated as "not @bot" (correct semantics: a group message that does not
     * mention the bot is not replied to).
     *
     * <p>M6-P1 (round-2 audit): the pre-fix implementation accepted ANY
     * documented-shaped mention element (both {@code "key"} and
     * {@code "open_id"} substrings present), so a group message @-mentioning
     * another member still triggered the agent. The bot identity is resolved
     * at {@link #start} from the {@code feishu.botOpenId} ChannelConfig option;
     * when no identity is configured the check is fail-closed (returns false —
     * never trigger on an unverifiable mention) instead of guessing.
     */
    private boolean isBotMentioned(FeishuInboundMessage message) {
        String botId = botOpenId;
        if (botId == null || botId.isEmpty()) {
            // fail-closed: without the bot identity no group message can be
            // proven to mention THIS bot (the one-time warning was logged at
            // start()). Never guess — a wrong guess replies to @other-user
            // messages.
            return false;
        }
        byte[] raw = message.getRawPayload();
        if (raw == null || raw.length == 0) {
            return false;
        }
        String json = new String(raw, StandardCharsets.UTF_8);
        int idx = json.indexOf("\"mentions\"");
        if (idx < 0) {
            return false;
        }
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
        if (inside.isEmpty()) {
            return false;
        }
        // split the array into individual mention elements and require an
        // exact open_id match against the bot's own identity. A mention of
        // any OTHER member (different open_id) must not trigger.
        String[] elements = inside.split("\\},\\{");
        for (String element : elements) {
            String openId = extractJsonTextField(element, "open_id");
            if (openId != null && openId.equals(botId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * M6-P1 (round-2 audit): resolve the bot's own open_id from the
     * {@code feishu.botOpenId} ChannelConfig option (set alongside
     * {@code feishu.appId}/{@code feishu.appSecret}). Returns null when not
     * configured — the caller then fail-closes.
     */
    private static String resolveBotOpenId(ChannelConfig config) {
        Object value = config != null ? config.getOption("feishu.botOpenId") : null;
        return value != null ? value.toString() : null;
    }

    /**
     * Resolve the Feishu credentials for {@link FeishuClient#start}, tiered
     * (plan 2026-09-14-1937-2 P2-CHANNEL, precedence documented in
     * {@code ai-gateway-defaults.beans.xml}):
     * <ol>
     *   <li>ChannelConfig options face — literal {@code feishu.appId} +
     *       {@code feishu.appSecret} pair (unchanged semantics; the options
     *       face intentionally has NO credentialId reference semantics);</li>
     *   <li>ChannelConfig options face — {@code feishu.credentials} object
     *       (existing behavior preserved);</li>
     *   <li>the injected {@code nopFeishuCredentials} bean (platform standard
     *       {@code nop.integration.feishu.*} config, incl. the
     *       {@code nop.integration.feishu.credentialId} credential-store
     *       reference resolved by {@code FeishuClient.start});</li>
     *   <li>an empty credentials object as last resort — lets
     *       {@code FeishuClient.start} surface a clear "appId not configured"
     *       error rather than an NPE here (fail-fast, never silent).</li>
     * </ol>
     */
    private FeishuCredentials resolveCredentials(ChannelConfig config) {
        Object appId = config != null ? config.getOption("feishu.appId") : null;
        Object appSecret = config != null ? config.getOption("feishu.appSecret") : null;
        if (appId != null && appSecret != null) {
            FeishuCredentials c = new FeishuCredentials();
            c.setAppId(appId.toString());
            c.setAppSecret(appSecret.toString());
            return c;
        }
        Object creds = config != null ? config.getOption("feishu.credentials") : null;
        if (creds instanceof FeishuCredentials) {
            return (FeishuCredentials) creds;
        }
        // Tier 3: the platform-standard FeishuCredentials bean injected from
        // nop-integration-feishu (nop.integration.feishu.* config). A null
        // injection (unit tests / manual construction) skips this tier.
        if (feishuCredentials != null) {
            return feishuCredentials;
        }
        // last-resort: an empty credentials object lets FeishuClient.start
        // surface a clear "appId not configured" error rather than an NPE here
        return new FeishuCredentials();
    }
}
