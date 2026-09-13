package io.nop.ai.gateway.login;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.bind.ChannelBindingInfo;
import io.nop.auth.api.bind.IChannelBindService;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.login.IAuthTokenProvider;
import io.nop.auth.core.login.ISessionBootstrap;
import io.nop.core.context.IServiceContext;
import io.nop.integration.api.bind.ChannelBindResult;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.integration.api.bind.IChannelBindProvider;
import io.nop.integration.api.channel.ChannelTypeCodes;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.gateway.login.NopAiGatewayErrors.ERR_CHANNEL_LOGIN_NOT_ENABLED;
import static io.nop.ai.gateway.login.NopAiGatewayErrors.ERR_CHANNEL_LOGIN_INVALID_CALLBACK;
import static io.nop.ai.gateway.login.NopAiGatewayErrors.ARG_MSG;
import static io.nop.ai.gateway.login.NopAiGatewayErrors.ARG_CHANNEL_TYPE;
import static io.nop.api.core.ApiErrors.ERR_CHECK_INVALID_ARGUMENT;

/**
 * QR-scan-login orchestration endpoint (W4). Receives a normalised channel
 * scan callback, resolves the bound platform user, bootstraps a login
 * session for that user, and mints a one-time {@code accessCode} that the
 * front-end exchanges for a {@code LoginResult} via the existing
 * {@code ILoginSpi.getLoginResultAsync(AccessCodeRequest)}.
 *
 * <p><b>Orchestration chain</b> (design §3.4 ③):
 * <ol>
 *   <li>look up the channel's {@link IChannelBindProvider} and let it parse
 *       the callback into a channel-side user identity ({@code extId}),</li>
 *   <li>reverse-lookup the binding via
 *       {@link IChannelBindService#findBinding} to recover the platform
 *       user id — if no effective binding exists the call fails explicitly
 *       (the user must bind the channel first; scan-login is not a binding
 *       flow),</li>
 *   <li>{@link ISessionBootstrap#createSessionForUserAsync} creates a full
 *       session (roles/tenant/dept/tokens) in {@code IUserContextCache},</li>
 *   <li>{@link IAuthTokenProvider#generateAccessCode} signs a one-time code
 *       over the session id, returned to the caller.</li>
 * </ol>
 *
 * <p><b>Identity trust (P0 hardening, audit ai-rest D5)</b>: the endpoint is
 * {@code publicAccess=true} and the whole callback (including
 * {@code rawPayload}) is caller-controlled, so the payload {@code extId} may
 * never select the login identity on its own. The server-side assertion of
 * who started the scan flow is the bind ticket: providers rehydrate the
 * ticket owner into {@code ChannelBindResult.getPlatformUserId()}. Login
 * fails closed when the provider asserts no ticket identity, and the
 * binding recovered from the extId must belong to that ticket owner — a
 * forged {@code open_id} pointing at another user's binding can never
 * bootstrap a session for that user. Full channel-side verification
 * (Feishu event signature / OAuth code exchange) remains the long-term
 * design-level fix; this check removes the caller-forged identity vector.
 *
 * <p><b>Exposure</b>: a {@code @BizModel} reachable as GraphQL
 * {@code ChannelLoginApi__loginByScan} or REST
 * {@code /r/ChannelLoginApi__loginByScan}, mirroring the existing
 * {@code LoginApiBizModel}. {@code @Auth(publicAccess=true)} because the
 * channel pushes the callback before any user session exists.
 *
 * <p><b>No silent skips</b>: a missing provider, a null provider result, a
 * missing binding, and a null bootstrapped context all throw
 * {@link NopException} rather than returning a null/empty code.
 */
@BizModel("ChannelLoginApi")
public class ChannelLoginApiBizModel {

    private final Map<String, IChannelBindProvider> providersByType = new LinkedHashMap<>();

    @Inject
    protected IAuthTokenProvider authTokenProvider;

    /**
     * Optional: the impl lives in {@code nop-auth-service} (LoginServiceImpl).
     * A channel-less gateway deployment that excludes {@code nop-auth-service}
     * starts with this null; {@code loginByScan} then fails explicitly on
     * first call rather than at construction (mirrors the optional
     * {@code userChannelResolver} wiring in {@code ChannelMessageServiceImpl}).
     */
    @Inject
    @Nullable
    protected ISessionBootstrap sessionBootstrap;

    @Inject
    @Nullable
    protected IChannelBindService channelBindService;

    /**
     * One-time accessCode TTL in seconds. Mirrors the short-lived nature of
     * an OAuth-style code: the front-end must exchange it promptly.
     */
    private long accessCodeExpireSeconds = 300;

    @InjectValue("@cfg:nop.ai.channel.login.access-code-expire-seconds|300")
    public void setAccessCodeExpireSeconds(long accessCodeExpireSeconds) {
        this.accessCodeExpireSeconds = accessCodeExpireSeconds;
    }

    /**
     * Collect {@link IChannelBindProvider} beans from the IoC container
     * (wired via {@code <ioc:collect-beans by-type>} in
     * {@code ai-gateway-defaults.beans.xml}). Mirrors the provider
     * collection in {@code ChannelBindServiceImpl}. An empty collection is
     * legitimate until concrete providers arrive in W5-2.
     */
    public void setChannelBindProviders(Collection<IChannelBindProvider> providers) {
        this.providersByType.clear();
        if (providers != null) {
            for (IChannelBindProvider p : providers) {
                registerProvider(p);
            }
        }
    }

    public void registerProvider(IChannelBindProvider provider) {
        if (provider == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelLoginApiBizModel.registerProvider: provider must not be null");
        }
        String type = provider.getChannelType();
        if (type == null || type.isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelLoginApiBizModel.registerProvider: provider.getChannelType() must not be null/empty");
        }
        if (providersByType.containsKey(type)) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("channelType", type).param("msg",
                    "ChannelLoginApiBizModel.registerProvider: duplicate provider for channelType=" + type);
        }
        providersByType.put(type, provider);
    }

    @BizMutation("loginByScan")
    @Auth(publicAccess = true)
    public CompletionStage<ScanLoginResult> loginByScanAsync(@RequestBean ChannelScanCallback callback,
                                                             IServiceContext context) {
        if (sessionBootstrap == null) {
            throw new NopException(ERR_CHANNEL_LOGIN_NOT_ENABLED).param(ARG_MSG,
                    "ChannelLoginApi: ISessionBootstrap bean is not available "
                            + "(nop-auth-service not on classpath); scan-login is disabled in this deployment");
        }
        if (channelBindService == null) {
            throw new NopException(ERR_CHANNEL_LOGIN_NOT_ENABLED).param(ARG_MSG,
                    "ChannelLoginApi: IChannelBindService bean is not available "
                            + "(nop-auth-service not on classpath); scan-login is disabled in this deployment");
        }
        if (callback == null || callback.getChannelType() == null || callback.getChannelType().isEmpty()) {
            throw new NopException(ERR_CHANNEL_LOGIN_INVALID_CALLBACK)
                    .param(ARG_CHANNEL_TYPE, String.valueOf(callback == null ? null : callback.getChannelType()))
                    .param(ARG_MSG, "callback.channelType must not be null/empty");
        }
        // 四步编排（provider 解析→绑定反查→会话引导→accessCode 签发 + MFA 适配）在 Processor（审计 AI-7）
        return new ChannelLoginScanProcessor(channelBindService, sessionBootstrap, authTokenProvider,
                accessCodeExpireSeconds).execute(callback, requireProvider(callback.getChannelType()));
    }

    private IChannelBindProvider requireProvider(String channelType) {
        IChannelBindProvider provider = providersByType.get(channelType);
        if (provider == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("channelType", channelType).param("msg",
                    "no IChannelBindProvider registered for channelType=" + channelType);
        }
        return provider;
    }
}
