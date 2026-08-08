/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;

import java.util.concurrent.CompletionStage;

/**
 * Bootstrap a full login session for an already-authenticated platform user,
 * without requiring credentials. Used by passwordless login flows where the
 * caller has already established the user's identity out-of-band — most
 * notably QR-scan channel login (W4): the scan callback resolves a bound
 * platform user id, and this SPI turns that id into a cached
 * {@link IUserContext} that the existing accessCode consumption chain
 * ({@link ILoginService#getUserContextAsync}) can read.
 *
 * <p>The returned {@link IUserContext} is registered in
 * {@link IUserContextCache} (both the session-id &rarr; context cache and the
 * user-name &rarr; session-id cache), exactly like a credential login. It
 * carries a fresh sessionId, accessToken, and refreshToken so that
 * {@link io.nop.auth.core.spi.ILoginSpi#getLoginResultAsync} can build a
 * complete {@code LoginResult} from it.
 *
 * <p><b>Why a separate SPI and not an additive method on
 * {@link ILoginService}?</b> The channel-integration roadmap requires the
 * existing login contract surface ({@code ILoginService} /
 * {@code ILoginSpi} / {@code IAuthTokenProvider}) to remain literally
 * unchanged. A new interface keeps those three untouched while still letting
 * the auth layer own session creation — the implementation reuses the same
 * user-loading and session-saving logic as credential login, so a
 * bootstrapped session is identical in shape to a credential one (same
 * roles/tenant/dept/tokens).
 *
 * <p><b>Cache topology</b>: in a monolithic deployment the implementation
 * bean (in {@code nop-auth-service}) and the accessCode consumer
 * ({@code LoginApiBizModel}, also in {@code nop-auth-service}) share one JVM
 * and one {@code IUserContextCache} instance, so a saved session is
 * immediately visible to the consumer.
 *
 * @since W4 (channel scan login)
 */
public interface ISessionBootstrap {

    /**
     * Create and cache a login session for the given platform user id. The
     * user must exist; an unknown id fails fast with a
     * {@link io.nop.api.core.exceptions.NopException} rather than returning
     * a null context.
     *
     * @param userId the platform user id (e.g. resolved from a channel
     *               binding by {@code IChannelBindService.findBinding}); never
     *               null/empty
     * @return a completion stage resolving to the newly-cached, non-null
     *         {@link IUserContext} (with sessionId/accessToken/refreshToken
     *         set); never resolves to null
     */
    CompletionStage<IUserContext> createSessionForUserAsync(String userId);
}
