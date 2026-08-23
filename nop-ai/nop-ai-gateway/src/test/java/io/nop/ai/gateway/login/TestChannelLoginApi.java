package io.nop.ai.gateway.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.bind.ChannelBindingInfo;
import io.nop.auth.api.bind.IChannelBindService;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.ISessionBootstrap;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.commons.util.StringHelper;
import io.nop.integration.api.bind.ChannelBindResult;
import io.nop.integration.api.bind.ChannelBindResultStatus;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.integration.api.bind.IChannelBindProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 orchestration tests for {@link ChannelLoginApiBizModel}. Drives
 * the full scan-callback &rarr; binding lookup &rarr; session bootstrap
 * &rarr; accessCode chain with stub collaborators and asserts every hop is
 * really exercised (Anti-Hollow / wiring verification, Minimum Rules #22
 * &amp; #23):
 *
 * <ul>
 *   <li><b>happy path</b>: stub provider returns an extId, the stub bind
 *       service returns a binding, the recording session bootstrap saves a
 *       real context, and the {@link JwtAuthTokenProvider} signs a non-empty
 *       accessCode. Asserts the bootstrap was actually called (call count
 *       &gt; 0) and that the returned code is non-empty/non-constant.</li>
 *   <li><b>not bound</b>: no effective binding for the extId &rarr; the call
 *       throws {@link NopException} (explicit failure, no silent empty code)
 *       and the session bootstrap is NEVER invoked.</li>
 *   <li><b>no provider</b>: the callback's channelType has no registered
 *       provider &rarr; explicit failure.</li>
 * </ul>
 *
 * <p>The {@link ISessionBootstrap} stub saves into a real
 * {@link io.nop.auth.core.login.IUserContextCache} so the same instance can
 * be reused by {@link TestChannelLoginAccessCode} to prove the consumption
 * side hits the cache.
 */
public class TestChannelLoginApi {

    private static final String CHANNEL = "feishu";
    private static final String EXT_ID = "feishu-open-id-1";
    private static final String USER_ID = "user-1";
    private static final String USER_NAME = "alice";

    private JwtAuthTokenProvider tokenProvider;
    private RecordingSessionBootstrap bootstrap;
    private StubBindService bindService;
    private StubBindProvider provider;
    private ChannelLoginApiBizModel api;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtAuthTokenProvider();
        tokenProvider.setEncKey("test-enc-key-for-scan-login");

        bootstrap = new RecordingSessionBootstrap();
        bindService = new StubBindService();
        // the stub ticket asserts USER_ID as its owner, mirroring FeishuBindProvider
        // (the ticket stores the platform user who started the flow server-side)
        provider = new StubBindProvider(CHANNEL, EXT_ID, USER_ID);

        api = new ChannelLoginApiBizModel();
        api.setAccessCodeExpireSeconds(300);
        // inject collaborators directly (mirrors TestChannelMessageService)
        setField("authTokenProvider", api, tokenProvider);
        setField("sessionBootstrap", api, bootstrap);
        setField("channelBindService", api, bindService);
        api.registerProvider(provider);
    }

    @Test
    void scanLoginBootstrapsSessionAndReturnsNonEmptyAccessCode() {
        bindService.binding = newBinding(USER_ID, CHANNEL, EXT_ID);

        ScanLoginResult result = api.loginByScanAsync(callback(CHANNEL, EXT_ID), null)
                .toCompletableFuture().join();

        // session bootstrap was REALLY called (not a no-op)
        assertEquals(1, bootstrap.createCallCount.get(),
                "ISessionBootstrap.createSessionForUserAsync must be called");
        assertEquals(USER_ID, bootstrap.lastUserId);
        // W5: the real channel loginType (feishu=20) must be propagated for audit fidelity
        assertEquals(io.nop.integration.api.channel.ChannelTypeCodes.LOGIN_TYPE_FEISHU,
                bootstrap.lastLoginType, "channel loginType must be propagated to the bootstrap");
        // accessCode is non-empty and not a hardcoded constant
        assertNotNull(result.getAccessCode());
        assertTrue(!result.getAccessCode().isEmpty(), "accessCode must be non-empty");
        assertNotEquals("access-code", result.getAccessCode(),
                "accessCode must be a real signed token, not a placeholder");
    }

    @Test
    void scanLoginWithoutBindingFailsExplicitlyAndNeverBootstraps() {
        // no effective binding for this extId
        bindService.binding = null;

        NopException ex = assertThrows(NopException.class, () ->
                api.loginByScanAsync(callback(CHANNEL, EXT_ID), null).toCompletableFuture().join());

        // session bootstrap must NEVER be called when there is no binding
        assertEquals(0, bootstrap.createCallCount.get(),
                "ISessionBootstrap must not be called when there is no effective binding");
        assertTrue(ex.getMessage().contains("no effective channel binding")
                        || ex.getMessage().contains("binding"),
                "failure message must explain the missing binding; got: " + ex.getMessage());
    }

    @Test
    void scanLoginWithoutRegisteredProviderFailsExplicitly() {
        // reset providers to empty
        api.setChannelBindProviders(Collections.emptyList());

        NopException ex = assertThrows(NopException.class, () ->
                api.loginByScanAsync(callback("dingtalk", "ext-2"), null).toCompletableFuture().join());

        assertEquals(0, bootstrap.createCallCount.get(),
                "ISessionBootstrap must not be called when no provider is registered");
        assertTrue(ex.getMessage().contains("no IChannelBindProvider"),
                "failure message must explain the missing provider; got: " + ex.getMessage());
    }

    @Test
    void scanLoginWithNullSessionBootstrapFailsExplicitly() {
        // simulate a channel-less deployment where nop-auth-service is absent
        setField("sessionBootstrap", api, null);

        NopException ex = assertThrows(NopException.class, () ->
                api.loginByScanAsync(callback(CHANNEL, EXT_ID), null).toCompletableFuture().join());

        assertTrue(ex.getMessage().contains("ISessionBootstrap bean is not available"),
                "failure message must explain the missing session bootstrap; got: " + ex.getMessage());
    }

    // ===================== P0 hardening regressions (audit ai-rest, D5) =====================

    @Test
    void scanLoginWithForgedExtIdCannotLogInAsAnotherUser() {
        // The endpoint is publicAccess and rawPayload is fully caller-controlled,
        // so the payload extId must NOT be able to select the login identity.
        // Attack shape: the attacker holds a VALID ticket minted for their own
        // account (ticket asserts attacker as owner) but sends a payload whose
        // open_id belongs to the victim's binding. Without the ticket-owner
        // cross-check this bootstraps a session for the victim (account takeover).
        String victimUserId = "victim-1";
        String victimExtId = "victim-open-id";
        provider = new StubBindProvider(CHANNEL, victimExtId, "attacker-1");
        api.setChannelBindProviders(Collections.singletonList(provider));
        bindService.binding = newBinding(victimUserId, CHANNEL, victimExtId);

        NopException ex = assertThrows(NopException.class, () ->
                api.loginByScanAsync(callback(CHANNEL, victimExtId), null).toCompletableFuture().join());

        assertEquals(0, bootstrap.createCallCount.get(),
                "ISessionBootstrap must NEVER be called when the extId binding does not "
                        + "belong to the ticket owner (forged channel identity)");
        assertTrue(ex.getMessage().contains("ticket"),
                "failure message must point at the ticket/binding mismatch; got: " + ex.getMessage());
    }

    @Test
    void scanLoginWithoutServerAssertedTicketIdentityFailsClosed() {
        // A provider that cannot rehydrate the ticket owner (missing/expired
        // ticket, or a protocol without server-side identity) asserts no
        // platformUserId — login must fail closed rather than fall back to
        // trusting the caller-provided extId.
        provider = new StubBindProvider(CHANNEL, EXT_ID, null);
        api.setChannelBindProviders(Collections.singletonList(provider));
        bindService.binding = newBinding(USER_ID, CHANNEL, EXT_ID);

        NopException ex = assertThrows(NopException.class, () ->
                api.loginByScanAsync(callback(CHANNEL, EXT_ID), null).toCompletableFuture().join());

        assertEquals(0, bootstrap.createCallCount.get(),
                "ISessionBootstrap must not be called without a server-asserted ticket identity");
        assertTrue(ex.getMessage().contains("platformUserId"),
                "failure message must explain the missing server-asserted identity; got: " + ex.getMessage());
    }

    // ---- helpers / stubs ---------------------------------------------------

    private static ChannelScanCallback callback(String channelType, String extId) {
        ChannelScanCallback cb = new ChannelScanCallback();
        cb.setChannelType(channelType);
        Map<String, Object> payload = new HashMap<>();
        payload.put("extId", extId);
        cb.setRawPayload(payload);
        return cb;
    }

    private static ChannelBindingInfo newBinding(String userId, String channelType, String extId) {
        ChannelBindingInfo b = new ChannelBindingInfo();
        b.setBindingId("bind-1");
        b.setChannelType(channelType);
        b.setExtId(extId);
        b.setPlatformUserId(userId);
        return b;
    }

    private static void setField(String name, Object target, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * Stub provider: returns a fixed extId for its channelType, plus the
     * ticket-owner identity it is configured to assert (mirrors
     * {@code FeishuBindProvider}, which rehydrates the ticket's
     * {@code platformUserId} server-side; {@code null} models a provider that
     * could not recover the ticket).
     */
    static class StubBindProvider implements IChannelBindProvider {
        final String channelType;
        final String extId;
        final String ticketPlatformUserId;

        StubBindProvider(String channelType, String extId) {
            this(channelType, extId, null);
        }

        StubBindProvider(String channelType, String extId, String ticketPlatformUserId) {
            this.channelType = channelType;
            this.extId = extId;
            this.ticketPlatformUserId = ticketPlatformUserId;
        }

        @Override
        public String getChannelType() {
            return channelType;
        }

        @Override
        public io.nop.integration.api.bind.BindTicket createBindTicket(String channelType, String platformUserId) {
            throw new UnsupportedOperationException("createBindTicket not used in scan-login tests");
        }

        @Override
        public ChannelBindResult onChannelScanCallback(ChannelScanCallback callback) {
            ChannelBindResult r = new ChannelBindResult();
            r.setExtId(extId);
            r.setPlatformUserId(ticketPlatformUserId);
            r.setStatus(ChannelBindResultStatus.BINDING_COMPLETED);
            return r;
        }
    }

    /** Stub bind service: returns the preset binding (or null) for any lookup. */
    static class StubBindService implements IChannelBindService {
        ChannelBindingInfo binding;

        @Override
        public io.nop.auth.api.bind.BindStartResult startBinding(String channelType, String platformUserId) {
            throw new UnsupportedOperationException("startBinding not used in scan-login tests");
        }

        @Override
        public ChannelBindingInfo completeBinding(String channelType, String platformUserId, String extId) {
            throw new UnsupportedOperationException("completeBinding not used in scan-login tests");
        }

        @Override
        public ChannelBindingInfo findBinding(String channelType, String extId) {
            return binding;
        }

        @Override
        public java.util.List<ChannelBindingInfo> listBindings(String userId) {
            throw new UnsupportedOperationException("listBindings not used in scan-login tests");
        }

        @Override
        public void unbind(String bindingId) {
            throw new UnsupportedOperationException("unbind not used in scan-login tests");
        }
    }

    /**
     * Recording session bootstrap: builds a real {@link UserContextImpl}
     * (with a generated sessionId + access/refresh tokens signed by the test
     * {@link JwtAuthTokenProvider}) so the returned context can be fed to
     * {@link io.nop.auth.core.login.IAuthTokenProvider#generateAccessCode}.
     * Records the last userId, loginType and call count for wiring assertions.
     */
    static class RecordingSessionBootstrap implements ISessionBootstrap {
        final AtomicInteger createCallCount = new AtomicInteger();
        String lastUserId;
        int lastLoginType;
        IUserContext lastContext;

        @Override
        public CompletionStage<IUserContext> createSessionForUserAsync(String userId, int loginType) {
            createCallCount.incrementAndGet();
            lastUserId = userId;
            lastLoginType = loginType;
            UserContextImpl ctx = new UserContextImpl();
            ctx.setUserId(userId);
            ctx.setUserName(USER_NAME);
            ctx.setSessionId(StringHelper.generateUUID());
            ctx.setAccessToken("access-token-" + StringHelper.generateUUID());
            ctx.setRefreshToken("refresh-token-" + StringHelper.generateUUID());
            lastContext = ctx;
            return CompletableFuture.completedFuture(ctx);
        }

        @Override
        public CompletionStage<IUserContext> createSessionForUserAsync(String userId) {
            return createSessionForUserAsync(userId, 4);
        }
    }
}
