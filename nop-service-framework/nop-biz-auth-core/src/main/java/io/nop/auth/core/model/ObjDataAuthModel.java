/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.model;

import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.model._gen._ObjDataAuthModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalScope;

import java.util.Set;

public class ObjDataAuthModel extends _ObjDataAuthModel {

    public ObjDataAuthModel() {

    }

    public void sort() {
        this.getRoleAuths().sort(RoleDataAuthModel::compareTo);
    }

    public RoleDataAuthModel getRoleAuth(IEvalScope scope, Set<String> roleIds, ISecurityContext context) {
        IUserContext userContext = context.getUserContext();

        for (RoleDataAuthModel roleAuth : this.getRoleAuths()) {
            if (isUserInRole(roleIds, userContext, roleAuth.getRoleIds())) {
                if (roleAuth.getWhen() == null || roleAuth.getWhen().passConditions(scope))
                    return roleAuth;
            }
        }
        return null;
    }

    private boolean isUserInRole(Set<String> roleIds, IUserContext userContext, Set<String> authRoleIds) {
        if (roleIds != null) {
            // 总是假定具有user角色
            if (authRoleIds.contains(AuthCoreConstants.ROLE_USER))
                return true;
            return CollectionHelper.containsAny(roleIds, authRoleIds);
        }
        return userContext.isUserInAnyRole(authRoleIds);
    }
}