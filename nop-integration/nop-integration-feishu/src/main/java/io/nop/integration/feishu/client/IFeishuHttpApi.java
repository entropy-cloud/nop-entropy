package io.nop.integration.feishu.client;

/**
 * Abstraction over the Feishu/Lark Open API HTTP calls needed by
 * {@link FeishuClient}: obtaining the Stream gateway endpoint, acquiring a
 * {@code tenant_access_token}, and sending messages via {@code im/v1/messages}.
 * Extends the public {@link IFeishuOAuthApi} (G12-13-03: the OAuth code &rarr;
 * {@code open_id} server-side verification calls used by the scan-bind
 * provider).
 *
 * <p>The production implementation {@code JdkFeishuHttpApi} wraps the JDK
 * {@link java.net.http.HttpClient} (Java 11+ stdlib — no external dependency,
 * per the W5-0 SDK-selection decision). Tests supply a fake that records call
 * counts.
 *
 * <p>Package-private: internal seam, not a public API contract (only the
 * inherited {@link IFeishuOAuthApi} methods are public).
 */
interface IFeishuHttpApi extends IFeishuOAuthApi {

    /** Resolve the Stream WebSocket gateway URI and connection ticket. */
    StreamEndpoint getStreamEndpoint(String appId, String appSecret);

    /**
     * Acquire a {@code tenant_access_token} (POST
     * {@code /open-apis/auth/v3/tenant_access_token/internal}). The client
     * caches the result until near-expiry.
     */
    AccessTokenResult getTenantAccessToken(String appId, String appSecret);

    /**
     * Send a message via {@code im/v1/messages}.
     *
     * @param accessToken    a valid {@code tenant_access_token}
     * @param receiveIdType  {@code chat_id} / {@code open_id} / {@code user_id} / {@code union_id}
     * @param receiveId      the receive id (recipient address)
     * @param msgType        {@code text} / {@code post} / ...
     * @param content        message body JSON string
     * @return the Feishu HTTP status code (2xx = success)
     */
    int sendMessage(String accessToken, String receiveIdType, String receiveId,
                    String msgType, String content);
}
