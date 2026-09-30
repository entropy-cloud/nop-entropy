package io.nop.integration.feishu.bind;

import io.nop.integration.api.bind.BindTicket;
import io.nop.integration.api.bind.ChannelBindResult;
import io.nop.integration.api.bind.ChannelBindResultStatus;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.integration.feishu.NopFeishuException;
import io.nop.integration.feishu.client.FeishuCredentials;
import io.nop.integration.feishu.client.IFeishuOAuthApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G12-13-03 回归：扫码回调的绑定身份必须以<b>服务端</b>换取的 open_id 为准。
 *
 * <ul>
 *   <li>回调 payload 同时携带 OAuth code 与客户端自声明 open_id 且两者不一致时，
 *       绑定使用服务端 code 换取的 open_id（客户端自声明不被信任）；</li>
 *   <li>服务端换取失败必须响亮抛错（不回退到客户端声明值），且 ticket 不被消费
 *       （同一 ticket 可重试）。</li>
 * </ul>
 */
class TestFeishuBindProviderServerIdentity {

    private FeishuBindProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FeishuBindProvider();
        FeishuCredentials creds = new FeishuCredentials();
        creds.setAppId("cli_test_app");
        creds.setAppSecret("secret");
        provider.setCredentials(creds);
        provider.setTicketTtlMs(60_000L);
    }

    /** 服务端解析结果可预设的桩 OAuth 通道。 */
    static class ServerSideOAuthApi implements IFeishuOAuthApi {
        final String openId;
        NopFeishuException failure;
        int exchangeCalls;

        ServerSideOAuthApi(String openId) {
            this.openId = openId;
        }

        @Override
        public String exchangeUserAccessToken(String appId, String appSecret, String code) {
            exchangeCalls++;
            if (failure != null) {
                throw failure;
            }
            return "u-access-token";
        }

        @Override
        public String fetchOpenId(String userAccessToken) {
            return openId;
        }
    }

    @Test
    void serverResolvedOpenIdWinsOverClientDeclaredOpenId() {
        ServerSideOAuthApi serverApi = new ServerSideOAuthApi("ou_server_resolved");
        provider.setHttpApi(serverApi);
        BindTicket ticket = provider.createBindTicket("feishu", "user-1");

        ChannelScanCallback callback = new ChannelScanCallback();
        callback.setChannelType("feishu");
        callback.setTicketId(ticket.getTicketId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "auth-code-1");
        // 客户端自声明一个与服务端解析不一致的 open_id —— 必须被忽略
        payload.put("open_id", "ou_client_declared");
        callback.setRawPayload(payload);

        ChannelBindResult result = provider.onChannelScanCallback(callback);

        assertNotNull(result);
        assertEquals(ChannelBindResultStatus.BINDING_COMPLETED, result.getStatus());
        assertEquals("ou_server_resolved", result.getExtId(),
                "the server-resolved open_id (OAuth code exchange) must win over the "
                        + "client-declared open_id");
        assertEquals(1, serverApi.exchangeCalls,
                "the OAuth code must really be exchanged server-side");
    }

    @Test
    void exchangeFailureFailsLoudlyAndKeepsTicketAlive() {
        ServerSideOAuthApi failingApi = new ServerSideOAuthApi("ou_should_never_be_used");
        failingApi.failure = new NopFeishuException("Feishu API unreachable");
        provider.setHttpApi(failingApi);
        BindTicket ticket = provider.createBindTicket("feishu", "user-1");

        ChannelScanCallback callback = new ChannelScanCallback();
        callback.setChannelType("feishu");
        callback.setTicketId(ticket.getTicketId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "auth-code-1");
        payload.put("open_id", "ou_client_declared");
        callback.setRawPayload(payload);

        // 服务端换取失败：响亮抛错，绝不回退到客户端声明的 open_id
        NopFeishuException ex = assertThrows(NopFeishuException.class,
                () -> provider.onChannelScanCallback(callback));
        assertTrue(ex.getMessage().contains("Feishu API unreachable"),
                "the exchange failure must propagate loudly: " + ex.getMessage());

        // ticket 未被消费：同一 ticket 可重试（换取成功后才单次消费）
        ServerSideOAuthApi recoveredApi = new ServerSideOAuthApi("ou_server_recovered");
        provider.setHttpApi(recoveredApi);
        ChannelBindResult retry = provider.onChannelScanCallback(callback);
        assertEquals(ChannelBindResultStatus.BINDING_COMPLETED, retry.getStatus(),
                "the same ticket must be retryable after a failed exchange");
        assertEquals("ou_server_recovered", retry.getExtId());
    }
}
