/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.oauth;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.commons.util.StringHelper;
import io.nop.credential.api.registry.CredentialType;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import io.nop.credential.config.CredentialConfigs;
import io.nop.credential.crypto.CredentialErrors;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialOauthState;
import io.nop.credential.service.CredentialProviderImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 出站 OAuth 2.0 客户端流程引擎（授权码闭环：发起 → 单一公开回调 → token 加密回写）。
 *
 * <p><b>唯一解密点不变式</b>：本类不持有 {@code CredentialCipher}；clientSecret 读取与
 * token 回写经 {@link CredentialProviderImpl} 引擎内部通道执行（回调路径无登录态，
 * 以 state 记录的发起人身份语义执行，与设计 §3.3 回调数据通道联合裁定一致——W9 时点
 * 发起人仅作审计记录，归属校验归 W11-impl 回补）。
 *
 * <p>失败语义全部 fail-closed（Rule #24）：state 未命中/过期/重放统一
 * {@code ERR_CREDENTIAL_OAUTH_STATE_INVALID}（不区分细节防探测）；token endpoint 错误、
 * clientSecret 缺失、类型不符、禁用/删除凭证均显式抛错。
 */
@Singleton
public class OAuthFlowService {

    /** 单一公开回调端点的 REST operation 名（GET /r/CredentialOAuthApi__oauthCallback）。 */
    public static final String CALLBACK_OPERATION = "CredentialOAuthApi__oauthCallback";

    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_CLIENT_SECRET = "clientSecret";

    private static final String RESP_TYPE_CODE = "code";

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected ICredentialTypeRegistry credentialTypeRegistry;

    /**
     * 唯一解密点（引擎内部通道）。注入实现类而非 SPI 接口——引擎通道方法不在 SPI 面上。
     */
    @Inject
    protected CredentialProviderImpl credentialProvider;

    @Inject
    protected NopCredentialOauthStateStore stateStore;

    @Inject
    protected OAuthTokenClient tokenClient;

    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setCredentialTypeRegistry(ICredentialTypeRegistry credentialTypeRegistry) {
        this.credentialTypeRegistry = credentialTypeRegistry;
    }

    public void setCredentialProvider(CredentialProviderImpl credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    public void setStateStore(NopCredentialOauthStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public void setTokenClient(OAuthTokenClient tokenClient) {
        this.tokenClient = tokenClient;
    }

    private IEntityDao<NopCredential> credentialDao() {
        return daoProvider.daoFor(NopCredential.class);
    }

    /**
     * 校验链：实例存在/未删/未禁用 → 类型为 oauth2。（登录态要求仅发起路径有——回调路径无
     * 登录态，以 state 绑定的发起人身份语义执行，见设计 §3.3 回调数据通道联合裁定。）
     */
    private CredentialType requireOauth2Credential(String credentialId) {
        NopCredential entity = credentialDao().getEntityById(credentialId);
        if (entity == null) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }
        if (entity.getDelFlag() != null && entity.getDelFlag() != 0) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_DELETED)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }
        CredentialType type = credentialTypeRegistry.getType(entity.getTypeName());
        if (!type.isOauth2Type()) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_NOT_OAUTH2_TYPE)
                    .param(CredentialErrors.ARG_TYPE_NAME, entity.getTypeName())
                    .param(CredentialErrors.ARG_AUTH_TYPE, type.getAuthType());
        }
        if ("disabled".equals(entity.getStatus())) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_DISABLED)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }
        return type;
    }

    private IUserContext requireUserContext() {
        IUserContext userContext = IUserContext.get();
        if (userContext == null || StringHelper.isEmpty(userContext.getUserId())) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_USER_CONTEXT_REQUIRED);
        }
        return userContext;
    }

    /**
     * redirect_uri：配置基础地址派生的单一公开回调端点 URL（token 交换时回传同值）。
     */
    public String buildCallbackUrl() {
        String baseUrl = CredentialConfigs.CFG_CREDENTIAL_OAUTH_CALLBACK_BASE_URL.get();
        if (StringHelper.isEmpty(baseUrl)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_CALLBACK_NOT_CONFIGURED);
        }
        return StringHelper.removeEnd(baseUrl.trim(), "/") + "/r/" + CALLBACK_OPERATION;
    }

    /**
     * 发起授权（登录态）：校验链通过后生成不可预测一次性 state、持久化绑定
     * （credentialId + 发起人 + TTL，创建时惰性清理过期行），返回授权 URL
     * （授权端点 + client_id + redirect_uri + scope + state + response_type=code）。
     */
    public String beginOAuthFlow(String credentialId) {
        IUserContext userContext = requireUserContext();
        CredentialType type = requireOauth2Credential(credentialId);

        Map<String, Object> fields = credentialProvider.engineGetDecryptedFields(credentialId);
        String clientId = (String) fields.get(FIELD_CLIENT_ID);
        String clientSecret = (String) fields.get(FIELD_CLIENT_SECRET);
        if (StringHelper.isEmpty(clientSecret)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_CLIENT_CREDENTIALS_MISSING)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId)
                    .param(CredentialErrors.ARG_FIELD_NAMES, FIELD_CLIENT_SECRET);
        }

        long ttlSeconds = CredentialConfigs.CFG_CREDENTIAL_OAUTH_STATE_TTL_SECONDS.get();
        String state = stateStore.create(credentialId, userContext.getUserId(), ttlSeconds);

        String redirectUri = buildCallbackUrl();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("client_id", StringHelper.isEmpty(clientId) ? "" : clientId);
        params.put("redirect_uri", redirectUri);
        if (!StringHelper.isEmpty(type.getOauth2().getScopes())) {
            params.put("scope", type.getOauth2().getScopes());
        }
        params.put("state", state);
        params.put("response_type", RESP_TYPE_CODE);

        String query = ApiStringHelper.encodeQuery(params);
        return ApiStringHelper.appendQuery(type.getOauth2().getAuthorizationEndpoint(), query);
    }

    /**
     * 单一公开回调：state 校验与一次性消费原子（条件 UPDATE + affected-row 判定，并发
     * 双回调恰一个成功）→ 以 state 绑定的发起人身份语义经引擎通道解密 clientSecret →
     * 令牌端点换 token 集 → 只写引擎保留字段回写 → 返回 WebContentBean 跳转页
     * （不携带 token 明文）。state 未命中/过期/重放统一 fail-closed（不区分细节防探测）。
     */
    public WebContentBean handleOAuthCallback(String code, String state) {
        if (StringHelper.isEmpty(code) || StringHelper.isEmpty(state)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_CALLBACK_PARAM_MISSING);
        }

        // 捕获绑定数据 → 条件 UPDATE 原子一次性消费（W8 DbMfaChallengeStore.consume 先例）
        NopCredentialOauthState binding = stateStore.peek(state);
        if (binding == null || binding.getExpireAt() == null
                || binding.getExpireAt() <= System.currentTimeMillis()) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_STATE_INVALID)
                    .param(CredentialErrors.ARG_STATE, state);
        }
        if (!stateStore.consume(state)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_STATE_INVALID)
                    .param(CredentialErrors.ARG_STATE, state);
        }

        String credentialId = binding.getCredentialId();
        CredentialType type = requireOauth2Credential(credentialId);

        Map<String, Object> fields = credentialProvider.engineGetDecryptedFields(credentialId);
        String clientId = (String) fields.get(FIELD_CLIENT_ID);
        String clientSecret = (String) fields.get(FIELD_CLIENT_SECRET);
        if (StringHelper.isEmpty(clientId) || StringHelper.isEmpty(clientSecret)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_CLIENT_CREDENTIALS_MISSING)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId)
                    .param(CredentialErrors.ARG_FIELD_NAMES, FIELD_CLIENT_ID + "," + FIELD_CLIENT_SECRET);
        }

        String redirectUri = buildCallbackUrl();
        OAuthTokenResponse token = tokenClient.exchangeAuthorizationCode(
                type.getOauth2().getTokenEndpoint(), clientId, clientSecret, code, redirectUri);

        long now = System.currentTimeMillis();
        Map<String, Object> tokenFields = new LinkedHashMap<>();
        tokenFields.put("accessToken", token.getAccessToken());
        if (token.getRefreshToken() != null) {
            tokenFields.put("refreshToken", token.getRefreshToken());
        }
        if (token.getExpiresIn() != null) {
            tokenFields.put("expiresAt", now + token.getExpiresIn() * 1000L);
        }
        if (token.getTokenType() != null) {
            tokenFields.put("tokenType", token.getTokenType());
        }
        if (token.getScope() != null) {
            tokenFields.put("scope", token.getScope());
        }
        credentialProvider.engineUpdateTokenFields(credentialId, tokenFields);

        return buildResultPage();
    }

    /**
     * 回调响应页（Phase 2 Decision：biz 层无 30x 原语，落地为 WebContentBean HTML——
     * meta-refresh/JS location 跳转到配置的前端结果页；未配置时输出内置静态完成提示）。
     * 响应体不含 token 明文（Exit Criteria 断言项）。
     */
    private WebContentBean buildResultPage() {
        String resultUrl = CredentialConfigs.CFG_CREDENTIAL_OAUTH_RESULT_PAGE_URL.get();
        String html;
        if (StringHelper.isEmpty(resultUrl)) {
            html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
                    + "<title>Authorization Complete</title></head>"
                    + "<body><p>OAuth authorization complete. You can close this window.</p></body></html>";
        } else {
            html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
                    + "<meta http-equiv=\"refresh\" content=\"0;url=" + StringHelper.escapeHtml(resultUrl) + "\"/>"
                    + "<title>Authorization Complete</title></head>"
                    + "<body><p>OAuth authorization complete. Redirecting...</p>"
                    + "<script type=\"text/javascript\">window.location.replace(\""
                    + StringHelper.escapeHtml(resultUrl) + "\");</script></body></html>";
        }
        return new WebContentBean(WebContentBean.CONTENT_TYPE_HTML, html);
    }
}
