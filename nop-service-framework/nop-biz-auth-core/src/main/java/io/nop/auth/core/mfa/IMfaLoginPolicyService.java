/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.CompletionStage;

/**
 * 外部身份登录入口（OAuth/SSO）的 MFA/角色策略判定 SPI（W13-impl，设计 §4.1 结论 10
 * ——一期遗留绕过修复的接入基座）。
 * <p>
 * <b>substrate 落点裁定（plan Phase 2 Decision）</b>：本接口落 nop-biz-auth-core
 * （nop-auth-sso classpath 可达），实现 bean 落 nop-auth-service（内部复用
 * {@code checkMfaRequired} 等价逻辑 + RoleMfaPolicyEvaluator）；消费方
 * {@code OAuthLoginServiceImpl} {@code @Inject @Nullable} 可选注入——未注册
 * （无 nop-auth-service 部署）时零介入 = 一期行为。禁止为接入在 nop-auth-sso 引入
 * 对 nop-auth-service/dao 的依赖边。
 * <p>
 * <b>本地用户与角色语义</b>：按 userName 解析本地 {@code NopAuthUser}；策略评估用本地
 * 角色快照（{@code NopAuthRoleMfaPolicy} 挂本地 roleId，IdP realm roles 不参与）；
 * 无本地用户映射 → 无策略可评估 → 放行（与"无策略角色"一致的空策略语义）。
 */
public interface IMfaLoginPolicyService {

    /**
     * 对外部身份执行与 {@code LoginServiceImpl.checkMfaRequired} 同语义的三层判定。
     *
     * @param userName  外部身份用户名（按其解析本地用户；userId=userName 的一期口径）
     * @param loginType 登录方式（审计与 challenge 携带）
     * @return {@code null} = 放行；受限决策 = 策略不达标（走 {@link #completeRestricted}）
     * @throws NopException challenge 分支：{@code ERR_AUTH_MFA_REQUIRED}
     *                      （errorParams 携带 challengeToken/mfaType/loginType，一期表达）
     */
    MfaLoginDecision checkMfaForUserName(String userName, int loginType) throws NopException;

    /**
     * 受限签发基座：设置 {@code mfaRestricted} 标志（"先设后存"）并保障 Dao-cache 会话行
     * 后持久化上下文（OAuth attrs——IdP accessToken/refreshToken——原样保留，不重建上下文）。
     * live 事实（plan Phase 2 Decision 3）：{@code DaoUserContextCache.saveUserContextAsync}
     * 无 NopAuthSession 行时静默 no-op——标志直接置上会丢（fail-open），故受限路径先落
     * 会话行（保留 OAuth 原 sessionId）再持久化。
     *
     * @return 持久化完成后的 userContext（同一实例，已带受限标志）
     */
    CompletionStage<IUserContext> completeRestricted(IUserContext userContext);
}
