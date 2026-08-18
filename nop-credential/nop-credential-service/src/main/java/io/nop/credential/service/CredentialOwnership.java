/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.commons.util.StringHelper;
import io.nop.credential.config.CredentialConfigs;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * W11 凭证归属统一（设计 §5.3）的运行时判定工具：scope 语义（NULL 视同 system）、
 * 管理员判定（{@code nop.credential.admin-roles} CSV + {@code IUserContext.isUserInAnyRole}）、
 * owner 判定与行级可见性。
 *
 * <p>凭证库不依赖 nop-auth 模块——管理员角色名以配置自持（设计 §5.1 结论 8 / §6.1 依赖边界）。
 */
public final class CredentialOwnership {

    public static final String SCOPE_SYSTEM = "system";
    public static final String SCOPE_USER = "user";

    private CredentialOwnership() {
    }

    /**
     * scope 取值是否合法（system|user）。
     */
    public static boolean isValidScope(String scope) {
        return SCOPE_SYSTEM.equals(scope) || SCOPE_USER.equals(scope);
    }

    /**
     * 是否 user 级凭证。存量行 scope 为 NULL 时视同 system（语义等价裁定，见 plan W11-impl Phase 1）。
     */
    public static boolean isUserScope(String scope) {
        return SCOPE_USER.equals(scope);
    }

    /**
     * 是否有效登录态（有用户上下文且 userId 非空）。
     */
    public static boolean hasLoginUser(IUserContext userContext) {
        return userContext != null && !StringHelper.isEmpty(userContext.getUserId());
    }

    /**
     * 管理员判定：{@code nop.credential.admin-roles}（CSV，缺省 admin,nop-admin）经
     * {@code IUserContext.isUserInAnyRole} 判定。无用户上下文恒为 false。
     */
    public static boolean isAdmin(IUserContext userContext) {
        if (userContext == null) {
            return false;
        }
        Set<String> roles = adminRoles();
        return !roles.isEmpty() && userContext.isUserInAnyRole(roles);
    }

    /**
     * 解析管理员角色集合（CSV → trim → 去空）。
     */
    public static Set<String> adminRoles() {
        String csv = CredentialConfigs.CFG_CREDENTIAL_ADMIN_ROLES.get();
        Set<String> roles = new LinkedHashSet<>();
        if (csv != null) {
            for (String role : csv.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    roles.add(trimmed);
                }
            }
        }
        return roles;
    }

    /**
     * owner 判定：当前登录用户即该 user 级凭证的归属人。
     */
    public static boolean isOwner(IUserContext userContext, String ownerId) {
        return hasLoginUser(userContext) && userContext.getUserId().equals(ownerId);
    }

    /**
     * 行级可见性（读过滤判定，设计 §5.3 BizModel 层前置过滤）：
     * system 级（含 NULL）全员可见；user 级 owner 或管理员可见。
     */
    public static boolean canSee(IUserContext userContext, String scope, String ownerId) {
        if (!isUserScope(scope)) {
            return true;
        }
        return isOwner(userContext, ownerId) || isAdmin(userContext);
    }

    /**
     * 写权限判定（设计 §5.3 写类分级）：system 级限管理员（无登录态的内部调用按一期行为放行）；
     * user 级限 owner+管理员（无登录态拒绝——owner 无法判定）。
     *
     * @return 拒绝原因；null 表示放行
     */
    public static String writeDenialReason(IUserContext userContext, String scope, String ownerId) {
        if (isUserScope(scope)) {
            if (isAdmin(userContext) || isOwner(userContext, ownerId)) {
                return null;
            }
            return "owner-or-admin";
        }
        // system 级：无登录态 = 内部调用（一期行为）；登录用户限管理员
        if (!hasLoginUser(userContext) || isAdmin(userContext)) {
            return null;
        }
        return "admin-required";
    }

    /**
     * 将空字符串归一为 null（saveCredential 的 scope/ownerId 可选输入语义）。
     */
    public static String normalizeEmptyToNull(String value) {
        return StringHelper.isEmpty(value) ? null : value;
    }

    /**
     * 管理员角色集合的字符串形式（错误 param 用）。
     */
    public static String adminRolesAsString(Collection<String> roles) {
        return String.join(",", roles);
    }
}
