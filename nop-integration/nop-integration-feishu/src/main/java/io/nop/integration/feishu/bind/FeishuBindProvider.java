package io.nop.integration.feishu.bind;

import io.nop.api.core.time.CoreMetrics;
import io.nop.integration.api.bind.BindTicket;
import io.nop.integration.api.bind.BindTicketStatus;
import io.nop.integration.api.bind.ChannelBindResult;
import io.nop.integration.api.bind.ChannelBindResultStatus;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.integration.api.bind.IChannelBindProvider;
import io.nop.integration.feishu.NopFeishuException;
import io.nop.integration.feishu.client.FeishuCredentials;
import io.nop.integration.feishu.client.IFeishuOAuthApi;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feishu scan-bind protocol implementation (W5-2). Builds a Feishu scan-login
 * QR payload (Option A — Feishu OAuth authorize URL, no bot push required) and
 * resolves the Feishu {@code open_id} from a scan callback.
 *
 * <p><b>QR payload selection (design §六 Open Question)</b>: Option A (Feishu
 * scan-login QR URL) chosen over Option B (self-built ticket + bot push):
 * <ul>
 *   <li>The QR encodes the Feishu OAuth authorize URL with
 *       {@code state=ticketId}; the platform renders it via
 *       {@code IQrcodeService}.</li>
 *   <li>Option A does NOT require a running {@code FeishuClient} Stream
 *       connection or bot push — the binding flow is fully decoupled from the
 *       message transport.</li>
 *   <li>The user scans with Feishu, authorises, and Feishu redirects to the
 *       platform callback carrying the OAuth {@code code}. The platform
 *       forwards it in {@link ChannelScanCallback#getRawPayload()} under the
 *       {@code code} key.</li>
 * </ul>
 *
 * <p><b>Identity trust (G12-13-03)</b>: the binding identity is resolved
 * <b>server-side</b> — the OAuth {@code code} is exchanged with the Feishu API
 * ({@code IFeishuOAuthApi}: code &rarr; {@code user_access_token} &rarr; user
 * info {@code open_id}) and the server-asserted {@code open_id} is the only
 * identity this provider ever puts on the {@link ChannelBindResult}. A
 * client-declared {@code open_id} field in {@code rawPayload} is
 * <b>deliberately ignored</b> (kept tolerated in the payload map — the
 * minimal-disruption option vs removing the field — but never read as
 * identity), because the callback endpoint is publicly reachable and the whole
 * payload is caller-controlled.
 *
 * <p><b>No silent no-op</b>: missing {@code code}, unknown ticket, expired
 * ticket, and a failed identity exchange all throw {@link NopFeishuException}
 * rather than returning a null/empty result. The ticket is consumed
 * (single-use) only AFTER a successful server-side identity resolution, so a
 * transient exchange failure can be retried on the same live ticket.
 *
 * <p><b>Layering</b>: depends only on {@code nop-integration-api} (the
 * {@code IChannelBindProvider} contract) + the Feishu client types from the
 * same module. Does NOT depend on any {@code nop-ai-*} module.
 */
public class FeishuBindProvider implements IChannelBindProvider {

    public static final String CHANNEL_TYPE = "feishu";

    private static final long DEFAULT_TICKET_TTL_MS = 5 * 60 * 1000L;
    private static final String DEFAULT_FEISHU_OPEN_HOST = "https://open.feishu.cn";

    private final ConcurrentHashMap<String, TicketEntry> tickets = new ConcurrentHashMap<>();

    private FeishuCredentials credentials;
    private long ticketTtlMs = DEFAULT_TICKET_TTL_MS;
    private String feishuOpenHost = DEFAULT_FEISHU_OPEN_HOST;

    /**
     * Server-side identity verification channel (G12-13-03). Lazily defaulted
     * to the JDK-backed implementation on first use (so {@code feishuOpenHost},
     * settable after construction, can be propagated as its base URL); tests
     * and alternative deployments inject a stub via {@link #setHttpApi}.
     */
    private IFeishuOAuthApi httpApi;

    public void setCredentials(FeishuCredentials credentials) {
        this.credentials = credentials;
    }

    public void setTicketTtlMs(long ticketTtlMs) {
        this.ticketTtlMs = ticketTtlMs;
    }

    public void setFeishuOpenHost(String feishuOpenHost) {
        this.feishuOpenHost = feishuOpenHost;
    }

    /**
     * Inject the OAuth verification channel (code &rarr; user_access_token
     * &rarr; open_id). Optional: defaults to the JDK implementation against
     * {@code feishuOpenHost}.
     */
    public void setHttpApi(IFeishuOAuthApi httpApi) {
        this.httpApi = httpApi;
    }

    @Override
    public String getChannelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public BindTicket createBindTicket(String channelType, String platformUserId) {
        if (!CHANNEL_TYPE.equals(channelType)) {
            throw new NopFeishuException(
                    "FeishuBindProvider.createBindTicket: channelType must be '" + CHANNEL_TYPE
                            + "' but was " + channelType);
        }
        if (platformUserId == null || platformUserId.isEmpty()) {
            throw new NopFeishuException(
                    "FeishuBindProvider.createBindTicket: platformUserId must not be null/empty");
        }
        purgeExpired();

        // P0 hardening (audit ai-rest D5): the ticket id is echoed in the public
        // scan callback (state=ticketId) and gates scan-login identity, so it
        // must be unguessable — a predictable sequence would let an attacker
        // race a live ticket minted by another user within its TTL window.
        String ticketId = "fs_bind_" + UUID.randomUUID().toString().replace("-", "");
        String appId = requireAppId();
        // Option A: Feishu scan-login OAuth authorize URL, state=ticketId
        String qrPayload = feishuOpenHost + "/open-apis/authen/v1/index?"
                + "app_id=" + appId
                + "&state=" + ticketId;

        long now = CoreMetrics.currentTimeMillis();
        long expiresAt = now + ticketTtlMs;
        tickets.put(ticketId, new TicketEntry(ticketId, platformUserId, qrPayload, expiresAt));

        BindTicket ticket = new BindTicket();
        ticket.setTicketId(ticketId);
        ticket.setQrPayload(qrPayload);
        ticket.setExpiresAt(new Timestamp(expiresAt));
        ticket.setStatus(BindTicketStatus.PENDING);
        return ticket;
    }

    @Override
    public ChannelBindResult onChannelScanCallback(ChannelScanCallback callback) {
        if (callback == null) {
            throw new NopFeishuException(
                    "FeishuBindProvider.onChannelScanCallback: callback must not be null");
        }
        purgeExpired();

        Map<String, Object> payload = callback.getRawPayload();
        if (payload == null || payload.isEmpty()) {
            throw new NopFeishuException(
                    "FeishuBindProvider.onChannelScanCallback: rawPayload must not be null/empty");
        }
        // G12-13-03: the callback must carry the OAuth authorization code. A
        // client-declared 'open_id' field is UNTRUSTED and deliberately ignored
        // here — the binding identity is resolved below by exchanging the code
        // with the Feishu API server-side.
        String code = stringField(payload, "code");
        if (code == null || code.isEmpty()) {
            throw new NopFeishuException(
                    "FeishuBindProvider.onChannelScanCallback: rawPayload missing required field 'code' "
                            + "(the OAuth authorization code; a client-declared 'open_id' is not accepted "
                            + "as identity)");
        }
        String ticketId = callback.getTicketId();
        if (ticketId == null || ticketId.isEmpty()) {
            throw new NopFeishuException(
                    "FeishuBindProvider.onChannelScanCallback: ticketId must not be null/empty");
        }
        TicketEntry entry = tickets.get(ticketId);
        if (entry == null) {
            throw new NopFeishuException(
                    "FeishuBindProvider.onChannelScanCallback: unknown or expired ticket: " + ticketId);
        }
        if (entry.isExpired()) {
            tickets.remove(ticketId);
            throw new NopFeishuException(
                    "FeishuBindProvider.onChannelScanCallback: ticket expired: " + ticketId);
        }

        // Server-side identity resolution (G12-13-03): code -> user_access_token
        // -> open_id. Runs BEFORE the ticket is consumed so a failed exchange
        // (network error, Feishu 5xx) can be retried on the same live ticket.
        String openId = resolveOpenIdServerSide(code);

        // consume the ticket (single-use binding flow)
        tickets.remove(ticketId);

        ChannelBindResult result = new ChannelBindResult();
        result.setExtId(openId);
        result.setPlatformUserId(entry.platformUserId);
        result.setTicketId(ticketId);
        result.setStatus(ChannelBindResultStatus.BINDING_COMPLETED);
        return result;
    }

    /**
     * Exchange the OAuth {@code code} with the Feishu API and return the
     * server-asserted {@code open_id}. This is the only identity source for
     * the binding result — caller-supplied identity fields are never used.
     */
    private String resolveOpenIdServerSide(String code) {
        String appId = requireAppId();
        String appSecret = requireAppSecret();
        String userAccessToken = requireHttpApi().exchangeUserAccessToken(appId, appSecret, code);
        return requireHttpApi().fetchOpenId(userAccessToken);
    }

    private IFeishuOAuthApi requireHttpApi() {
        if (httpApi == null) {
            httpApi = IFeishuOAuthApi.jdkDefault(feishuOpenHost);
        }
        return httpApi;
    }

    private String requireAppId() {
        if (credentials == null || credentials.getAppId() == null || credentials.getAppId().isEmpty()) {
            throw new NopFeishuException(
                    "FeishuBindProvider: FeishuCredentials.appId is not configured");
        }
        return credentials.getAppId();
    }

    private String requireAppSecret() {
        if (credentials == null || credentials.getAppSecret() == null || credentials.getAppSecret().isEmpty()) {
            throw new NopFeishuException(
                    "FeishuBindProvider: FeishuCredentials.appSecret is not configured");
        }
        return credentials.getAppSecret();
    }

    private static String stringField(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v == null ? null : v.toString();
    }

    private void purgeExpired() {
        long now = CoreMetrics.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue().isExpiredBefore(now));
    }

    private static final class TicketEntry {
        final String ticketId;
        final String platformUserId;
        final String qrPayload;
        final long expiresAt;

        TicketEntry(String ticketId, String platformUserId, String qrPayload, long expiresAt) {
            this.ticketId = ticketId;
            this.platformUserId = platformUserId;
            this.qrPayload = qrPayload;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return CoreMetrics.currentTimeMillis() >= expiresAt;
        }

        boolean isExpiredBefore(long now) {
            return now >= expiresAt;
        }
    }
}
