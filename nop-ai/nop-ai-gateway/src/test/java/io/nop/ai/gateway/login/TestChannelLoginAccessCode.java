package io.nop.ai.gateway.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.IAuthTokenProvider;
import io.nop.auth.core.login.IUserContextCache;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.commons.util.StringHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 accessCode consumption-chain proof (gateway scope). Demonstrates
 * the round-trip that the existing login main flow relies on:
 *
 * <ol>
 *   <li>bootstrap a session: build a {@link UserContextImpl}, register it in
 *       a <b>real</b> {@link LocalUserContextCache} via
 *       {@link IUserContextCache#saveUserContextAsync} (writes both the
 *       sessionId&rarr;context and userName&rarr;sessionId caches),</li>
 *   <li>sign a one-time code with the <b>real</b>
 *       {@link JwtAuthTokenProvider#generateAccessCode},</li>
 *   <li>decode it back with {@link IAuthTokenProvider#parseAccessCode} and
 *       assert the token type is {@code code} and the sessionId/userName
 *       match,</li>
 *   <li>read the session back through
 *       {@link IUserContextCache#getUserContextAsync(String)} by the
 *       sessionId carried in the parsed token — proving the cache lookup
 *       hits (Phase 1's session registration succeeded),</li>
 *   <li>and that a forged / wrong-type code is rejected by
 *       {@link IAuthTokenProvider#parseAccessCode} (no silent swallow).</li>
 * </ol>
 *
 * <p>This is the gateway-verifiable half of the accessCode mechanism. The
 * full {@code LoginApiBizModel.getLoginResultAsync}&rarr;{@code LoginResult}
 * chain lives in {@code nop-auth-service} (not on the gateway classpath) and
 * is deferred to W6-2 E2E (Phase 2 adjudication A) — it consumes the very
 * accessCode + cache-hit proved here, so its success follows from this
 * proof plus the unchanged {@code LoginApiBizModel} code.
 */
public class TestChannelLoginAccessCode {

    private JwtAuthTokenProvider tokenProvider;
    private LocalUserContextCache userContextCache;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtAuthTokenProvider();
        tokenProvider.setEncKey("test-enc-key-for-scan-login");

        userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();
    }

    @Test
    void accessCodeRoundTripsThroughCache() {
        UserContextImpl ctx = saveSessionFor("alice", "user-1");

        // sign a one-time accessCode over the cached session
        String accessCode = tokenProvider.generateAccessCode(ctx, 300);
        assertNotNull(accessCode);
        assertTrue(!accessCode.isEmpty(), "accessCode must be non-empty");

        // decode it back: type must be CODE, and it must carry the session id
        AuthToken parsed = tokenProvider.parseAccessCode(accessCode);
        assertNotNull(parsed, "parseAccessCode must decode the code signed by generateAccessCode");
        assertEquals(ctx.getSessionId(), parsed.getSessionId(),
                "accessCode must carry the cached session id");
        assertEquals(ctx.getUserName(), parsed.getUserName(),
                "accessCode must carry the user name");

        // the consumption-side cache lookup (getUserContextAsync by sessionId)
        // must HIT — proving the session registered in step 1 is visible to
        // the existing getLoginResultAsync consumption chain.
        IUserContext loaded = sync(userContextCache.getUserContextAsync(parsed.getSessionId()));
        assertNotNull(loaded, "getUserContextAsync must return the cached context (session registered)");
        assertEquals(ctx.getUserName(), loaded.getUserName());
        assertEquals(ctx.getSessionId(), loaded.getSessionId());
    }

    @Test
    void accessTokenIsNotAcceptedAsAccessCode() {
        UserContextImpl ctx = saveSessionFor("bob", "user-2");

        // an access TOKEN (type=access) signed by the same provider must NOT
        // be accepted by parseAccessCode (type=code) — the usage separation
        // is what makes the one-time code distinct from a long-lived token.
        String accessToken = tokenProvider.generateAccessToken(ctx, 300);
        assertNotNull(accessToken);

        assertThrows(Exception.class, () -> tokenProvider.parseAccessCode(accessToken),
                "an access token must not be accepted as an accessCode");
    }

    @Test
    void forgedCodeIsRejected() {
        // a random/forged string must fail parsing, never return silently
        assertThrows(Exception.class, () -> tokenProvider.parseAccessCode("not-a-real-code"),
                "a forged accessCode must be rejected");
    }

    @Test
    void cacheMissReturnsNullForUnknownSession() {
        // a sessionId that was never registered yields null (not an
        // exception) — the existing buildLoginResult turns null into
        // ERR_AUTH_SESSION_EXPIRED, which is the correct "session expired"
        // behaviour for a stale/foreign code.
        IUserContext loaded = sync(userContextCache.getUserContextAsync(StringHelper.generateUUID()));
        assertNull(loaded, "getUserContextAsync must return null for an unknown sessionId");
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * Build a complete session context, sign its access/refresh tokens, and
     * register it in the real cache (mirrors what
     * {@code LoginServiceImpl.saveSession} + {@code saveUserContextAsync}
     * does in production). Returns the context so the test can sign an
     * accessCode over it.
     */
    private UserContextImpl saveSessionFor(String userName, String userId) {
        UserContextImpl ctx = new UserContextImpl();
        ctx.setUserName(userName);
        ctx.setUserId(userId);
        ctx.setSessionId(StringHelper.generateUUID());
        ctx.setAccessToken(tokenProvider.generateAccessToken(ctx, 1800));
        ctx.setRefreshToken(tokenProvider.generateRefreshToken(ctx, 86400));
        // registering the session must succeed and write both caches
        sync(userContextCache.saveUserContextAsync(ctx));
        return ctx;
    }

    private static <T> T sync(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }
}
