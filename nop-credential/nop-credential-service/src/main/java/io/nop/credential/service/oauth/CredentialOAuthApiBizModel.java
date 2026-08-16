/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.oauth;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.beans.WebContentBean;
import io.nop.core.context.IServiceContext;
import jakarta.inject.Inject;

/**
 * OAuth 流程引擎的 API 面（Api 型 BizModel，先例 {@code LoginApiBizModel}——无聚合根 xmeta，
 * 经 REST `/r/{operationName}` 与 GraphQL 可达）。
 *
 * <p><b>公开攻击面最小化</b>（Phase 2 安全审查项）：仅回调 {@link #oauthCallback} 为
 * {@code @Auth(publicAccess=true)}（单一公开回调端点，state 为不可预测+一次性+短 TTL 的
 * bearer capability）；发起 {@link #beginOAuthFlow} 为登录态（默认鉴权）。
 *
 * <p>两种访问方式：GraphQL（{@code mutation { CredentialOAuthApi__beginOAuthFlow }}）与
 * REST（{@code POST/GET /r/CredentialOAuthApi__oauthCallback?code=..&state=..}——
 * 授权服务器 30x 回跳浏览器到该 URL，普通 query 参数由 REST 层映射为 action 参数）。
 */
@BizModel("CredentialOAuthApi")
public class CredentialOAuthApiBizModel {

    @Inject
    OAuthFlowService oauthFlowService;

    public void setOauthFlowService(OAuthFlowService oauthFlowService) {
        this.oauthFlowService = oauthFlowService;
    }

    /**
     * 发起 OAuth 授权（登录态）。校验链通过后返回授权 URL，由前端跳转第三方。
     */
    @Description("发起OAuth授权，返回授权URL")
    @BizMutation
    public String beginOAuthFlow(@Name("credentialId") String credentialId, IServiceContext context) {
        return oauthFlowService.beginOAuthFlow(credentialId);
    }

    /**
     * 单一公开回调端点（publicAccess；浏览器顶层跳转流）。接收 {@code code + state}，
     * token 集加密回写后返回 HTML 跳转页（不携带 token 明文）。
     * state 未命中/过期/重放统一 fail-closed。
     */
    @Description("OAuth授权回调（公开端点，返回跳转页）")
    @BizQuery
    @Auth(publicAccess = true)
    public WebContentBean oauthCallback(@Name("code") String code,
                                        @Name("state") String state,
                                        IServiceContext context) {
        return oauthFlowService.handleOAuthCallback(code, state);
    }
}
