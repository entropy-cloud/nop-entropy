/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.config;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.util.List;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * nop-credential 模块的配置项引用。配置项可通过 {@code application.yaml} 或
 * 环境变量（Nop 配置支持环境变量到属性名的映射）注入。
 */
public interface CredentialConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(CredentialConfigs.class);

    /**
     * 主密钥列表，每条形如 {@code keyId:passphrase}。keyId 须匹配 {@code [A-Za-z0-9_-]+}。
     * 可通过环境变量 {@code NOP_CREDENTIAL_MASTER_KEYS} 注入。
     */
    @Description("凭证库主密钥列表，每条 keyId:passphrase")
    @SuppressWarnings("unchecked")
    IConfigReference<List<String>> CFG_CREDENTIAL_MASTER_KEYS = (IConfigReference<List<String>>) (IConfigReference<?>) varRef(
            s_loc, "nop.credential.master-keys", List.class, null);

    /**
     * 当前用于加密的 active key 的 keyId。未设置时使用列表中的第一个 keyId。
     */
    @Description("当前用于加密的 active 主密钥 keyId（可选，缺省取列表首项）")
    IConfigReference<String> CFG_CREDENTIAL_ACTIVE_KEY_ID = varRef(
            s_loc, "nop.credential.active-key-id", String.class, null);

    // ==================== W9 OAuth 流程引擎（nop.credential.oauth.*） ====================

    /**
     * OAuth state 绑定的 TTL（秒）。缺省 600（10 分钟），与设计 §3.3 一致。
     */
    @Description("OAuth state 绑定 TTL 秒数（缺省 600）")
    IConfigReference<Integer> CFG_CREDENTIAL_OAUTH_STATE_TTL_SECONDS = varRef(
            s_loc, "nop.credential.oauth.state-ttl-seconds", Integer.class, 600);

    /**
     * 惰性刷新窗口（秒）：now 距 expiresAt 小于该值时触发 refreshToken 刷新。缺省 300。
     * 类型文件 oauth2 元数据中的 refreshWindowSeconds 可按类型覆盖。
     */
    @Description("OAuth 惰性刷新窗口秒数（缺省 300）")
    IConfigReference<Integer> CFG_CREDENTIAL_OAUTH_REFRESH_WINDOW_SECONDS = varRef(
            s_loc, "nop.credential.oauth.refresh-window-seconds", Integer.class, 300);

    /**
     * OAuth 回调端点对外基础地址（部署方配置外部可达地址，如 https://ops.example.com）。
     * redirect_uri = {base}/r/CredentialOAuthApi__oauthCallback。未配置时发起授权 fail-closed。
     */
    @Description("OAuth 回调基础地址（外部可达，redirect_uri 派生自它）")
    IConfigReference<String> CFG_CREDENTIAL_OAUTH_CALLBACK_BASE_URL = varRef(
            s_loc, "nop.credential.oauth.callback-base-url", String.class, null);

    /**
     * 授权结果前端页 URL（回调成功后浏览器跳转目标）。未配置时回调页输出内置静态完成提示。
     */
    @Description("OAuth 授权结果前端页 URL（回调跳转目标，可选）")
    IConfigReference<String> CFG_CREDENTIAL_OAUTH_RESULT_PAGE_URL = varRef(
            s_loc, "nop.credential.oauth.result-page-url", String.class, null);
}
