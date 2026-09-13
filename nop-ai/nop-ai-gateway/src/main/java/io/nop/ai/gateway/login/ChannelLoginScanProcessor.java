package io.nop.ai.gateway.login;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiConstants;
import io.nop.integration.api.bind.ChannelBindResult;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.auth.api.bind.ChannelBindingInfo;
import io.nop.integration.api.bind.IChannelBindProvider;
import io.nop.integration.api.channel.ChannelTypeCodes;
import io.nop.auth.api.bind.IChannelBindService;
import io.nop.auth.core.login.IAuthTokenProvider;
import io.nop.auth.core.login.ISessionBootstrap;
import io.nop.api.core.auth.IUserContext;

import java.util.concurrent.CompletionStage;

import static io.nop.ai.gateway.login.NopAiGatewayErrors.ARG_CHANNEL_TYPE;
import static io.nop.ai.gateway.login.NopAiGatewayErrors.ARG_MSG;
import static io.nop.ai.gateway.login.NopAiGatewayErrors.ERR_CHANNEL_LOGIN_IDENTITY_MISMATCH;
import static io.nop.ai.gateway.login.NopAiGatewayErrors.ERR_CHANNEL_LOGIN_INVALID_CALLBACK;
import static io.nop.ai.gateway.login.NopAiGatewayErrors.ERR_CHANNEL_LOGIN_NO_BINDING;
import static io.nop.ai.gateway.login.NopAiGatewayErrors.ERR_CHANNEL_LOGIN_NO_SERVER_IDENTITY;
import static io.nop.ai.gateway.login.NopAiGatewayErrors.ERR_CHANNEL_LOGIN_SESSION_FAILED;

/**
 * 扫码登录四步编排 Processor（审计 AI-7 拆分）：provider 解析→绑定反查→会话引导→
 * accessCode 签发 + MFA 适配。BizModel 保持薄入口。
 */
public class ChannelLoginScanProcessor {

    /**
     * Error-code value for {@code ERR_AUTH_MFA_REQUIRED} (defined in nop-auth-service's
     * {@code NopAuthErrors}). {@code nop-ai-gateway} does NOT depend on {@code nop-auth-service}
     * (only {@code nop-auth-api} + {@code nop-biz-auth-core}), so the constant cannot be imported;
     * match by this stable string value instead (Phase 3 Decision, option a).
     */
    static final String MFA_REQUIRED_ERROR_CODE = "nop.err.auth.mfa-required";

    /** errorParam keys carried by the MFA_REQUIRED exception (design §3.8 ARG_* constants). */
    static final String ATTR_CHALLENGE_TOKEN = "challengeToken";
    static final String ATTR_MFA_TYPE = "mfaType";
    static final String ATTR_LOGIN_TYPE = "loginType";

    private final IChannelBindService channelBindService;
    private final ISessionBootstrap sessionBootstrap;
    private final IAuthTokenProvider authTokenProvider;
    private final long accessCodeExpireSeconds;

    public ChannelLoginScanProcessor(IChannelBindService channelBindService,
                                     ISessionBootstrap sessionBootstrap,
                                     IAuthTokenProvider authTokenProvider,
                                     long accessCodeExpireSeconds) {
        this.channelBindService = channelBindService;
        this.sessionBootstrap = sessionBootstrap;
        this.authTokenProvider = authTokenProvider;
        this.accessCodeExpireSeconds = accessCodeExpireSeconds;
    }

    public CompletionStage<ScanLoginResult> execute(ChannelScanCallback callback, IChannelBindProvider provider) {
        String channelType = callback.getChannelType();

        // 1. parse the callback via the channel's provider -> extId
        ChannelBindResult bindResult = provider.onChannelScanCallback(callback);
        if (bindResult == null) {
            throw new NopException(ERR_CHANNEL_LOGIN_INVALID_CALLBACK).param(ARG_CHANNEL_TYPE, channelType)
                    .param(ARG_MSG, "IChannelBindProvider.onChannelScanCallback returned null");
        }
        String extId = bindResult.getExtId();
        if (extId == null || extId.isEmpty()) {
            throw new NopException(ERR_CHANNEL_LOGIN_INVALID_CALLBACK).param(ARG_CHANNEL_TYPE, channelType)
                    .param(ARG_MSG, "IChannelBindProvider.onChannelScanCallback returned no extId");
        }

        // P0 hardening (audit ai-rest D5): this endpoint is publicAccess and the
        // whole ChannelScanCallback (rawPayload included) is caller-controlled,
        // so the payload extId must never select the login identity on its own.
        // The ONLY server-side assertion of who started the scan flow is the
        // bind ticket: providers rehydrate the ticket owner into
        // ChannelBindResult.getPlatformUserId() (FeishuBindProvider rejects
        // unknown/expired tickets itself and always sets it). Fail closed when
        // the provider asserts no identity.
        String ticketOwnerId = bindResult.getPlatformUserId();
        if (ticketOwnerId == null || ticketOwnerId.isEmpty()) {
            throw new NopException(ERR_CHANNEL_LOGIN_NO_SERVER_IDENTITY)
                    .param(ARG_CHANNEL_TYPE, channelType)
                    .param("ticketId", bindResult.getTicketId())
                    .param(ARG_MSG, "IChannelBindProvider.onChannelScanCallback returned no server-asserted "
                            + "platformUserId (ticket missing, expired or not linked to a platform user); "
                            + "scan-login refuses to trust the caller payload");
        }

        // 2. reverse-lookup the binding. No effective binding => scan-login
        //    cannot proceed (the user has not bound this channel). This is a
        //    binding flow, not a login flow — fail explicitly, do NOT fall
        //    through to completeBinding (that is a separate binding-initiate
        //    endpoint's job) and do NOT return an empty code.
        ChannelBindingInfo binding = channelBindService.findBinding(channelType, extId);
        if (binding == null) {
            throw new NopException(ERR_CHANNEL_LOGIN_NO_BINDING).param(ARG_CHANNEL_TYPE, channelType)
                    .param("extId", extId);
        }

        // P0 hardening (continued): the binding recovered from the caller-provided
        // extId must belong to the server-asserted ticket owner. The login
        // identity is therefore the ticket identity; the extId acts only as a
        // cross-checked hint. A forged open_id pointing at another user's
        // binding can never bootstrap a session for that user.
        if (!ticketOwnerId.equals(binding.getPlatformUserId())) {
            throw new NopException(ERR_CHANNEL_LOGIN_IDENTITY_MISMATCH)
                    .param(ARG_CHANNEL_TYPE, channelType)
                    .param("extId", extId)
                    .param("ticketId", bindResult.getTicketId())
                    .param("ticketOwnerId", ticketOwnerId)
                    .param("bindingUserId", binding.getPlatformUserId());
        }

        // 3. bootstrap a full session for the bound platform user, then
        //    4. mint a one-time accessCode over the session id.
        //    loginType is derived from the real channelType (20-23) so audit and
        //    the MFA exit stay accurate (W5 parameterisation; scan-result MFA
        //    adaptation itself is W6).
        int loginType = ChannelTypeCodes.loginType(channelType);
        if (loginType < 0) {
            loginType = AuthApiConstants.LOGIN_TYPE_SSO;
        }
        final int resolvedLoginType = loginType;
        // createSessionForUserAsync throws ERR_AUTH_MFA_REQUIRED SYNCHRONOUSLY when the bound
        // user has MFA enabled (LoginServiceImpl.java:355 bare `throw`, not a rejected future).
        // A try/catch around the call intercepts it; a .exceptionally() handler on the returned
        // stage would NOT (the sync throw bypasses the CompletionStage chain). See plan Phase 3.
        try {
            return sessionBootstrap.createSessionForUserAsync(binding.getPlatformUserId(), resolvedLoginType)
                    .thenApply(userContext -> {
                        if (userContext == null) {
                            throw new NopException(ERR_CHANNEL_LOGIN_SESSION_FAILED)
                                    .param("userId", binding.getPlatformUserId())
                                    .param(ARG_MSG, "ISessionBootstrap.createSessionForUserAsync returned null context");
                        }
                        return buildResult(userContext);
                    });
        } catch (NopException e) {
            // MFA adaptation (W6, design §3.2 / §五): capture only ERR_AUTH_MFA_REQUIRED and translate
            // to a ScanLoginResult carrying the challenge params. All other exceptions bubble unchanged.
            // Error-code identification裁决 (Phase 3 Decision, option a): nop-ai-gateway does NOT depend
            // on nop-auth-service, so the constant cannot be imported — match by the stable error-code
            // string value instead (avoids a cross-module public-API migration).
            if (MFA_REQUIRED_ERROR_CODE.equals(e.getErrorCode())) {
                return io.nop.api.core.util.FutureHelper.success(buildMfaResult(e));
            }
            throw e;
        }
    }

    /**
     * Build a {@link ScanLoginResult} that signals MFA is required, carrying the challenge
     * params from the captured {@code ERR_AUTH_MFA_REQUIRED} exception. The challenge itself
     * was created upstream by {@code createSessionForUserAsync} and stored in
     * {@code MfaChallengeStore}; this method only forwards the params.
     */
    private ScanLoginResult buildMfaResult(NopException mfaException) {
        ScanLoginResult result = new ScanLoginResult();
        result.setMfaRequired(true);
        Object challengeToken = mfaException.getParam(ATTR_CHALLENGE_TOKEN);
        Object mfaType = mfaException.getParam(ATTR_MFA_TYPE);
        Object loginType = mfaException.getParam(ATTR_LOGIN_TYPE);
        if (challengeToken instanceof String) {
            result.setChallengeToken((String) challengeToken);
        }
        if (mfaType instanceof String) {
            result.setMfaType((String) mfaType);
        }
        if (loginType instanceof Integer) {
            result.setLoginType((Integer) loginType);
        }
        return result;
    }

    private ScanLoginResult buildResult(IUserContext userContext) {
        String accessCode = authTokenProvider.generateAccessCode(userContext, accessCodeExpireSeconds);
        ScanLoginResult result = new ScanLoginResult();
        result.setAccessCode(accessCode);
        // W13: restricted-session flag (role-level MFA policy unmet) — the session is
        // issued restricted; the front-end renders the restricted-guidance flow.
        result.setMfaRestricted(userContext.isMfaRestricted());
        return result;
    }
}
