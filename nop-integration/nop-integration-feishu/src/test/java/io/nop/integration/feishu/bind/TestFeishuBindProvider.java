package io.nop.integration.feishu.bind;

import io.nop.integration.api.bind.BindTicket;
import io.nop.integration.api.bind.BindTicketStatus;
import io.nop.integration.api.bind.ChannelBindResult;
import io.nop.integration.api.bind.ChannelBindResultStatus;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.integration.feishu.NopFeishuException;
import io.nop.integration.feishu.client.FeishuCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FeishuBindProvider}. Verifies the QR-payload selection
 * (Option A — Feishu OAuth URL), ticket lifecycle, scan-callback parsing, and
 * the "no silent no-op" contract (missing fields / unknown / expired tickets
 * all throw explicitly).
 */
class TestFeishuBindProvider {

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

    @Test
    void createBindTicketReturnsPendingTicketWithQrPayload() {
        BindTicket ticket = provider.createBindTicket("feishu", "user-1");

        assertNotNull(ticket.getTicketId(), "ticketId must not be null");
        assertEquals(BindTicketStatus.PENDING, ticket.getStatus(),
                "newly minted ticket must be PENDING");
        assertNotNull(ticket.getExpiresAt(), "expiresAt must not be null");
        String qr = ticket.getQrPayload();
        assertNotNull(qr, "qrPayload must not be null");
        assertFalse(qr.isEmpty(), "qrPayload must not be empty placeholder");
        // Option A: Feishu OAuth authorize URL carrying app_id + state=ticketId
        assertTrue(qr.contains("app_id=cli_test_app"),
                "qrPayload must carry the configured app_id");
        assertTrue(qr.contains("state=" + ticket.getTicketId()),
                "qrPayload must carry state=ticketId for callback correlation");
    }

    @Test
    void onCallbackReturnsCompletedWithOpenId() {
        BindTicket ticket = provider.createBindTicket("feishu", "user-1");

        ChannelScanCallback callback = new ChannelScanCallback();
        callback.setChannelType("feishu");
        callback.setTicketId(ticket.getTicketId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("open_id", "ou_scanned_user");
        callback.setRawPayload(payload);

        ChannelBindResult result = provider.onChannelScanCallback(callback);

        assertNotNull(result);
        assertEquals(ChannelBindResultStatus.BINDING_COMPLETED, result.getStatus());
        assertEquals("ou_scanned_user", result.getExtId(),
                "extId must be the open_id from the callback payload");
        assertEquals("user-1", result.getPlatformUserId(),
                "platformUserId must be rehydrated from the ticket");
        assertEquals(ticket.getTicketId(), result.getTicketId());
    }

    @Test
    void onCallbackWithUnknownTicketFailsExplicitly() {
        ChannelScanCallback callback = new ChannelScanCallback();
        callback.setChannelType("feishu");
        callback.setTicketId("fs_bind_unknown");
        Map<String, Object> payload = new HashMap<>();
        payload.put("open_id", "ou_x");
        callback.setRawPayload(payload);

        NopFeishuException ex = assertThrows(NopFeishuException.class,
                () -> provider.onChannelScanCallback(callback));
        assertTrue(ex.getMessage().contains("unknown or expired ticket"),
                "unknown ticket must fail explicitly, not silently: " + ex.getMessage());
    }

    @Test
    void onCallbackWithMissingPayloadFailsExplicitly() {
        BindTicket ticket = provider.createBindTicket("feishu", "user-1");

        // missing open_id (non-empty map so we reach the open_id check)
        ChannelScanCallback noOpenId = new ChannelScanCallback();
        noOpenId.setChannelType("feishu");
        noOpenId.setTicketId(ticket.getTicketId());
        Map<String, Object> noOpenIdPayload = new HashMap<>();
        noOpenIdPayload.put("some_other_field", "x");
        noOpenId.setRawPayload(noOpenIdPayload);
        NopFeishuException ex1 = assertThrows(NopFeishuException.class,
                () -> provider.onChannelScanCallback(noOpenId));
        assertTrue(ex1.getMessage().contains("open_id"),
                "missing open_id must fail explicitly: " + ex1.getMessage());

        // null rawPayload
        ChannelScanCallback nullPayload = new ChannelScanCallback();
        nullPayload.setChannelType("feishu");
        nullPayload.setTicketId(ticket.getTicketId());
        nullPayload.setRawPayload(null);
        NopFeishuException ex2 = assertThrows(NopFeishuException.class,
                () -> provider.onChannelScanCallback(nullPayload));
        assertTrue(ex2.getMessage().contains("rawPayload"),
                "null rawPayload must fail explicitly: " + ex2.getMessage());

        // missing ticketId
        ChannelScanCallback noTicket = new ChannelScanCallback();
        noTicket.setChannelType("feishu");
        noTicket.setTicketId(null);
        Map<String, Object> p = new HashMap<>();
        p.put("open_id", "ou_y");
        noTicket.setRawPayload(p);
        NopFeishuException ex3 = assertThrows(NopFeishuException.class,
                () -> provider.onChannelScanCallback(noTicket));
        assertTrue(ex3.getMessage().contains("ticketId"),
                "missing ticketId must fail explicitly: " + ex3.getMessage());
    }

    @Test
    void onCallbackWithExpiredTicketFailsExplicitly() {
        provider.setTicketTtlMs(1L); // 1ms TTL
        BindTicket ticket = provider.createBindTicket("feishu", "user-1");
        try {
            Thread.sleep(20L); // let it expire
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ChannelScanCallback callback = new ChannelScanCallback();
        callback.setChannelType("feishu");
        callback.setTicketId(ticket.getTicketId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("open_id", "ou_z");
        callback.setRawPayload(payload);

        NopFeishuException ex = assertThrows(NopFeishuException.class,
                () -> provider.onChannelScanCallback(callback));
        assertTrue(ex.getMessage().contains("unknown or expired") || ex.getMessage().contains("expired"),
                "expired ticket must fail explicitly: " + ex.getMessage());
    }

    @Test
    void createBindTicketRejectsWrongChannelType() {
        assertThrows(NopFeishuException.class,
                () -> provider.createBindTicket("dingtalk", "user-1"),
                "wrong channelType must be rejected");
        assertThrows(NopFeishuException.class,
                () -> provider.createBindTicket("feishu", null),
                "null platformUserId must be rejected");
    }

    @Test
    void ticketIdsAreUnpredictableRandomTokens() {
        // P0 hardening regression (audit ai-rest D5): the ticket id gates the
        // public scan-login identity, so it must not be a guessable sequence
        // (fs_bind_1, fs_bind_2, ...) — otherwise an attacker could race a live
        // ticket minted by another user within its TTL window.
        BindTicket t1 = provider.createBindTicket("feishu", "user-1");
        BindTicket t2 = provider.createBindTicket("feishu", "user-2");

        assertNotNull(t1.getTicketId());
        assertNotNull(t2.getTicketId());
        assertNotEquals(t1.getTicketId(), t2.getTicketId(),
                "consecutive tickets must not share an id");
        assertFalse(t1.getTicketId().matches("fs_bind_\\d+"),
                "ticketId must not be a guessable numeric sequence: " + t1.getTicketId());
        assertFalse(t2.getTicketId().matches("fs_bind_\\d+"),
                "ticketId must not be a guessable numeric sequence: " + t2.getTicketId());
    }

    @Test
    void consumedTicketCannotBeReused() {
        BindTicket ticket = provider.createBindTicket("feishu", "user-1");

        ChannelScanCallback callback = new ChannelScanCallback();
        callback.setChannelType("feishu");
        callback.setTicketId(ticket.getTicketId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("open_id", "ou_first");
        callback.setRawPayload(payload);

        // first use succeeds
        ChannelBindResult first = provider.onChannelScanCallback(callback);
        assertEquals(ChannelBindResultStatus.BINDING_COMPLETED, first.getStatus());

        // second use fails (single-use ticket consumed)
        assertThrows(NopFeishuException.class,
                () -> provider.onChannelScanCallback(callback),
                "consumed ticket must not be reusable");
    }
}
