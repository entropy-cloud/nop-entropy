/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.auth;

import java.util.Set;

/**
 * 操作权限检查：检查是否允许在业务实体上执行某个业务动作。
 */
public interface IActionAuthChecker {
    boolean isPermitted(String permission, IUserContext userContext);

    default boolean isAllPermitted(Set<String> permissions, IUserContext userContext) {
        if (permissions == null || permissions.isEmpty())
            return true;

        for (String permission : permissions) {
            if (!isPermitted(permission, userContext))
                return false;
        }
        return true;
    }
}