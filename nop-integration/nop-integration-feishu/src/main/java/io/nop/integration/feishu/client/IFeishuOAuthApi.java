package io.nop.integration.feishu.client;

/**
 * Server-side Feishu OAuth identity verification contract (G12-13-03): the
 * two Open API calls needed to resolve a scan-login user's identity from the
 * OAuth {@code authorization_code} delivered by the scan callback.
 *
 * <p>Declared separately from the package-private {@link IFeishuHttpApi}
 * (which extends it) because the consumer lives in a sibling package
 * ({@code io.nop.integration.feishu.bind.FeishuBindProvider}); this keeps the
 * new public surface limited to exactly the two OAuth methods while the
 * Stream/message internals stay package-private.
 *
 * <p>Official Feishu v1 endpoints (matching the {@code /authen/v1/index}
 * authorize URL built by {@code FeishuBindProvider}):
 * <ul>
 *   <li>{@code POST /open-apis/authen/v1/accessToken} — exchange the
 *       authorization {@code code} for a {@code user_access_token};</li>
 *   <li>{@code GET /open-apis/authen/v1/user_info} — read the Feishu user
 *       identity (notably {@code open_id}) with that token.</li>
 * </ul>
 *
 * <p>Implementations throw {@code NopFeishuException} on transport failures
 * and on responses missing the expected field (fail-loud, no silent nulls).
 */
public interface IFeishuOAuthApi {

    /**
     * Exchange an OAuth authorization {@code code} for a
     * {@code user_access_token} (POST
     * {@code /open-apis/authen/v1/accessToken}, grant type
     * {@code authorization_code}).
     *
     * @return the {@code user_access_token}
     * @throws io.nop.integration.feishu.NopFeishuException on transport failure
     *                                                      or a response without {@code access_token}
     */
    String exchangeUserAccessToken(String appId, String appSecret, String code);

    /**
     * Fetch the Feishu user info for a {@code user_access_token} (GET
     * {@code /open-apis/authen/v1/user_info}) and return its {@code open_id}.
     *
     * @return the server-asserted {@code open_id}
     * @throws io.nop.integration.feishu.NopFeishuException on transport failure
     *                                                      or a response without {@code open_id}
     */
    String fetchOpenId(String userAccessToken);

    /**
     * Production default backed by the JDK {@code HttpClient}. Static factory
     * because the implementation class is deliberately package-private.
     *
     * @param baseUrl Feishu Open API base host (e.g. {@code https://open.feishu.cn});
     *                {@code null} keeps the implementation default
     */
    static IFeishuOAuthApi jdkDefault(String baseUrl) {
        JdkFeishuHttpApi api = new JdkFeishuHttpApi();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            api.setBaseUrl(baseUrl);
        }
        return api;
    }
}
