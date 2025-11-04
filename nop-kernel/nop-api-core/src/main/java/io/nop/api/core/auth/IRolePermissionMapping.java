/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.auth;

import java.util.Set;

public interface IRolePermissionMapping {
    /**
     * 返回允许访问指定权限的所有角色的列表
     *
     * @param permissions 权限标识
     * @return 角色列表
     */
    Set<String> getRolesWithPermission(Set<String> permissions);
}
