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
import io.nop.auth.core.login.IAuthTokenProvider;
import io.nop.auth.core.login.ISessionBootstrap;
import io.nop.core.context.IServiceContext;
import io.nop.integration.api.bind.ChannelBindResult;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.integration.api.bind.IChannelBindProvider;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

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
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelLoginApi: ISessionBootstrap bean is not available (nop-auth-service not on classpath); scan-login is disabled in this deployment");
        }
        if (channelBindService == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelLoginApi: IChannelBindService bean is not available (nop-auth-service not on classpath); scan-login is disabled in this deployment");
        }
        if (callback == null || callback.getChannelType() == null || callback.getChannelType().isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelLoginApiBizModel.loginByScan: callback.channelType must not be null/empty");
        }
        String channelType = callback.getChannelType();

        // 1. parse the callback via the channel's provider -> extId
        IChannelBindProvider provider = requireProvider(channelType);
        ChannelBindResult bindResult = provider.onChannelScanCallback(callback);
        if (bindResult == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("channelType", channelType).param("msg",
                    "IChannelBindProvider.onChannelScanCallback returned null for channelType=" + channelType);
        }
        String extId = bindResult.getExtId();
        if (extId == null || extId.isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("channelType", channelType).param("msg",
                    "IChannelBindProvider.onChannelScanCallback returned no extId for channelType=" + channelType);
        }

        // 2. reverse-lookup the binding. No effective binding => scan-login
        //    cannot proceed (the user has not bound this channel). This is a
        //    binding flow, not a login flow — fail explicitly, do NOT fall
        //    through to completeBinding (that is a separate binding-initiate
        //    endpoint's job) and do NOT return an empty code.
        ChannelBindingInfo binding = channelBindService.findBinding(channelType, extId);
        if (binding == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT)
                    .param("channelType", channelType)
                    .param("extId", extId)
                    .param("msg", "no effective channel binding for extId; bind the channel before scan-login");
        }

        // 3. bootstrap a full session for the bound platform user, then
        //    4. mint a one-time accessCode over the session id.
        return sessionBootstrap.createSessionForUserAsync(binding.getPlatformUserId()).thenApply(userContext -> {
            if (userContext == null) {
                throw new NopException(ERR_CHECK_INVALID_ARGUMENT)
                        .param("userId", binding.getPlatformUserId())
                        .param("msg", "ISessionBootstrap.createSessionForUserAsync returned null context");
            }
            return buildResult(userContext);
        });
    }

    private ScanLoginResult buildResult(IUserContext userContext) {
        String accessCode = authTokenProvider.generateAccessCode(userContext, accessCodeExpireSeconds);
        ScanLoginResult result = new ScanLoginResult();
        result.setAccessCode(accessCode);
        return result;
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
