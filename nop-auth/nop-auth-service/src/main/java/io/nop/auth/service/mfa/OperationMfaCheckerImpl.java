/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.api.mfa.IOperationMfaChecker;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_OPERATION_MFA_ENABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_ENABLED;
import static io.nop.auth.service.NopAuthErrors.ARG_CHALLENGE_TOKEN;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ARG_OPERATION;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED;

/**
 * 操作级 MFA 拦截判定实现（设计 §3.3 拦截判定伪代码，W12-impl）。
 * <p>
 * executor 两检查点经 {@link IOperationMfaChecker} 调用本类。判定顺序：
 * <ol>
 *   <li>非敏感方法零介入（executor 侧 mfaRequiredMeta 判定，本类不被调用）。</li>
 *   <li>操作级总开关 {@code nop.auth.operation-mfa.enabled}（缺省 false）。</li>
 *   <li>无用户上下文兜底放行（构建期已禁止 publicAccess 组合）。</li>
 *   <li>setting status==enabled 检查：未启用 MFA 的用户不拦截（无第二因子可验即无
 *       "二次验证"可言；强制启用归角色级策略 W13，分层正交）。</li>
 *   <li>票核验：{@code X-Nop-Op-Mfa-Token} 头 → peek → scene==operation + 已验证
 *       （peek 不变式：verifiedAt 非空 ⇒ 票在窗口内）+ payload.operation 匹配 +
 *       payload.sessionId 匹配 → 原子 consume 成功者放行（并发双花防护）。</li>
 *   <li>未通过则创建 scene=operation challenge（payload={operation, sessionId}）→ 抛
 *       {@code ERR_AUTH_OPERATION_MFA_REQUIRED}（errorParams 携带 challengeToken/mfaType/operation）。</li>
 * </ol>
 * 审计：challenge 发起/票消费两事件经 {@link IAuditService#saveAudit}（验证成功/失败在
 * mfaVerifyOperation 端点），记录 operation 与 sessionId。
 * <p>
 * W13 接缝：受限会话白名单前置分支将插在 enabled 判定之前（设计 §3.3 注释），本类
 * 不预留半成品分支。
 */
public class OperationMfaCheckerImpl implements IOperationMfaChecker {

    /** 操作级一次性票的请求头通道（设计 §3.3）。 */
    public static final String HEADER_OP_MFA_TOKEN = "X-Nop-Op-Mfa-Token";

    /** payload 契约键（设计 §3.3：GraphQL operation 全名 + 会话 ID）。 */
    public static final String PAYLOAD_OPERATION = "operation";
    public static final String PAYLOAD_SESSION_ID = "sessionId";

    /** 操作级 challenge 的 loginType 取值（非登录场景，登录级出口判定不适用）。 */
    public static final int LOGIN_TYPE_OPERATION = 0;

    @Inject
    @Nullable
    protected MfaChallengeStore mfaChallengeStore;

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    @Nullable
    protected IAuditService auditService;

    @Override
    public void check(String operationName, IUserContext userContext, Map<String, Object> requestHeaders) {
        // 操作级总开关（缺省 false——关闭时框架零介入，一期零回归）
        if (!CFG_AUTH_OPERATION_MFA_ENABLED.get())
            return;
        // 无用户上下文兜底放行（构建期已禁止 @MfaRequired + publicAccess 组合）
        if (userContext == null || StringHelper.isEmpty(userContext.getUserId()))
            return;
        // store 未装配 = MFA 功能整体不可用 = 放行（对齐一期 checkMfaRequired 分支 1b）
        if (mfaChallengeStore == null)
            return;

        NopAuthUser user = daoForUser().getEntityById(userContext.getUserId());
        String tenantId = user != null ? user.getTenantId() : null;
        NopAuthMfaSetting setting = loadMfaSetting(userContext.getUserId());
        // 未启用 MFA 的用户操作级不拦截（设计 §3.1 结论 7）
        if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus())
                || StringHelper.isEmpty(setting.getMfaType()))
            return;

        // 票核验（一次性短 TTL 票，绑定 operation + sessionId）
        String token = getHeader(requestHeaders, HEADER_OP_MFA_TOKEN);
        if (!StringHelper.isEmpty(token)) {
            MfaChallenge c = mfaChallengeStore.peek(token);
            if (c != null && MfaChallenge.SCENE_OPERATION.equals(c.getScene())
                    && c.getVerifiedAt() != null
                    && isTicketFor(c, operationName, userContext.getSessionId())
                    && mfaChallengeStore.consume(token) != null) {
                // 仅原子消费成功者放行（并发双花防护）；票窗口已由 peek 不变式保证
                auditOperationMfa("op-mfa-ticket-consumed", operationName, userContext, token);
                return;
            }
        }

        // 未通过：创建 operation challenge（payload 一次写入）→ 抛 REQUIRED
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(PAYLOAD_OPERATION, operationName);
        payload.put(PAYLOAD_SESSION_ID, userContext.getSessionId());
        String challengeToken = mfaChallengeStore.create(MfaChallenge.SCENE_OPERATION,
                userContext.getUserId(), setting.getMfaType(), LOGIN_TYPE_OPERATION,
                tenantId, setting.getPhone(), JsonTool.stringify(payload));

        auditOperationMfa("op-mfa-challenge-issued", operationName, userContext, challengeToken);

        throw new NopException(ERR_AUTH_OPERATION_MFA_REQUIRED)
                .param(ARG_CHALLENGE_TOKEN, challengeToken)
                .param(ARG_MFA_TYPE, setting.getMfaType())
                .param(ARG_OPERATION, operationName);
    }

    /** 票绑定校验：payload.operation 与 payload.sessionId 同时匹配。 */
    private boolean isTicketFor(MfaChallenge c, String operationName, String sessionId) {
        if (StringHelper.isEmpty(c.getPayload()))
            return false;
        Map<String, Object> payload = JsonTool.parseMap(c.getPayload());
        if (payload == null)
            return false;
        return operationName.equals(payload.get(PAYLOAD_OPERATION))
                && sessionId != null && sessionId.equals(payload.get(PAYLOAD_SESSION_ID));
    }

    /** 请求头大小写不敏感读取（live extractClientIp 双大小写先例）。 */
    private static String getHeader(Map<String, Object> headers, String name) {
        if (headers == null)
            return null;
        Object v = headers.get(name);
        if (v == null)
            v = headers.get(name.toLowerCase());
        if (v == null)
            v = headers.get(name.toUpperCase());
        return v == null ? null : v.toString();
    }

    private NopAuthMfaSetting loadMfaSetting(String userId) {
        if (StringHelper.isEmpty(userId))
            return null;
        return daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
    }

    private IEntityDao<NopAuthUser> daoForUser() {
        return daoProvider.daoFor(NopAuthUser.class);
    }

    /** 审计事件（challenge 发起/票消费），记录 operation 与 sessionId。 */
    private void auditOperationMfa(String event, String operationName, IUserContext userContext, String challengeToken) {
        if (auditService == null)
            return;
        AuditRequest audit = new AuditRequest();
        audit.setOperation(operationName);
        audit.setDescription("operation-mfa:" + event);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userContext.getUserId());
        audit.setUserName(userContext.getUserName());
        audit.setSessionId(userContext.getSessionId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", event);
        data.put("challengeToken", challengeToken);
        audit.setRequestData(JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }
}
