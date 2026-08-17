/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.login.IUserContextCache;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.IMfaLoginPolicyService;
import io.nop.auth.core.mfa.MfaLoginDecision;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthSession;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.commons.util.StringHelper;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.concurrent.CompletionStage;

import static io.nop.auth.api.AuthApiConstants.LOGIN_TYPE_PHONE_SMS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_ENABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_ENABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_SMS;
import static io.nop.auth.service.NopAuthErrors.ARG_CHALLENGE_TOKEN;
import static io.nop.auth.service.NopAuthErrors.ARG_LOGIN_TYPE;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_REQUIRED;

/**
 * 外部身份登录入口（OAuth/SSO）的 MFA/角色策略判定实现（W13-impl，设计 §4.1 结论 10
 * ——一期遗留绕过修复：{@code OAuthLoginServiceImpl} 原先自行 buildUserContext +
 * saveUserContextAsync 直签，零 MFA 判定）。
 * <p>
 * 内部复用 {@code LoginServiceImpl.checkMfaRequired} 等价逻辑（三层判定矩阵同语义：
 * 全局开关 → store 装配 → 角色策略第三态 → setting 启用检查 → 因子等同 → challenge
 * 创建）+ {@link RoleMfaPolicyEvaluator}（本地角色快照口径）。错误码常量
 * {@code ERR_AUTH_MFA_REQUIRED} 在本模块（nop-auth-service），challenge 分支经本实现
 * 抛出——nop-auth-sso 不引入依赖边（substrate 落点裁定）。
 * <p>
 * migration note：该入口从"永不拦截"变为"与密码路径同语义"（一期遗留 gap 的修复，
 * 属预期行为变更）。
 */
public class MfaLoginPolicyServiceImpl implements IMfaLoginPolicyService {

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    @Nullable
    protected MfaChallengeStore mfaChallengeStore;

    @Inject
    @Nullable
    protected IUserContextCache userContextCache;

    @Inject
    protected RoleMfaPolicyEvaluator roleMfaPolicyEvaluator;

    @Override
    public MfaLoginDecision checkMfaForUserName(String userName, int loginType) {
        // 一期分支 1/1b（原样语义）：全局开关关闭 / store 未装配 = MFA 子系统整体旁路
        if (!CFG_AUTH_MFA_ENABLED.get())
            return null;
        if (mfaChallengeStore == null)
            return null;
        // 无本地用户映射 → 无策略可评估 → 放行（空策略语义；realm roles 不参与策略评估）
        if (StringHelper.isEmpty(userName))
            return null;
        NopAuthUser user = getUserByUserName(userName);
        if (user == null)
            return null;

        return ContextProvider.runWithTenant(user.getTenantId(), () -> {
            // 等价 checkMfaRequired（设计 §4.3 三层判定）
            RoleMfaPolicy policy = roleMfaPolicyEvaluator.evaluateForUser(user.getUserId());
            NopAuthMfaSetting setting = loadMfaSetting(user.getUserId());
            if (policy.getMaxLevel() > 0) {
                boolean mfaEnabled = setting != null && MFA_STATUS_ENABLED.equals(setting.getStatus())
                        && !StringHelper.isEmpty(setting.getMfaType());
                if (!mfaEnabled || RoleMfaPolicyEvaluator.factorLevel(setting.getMfaType()) < policy.getMaxLevel()) {
                    return MfaLoginDecision.restricted();
                }
            }
            if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus()))
                return null;
            String mfaType = setting.getMfaType();
            if (StringHelper.isEmpty(mfaType))
                return null;
            if (loginType == LOGIN_TYPE_PHONE_SMS && MFA_TYPE_SMS.equals(mfaType))
                return null;
            // challenge 分支：与一期表达一致（ERR_AUTH_MFA_REQUIRED + errorParams 三元组）。
            // W14-impl 触点③（W13 登记的同构副本同步不变式履行）：webauthn 类型经 helper 增量
            // payload.cryptoChallenge（与 LoginServiceImpl.checkMfaRequired 共用 createLoginChallenge，
            // 两副本不再漂移）；其余类型一期五参语义逐字节等价
            String challengeToken = MfaChallengeHelper.createLoginChallenge(mfaChallengeStore,
                    user.getUserId(), mfaType, loginType, user.getTenantId(), setting.getPhone());
            throw new NopException(ERR_AUTH_MFA_REQUIRED)
                    .param(ARG_CHALLENGE_TOKEN, challengeToken)
                    .param(ARG_MFA_TYPE, mfaType)
                    .param(ARG_LOGIN_TYPE, loginType);
        });
    }

    @Override
    public CompletionStage<IUserContext> completeRestricted(IUserContext userContext) {
        if (userContext == null)
            return FutureHelper.success(null);
        // 标志先设后存（不重建上下文——OAuth attrs：IdP accessToken/refreshToken 原样保留）。
        // 触达时间补齐（OAuth buildUserContext 不设置该字段，缺省 0 会被 Dao-cache 落成
        // epoch 导致会话立即过期——live 执行期核定）
        if (userContext instanceof UserContextImpl) {
            UserContextImpl impl = (UserContextImpl) userContext;
            impl.setMfaRestricted(true);
            if (impl.getLastAccessTime() <= 0) {
                impl.setLastAccessTime(CoreMetrics.currentTimeMillis());
            }
        }
        // Dao-cache 基座：无 NopAuthSession 行时 saveUserContextAsync 静默 no-op（标志会丢，
        // fail-open）——受限路径先落会话行（保留 OAuth 原 sessionId）再持久化
        ensureSessionRow(userContext);
        if (userContextCache == null)
            return FutureHelper.success(userContext);
        return userContextCache.saveUserContextAsync(userContext).thenApply(v -> userContext);
    }

    /** 落 NopAuthSession 行（幂等：已存在即跳过），保留上下文现有 sessionId。 */
    private void ensureSessionRow(IUserContext userContext) {
        String sessionId = userContext.getSessionId();
        if (StringHelper.isEmpty(sessionId))
            return;
        String tenantId = StringHelper.isEmpty(userContext.getTenantId())
                ? DaoConstants.DEFAULT_TENANT_ID : userContext.getTenantId();
        ContextProvider.runWithTenant(tenantId, () -> {
            IEntityDao<NopAuthSession> dao = daoProvider.daoFor(NopAuthSession.class);
            if (dao.getEntityById(sessionId) != null)
                return null;
            NopAuthSession session = dao.newEntity();
            session.setSessionId(sessionId);
            session.setUserId(userContext.getUserId());
            session.setUserName(userContext.getUserName());
            session.setTenantId(tenantId);
            session.setLoginType(userContext instanceof UserContextImpl
                    ? ((UserContextImpl) userContext).getLoginType() : 0);
            session.setLogoutType(AuthApiConstants.LOGOUT_TYPE_NONE);
            session.setLoginTime(CoreMetrics.currentTimestamp());
            session.setLastAccessTime(CoreMetrics.currentDateTime());
            dao.saveEntity(session);
            return null;
        });
    }

    private NopAuthUser getUserByUserName(String userName) {
        NopAuthUser example = new NopAuthUser();
        example.setUserName(userName);
        return daoProvider.daoFor(NopAuthUser.class).findFirstByExample(example);
    }

    private NopAuthMfaSetting loadMfaSetting(String userId) {
        if (StringHelper.isEmpty(userId))
            return null;
        return daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
    }
}
