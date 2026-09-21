/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.auth.service.NopAuthConstants;

import java.util.Set;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_USER_NOT_LOGIN;
import static io.nop.auth.service.NopAuthErrors.ARG_USER_ID;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;

/**
 * MFA/账号管理的访问守卫共享落点（design nop-auth §3.4 接缝约束——requireCurrentUserId/
 * requireAdmin 被"保留的用户管理方法"与"迁走的 MFA 方法"共用，单一落点不复制）。
 */
public final class MfaAccessGuard {

    private MfaAccessGuard() {
    }

    public static String requireCurrentUserId(IServiceContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null || io.nop.commons.util.StringHelper.isEmpty(userContext.getUserId())) {
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);
        }
        return userContext.getUserId();
    }

    /**
     * 运行时 admin 角色校验（defense-in-depth，配合权限门禁）：仅授予操作权限不足以放行，
     * 还要求调用方具有 admin/nop-admin 角色。
     */
    public static void requireAdmin(IServiceContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null) {
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);
        }
        Set<String> roles = userContext.getRoles();
        if (roles == null || (!roles.contains(NopAuthConstants.ROLE_ADMIN)
                && !roles.contains(NopAuthConstants.ROLE_NOP_ADMIN))) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userContext.getUserId())
                    .param("msg", "only admin can perform this account management action");
        }
    }
}
