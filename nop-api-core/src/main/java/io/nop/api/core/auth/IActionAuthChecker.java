/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.auth;

import io.nop.api.core.util.MultiCsvSet;

import java.util.Set;

/**
 * 操作权限检查：检查是否允许在业务实体上执行某个业务动作。
 */
public interface IActionAuthChecker {
    boolean isPermitted(String permission, ISecurityContext context);

    default boolean isAllPermitted(Set<String> permissions, ISecurityContext context) {
        if (permissions == null || permissions.isEmpty())
            return true;

        for (String permission : permissions) {
            if (!isPermitted(permission, context))
                return false;
        }
        return true;
    }

    default boolean isPermissionSetSatisfied(MultiCsvSet permissionSet, ISecurityContext context) {
        if (permissionSet == null || permissionSet.isEmpty())
            return true;

        for (Set<String> permissions : permissionSet) {
            if (isAllPermitted(permissions, context))
                return true;
        }
        return false;
    }
}