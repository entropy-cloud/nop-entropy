package io.nop.ai.gateway.channel;

/**
 * Transport-layer abstraction for an external messaging channel (Feishu,
 * DingTalk, WeCom, Webhook, ...). A connector implementation bridges one
 * specific channel protocol to the agent engine: it forwards inbound user
 * messages to {@link io.nop.ai.agent.engine.IAgentEngine#sendMessage} and
 * delivers agent responses by subscribing to
 * {@link io.nop.ai.agent.engine.IAgentEventPublisher}.
 *
 * <p><b>Engine zero-change</b>: adding a new channel requires only
 * implementing this interface and registering the connector in Nop IoC; the
 * agent engine code is untouched.
 *
 * <p>See {@code ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md}
 * §5–§8 for the authoritative design. The {@code sendOutbound} method
 * adjudicates the gap between the transport design (§5) and the business
 * design (§3.2 of {@code nop-ai-channel-integration-design.md}).
 */
public interface IChannelConnector {

    /**
     * Channel type identifier (e.g. {@code "feishu"}, {@code "dingtalk"},
     * {@code "wecom"}, {@code "webhook"}, {@code "api"}). Used by
     * {@code ChannelConnectorManager} for registration and lookup.
     */
    String getChannelType();

    /**
     * Start the channel connection (establish webhook listener / long
     * connection / polling). The connector should retain {@code context} and
     * use it to drive the agent engine for the lifetime of the connection.
     */
    void start(ChannelConnectorContext context);

    /**
     * Gracefully stop the channel connection and release resources.
     */
    void stop();

    /**
     * Declare this channel's capabilities (Markdown support, file upload,
     * streaming, rate limits, etc.), used by message formatting and
     * permission decisions.
     */
    ChannelCapabilities getCapabilities();

    /**
     * Business-driven outbound send (design §3.2): push a non-Agent
     * notification to a known channel address. This is distinct from the
     * Agent session response path, which is delivered via
     * {@link io.nop.ai.agent.engine.IAgentEventPublisher} subscription
     * (design §7.2): {@code sendOutbound} carries proactive notifications
     * that do not trigger agent reasoning, while agent replies flow back
     * through the event subscription the connector established in
     * {@link #start}.
     *
     * <p>{@code channelAddress} is the channel-native address of the
     * recipient (e.g. a Feishu {@code chat_id}); the connector translates
     * {@code outboundMessage} into the channel's native send API.
     *
     * @param channelAddress  channel-native recipient address; never null
     * @param outboundMessage transport-layer outbound carrier; never null
     */
    void sendOutbound(String channelAddress, ChannelOutboundMessage outboundMessage);
}
